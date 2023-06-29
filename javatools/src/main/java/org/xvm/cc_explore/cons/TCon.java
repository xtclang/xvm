package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.tvar.TVar;

/**
  Exploring XEC Constants
 */
public abstract class TCon extends Const {

  private TVar _tvar;
  public final TVar tvar() {
    assert _tvar!=null;
    return _tvar.unified() ? (_tvar=_tvar.find()) : _tvar;
  }
  public final boolean has_tvar() { return _tvar!=null; }
  // Set the TVar
  public final TVar setype( XEC.ModRepo repo ) {
    if( _tvar!=null ) return _tvar;
    TVar tv =  _setype(repo);
    return (_tvar = tv);
  }

  // Sub TCons use this return the initial tvar; and can be assured that they
  // are called only once, and they do not need to assign to tvar.
  TVar _setype( XEC.ModRepo repo ) { throw XEC.TODO(); }
  
  public static TCon[] tcons( CPool X ) {
    int len = X.u31();
    if( len==0 ) return null;
    TCon[] cs = new TCon[len];
    for( int i=0; i<len; i++ )
      cs[i] = (TCon)X.xget();
    return cs;
  }
}
