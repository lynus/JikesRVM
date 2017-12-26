package org.mmtk.utility.heap;

import org.mmtk.policy.Space;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.heap.layout.HeapLayout;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;

@Uninterruptible public class CountingPageResource extends PageResource {
    private Address cursor;
    private Address limit;

    public CountingPageResource(Space space, Address start) {
        super(space, start);
        cursor = start;
        limit = Address.zero();
    }

    @Override
    public int getAvailablePhysicalPages() {
        return Conversions.bytesToPages(limit.diff(start));
    }

    @Override
    Address allocPages(int reservedPages, int requiredPages, boolean zeroed) {
        Address old = cursor;
        Extent bytes = Conversions.pagesToBytes(requiredPages);
        if (cursor.plus(bytes).GT(limit))
          return Address.zero();
        cursor = cursor.plus(bytes);
        HeapLayout.mmapper.ensureMapped(old, requiredPages);
        VM.memory.zero(zeroNT, old, bytes);
        return old;
    }

    @Override
    public int adjustForMetaData(int pages) {
        VM.assertions.fail("Will never be called!");
        return 0;
    }

    public void setLimit(Extent limit) {
        this.limit = this.start.plus(limit);
    }
}
