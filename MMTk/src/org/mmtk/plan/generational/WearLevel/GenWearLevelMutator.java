package org.mmtk.plan.generational.WearLevel;

import org.mmtk.plan.generational.GenMutator;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

@Uninterruptible public class GenWearLevelMutator extends GenMutator {
  private final CopyLocal mature;   //pretenure object to mature space

  public GenWearLevelMutator() {
    mature = new CopyLocal();
  }

  @Override
  public void initMutator(int id) {
    super.initMutator(id);
    mature.rebind(GenWearLevel.toSpace());
  }

  @Override
  @Inline
  public final Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == GenWearLevel.ALLOC_MATURE)
      return mature.alloc(bytes, align, offset);
    return super.alloc(bytes, align, offset, allocator, site);
  }

  @Override
  public final Allocator getAllocatorFromSpace(Space space) {
    if (space == GenWearLevel.matureSpace0 || space == GenWearLevel.matureSpace1)
      return mature;
    return super.getAllocatorFromSpace(space);
  }

  @Override
  public void collectionPhase(short phaseId, boolean primary) {
    if (global().traceFullHeap()) {
      if (phaseId == GenWearLevel.RELEASE) {
        super.collectionPhase(phaseId, primary);
        if (global().gcFullHeap)
          mature.rebind(GenWearLevel.toSpace());
        return;
      }
    }
    super.collectionPhase(phaseId, primary);
  }

  private static GenWearLevel global() {
    return (GenWearLevel) VM.activePlan.global();
  }

  @Override
  public void intWriteCount(ObjectReference object, Address slot) {
    global().updateWriteCount(object, slot);
  }
}
