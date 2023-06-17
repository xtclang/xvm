package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class ParamTCon extends TCon {
  TCon _con;
  TCon[] _parms;
  ClassPart _clz;
  Part[] _parts;
  
  public ParamTCon( CPool X ) {
    X.u31();
    X.skipAry();
  }
  @Override public void resolve( CPool X ) {
    _con = (TCon)X.xget();
    _parms = TCon.tcons(X);
  }
  @Override public Part link(XEC.ModRepo repo) {
    if( _clz!=null ) return _clz;
    _clz = (ClassPart)_con.link(repo);
    _parts = new Part[_parms.length];
    for( int i=0; i<_parms.length; i++ )
      _parts[i] = _parms[i].link(repo);
    return _clz;
  }
}
