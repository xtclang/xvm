package org.xvm.cc_explore;

/**
  Exploring XEC Constants
 */
public class IntConst extends Const {
  final Format _f;
  final long _x;
  IntConst( XEC.XParser X, Const.Format f ) {
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
  @Override void resolve( CPool pool ) {}
}
