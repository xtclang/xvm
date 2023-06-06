package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.Annot;
import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class AccessTCon extends TCon {
  private final transient int _tx;
  private final Access _access;
  TCon _con;
  public AccessTCon( FilePart X ) {
    _tx   = X.u31();
    _access = Access.valueOf(X.u31());
  }
  @Override public void resolve( CPool pool ) { _con = (TCon)pool.get(_tx); }
}
