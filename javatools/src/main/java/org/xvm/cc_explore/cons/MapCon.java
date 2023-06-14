package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class MapCon extends Const {
  final Format _f;
  
  private TCon _t;              // Type for whole map
  private Const[] _keys, _vals;
  
  public MapCon( FilePart X, Const.Format f ) {
    _f = f;
    X.u31();
    int len = X.u31();
    for( int i=0; i<len; i++ ) {  X.u31();  X.u31();  }
  }
  @Override public void resolve( FilePart X ) {
    TCon t = (TCon)X.xget();
    _keys = new Const[X.u31()];
    _vals = new Const[_keys.length];
    for( int i=0; i<_keys.length; i++ ) {
      _keys[i] = X.xget();
      _vals[i] = X.xget();
    }
  }
}
