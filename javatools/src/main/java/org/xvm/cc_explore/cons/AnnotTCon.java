package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.Annot;
import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class AnnotTCon extends TCon {
  Annot _an;
  TCon _con;
  public AnnotTCon( FilePart X ) {
    X.u31();
    X.u31();
  }
  @Override public void resolve( FilePart X ) {
    _an = (Annot)X.xget();
    _con = (TCon)X.xget();
  }
}
