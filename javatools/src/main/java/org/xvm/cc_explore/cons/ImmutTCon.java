package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class ImmutTCon extends TCon {
  private transient int _tx;  // Type index for type
  TCon _icon;
  public ImmutTCon( FilePart X ) {
    _tx = X.u31();
  }
  @Override public void resolve( CPool pool ) {
    _icon = (TCon)pool.get(_tx);
  }
}