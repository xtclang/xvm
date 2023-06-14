package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.XEC;

/**
  Exploring XEC Constants
 */
public abstract class TCon extends Const {
  // Resolve an array
  public static TCon[] resolveAry( CPool pool, int[] xs) {
    TCon[] cs = new TCon[xs.length];
    for( int i=0; i<xs.length; i++ )
      cs[i] = (TCon)pool.get(xs[i]);
    return cs;
  }
}
