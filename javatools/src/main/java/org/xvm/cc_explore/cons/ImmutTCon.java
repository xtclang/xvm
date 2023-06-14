package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class ImmutTCon extends TCon {
  TCon _icon;
  public ImmutTCon( FilePart X ) { X.u31(); }
  @Override public void resolve( FilePart X ) { _icon = (TCon)X.xget(); }
}
