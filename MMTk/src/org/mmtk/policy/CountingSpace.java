package org.mmtk.policy;

import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.CardTable;
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
    private Extent highWaterMark;
    public Space targetSpace;
    private Address targetSpaceBase;
    public CountingSpace(String name, VMRequest vmRequest) {
        super(name, false, true, true, vmRequest);
        pr = new CountingPageResource(this, start);
        length = Extent.zero();
        highWaterMark = Extent.zero();
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
        highWaterMark = len;
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

    public void grow(Space space, Address addr, Extent bytes) {
        if (space != targetSpace)
            return;
        Address base = ((Map64) HeapLayout.vmMap).getSpaceBaseAddress(targetSpace);
        Extent newleng = addr.plus(bytes).diff(base).toWord().toExtent();
        if (newleng.GT(highWaterMark))
            highWaterMark = newleng;
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

    public Space getTargetSpace(Space exclude) {
        String name, excludeName = null;
        boolean getNursery = Options.nurseryCountWrite.getValue();
        Space ret = null;
        if (exclude != null)
            excludeName = exclude.getName();

        for (Space sp : getSpaces()) {
            name = sp.getName();
            if (getNursery && name == "nursery") {
                ret = sp;
                break;
            }
            if (!getNursery && name != "boot" && name != "immortal" && name != "meta" && name != "los" &&
                    name != "sanity" && name != "non-moving" && name != "sm-code" && name != "lg-code" &&
                    name != "vm" && name != "write-counter" && name != "nursery" && name != "cardtable") {
                if (excludeName != null && name == excludeName)
                    continue;
                ret = sp;
                break;
            }
        }
        if (ret != null) {
            if (Options.verbose.getValue() > 1) {
                Log.write("getTargetSpace: ");
                Log.writeln(ret.getName());
                Log.writeln("target space base address: ", ((Map64) HeapLayout.vmMap).getSpaceBaseAddress(ret));
            }
        } else if (Options.verbose.getValue() > 1) {
            Log.writeln("failed to get target space!");
        }
        targetSpace = ret;
        targetSpaceBase = ((Map64)HeapLayout.vmMap).getSpaceBaseAddress(targetSpace);
        return ret;
    }
    public void updateCounter(Address start, Address end) {
        if (!isInSpace(targetSpace.getDescriptor(), start)
            || !isInSpace(targetSpace.getDescriptor(), end))
            return;
        start = start.toWord().and(Word.fromIntSignExtend(~7)).toAddress();
        end = end.plus(7).toWord().and(Word.fromIntSignExtend(~7)).toAddress();
        Offset from = start.diff(targetSpaceBase);
        end  = this.start.plus(end.diff(targetSpaceBase));
        Address addr = this.start.plus(from);
        do {
            long val = addr.loadLong();
            val++;
            addr.store(val);
            addr = addr.plus(8);
        } while (addr.LT(end));
    }

    @Inline
    private void _updateCounter(Offset offset) {
        offset = offset.toWord().and(Word.fromIntSignExtend(~7)).toOffset();
        Address addr = this.start.plus(offset);
        long val = addr.loadLong();
        val++;
        addr.store(val);
    }

    @Inline
    public void updateCounter(Address slot) {
        if (slot.isZero()) {
            Log.writeln("updateCounter encounter Zero slot!");
            return;
        }
        if (!isInSpace(targetSpace.getDescriptor(), slot)) {
            return;
        }
        Offset offset = slot.diff(targetSpaceBase);
        _updateCounter(offset);
    }

    @Inline
    public void updateCounter(Address slot, CardTable.Mapper mapper) {
        if (slot.isZero()) {
            Log.writeln("updateCounter encounter Zero slot!");
            return;
        }
        if (!isInSpace(targetSpace.getDescriptor(), slot)) {
            return;
        }
        if (Options.verbose.getValue() > 4) {
            Log.write("updateCounter slot:");
            Log.writeln(slot);
        }
        Offset offset = slot.diff(targetSpaceBase);
        Offset to = mapper.translate(offset);
        _updateCounter(to);
    }

    @Inline
    public void updateCounter(Address start, Address end, CardTable.Mapper mapper) {
        if (!isInSpace(targetSpace.getDescriptor(), start)
            || !isInSpace(targetSpace.getDescriptor(), end))
            return;
        Address addr = start;
        Offset offset, to;
        while (addr.LT(end)) {
            offset = addr.diff(targetSpaceBase);
            to = mapper.translate(offset);
            _updateCounter(to);
            addr = addr.plus(8);
        }
    }
    public void dumpCounts() {
        if (Options.verbose.getValue() > 1) {
            Log.write("Start dumping write counts for space: ");
            Log.write(this.getName());
            Log.write(" length: ");
            Log.write(highWaterMark.toLong() >>> 20);
            Log.write(" MB ");
            Log.write((highWaterMark.toLong() >>> 10) & ((1 << 10)-1));
            Log.writeln(" KB");

        }
        Address end = start.plus(highWaterMark).minus(1);
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
            if (addr.plus(4096).LE(end))
                FileLog.write(',');
            thre++;
            if (thre % 100 == 0) {
                FileLog.flush();
            }
        }

    }
}
