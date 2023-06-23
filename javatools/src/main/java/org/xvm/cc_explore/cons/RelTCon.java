package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public abstract class RelTCon extends TCon {
  TCon  _con1,  _con2;
  private XType _type1, _type2;
  public RelTCon( CPool X ) {
    X.u31();
    X.u31();
  }
  @Override public void resolve( CPool X ) {
    _con1 = (TCon)X.xget();
    _con2 = (TCon)X.xget();
  }
  @Override public XType link(XEC.ModRepo repo) {
    if( _type1!=null ) return _type1;
    _type2 = _con2.link(repo);
    return (_type1 = _con1.link(repo));
  }
}
