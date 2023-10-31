package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import java.math.BigInteger;

/**
  Exploring XEC Constants
 */
public class IntCon extends TCon {
  public  final Format _f;
  public  final long _x;          // Only valid if _big is null
  public  final BigInteger _big;  // If null, _x holds the value.  If not-null _x is 0.
  private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
  private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
  
  public IntCon( CPool X, Const.Format f ) {
    _f = f;

    int z = X.isize();          // Read the size; drop extra bits beyond size
    if( z == -1 ) {             // Size is Huge
      _x = 0;
      _big = X.bigint(X.i32()); // Extra size read before BigInteger
    } else if( z <= 9 ) {       // Size is Tiny to Large
      X.undo();                 // Push extra bits back
      _x = X.pack64();          // Do a normal packed read
      _big = null;      
    } else {                    // Size is modest Huge
      BigInteger big =  X.bigint(z-1); // Already read size, just do BigInteger
      if( LONG_MIN.compareTo(big) <= 0 && big.compareTo(LONG_MAX) <= 0 ) {
        _x = big.longValueExact();
        _big = null;
      } else {
        _x = 0;
        _big = big;
      }
    }

    int c = switch( f ) {   // Size in bytes of int constant
    case Int16, UInt16 -> 2;
    case Int32, UInt32 -> 4;
    case Int64, UInt64 -> 8;
    case Int128, UInt128 -> 16;
    case IntN, UIntN -> 1024;
    default -> { System.err.println(f); throw XEC.TODO(); }
    };
    
    boolean unsigned = switch( f ) {
    case  Int16,  Int32,  Int64,  Int128,  IntN -> false;
    case UInt16, UInt32, UInt64, UInt128, UIntN -> true;
    default -> { System.err.println(f); throw XEC.TODO(); }
    };

    // Sanity check size
    if( _big==null ) {
      // Already fits in a long.  Sizes >= 8 will always work.
      // Sizes < 8 need a range check.
      if( c < 8 ) {
        long rng = 1L<<(c<<3);
        if(  unsigned && _x < 0 ) throw new IllegalArgumentException("illegal unsigned value: " + _x);
        if(  unsigned && _x >= rng ) throw bad(c);
        if( !unsigned && !(-(rng>>1) <= _x && _x < (rng>>1)) )
          throw bad(c);
      }
    } else {
      if(  unsigned && ((_big.bitLength()+7)>>3) > c ) throw bad(c);
      if( !unsigned && (_big.bitLength()>>3) + 1 > c ) throw bad(c);
    }
  }
  private IllegalArgumentException bad(int c) {
    return new IllegalArgumentException("value exceeds " + c + " bytes: " + (_big==null ? ""+_x : _big.toString()));
  }
}
