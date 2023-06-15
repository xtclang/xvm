package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.CPool;

/**
  Exploring XEC Constants
 */
public class ImmutTCon extends TCon {
  TCon _icon;
  public ImmutTCon( CPool X ) { X.u31(); }
  @Override public void resolve( CPool X ) { _icon = (TCon)X.xget(); }
}
