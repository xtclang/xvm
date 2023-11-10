package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public abstract class TCon extends Const {

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
