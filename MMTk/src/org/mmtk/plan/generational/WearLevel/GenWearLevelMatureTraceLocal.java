package org.mmtk.plan.generational.WearLevel;

import org.mmtk.plan.Trace;
import org.mmtk.plan.generational.Gen;
import org.mmtk.plan.generational.GenCollector;
import org.mmtk.plan.generational.GenMatureTraceLocal;
import org.mmtk.policy.Space;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;

@Uninterruptible public class GenWearLevelMatureTraceLocal extends GenMatureTraceLocal {
  public GenWearLevelMatureTraceLocal(Trace global, GenCollector plan) {
    super(global, plan);
  }
  private static GenWearLevel global() {
    return (GenWearLevel) VM.activePlan.global();
  }

  @Override
  public ObjectReference traceObject(ObjectReference object) {
    if (object.isNull()) return object;
    if (Space.isInSpace(GenWearLevel.MS0, object))
      return GenWearLevel.matureSpace0.traceObject(this, object, Gen.ALLOC_MATURE_MAJORGC);
    if (Space.isInSpace(GenWearLevel.MS1, object))
      return GenWearLevel.matureSpace1.traceObject(this, object, Gen.ALLOC_MATURE_MAJORGC);
    return super.traceObject(object);
  }

  @Override
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (Space.isInSpace(GenWearLevel.MS0, object))
      return GenWearLevel.hi ? GenWearLevel.matureSpace0.isLive(object) : true;
    if (Space.isInSpace(GenWearLevel.MS1, object))
      return GenWearLevel.hi ? true : GenWearLevel.matureSpace1.isLive(object);
    return super.isLive(object);
  }

  @Override
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    if (Space.isInSpace(GenWearLevel.toSpaceDesc(), object))
      return true;
    if (Space.isInSpace(GenWearLevel.fromSpaceDesc(), object))
      return false;
    return super.willNotMoveInCurrentCollection(object);
  }
}
