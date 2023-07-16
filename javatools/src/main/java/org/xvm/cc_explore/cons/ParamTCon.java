package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.tvar.TVar;
import org.xvm.cc_explore.tvar.TVStruct;

/**
   Parameterized Type Constant.
 */
public class ParamTCon extends TCon {
  public TCon _con;
  public TCon[] _parms;
  public final TVar[] _types;
  
  public ParamTCon( CPool X ) {
    X.u31();
    int len = X.skipAry();
    _types = len==0 ? null : new TVar[len];
  }
  @Override public SB str(SB sb) {
    if( _con instanceof TermTCon tt ) sb.p(tt.name());
    sb.p("<>");
    return _parms==null ? sb : _parms[0].str(sb.p(" -> "));
  }
  @Override public void resolve( CPool X ) {
    _con = (TCon)X.xget();
    _parms = TCon.tcons(X);
  }
  
  @Override TVar _setype( XEC.ModRepo repo ) {
    if( _parms!=null )
      for( int i=0; i<_parms.length; i++ )
       _types[i] = _parms[i].setype(repo);
    return _con.setype(repo);
  }

  public TVStruct clz() { return (TVStruct)tvar(); }
}
