package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.Annot;
import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class AccessTCon extends TCon {
  private final Access _access;
  TCon _con;
  public AccessTCon( FilePart X ) {
    X.u31();                    // Skip index for _con
    _access = Access.valueOf(X.u31());
  }
  @Override public void resolve( FilePart X ) { _con = (TCon)X.xget(); }
}
