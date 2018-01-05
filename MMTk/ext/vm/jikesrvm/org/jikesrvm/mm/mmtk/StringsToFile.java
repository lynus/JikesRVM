package org.jikesrvm.mm.mmtk;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.BootRecord;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class StringsToFile extends Strings {
  private static int logFileDesc = BootRecord.the_boot_record.logFileDesc;
  @Override
  public void write(char[] c, int len) {
    VM.sysFileWrite(logFileDesc, c, len);
  }

  @Override
  public void writeThreadId(char[] c, int len) {
    VM.tsysFileWrite(logFileDesc, c, len);
  }


}
