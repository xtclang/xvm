package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.Annot;
import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class AnnotTCon extends TCon {
  private final transient int _anox, _tx;  // Type index for annotation, type
  Annot _an;
  TCon _con;
  public AnnotTCon( FilePart X ) {
    _anox = X.u31();
    _tx   = X.u31();
  }
  @Override public void resolve( CPool pool ) {
    _an = (Annot)pool.get(_anox);
    _con = (TCon)pool.get(_tx);
  }
}
