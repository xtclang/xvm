package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class TermTCon extends TCon {
  private Const _id;
  public TermTCon( FilePart X ) { X.u31(); }
  @Override public void resolve( FilePart X ) { _id = X.xget(); }
}
