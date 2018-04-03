package org.mmtk.policy;

import org.mmtk.utility.heap.VMRequest;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;

@Uninterruptible public class DummyCountingSpace extends CountingSpace {
  public DummyCountingSpace(String name, VMRequest vmRequest) {
    super(name, vmRequest);
  }

  @Override
  public boolean populateCounters(Extent len) {
    return true;
  }

  @Override
  public void setLimit(Extent limit) {

  }

  @Override
  public void grow() {

  }

  @Override
  public void grow(Space sp, Address addr, Extent bytes) {

  }

  @Override
  public Space getTargetSpace(Space ex) {
      return null;
  }

  @Override
  public void updateCounter(Address start, Address end) {

  }

  @Override
  public void updateCounter(Address slot) {

  }

  @Override
  public void dumpCounts() {

  }
}
