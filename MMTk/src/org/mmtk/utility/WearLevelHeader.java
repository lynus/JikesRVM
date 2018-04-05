package org.mmtk.utility;

import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;

@Uninterruptible public class WearLevelHeader {
  private static final int MARK_OBSERVE_BITS = ForwardingWord.FORWARDING_BITS + 1;
  private static final byte MARK_OBSERVE = 1 << MARK_OBSERVE_BITS;
  private static final int MARK_HOT_BITS = MARK_OBSERVE_BITS + 1;
  private static final byte MARK_HOT = 1 << MARK_HOT_BITS;

  @Inline
  static public void markObserve(ObjectReference object) {
    byte old = VM.objectModel.readAvailableByte(object);
    VM.objectModel.writeAvailableByte(object, (byte)(old | MARK_OBSERVE));
  }

  @Inline
  static public void markHot(ObjectReference object) {
    byte old = VM.objectModel.readAvailableByte(object);
    byte new_ = (byte)(old | MARK_HOT | ~MARK_OBSERVE);
    VM.objectModel.writeAvailableByte(object, new_);
  }

  @Inline
  static public void clearObserve(ObjectReference object) {
    byte old = VM.objectModel.readAvailableByte(object);
    byte new_ = (byte) (old | ~MARK_OBSERVE);
    VM.objectModel.writeAvailableByte(object, new_);
  }
}
