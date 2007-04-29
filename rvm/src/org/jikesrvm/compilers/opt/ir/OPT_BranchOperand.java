/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.VM;

/**
 * Represents a branch target.
 *
 * @see OPT_Operand
 * @author John Whaley
 */
public final class OPT_BranchOperand extends OPT_Operand {

  /**
   * Target of this branch.
   */
  public OPT_Instruction target;

  /**
   * Construct a new branch operand with the given target.
   * <STRONG> Precondition: </STRONG> targ must be a Label instruction.
   * 
   * @param targ target of branch
   */
  public OPT_BranchOperand(OPT_Instruction targ) {
    if (VM.VerifyAssertions) VM._assert(Label.conforms(targ));
    target = targ;
  }

  /**
   * Returns a copy of this branch operand.
   * 
   * @return a copy of this operand
   */
  public OPT_Operand copy() {
    return new OPT_BranchOperand(target);
  }

  /**
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code> 
   *           if they are not.
   */
  public boolean similar(OPT_Operand op) {
    return (op instanceof OPT_BranchOperand) &&
           (target == ((OPT_BranchOperand)op).target);
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return "LABEL"+Label.getBlock(target).block.getNumber(); 
  }

}



