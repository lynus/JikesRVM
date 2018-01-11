package org.mmtk.policy;

import org.mmtk.plan.Plan;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.FileLog;
import org.mmtk.utility.Log;
import org.mmtk.utility.heap.CountingPageResource;
import org.mmtk.utility.heap.HeapGrowthManager;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.heap.layout.HeapLayout;
import org.mmtk.utility.heap.layout.Map64;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.*;

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

    public static Space getTargetSpace() {
        String name;
        for (Space sp : getSpaces()) {
            name = sp.getName();
            if (name != "boot" && name != "immortal" && name != "meta" && name != "los" && name != "sanity" && name != "non-moving"
                    && name != "sm-code" && name != "lg-code" && name != "vm" && name != "write-counter") {
                return sp;
            }
        }
        return null;
    }

    public void updateCounter(Address start, Address end) {
        if(VM.VERIFY_ASSERTIONS)
            VM.assertions._assert(isInSpace(Plan.targetSpace.getDescriptor(), start)
                    && isInSpace(Plan.targetSpace.getDescriptor(), end));
        Address base = ((Map64) HeapLayout.vmMap).getSpaceBaseAddress(Plan.targetSpace);
        start = start.toWord().and(Word.fromIntSignExtend(~7)).toAddress();
        end = end.plus(7).toWord().and(Word.fromIntSignExtend(~7)).toAddress();
        Offset from = start.diff(base);
        end  = this.start.plus(end.diff(base));
        if (VM.VERIFY_ASSERTIONS)
            VM.assertions._assert(end.LE(((CountingPageResource)pr).getLimit()));
        Address addr = this.start.plus(from);
        do {
            long val = addr.loadLong();
            val++;
            addr.store(val);
            addr = addr.plus(8);
        } while (addr.LT(end));
    }

    public void dumpCounts() {
        if (Options.verbose.getValue() >2) {
            Log.write("Start dumping write counts for space: ");
            Log.write(this.getName());
            Log.write(" length: ");
            Log.write(length.toLong() >> 20);
            Log.writeln(" MB");
        }
        Address end = start.plus(length).minus(1);
        Address addr = start;
        int thre = 0;
        int i = 0;
        long sum;
        while (addr.plus(4096).LE(end)) {
            sum = 0;
            for(i = 0; i < 4096/8; i++) {
                sum = sum + addr.loadLong();
                addr = addr.plus(8);
            }
            double mean = sum / 512;
            FileLog.write(mean, 1);
            FileLog.write(',');
            thre++;
            if (thre % 100 == 0) {
                FileLog.flush();
            }
        }

    }
}
