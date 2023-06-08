package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class MapCon extends Const {
  final Format _f;
  private transient int _tx;     // Type index for whole array
  private transient int[] _keyxs, _valxs; // Types for keys, values
  private Const[] _keys, _vals;
  
  public MapCon( FilePart X, Const.Format f ) {
    _f = f;
    _tx = X.u31();
    int len = X.u31();
    _keyxs = new int[len];
    _valxs = new int[len];
    for( int i=0; i<len; i++ ) {
      _keyxs[i] = X.u31();
      _valxs[i] = X.u31();
    }
  }
  @Override public void resolve( CPool pool ) {
    TCon t = (TCon)pool.get(_tx);
    _keys = resolveAry(pool,_keyxs);
    _vals = resolveAry(pool,_valxs);
  }
}
