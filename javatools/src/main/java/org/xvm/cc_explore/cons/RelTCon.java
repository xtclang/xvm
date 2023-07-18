package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.tvar.TVar;

/**
  Exploring XEC Constants
 */
public abstract class RelTCon extends TCon implements ClzCon {
  TCon  _con1,  _con2;
  RelPart _part;
  public RelTCon( CPool X ) {
    X.u31();
    X.u31();
  }

  @Override public void resolve( CPool X ) {
    _con1 = (TCon)X.xget();
    _con2 = (TCon)X.xget();
  }

  @Override public Part link( XEC.ModRepo repo ) {
    if( _part!=null ) return _part;
    Part p1 = _con1.link(repo);
    Part p2 = _con2.link(repo);
    return (_part = new RelPart(p1,p2));
  }

  @Override public RelPart clz() { assert _part!=null; return _part; }
  
  // Note that the _tvar type set here is used to stop repeated setype calls
  // but isn't really any sensible TVar.
  @Override TVar _setype() {
    _con1.setype();
    _con2.setype();
    throw XEC.TODO();
  }
}
