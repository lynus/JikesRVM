/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan.refcount;

import org.mmtk.utility.deque.*;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements a dec-buffer for a reference counting collector
 *
 * @see org.mmtk.plan.TransitiveClosure
 */
@Uninterruptible
public final class RCDecBuffer extends ObjectReferenceBuffer {
  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param queue The shared deque that is used.
   */
  public RCDecBuffer(SharedDeque queue) {
    super("dec", queue);
  }

  @Override
  @Inline
  protected void process(ObjectReference object) {
    if (RCBase.isRCObject(object)) {
      push(object);
    }
  }
}
