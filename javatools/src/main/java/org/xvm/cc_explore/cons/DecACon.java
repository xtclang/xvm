package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class DecACon extends Const {
  private DecCon _dec;
  public DecACon( FilePart X ) { X.u31(); }
  @Override public void resolve( FilePart X ) { _dec = (DecCon)X.xget(); }
}
