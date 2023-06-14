package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public abstract class RelTCon extends TCon {
  private TCon _con1, _con2;
  public RelTCon( FilePart X ) {
    X.u31();
    X.u31();
  }
  @Override public void resolve( FilePart X ) {
    _con1 = (TCon)X.xget();
    _con2 = (TCon)X.xget();
  }
}
