package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.tvar.TVar;

/**
  Exploring XEC Constants
 */
public abstract class RelTCon extends TCon {
  TCon  _con1,  _con2;
  public RelTCon( CPool X ) {
    X.u31();
    X.u31();
  }
  public TCon con1() { return _con1; }
  public TCon con2() { return _con2; }
  @Override public void resolve( CPool X ) {
    _con1 = (TCon)X.xget();
    _con2 = (TCon)X.xget();
  }
  @Override public TVar _setype(XEC.ModRepo repo) {
    _con1.setype(repo);
    _con2.setype(repo);
    return null;
  }
}
