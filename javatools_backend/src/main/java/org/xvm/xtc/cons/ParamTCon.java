package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.ClassPart;
import org.xvm.xtc.Part;
import org.xvm.util.SB;

/**
   Parameterized Type Constant.
 */
public class ParamTCon extends TCon implements ClzCon {
  public TCon _con;
  public TCon[] _parms;
  private ClassPart _part;
  
  public ParamTCon( CPool X ) {
    X.u31();
    X.skipAry();
  }
  @Override public SB str(SB sb) {
    if( _con instanceof TermTCon tt ) sb.p(tt.name());
    sb.p("<>");
    return _parms==null ? sb : _parms[0].str(sb.p(" -> "));
  }
  @Override public ClassPart clz () { return _part; }
  @Override public ClassPart part() { return _part; }
  
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
  @Override int _eq( TCon tc ) {
    ParamTCon ptc = (ParamTCon)tc; // Invariant when called
    assert _part!=null && ptc._part!=null;
    if( _part != ptc._part ) return -1;
    if( _parms == ptc._parms ) return 1;
    if( _parms == null || ptc._parms==null ) return -1;
    if( _parms.length != ptc._parms.length ) return -1;
    // TODO: This code ignores matching of the parameter, which fails in the TCK for Decimal!
    // This needs a proper XTC ISA test
    //return _part == ptc._part ? 1 : -1;
    //int rez = 1;
    //for( int i=0; i<_parms.length; i++ ) {
    //  rez = Math.min(rez,_parms[i]._eq(ptc._parms[i]));
    //  if( rez == -1 ) return -1;
    //}
    //return rez;
  }

  // Is a generic TermTCon?
  @Override public TermTCon is_generic() {
    return _parms.length==1 && _parms[0] instanceof TermTCon ttc ? ttc : null;
  }
}
