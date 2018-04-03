package org.mmtk.plan.generational.WearLevel;

import org.mmtk.plan.generational.copying.GenCopyConstraints;
import org.mmtk.policy.WearLevelCopySpace;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible public class GenWearLevelConstraints extends GenCopyConstraints {
  @Override
  public int gcHeaderBits() {
    return WearLevelCopySpace.LOCAL_GC_BITS_REQUIRED;
  }
}
