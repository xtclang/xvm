package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public abstract class TCon extends Const {
  public static TCon[] tcons( CPool X ) {    
    TCon[] cs = new TCon[X.u31()];
    for( int i=0; i<cs.length; i++ )
      cs[i] = (TCon)X.xget();
    return cs;
  }
}
