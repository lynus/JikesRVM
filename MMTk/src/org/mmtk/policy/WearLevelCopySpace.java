package org.mmtk.policy;

import org.mmtk.utility.heap.VMRequest;

public class WearLevelCopySpace extends CopySpace {
  public static final int LOCAL_GC_BITS_REQUIRED = 3;

  public WearLevelCopySpace(String name, boolean fromSpace,  VMRequest vmRequest) {
    super(name, fromSpace, vmRequest);
  }
}
