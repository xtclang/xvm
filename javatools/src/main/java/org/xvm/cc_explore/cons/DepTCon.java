package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public abstract class DepTCon extends TCon {
  private final transient int _parx;
  private TCon _par;
  DepTCon( FilePart X ) { _parx = X.u31(); }
  @Override public void resolve( CPool pool ) { _par = (TCon)pool.get(_parx); }
}
