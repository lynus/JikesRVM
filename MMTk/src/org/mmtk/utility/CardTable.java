package org.mmtk.utility;

import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.Plan;
import org.mmtk.policy.Space;
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
  private static Lock lock;
  private static final int LOG_CARD_SIZE = 13; //8K
  private static final int CARD_SIZE = 1 << LOG_CARD_SIZE;
  private static Space targetSpace;
  private static Address targetSpaceBase = Address.zero();
  private static Space backingSpace;
  private static Extent length;

  private MutatorContext mutator;
  private Address start;
  public long writeCount;
  public CardTable(MutatorContext mutator) {
    this.mutator = mutator;
    start = Address.zero();
  }

  static {
    setTargetSpace();
    backingSpace = Plan.cardTableSpace;
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

  private Address getPages() {
    if (length.EQ(Extent.zero()))
      length = Plan.getMaxMemory().toWord().rshl(LOG_CARD_SIZE).toExtent().plus(1);
    Address ret = backingSpace.acquire(Conversions.bytesToPagesUp(length));
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(!ret.isZero());
    return ret;
  }

  @Inline
  public void inc(Address slot) {
    if (!slot.isZero()) {
      if (isInSpace(targetSpace.getDescriptor(), slot)) {
        if (start.isZero()) {
          start = getPages();
        }
        writeCount++;
        Offset offset = slot.diff(targetSpaceBase).toWord().rshl(LOG_CARD_SIZE).toOffset();
        // is the following load-inc-store compiled by opt compiler efficient?
        Address card = start.plus(offset);
        byte val = card.loadByte();
        val++;
        card.store(val);
      }
    }
  }
}
