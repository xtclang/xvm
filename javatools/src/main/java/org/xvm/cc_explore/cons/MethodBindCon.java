package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class MethodBindCon extends Const {
  private MethodCon _method;
  public MethodBindCon( CPool X ) { X.u31(); }
  @Override public void resolve( CPool X ) { _method = (MethodCon)X.xget(); }
}
