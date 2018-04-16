package org.mmtk.utility.options;


public class WearLevel extends org.vmutil.options.BooleanOption {
  public WearLevel() {
    super(Options.set, "Wear Level",
        "enable wear leveling", false);
  }

}
