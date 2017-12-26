package org.mmtk.policy;

import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.Log;
import org.mmtk.utility.heap.CountingPageResource;
import org.mmtk.utility.heap.HeapGrowthManager;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.ObjectReference;

@Uninterruptible public class CountingSpace extends Space {
    private Extent length;
    public CountingSpace(String name, VMRequest vmRequest) {
        super(name, false, true, true, vmRequest);
        pr = new CountingPageResource(this, start);
        length = Extent.zero();
    }

    @Override
    public void release(Address start) {
        VM.assertions.fail("will never be called!");
    }

    @Override
    public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object) {
        VM.assertions.fail("will never be called!");
        return null;
    }

    @Override
    public boolean isLive(ObjectReference object) {
        VM.assertions.fail("will never be called!");
        return false;
    }

    public boolean populateCounters(Extent len) {
        int pages = Conversions.bytesToPages(len);
        if (pr.getNewPages(pages, pages, true).isZero())
            return false;
        this.length = len;
        return true;
    }

    public void setLimit(Extent limit) {
        ((CountingPageResource)pr).setLimit(limit);
    }


    public void grow() {
        Extent currentHeap = HeapGrowthManager.getCurrentHeapSize();
        if (length.LT(currentHeap)) {
            int pages = Conversions.bytesToPages(currentHeap.minus(length));
            if (pr.getNewPages(pages, pages, true).isZero())
                VM.assertions.fail("Counting Space grow fail!");
            length = currentHeap;
            if (Options.verbose.getValue() > 2) {
                Log.write("Counter space grows to ");
                Log.write(length.toLong() >> 20);
                Log.writeln(" MB");
            }

        }
    }

    public static int getTargetSpace() {
        String name;
        for (Space sp : getSpaces()) {
            name = sp.getName();
            if (name != "boot" && name != "immortal" && name != "meta" && name != "los" && name != "sanity" && name != "non-moving"
                    && name != "sm-code" && name != "lg-code") {
                return sp.getDescriptor();
            }
        }
        return -1;
    }
}
