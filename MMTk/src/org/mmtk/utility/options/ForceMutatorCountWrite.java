package org.mmtk.utility.options;

public final class ForceMutatorCountWrite extends org.vmutil.options.BooleanOption{
  public ForceMutatorCountWrite() {
    super(Options.set, "Mutator Count Write",
        "Force mutator count write", false);
  }
}
