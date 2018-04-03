package org.mmtk.plan.generational.WearLevel;

import org.mmtk.plan.Trace;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.plan.generational.Gen;

import org.mmtk.policy.CopySpace;
import org.mmtk.policy.DualCountingSpace;
import org.mmtk.policy.Space;
import org.mmtk.policy.WearLevelCopySpace;
import org.mmtk.utility.heap.VMRequest;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;

@Uninterruptible public class GenWearLevel extends Gen {
  static boolean hi = false;
  static WearLevelCopySpace matureSpace0 = new WearLevelCopySpace("ss0", false, VMRequest.discontiguous());
  static WearLevelCopySpace matureSpace1 = new WearLevelCopySpace("ss1", true, VMRequest.discontiguous());
  static final int MS0 = matureSpace0.getDescriptor();
  static final int MS1 = matureSpace1.getDescriptor();

  final Trace matureTrace;
  public GenWearLevel() {
    super();
    matureTrace = new Trace(metaDataSpace);
  }

  @Override
  protected boolean copyMature() {
    return true;
  }

  static CopySpace toSpace() {
    return hi? matureSpace1 : matureSpace0;
  }

  static int toSpaceDesc() {
    return hi? MS1 : MS0;
  }

  static CopySpace fromSpace() {
    return hi? matureSpace0 : matureSpace1;
  }

  static int fromSpaceDesc() {
    return hi? MS0 : MS1;
  }

  @Override
  @Inline
  public void collectionPhase(short phaseId) {
    if (traceFullHeap()) {
      if (phaseId == PREPARE) {
        super.collectionPhase(phaseId);
        hi = !hi; // flip the semi-spaces
        matureSpace0.prepare(hi);
        matureSpace1.prepare(!hi);
        matureTrace.prepare();
        return;
      }
      if (phaseId == CLOSURE) {
        matureTrace.prepare();
        return;
      }
      if (phaseId == RELEASE) {
        matureTrace.release();
        fromSpace().release();
        super.collectionPhase(phaseId);
        return;
      }
    }
    super.collectionPhase(phaseId);
  }

  @Override
  @Inline
  public int getPagesUsed() {
    return toSpace().reservedPages() + super.getPagesUsed();
  }

  @Override
  public final int getCollectionReserve() {
    // we must account for the number of pages required for copying,
    // which equals the number of semi-space pages reserved
    return toSpace().reservedPages() + super.getCollectionReserve();
  }

  @Override
  public int getMaturePhysicalPagesAvail() {
    // GenCopy divides the following by 2
    return toSpace().availablePhysicalPages();
  }

  @Override
  protected Space activeMatureSpace() {
    return toSpace();
  }

  @Override
  @Interruptible
  protected void registerSpecializedMethods() {
    TransitiveClosure.registerSpecializedScan(SCAN_MATURE, GenWearLevelMatureTraceLocal.class);
    super.registerSpecializedMethods();
  }

  @Override
  public boolean hasSemiSpace() {
    return true;
  }

  @Override
  public void updateWriteCountRange(Address start, Address end) {
    ((DualCountingSpace)counterSpace).updateCounter(toSpace(), start, end);
  }

  @Override
  public void updateWriteCount(Address slot) {
    ((DualCountingSpace)counterSpace).updateCounter(toSpace(), slot);
  }
}
