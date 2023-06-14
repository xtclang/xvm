package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class MethodBindCon extends Const {
  private MethodCon _method;
  public MethodBindCon( FilePart X ) { X.u31(); }
  @Override public void resolve( FilePart X ) { _method = (MethodCon)X.xget(); }
}
