package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class PropClzCon extends DepTCon {
  private final transient int _propx;
  private PropCon _prop;
  public PropClzCon( FilePart X ) { super(X); _propx = X.u31(); }
  @Override public void resolve( CPool pool ) { super.resolve(pool); _prop = (PropCon)pool.get(_propx); }  
}
