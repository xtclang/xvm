package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.tvar.TVBase;

/**
  Exploring XEC Constants
 */
public class DecACon extends TCon {
  private DecCon _dec;
  public DecACon( CPool X ) { X.u31(); }
  @Override public void resolve( CPool X ) { _dec = (DecCon)X.xget(); }
  @Override TVBase _setype() { return new TVBase(this); }
}
