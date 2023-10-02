package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.tvar.*;
import org.xvm.cc_explore.util.SB;

/**
  Exploring XEC Constants
 */
public class SigCon extends TCon implements IdCon {
  public String _name;
  public TCon[] _args;
  public TCon[] _rets;
  private boolean _linked;
  
  public SigCon( CPool X ) {
    X.u31();
    X.skipAry();
    X.skipAry();
  }
  @Override public SB str(SB sb) { return sb.p(_name).p("{}"); }
  
  @Override public void resolve( CPool X ) {
    _name =((StringCon)X.xget())._str;
    _args = TCon.tcons(X);
    _rets = TCon.tcons(X);
  }  
  @Override public String name() { return _name; }
  public TCon[] rawRets () { return _rets; }
  public TCon[] rawParms() { return _args; }

  @Override public Part link(XEC.ModRepo repo) {
    if( _linked ) return null;
    _linked=true;
    if( _args!=null )
      for( TCon tcon : _args )
        tcon.link(repo);
    if( _rets!=null )
      for( TCon tcon : _rets )
        tcon.link(repo);
    return null;
  }
  
}
