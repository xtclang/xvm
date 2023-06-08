package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class DecACon extends Const {
  private transient int _decx;  // Type index for parent
  private DecCon _dec;
  public DecACon( FilePart X ) { _decx = X.u31(); }
  @Override public void resolve( CPool pool ) { _dec = (DecCon)pool.get( _decx); }
}
