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
import org.vmmagic.pragma.Inline;
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
        if(Options.verbose.getValue() > 1)
            Log.writeln("Counting space initial length: ", length);
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
            if (Options.verbose.getValue() > 1) {
                Log.write("Counter space grows to ");
                Log.write(length.toLong() >> 20);
                Log.write(" MB");
                Log.write((length.toLong() >>> 10) & ((1 << 10)-1));
                Log.writeln(" KB");
            }

        }
    }

    public void grow(Address addr, Extent bytes) {
        Address base = ((Map64) HeapLayout.vmMap).getSpaceBaseAddress(Plan.targetSpace);
        Extent newleng = addr.plus(bytes).diff(base).toWord().toExtent();
        if (newleng.GT(length)) {
            int pages = Conversions.bytesToPagesUp(newleng.minus(length));
            pr.getNewPages(pages, pages, true);
            length = newleng;
            if (Options.verbose.getValue() > 3) {
                Log.write("Counter space grows to ");
                Log.writeln(start.plus(newleng));
            }
        }

    }

    public static Space getTargetSpace() {
        String name;
        boolean getNursery = Options.nurseryCountWrite.getValue();
        Space ret = null;
        for (Space sp : getSpaces()) {
            name = sp.getName();
            if (getNursery && name == "nursery") {
                ret = sp;
                break;
            }
            if (!getNursery && name != "boot" && name != "immortal" && name != "meta" && name != "los" &&
                    name != "sanity" && name != "non-moving" && name != "sm-code" && name != "lg-code" &&
                    name != "vm" && name != "write-counter" && name != "nursery") {
                ret = sp;
                break;
            }
        }
        if (ret != null) {
            if (Options.verbose.getValue() > 1) {
                Log.writeln("target space base address: ", ((Map64) HeapLayout.vmMap).getSpaceBaseAddress(ret));
            }
        } else if (Options.verbose.getValue() > 1) {
            Log.writeln("failed to get target space!");
        }

        return ret;
    }
    public void updateCounter(Address start, Address end) {
        if (!isInSpace(Plan.targetSpace.getDescriptor(), start)
            || !isInSpace(Plan.targetSpace.getDescriptor(), end))
            return;
        if (Options.verbose.getValue() > 4) {
            Log.write("updateCount [");
            Log.write(start);
            Log.write(", ");
            Log.write(end);
            Log.writeln(']');
        }
        Address base = ((Map64) HeapLayout.vmMap).getSpaceBaseAddress(Plan.targetSpace);
        start = start.toWord().and(Word.fromIntSignExtend(~7)).toAddress();
        end = end.plus(7).toWord().and(Word.fromIntSignExtend(~7)).toAddress();
        Offset from = start.diff(base);
        end  = this.start.plus(end.diff(base));
        if (VM.VERIFY_ASSERTIONS) {
            VM.assertions._assert(end.LE(this.start.plus(length)));
        }
        Address addr = this.start.plus(from);
        do {
            long val = addr.loadLong();
            val++;
            addr.store(val);
            addr = addr.plus(8);
        } while (addr.LT(end));
    }
    @Inline
    public void updateCounter(Address slot) {
        if (slot.isZero()) {
            Log.writeln("updateCounter encounter Zero slot!");
            return;
        }
        if (!isInSpace(Plan.targetSpace.getDescriptor(), slot)) {
            return;
        }
        if (Options.verbose.getValue() > 4) {
            Log.write("updateCounter slot:");
            Log.writeln(slot);
        }
        Address base = ((Map64) HeapLayout.vmMap).getSpaceBaseAddress(Plan.targetSpace);
        slot = slot.toWord().and(Word.fromIntSignExtend(~7)).toAddress();
        Address addr = this.start.plus(slot.diff(base));
        if (addr.GT(start.plus(length))) {
            Log.write("updateCounter is going to write beyond counting space range: ");
            Log.write("slot: ", slot);
            Log.write("  targetspace base: ", base);
            Log.writeln(" counterspace start: ", this.start);
            Log.flush();
            VM.assertions.fail("stop at updatecounter.");
        }
        long val = addr.loadLong();
        val++;
        if (val == 0) {
            Log.write("updateCounter overflow slot: ");
            Log.writeln(slot);
        }
        addr.store(val);
    }
    public void dumpCounts() {
        if (Options.verbose.getValue() > 1) {
            Log.write("Start dumping write counts for space: ");
            Log.write(this.getName());
            Log.write(" length: ");
            Log.write(length.toLong() >>> 20);
            Log.write(" MB ");
            Log.write((length.toLong() >>> 10) & ((1 << 10)-1));
            Log.writeln(" KB");

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
//            double mean = sum / 512;
//            FileLog.write(mean, 1);
            FileLog.write(sum);
            FileLog.write(',');
            thre++;
            if (thre % 100 == 0) {
                FileLog.flush();
            }
        }

    }
}
