package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class MapCon extends TCon {
  final Format _f;
  
  public TCon _t;              // Type for whole map
  public final Const[] _keys, _vals;

  private Part _part;
  private Part[] _parts;
  
  public MapCon( CPool X, Const.Format f ) {
    _f = f;
    X.u31();
    int len = X.u31();
    _keys = new Const[len];
    _vals = new Const[len];
    for( int i=0; i<len; i++ ) {  X.u31();  X.u31();  }
  }
  @Override public void resolve( CPool X ) {
    _t = (TCon)X.xget();
    int len = X.u31();
    for( int i=0; i<len; i++ ) {
      _keys[i] = X.xget();
      _vals[i] = X.xget();
    }
  }
}
