package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.XEC;

/**
  Exploring XEC Constants
 */
public class IntCon extends Const {
  final Format _f;
  final long _x;
  public IntCon( XEC.XParser X, Const.Format f ) {
    _f = f;
    _x = X.pack64();        // TODO: larger numbers need more support here
    
    int c = switch( f ) {
    case Int -> 16;
    default -> throw XEC.TODO();
    };
    
    boolean unsigned = switch( f ) {
    case Int -> false;
    default -> throw XEC.TODO();
    };

    if( unsigned ) XEC.TODO();   // Need proper fitting test
    if( c <8 ) throw XEC.TODO(); // Need proper fitting test, right now the constant is 64bits always fits in 8
  }  
  @Override public void resolve( CPool pool ) {}
}
