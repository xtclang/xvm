package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.Annot;
import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FileComponent;
import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class AnnotTCon extends TCon {
  private transient int _anox, _tx;  // Type index for annotation, type
  Annot _an;
  TCon _con;
  public AnnotTCon( FileComponent X ) throws IOException {
    _anox = X.u31();
    _tx   = X.u31();
  }
  @Override public void resolve( CPool pool ) {
    _an = (Annot)pool.get(_anox);
    _con = (TCon)pool.get(_tx);
  }
}
