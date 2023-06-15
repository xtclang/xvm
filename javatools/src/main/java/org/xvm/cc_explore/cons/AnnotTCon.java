package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.Annot;
import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.CPool;

/**
  Exploring XEC Constants
 */
public class AnnotTCon extends TCon {
  Annot _an;
  TCon _con;
  public AnnotTCon( CPool X ) {
    X.u31();
    X.u31();
  }
  @Override public void resolve( CPool X ) {
    _an = (Annot)X.xget();
    _con = (TCon)X.xget();
  }
}
