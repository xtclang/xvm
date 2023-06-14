package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public abstract class DepTCon extends TCon {
  private TCon _par;
  DepTCon( FilePart X ) { X.u31(); }
  @Override public void resolve( FilePart X ) { _par = (TCon)X.xget(); }
}
