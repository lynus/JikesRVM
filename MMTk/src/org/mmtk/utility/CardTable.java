package org.mmtk.utility;

import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.Plan;
import org.mmtk.policy.Space;
import org.mmtk.utility.deque.WriteBuffer;
import org.mmtk.utility.heap.layout.HeapLayout;
import org.mmtk.utility.heap.layout.Map64;
import org.mmtk.vm.Lock;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Offset;
import static org.mmtk.policy.Space.isInSpace;

@Uninterruptible public class CardTable {
  protected static final int NUM_HOTTEST_CARD = 512;
  protected static final double THRESHOLD_RATE = 0.5;
  private static final int LOG_MAX_NUM_CARDS = 20;
  protected static int NUM_CARDS;
  private static Lock lock;
  private static final int LOG_CARD_UNIT = 13; //8K
  private static final int LOG_CARD_SIZE = 3;
  private static Space targetSpace;
  private static Address targetSpaceBase = Address.zero();
  private MutatorContext mutator;
  private Address start;
  public long writeCount;
  public long min;
  public CardTable(MutatorContext mutator) {
    this.mutator = mutator;
    start = Address.zero();
  }

  static {
    setTargetSpace();
  }

  public static void setTargetSpace() {
    String name;
    Space ret = null;
    for (Space sp : Space.getSpaces()) {
      name = sp.getName();
      if (name != "boot" && name != "immortal" && name != "meta" && name != "los" &&
          name != "sanity" && name != "non-moving" && name != "sm-code" && name != "lg-code" &&
          name != "vm" && name != "write-counter" && name != "nursery" && name != "cardtable") {
        ret = sp;
        break;
      }
    }
    if (ret != null) {
      Log.write("CardTable getTargetSpace: ");
      Log.writeln(ret.getName());
      Log.writeln("target space base address: ", ((Map64) HeapLayout.vmMap).getSpaceBaseAddress(ret));
    } else
      VM.assertions.fail("CardTable failed to get target space!");
    targetSpace = ret;
    targetSpaceBase = ((Map64)HeapLayout.vmMap).getSpaceBaseAddress(targetSpace);
  }

  private static  Address getPages(int log_size) {
    if (NUM_CARDS == 0) {
      NUM_CARDS = Plan.getMaxMemory().toWord().rshl(LOG_CARD_UNIT).toInt();
      if (NUM_CARDS >= 1 << LOG_MAX_NUM_CARDS)
        VM.assertions.fail("NUM CARDS too large!");
    }

    Address ret = Plan.cardTableSpace.acquire(Conversions.bytesToPagesUp(Extent.fromIntZeroExtend(NUM_CARDS << log_size)));

    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(!ret.isZero());
    return ret;
  }

  @Inline
  public void inc(Address slot) {
    if (!slot.isZero()) {
      if (isInSpace(targetSpace.getDescriptor(), slot)) {
        if (start.isZero()) {
          start = getPages(LOG_CARD_SIZE);
        }
        writeCount++;
        int card = slot.diff(targetSpaceBase).toWord().rshl(LOG_CARD_UNIT).toInt();
        Offset offset = Offset.fromIntZeroExtend(card << LOG_CARD_SIZE);
        // is the following load-inc-store compiled by opt compiler efficient?
        long val = start.plus(offset).loadLong();
        val++;
        start.plus(offset).store(val);

      }
    }
  }

  @Inline
  private long min(long x, long y) {
    return x < y ? x : y;
  }

  @Inline
  public void inc(Address start, Address end) {
    if (!isInSpace(targetSpace.getDescriptor(), start)
        || !isInSpace(targetSpace.getDescriptor(), end))
      return;
    if (start.isZero())
      this.start = getPages(LOG_CARD_SIZE);
    writeCount += end.diff(start).toLong();
    long s = start.diff(targetSpaceBase).toLong();
    long e = end.diff(targetSpaceBase).toLong();
    long i =  s;
    long j = min(e, ((i + (1 << LOG_CARD_UNIT) - 1) >> LOG_CARD_UNIT) << LOG_CARD_UNIT);
    long value;
    Address card = Address.fromLong((i >> LOG_CARD_UNIT) << LOG_CARD_SIZE);
    value = card.loadLong();
    value += j - i;
    card.store(value);
    while (j != e) {
      i = j;
      j = min(e, i + 1 << LOG_CARD_UNIT);
      card = Address.fromLong((i >> LOG_CARD_UNIT) << LOG_CARD_SIZE);
      value = card.loadLong();
      value += j - i;
      card.store(value);
    }
  }

  public int findHottest(WriteBuffer buffer) {
    int ret = 0;
    long min = 999999L;
    long threshold = (long)(writeCount * THRESHOLD_RATE / NUM_HOTTEST_CARD);
    long val;
    for (int i = 0; i < NUM_CARDS; i++) {
     val = start.plus(i << LOG_CARD_SIZE).loadLong();
      if (val >= threshold) {
        buffer.insert(Address.fromIntZeroExtend(i));
        ret++;
        if (val < min)
          min = val;
      }
    }
    this.min = min;
    return ret;
  }

  @Inline
  public void clearCardTable() {
    final long z = 0L;
    for (int i = 0; i < NUM_CARDS; i++) {
      start.plus(i << LOG_CARD_SIZE).store(z);
    }
  }
  @Uninterruptible private static class Random {
    private long seed;
    public void init() {
      setSeed(VM.statistics.nanoTime());
    }

    private void setSeed(long seed) {
      this.seed = (seed ^ 0x5DEECE66DL) & ((1L << 48) - 1);
    }

    public int next(int bits) {
      seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
      return (int) (seed >>> (48 - bits));
    }

    public int nextInt(int n) {
      if (n <= 0)
        VM.assertions.fail("n must be positive");
      if ((n & -n) == n)
        return (int)((n * (long)next(31)) >> 31);
      int bits, val;
      do {
        bits = next(31);
        val = bits % n;
      } while (bits - val + (n - 1) < 0);
      return val;
    }
  }
  @Uninterruptible public static class Mapper {
    private final int LOG_ELEMENT_SIZE = 2; // 4bytes
    private final int MARK_SHIFT = LOG_MAX_NUM_CARDS;
    private final int MARK_MASK = ~((1 << MARK_SHIFT) -1);
    private final long OFFSET_MASK = (1L << LOG_CARD_UNIT) - 1L;
    private int currentMapMark;

    private int calledTimes;
    private int mapTimes;
    private int randomRetries;

    private Address start = Address.zero();
    private Random rand = new Random();

    private void init() {
      this.start = getPages(LOG_ELEMENT_SIZE);
      if (VM.VERIFY_ASSERTIONS)
        VM.assertions._assert(!start.isZero());
      for (int i = 0; i < NUM_CARDS; i++) {
        write(i, i);
      }
      rand.init();
    }
    public void prepare() {
      calledTimes = 0;
      mapTimes = 0;
      randomRetries = 0;
      setCurrentMapMark();
    }
    private void setCurrentMapMark() {
      this.currentMapMark = rand.next(12) << MARK_SHIFT;
    }

    @Inline
    private int read(int card) {
      return start.plus(card << LOG_ELEMENT_SIZE).loadInt();
    }

    @Inline
    private void write(int card, int val) {
      start.plus(card << LOG_ELEMENT_SIZE).store(val);
    }

    //before: card-->X     to-->Y
    //after:  card-->Y     to-->X
    public int map(int card) {
      if (start == Address.zero())
        init();
      calledTimes++;
      int to, X, Y;
      X = read(card);
      if ((X & MARK_MASK) == currentMapMark) {
//        Log.write("CardTable:map() card ");
//        Log.write(card);
//        Log.writeln(" already mapped. skip");
        return -1;
      }
      mapTimes++;
      to = rand.nextInt(NUM_CARDS);
      Y = read(to);
      while ((Y & MARK_MASK) == currentMapMark) {
//        Log.write("CardTable:map() card ");
//        Log.write(to);
//        Log.writeln("has been mapped by previous map. retry");
        to = rand.nextInt(NUM_CARDS);
        Y = read(to);
        randomRetries++;
      }

      X = (X & ~MARK_MASK) | currentMapMark;
      Y = (Y & ~MARK_MASK) | currentMapMark;
      write(card, Y);
      write(to, X);
      return to;
    }

    @Inline
    public Offset translate(Offset slot) {
      if (start == Address.zero())
        init();
      int card = (int)(slot.toLong() >> LOG_CARD_UNIT);
      int to = read(card);
      to = to & (~MARK_MASK);
      long base = (long)(to << LOG_CARD_UNIT);
      return Offset.fromLong(base + (slot.toLong() & OFFSET_MASK));
    }

    public void printSummery() {
      Log.write("CardTable: calledTimes ");
      Log.write(calledTimes);
      Log.write(" mapTimes ");
      Log.write(mapTimes);
      Log.write(" random retries ");
      Log.writeln(randomRetries);
    }
  }

}
