package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class MapCon extends Const {
  final Format _f;
  
  private TCon _t;              // Type for whole map
  private final Const[] _keys, _vals;

  private Part _part;
  private MMethodPart[] _meths;
  
  public MapCon( CPool X, Const.Format f ) {
    _f = f;
    X.u31();
    int len = X.u31();
    _keys = new Const[len];
    _vals = new Const[len];
    for( int i=0; i<len; i++ ) {  X.u31();  X.u31();  }
  }
  @Override public void resolve( CPool X ) {
    TCon t = (TCon)X.xget();
    int len = X.u31();
    for( int i=0; i<len; i++ ) {
      _keys[i] = X.xget();
      _vals[i] = X.xget();
    }
  }
  @Override public Part link(XEC.ModRepo repo) {
    if( _meths!=null ) return null; // Already linked
    _meths = new MMethodPart[_vals.length];
    if( _t!=null ) _part = _t.link(repo);
    for( int i=0; i<_vals.length; i++ ) {
      _keys[i].link(repo);
      _meths[i] = (MMethodPart)_vals[i].link(repo);
    }
    return null;
  }
}
