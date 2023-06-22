package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;

/**
  Parameterized Type Constant.  This always has 2 parameters: the DataType and
  the OuterType.  Return the DataType part.
 */
public class ParamTCon extends TCon {
  TCon _con;
  TCon[] _parms;
  ClassPart _clz;
  XType[] _types;
  
  public ParamTCon( CPool X ) {
    X.u31();
    X.skipAry();
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
  @Override public XType link(XEC.ModRepo repo) {
    if( _clz!=null ) return _clz;
    _clz = (ClassPart)_con.link(repo);
    if( _parms!=null ) {
      _types = new XType[_parms.length];
      for( int i=0; i<_parms.length; i++ )
        _types[i] = _parms[i].link(repo);
    }
    return _clz;
  }
}
