package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.tvar.TVar;

/**
  Exploring XEC Constants
 */
public abstract class TCon extends Const {

  private TVar _tvar;
  public final boolean has_tvar() { return _tvar!=null; }
  // Normal access, already set (never null)
  public final TVar tvar() { return _tvar.unified() ? (_tvar=_tvar.find()) : _tvar; }
  // Set the TVar; can be null or not
  public final TVar setype( ) {
    if( _tvar!=null ) return tvar();
    TVar tv = _setype();
    assert !tv.unified();
    return (_tvar=tv);
  }

  // Sub TCons use this return the initial tvar; and can be assured that they
  // are called only once, and they do not need to assign to tvar.
  TVar _setype( ) { throw XEC.TODO(); }

  // Only used to break cyclic tvar construction
  void setype_stop_cycles(TVar tv) { _tvar = tv; }

  // Used in signature matching.  Not called "equals" because I don't want to
  // (yet) sign up for the whole equals contract.
  public final int eq( TCon tc ) {
    if( this==tc ) return 1;
    if( this instanceof TermTCon ttc && ttc._id instanceof PropCon )
      return 0; // Wildcard, or type leaf match
    if( getClass()!=tc.getClass() ) return -1;
    return _eq(tc);
  }
  int _eq( TCon tc ) { throw XEC.TODO(); }
  
  public static TCon[] tcons( CPool X ) {
    int len = X.u31();
    if( len==0 ) return null;
    TCon[] cs = new TCon[len];
    for( int i=0; i<len; i++ )
      cs[i] = (TCon)X.xget();
    return cs;
  }

  public TermTCon is_generic() { return null; }
}
