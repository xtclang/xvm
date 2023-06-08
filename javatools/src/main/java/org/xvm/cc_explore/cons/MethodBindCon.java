package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class MethodBindCon extends Const {
  private transient int _methodx;  // Type index for method
  private MethodCon _method;
  public MethodBindCon( FilePart X ) { _methodx = X.u31(); }
  @Override public void resolve( CPool pool ) { _method = (MethodCon)pool.get(_methodx); }
}
