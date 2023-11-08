package org.xvm.cons;

import org.xvm.*;

/**
  Exploring XEC Constants
 */
public class DecACon extends TCon {
  private DecCon _dec;
  public DecACon( CPool X ) { X.u31(); }
  @Override public void resolve( CPool X ) { _dec = (DecCon)X.xget(); }
}
