package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.ClassPart;
import org.xvm.xtc.Part;
import org.xvm.util.SB;

/**
   Parameterized Type Constant.
 */
public class ParamTCon extends PartCon implements ClzCon {
  private TCon _con;
  public TCon[] _parms;
  
  public ParamTCon( CPool X ) {
    X.u31();
    X.skipAry();
  }
  @Override public SB str(SB sb) {
    if( _con instanceof TermTCon tt ) sb.p(tt.name());
    sb.p("<>");
    return _parms==null ? sb : _parms[0].str(sb.p(" -> "));
  }
  @Override public ClassPart clz () { return (ClassPart)part(); }
  @Override Part _part() { return _con.part(); }
  @Override public String name() { throw XEC.TODO(); }
  
  @Override public void resolve( CPool X ) {
    _con = (TCon)X.xget();
    _parms = TCon.tcons(X);
  }
  
  // Is a generic TermTCon?
  @Override public TermTCon is_generic() {
    return _parms.length==1 && _parms[0] instanceof TermTCon ttc ? ttc : null;
  }
}
