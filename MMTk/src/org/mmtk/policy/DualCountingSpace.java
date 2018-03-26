package org.mmtk.policy;

import org.mmtk.utility.FileLog;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;

@Uninterruptible public class DualCountingSpace extends CountingSpace {
    private CountingSpace sp0, sp1;
    private boolean forNursery;
    public DualCountingSpace(String name, VMRequest vmRequest) {
        super(name, vmRequest);
        sp0 = new CountingSpace("counter0", VMRequest.discontiguous());
        sp1 = new CountingSpace("counter1", VMRequest.discontiguous());
    }

    @Override
    public Space getTargetSpace(Space exclude) {
        Space t0 = sp0.getTargetSpace(null);
        if (!forNursery) {
            Space t1 = sp1.getTargetSpace(t0);
            if (VM.VERIFY_ASSERTIONS) {
                VM.assertions._assert(t1 != null);
            }
        }
        return t0;
    }

    @Override
    public boolean populateCounters(Extent len) {
        forNursery = Options.nurseryCountWrite.getValue();
        if (forNursery)
            return sp0.populateCounters(len);
        else {
            return sp0.populateCounters(Extent.fromLong(len.toLong() >> 1)) &&
                sp1.populateCounters(Extent.fromLong(len.toLong() >> 1));
        }
    }

    @Override
    public void grow(Space space, Address addr, Extent bytes) {
        sp0.grow(space, addr, bytes);
        if(!forNursery)
            sp1.grow(space, addr, bytes);
    }

    @Override
    public void updateCounter(Address start, Address end) {
        VM.assertions.fail("DualCountingSpace shouldn't be called this method");
    }

    @Override
    public void updateCounter(Address slot) {
        VM.assertions.fail("DualCountingSpace shouldn't be called this method");

    }

    public void updateCounter(Space target, Address addr, Address end) {
        if (target == sp0.targetSpace)
            sp0.updateCounter(addr, end);
        else if (!forNursery && target == sp1.targetSpace)
            sp1.updateCounter(addr, end);
    }

    public void updateCounter(Space target, Address slot) {
        if (target == sp0.targetSpace)
            sp0.updateCounter(slot);
        else if (!forNursery && target == sp1.targetSpace)
            sp1.updateCounter(slot);
    }

    @Override
    public void dumpCounts() {
        sp0.dumpCounts();
        if (!forNursery) {
            FileLog.writeln();
            sp1.dumpCounts();
        }
    }

}
