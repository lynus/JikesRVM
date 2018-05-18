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

import static org.mmtk.plan.generational.MyConfig.MAPTOOHOT;
import static org.mmtk.plan.generational.MyConfig.MONITORTYPE;
import static org.mmtk.plan.generational.MyConfig.WRITETYPE_ALL;
import static org.mmtk.policy.Space.isInSpace;

@Uninterruptible public class CardTable {
  protected static final int NUM_HOTTEST_CARD = 1024;
  protected static final double THRESHOLD_RATE = 0.5;
  private static final int LOG_MAX_NUM_CARDS = 20;
  protected static int NUM_CARDS;
  private static final int MAX_SELECT_CARD = 50;  // findhottest() only selects this many cards
  private static final long TOO_HOT_COUNT_THRESHOLD = MONITORTYPE != WRITETYPE_ALL? 5000L:10000L;
  private static final long MIN_SELECTED_CARD_COUNT = MONITORTYPE != WRITETYPE_ALL?10:1000; //selected card's write count be greater than this
  private static Lock lock;
  private static final int LOG_CARD_UNIT = 13; //8K
  private static final int LOG_CARD_SIZE = 3;

  private static final int LOG_2ND_CARD_UNIT = 4;   //each 2nd card correspond to 512 1st card, aka 4M heap
  private static final int LOG_2ND_CARD_SIZE = 0;   //echo 2nd card element is 1 byte
  private static final byte DIRTY = 1;
  private static final byte CLEAN = 0;
  private static  int NUM_2ND_CARDS;

  private static Space targetSpace;
  private static Address targetSpaceBase = Address.zero();
  private static Mapper mapper;
  private MutatorContext mutator;
  private Address start, candidateStart, start_2nd;
  private Random rand;
  public long writeCount;
  public long min;
  public CardTable(MutatorContext mutator) {
    this.mutator = mutator;
    start = Address.zero();
    rand = new Random();
  }

  static {
    setTargetSpace();
  }

  private void init() {
    start = getPages(LOG_CARD_SIZE);
    start_2nd = getPages(LOG_2ND_CARD_SIZE);
    initCandidateArray();
    rand.init();
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
      Log.write("NUM_CARDS:");
      Log.writeln(NUM_CARDS);
      NUM_2ND_CARDS = (NUM_CARDS + (1 << LOG_2ND_CARD_UNIT) - 1) >> LOG_2ND_CARD_UNIT;
      Log.write("NUM_2nd_CARDS:");
      Log.writeln(NUM_2ND_CARDS);
      if (NUM_CARDS >= 1 << LOG_MAX_NUM_CARDS)
        VM.assertions.fail("NUM CARDS too large!");
    }
    Address ret = Plan.cardTableSpace.allocPages(Conversions.bytesToPagesUp(Extent.fromIntZeroExtend(NUM_CARDS << log_size)));
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(!ret.isZero());
    return ret;
  }

  private void initCandidateArray() {
    candidateStart = Plan.cardTableSpace.allocPages((Conversions.bytesToPagesUp(Extent.fromIntZeroExtend(MAX_SELECT_CARD << 2))));
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(!candidateStart.isZero());
  }
  @Inline
  public void inc(Address slot) {
    if (!slot.isZero()) {
      if (isInSpace(targetSpace.getDescriptor(), slot)) {
        if (start.isZero()) {
          init();
        }
        writeCount++;
        int card = slot.diff(targetSpaceBase).toWord().rshl(LOG_CARD_UNIT).toInt();
        Offset offset = Offset.fromIntZeroExtend(card << LOG_CARD_SIZE);
        // is the following load-inc-store compiled by opt compiler efficient?
        long val = start.plus(offset).loadLong();
        if (val == Mapper.MARK_TODRAM)
          return;
        val++;
        start.plus(offset).store(val);
        int card_2nd = card >> LOG_2ND_CARD_UNIT;
        start_2nd.plus(card_2nd).store(DIRTY);
      }
    }
  }

  @Inline
  private long min(long x, long y) {
    return x < y ? x : y;
  }

  @Inline
  private long max(long x, long y) {
    return x > y ? x : y;
  }

  @Inline
  public void inc(Address start, Address end) {
    if (!isInSpace(targetSpace.getDescriptor(), start)
        || !isInSpace(targetSpace.getDescriptor(), end))
      return;
    if (start.isZero()) {
      init();
    }
    writeCount += end.diff(start).toLong();
    long s = start.diff(targetSpaceBase).toLong();
    long e = end.diff(targetSpaceBase).toLong();
    long i =  s;
    long j = min(e, ((i + (1 << LOG_CARD_UNIT) - 1) >> LOG_CARD_UNIT) << LOG_CARD_UNIT);
    long value;
    int card = (int)(i >> LOG_CARD_UNIT);
    int card_2nd = card >> LOG_2ND_CARD_UNIT;
    Address addr = start.plus(card << LOG_CARD_SIZE);
    value = addr.loadLong();
    if (value != Mapper.MARK_TODRAM) {
      value += j - i;
      addr.store(value);
      start_2nd.plus(card_2nd).store(DIRTY);
    }
    while (j != e) {
      i = j;
      j = min(e, i + 1 << LOG_CARD_UNIT);
      card = (int)(i >> LOG_CARD_UNIT);
      addr = start.plus(card << LOG_CARD_SIZE);
      value = addr.loadLong();
      if (value != Mapper.MARK_TODRAM) {
        value += j - i;
        addr.store(value);
        card_2nd = card >> LOG_2ND_CARD_UNIT;
        start_2nd.plus(card_2nd).store(DIRTY);
      }
    }
  }

  public int findHottest(WriteBuffer buffer) {
    int  selected = 0, nonzero = 0, tried;
    long val, _v, min = 99999999, loops=0;
    Address addr;
    for (int i = 0; i < NUM_CARDS; i++) {
      loops++;
      addr = start.plus(i << LOG_CARD_SIZE);
      val = addr.loadLong();
      if (val != 0)
        nonzero++;
      if (val >= TOO_HOT_COUNT_THRESHOLD) {
        mapper.mapToDRAM(i);
        continue;
      }
      if (val >= MIN_SELECTED_CARD_COUNT) {
        if (selected < MAX_SELECT_CARD) {
          candidateStart.plus(selected << 2).store(i);
          selected++;

          if (min > val)
            min = val;
        } else {  
          //candidate array is exhausted. remaining item has to
          // randomly select a place and replace the old one.
          tried = 0;
          while (tried < 10) {
            int card, randplace;
            randplace = rand.nextInt(MAX_SELECT_CARD) << 2;
            card = candidateStart.plus(randplace).loadInt();
            _v = start.plus(card << LOG_CARD_SIZE).loadLong();
            if (_v < val) {
              candidateStart.plus(randplace).store(i);
              if (min > val)
                min = val;
              break;
            }
            tried++;
          }
        }
      }
    }
    for (int i = 0; i < selected; i++) {
      int card;
      card = candidateStart.plus(i << 2).loadInt();
      buffer.insert(Address.fromIntZeroExtend(card));
      start.plus(card << LOG_CARD_SIZE).store(0L);
    }
    Log.write("findHottest mutator ");
    Log.write(mutator.getId());
    Log.write(" select ");
    Log.write(selected);
    Log.write(" min count ");
    Log.write(min);
    Log.write(" nonzero ");
    Log.write(nonzero);
    Log.write(" loops");
    Log.writeln(loops);
    return selected;
  }

  public int findHottest_1(WriteBuffer buffer) {
    int selected = 0, nonzero = 0, nonzero_2nd = 0, tried;
    long val, min=9999999;
    byte dirty;
    Address addr;
    long select_thr = MIN_SELECTED_CARD_COUNT;
    for (int card_2nd = 0; card_2nd < NUM_2ND_CARDS; card_2nd++) {
      dirty = start_2nd.plus(card_2nd).loadByte();
      if (dirty == CLEAN)
        continue;
      nonzero_2nd++;
      start_2nd.plus(card_2nd).store(CLEAN);
      for (int i = card_2nd << LOG_2ND_CARD_UNIT;
           i < (card_2nd + 1) << LOG_2ND_CARD_UNIT; i++) {
        addr = start.plus(i << LOG_CARD_SIZE);
        val = addr.loadLong();
        if (val != 0)
          nonzero++;
        if (MAPTOOHOT && val >= TOO_HOT_COUNT_THRESHOLD) {
          mapper.mapToDRAM(i);
          continue;
        }
        if (val >= select_thr) {
          if (selected < MAX_SELECT_CARD) {
            candidateStart.plus(selected << 2).store(i);
            selected++;
            if (min > val)
              min = val;
          } else {
            tried = 0;
            while (tried < 10) {
              int card, randplace;
              long _v;
              randplace = rand.nextInt(MAX_SELECT_CARD) << 2;
              card = candidateStart.plus(randplace).loadInt();
              _v = start.plus(card << LOG_CARD_SIZE).loadLong();
              if (_v < val) {
                candidateStart.plus(randplace).store(i);
                select_thr = val;
                if (min > val)
                  min =val;
                break;
              }
              tried++;
            }
          }
        }
      }
    }
    for (int i = 0; i < selected; i++) {
      int card;
      card = candidateStart.plus(i << 2).loadInt();
      buffer.insert(Address.fromIntZeroExtend(card));
      start.plus(card << LOG_CARD_SIZE).store(0L);
    }
    Log.write("findHottest mutator ");
    Log.write(mutator.getId());
    Log.write(" select ");
    Log.write(selected);
    Log.write(" min count ");
    Log.write(min);
    Log.write(" nonzero ");
    Log.write(nonzero);
    Log.write(" 2nd nonzero ");
    Log.writeln(nonzero_2nd);
    return selected;
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
    private static final int LOG_ELEMENT_SIZE = 2; // 4bytes
    private static final int MARK_SHIFT = LOG_MAX_NUM_CARDS;
    private static final int MARK_MASK = ~((1 << MARK_SHIFT) -1);
    private static final long OFFSET_MASK = (1L << LOG_CARD_UNIT) - 1L;
    private static final int MARK_TODRAM = 1 << 31;
    private static int numDRAM = 0;
    private int currentMapMark;

    private int calledTimes;
    private int mapTimes;
    private int randomRetries;

    private Address start = Address.zero();
    private Address exchange1 = Address.zero();
    private Address exchange2 = Address.zero();
    private Random rand = new Random();
    public boolean ready = false;
    public void init() {
      this.start = getPages(LOG_ELEMENT_SIZE);

      this.exchange1 = Plan.cardTableSpace.allocPages(20);
      this.exchange2 = Plan.cardTableSpace.allocPages(20);
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert(!start.isZero());
        VM.assertions._assert(!exchange1.isZero() && !exchange2.isZero());
      }
      for (int i = 0; i < NUM_CARDS; i++) {
        write(i, i);
      }
      rand.init();
      mapper = this;
    }
    public void prepare() {
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

    private void mapToDRAM(int card) {
      if (isInDRAM(card))
        return;
      write(card, MARK_TODRAM);
      numDRAM++;
    }

    @Inline
    private boolean isInDRAM(int card) {
      return (read(card) == MARK_TODRAM);
    }

    //before: card-->X     to-->Y
    //after:  card-->Y     to-->X
    public int map(int card) {
      calledTimes++;
      int to, X, Y;
      X = read(card);
      if (X == MARK_TODRAM) {
        return -1;
      }
      if ((X & MARK_MASK) == currentMapMark) {
        return -1;
      }
      mapTimes++;
      to = rand.nextInt(NUM_CARDS);
      Y = read(to);
      while ((Y & MARK_MASK) == currentMapMark || Y == MARK_TODRAM) {
        to = rand.nextInt(NUM_CARDS);
        Y = read(to);
        randomRetries++;
      }
      Address a = targetSpaceBase.plus((X << LOG_CARD_UNIT) & ((1<<24)-1));
      Address b = targetSpaceBase.plus((Y << LOG_CARD_UNIT) & ((1<<24)-1));
      Log.write("map: a ");
      Log.write(a);
      Log.write(" b ");
      Log.writeln(b);
      VM.memory.pageCopy(exchange1, a, 20);
      VM.memory.pageCopy(exchange2, b, 20);
      X = (X & ~MARK_MASK) | currentMapMark;
      Y = (Y & ~MARK_MASK) | currentMapMark;
      write(card, Y);
      write(to, X);
      return to;
    }

    @Inline
    public Offset translate(Offset slot) {

      int card = (int)(slot.toLong() >> LOG_CARD_UNIT);
      int to = read(card);
      if (to == MARK_TODRAM)
        return Offset.fromIntSignExtend(-1);
      to = to & (~MARK_MASK);
      long base = (long)(to << LOG_CARD_UNIT);
      return Offset.fromLong(base + (slot.toLong() & OFFSET_MASK));
    }

    public void printSummery(int gcCount) {
      if (start != Address.zero()) {
        Log.write("CardTable: calledTimes ");
        Log.write(calledTimes);
        Log.write(" mapTimes ");
        Log.write(mapTimes);
        Log.write(" mean ");
        Log.write((double) (mapTimes / gcCount));
        Log.write(" mapToDRAM ");
        Log.writeln(numDRAM);
      }
    }
  }

}
