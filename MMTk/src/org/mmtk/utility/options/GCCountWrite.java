package org.mmtk.utility.options;

public final class GCCountWrite extends org.vmutil.options.BooleanOption {
    public GCCountWrite() {
        super(Options.set, "gc count write", "Force MMTK count GC's write to heap",
              false);
    }
}
