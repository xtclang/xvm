package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.tvar.*;
import org.xvm.cc_explore.util.SB;

/**
  Exploring XEC Constants
 */
public class SigCon extends TCon implements IdCon {
  String _name;
  TCon[] _args;
  TCon[] _rets;
  
  public SigCon( CPool X ) {
    X.u31();
    X.skipAry();
    X.skipAry();
  }
  @Override public SB str(SB sb) { return sb.p(_name).p("{}"); }
  
  @Override public void resolve( CPool X ) {
    _name  =((StringCon)X.xget())._str;
    _args = TCon.tcons(X);
    _rets  = TCon.tcons(X);
  }  
  @Override public String name() { return _name; }
  public TCon[] rawRets () { return _rets ; }
  public TCon[] rawParms() { return _args; }

  @Override TVar _setype( XEC.ModRepo repo ) {
    TVLambda lam = new TVLambda(_args==null ? 0 : _args.length, _rets==null ? 0 : _rets.length);
    set_tvar_cycle_break(lam);
    if( _args != null )
      for( int i=0; i<_args.length; i++ )
        _args[i].setype(repo).unify(lam.arg(i));
    if( _rets != null )
      for( int i=0; i<_rets.length; i++ )
        _rets[i].setype(repo).unify(lam.arg(lam._nargs+i));
    return lam;
  }
}
