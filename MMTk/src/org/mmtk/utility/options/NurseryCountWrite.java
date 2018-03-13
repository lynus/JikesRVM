package org.mmtk.utility.options;

public class NurseryCountWrite extends org.vmutil.options.BooleanOption {
    public NurseryCountWrite() {
        super(Options.set, "count for nursery", "write count for nursery space",
                false);
    }
}
