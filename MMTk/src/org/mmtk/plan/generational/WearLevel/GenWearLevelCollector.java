package org.mmtk.plan.generational.WearLevel;

import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.generational.Gen;
import org.mmtk.plan.generational.GenCollector;
import org.mmtk.policy.CopyLocal;
import org.mmtk.utility.ForwardingWord;
import org.mmtk.utility.HeaderByte;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

@Uninterruptible
public class GenWearLevelCollector extends GenCollector {
  private final CopyLocal mature;
  private final GenWearLevelMatureTraceLocal matureTrace;

  public GenWearLevelCollector() {
    mature = new CopyLocal(GenWearLevel.toSpace());
    matureTrace = new GenWearLevelMatureTraceLocal(global().matureTrace, this);
  }

  @Override
  @Inline
  public Address allocCopy(ObjectReference original, int bytes, int align,
                           int offset, int allocator) {
    if (allocator == Plan.ALLOC_LOS) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Allocator.getMaximumAlignedSize(bytes, align) >
          Plan.MAX_NON_LOS_COPY_BYTES);
      return los.alloc(bytes, align, offset);
    } else {
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert(bytes <= Plan.MAX_NON_LOS_COPY_BYTES);
        VM.assertions._assert(allocator == GenWearLevel.ALLOC_MATURE_MINORGC ||
            allocator == GenWearLevel.ALLOC_MATURE_MAJORGC);
      }
      return mature.alloc(bytes, align, offset);
    }
  }

  @Override
  @Inline
  public final void postCopy(ObjectReference object, ObjectReference typeRef,
                             int bytes, int allocator) {
    ForwardingWord.clearForwardingBits(object);
    if (allocator == Plan.ALLOC_LOS)
      Plan.loSpace.initializeHeader(object, false);
    if (Gen.USE_OBJECT_BARRIER)
      HeaderByte.markAsUnlogged(object);
  }

  @Override
  public void collectionPhase(short phaseId, boolean primary) {
    if (global().traceFullHeap()) {
      if (phaseId == GenWearLevel.PREPARE) {
        super.collectionPhase(phaseId, primary);
        mature.rebind(GenWearLevel.toSpace());
      }
      if (phaseId == GenWearLevel.CLOSURE) {
        matureTrace.completeTrace();
        return;
      }
      if (phaseId == GenWearLevel.RELEASE) {
        matureTrace.release();
        super.collectionPhase(phaseId, primary);
        return;
      }
    }
    super.collectionPhase(phaseId, primary);
  }
  private static GenWearLevel global() {
    return (GenWearLevel) VM.activePlan.global();
  }

  @Override
  public final TraceLocal getFullHeapTrace() {
    return matureTrace;
  }
}
