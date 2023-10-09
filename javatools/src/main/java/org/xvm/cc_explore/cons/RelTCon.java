package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.tvar.TVar;

/**
  Exploring XEC Constants
 */
public abstract class RelTCon extends TCon implements ClzCon {
  public TCon  _con1,  _con2;
  RelPart _part;
  public RelTCon( CPool X ) {
    X.u31();
    X.u31();
  }
  
  @Override public RelPart clz() { assert _part!=null; return _part; }
  
  @Override public void resolve( CPool X ) {
    _con1 = (TCon)X.xget();
    _con2 = (TCon)X.xget();
  }

  @Override public Part link( XEC.ModRepo repo ) {
    if( _part!=null ) return _part;
    Part p1 = _con1.link(repo);
    Part p2 = _con2.link(repo);
    return (_part = new RelPart(p1,p2,op()));
  }

  @Override int _eq( TCon tc ) {
    RelTCon rt = (RelTCon)tc; // Invariant when called
    return Math.min(_con1.eq(rt._con1), _con2.eq(rt._con2));
  }

  abstract RelPart.Op op();
}
