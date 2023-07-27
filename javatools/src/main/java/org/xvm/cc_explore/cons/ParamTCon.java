package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.tvar.TVar;

/**
   Parameterized Type Constant.
 */
public class ParamTCon extends TCon implements ClzCon {
  public TCon _con;
  public TCon[] _parms;
  public final TVar[] _types;
  private ClassPart _part;
  
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
  @Override public ClassPart clz() { return _part; }
  
  @Override public void resolve( CPool X ) {
    _con = (TCon)X.xget();
    _parms = TCon.tcons(X);
  }
  
  @Override public Part link( XEC.ModRepo repo ) {
    if( _part!=null ) return _part;
    _part = (ClassPart)_con.link(repo);
    if( _parms!=null )
      for( TCon parm : _parms )
        parm.link(repo);
    assert _part!=null;
    return _part;
  }
  @Override TVar _setype() {
    if( _parms!=null )
      for( int i=0; i<_parms.length; i++ )
       _types[i] = _parms[i].setype();
    return _con.setype();
  }
}
