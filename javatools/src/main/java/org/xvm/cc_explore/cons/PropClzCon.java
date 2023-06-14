package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class PropClzCon extends DepTCon {
  private PropCon _prop;
  public PropClzCon( FilePart X ) { super(X); X.u31(); }
  @Override public void resolve( FilePart X ) { super.resolve(X); _prop = (PropCon)X.xget(); }  
}
