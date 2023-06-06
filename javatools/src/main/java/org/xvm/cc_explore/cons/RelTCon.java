package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class RelTCon extends TCon {
  private final transient int _t1x, _t2x;
  private TCon _con1, _con2;
  public RelTCon( FilePart X ) {
    _t1x = X.u31();
    _t2x = X.u31();
  }
  @Override public void resolve( CPool pool ) {
    _con1 = (TCon)pool.get(_t1x);
    _con2 = (TCon)pool.get(_t2x);
  }
}
