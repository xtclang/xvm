package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.util.SB;
import java.math.BigInteger;

/**
  Exploring XEC Constants
 */
public class IntCon extends NumCon {
  public  final Format _f;
  public  final BigInteger _big;  // If null, _x holds the value.  If not-null _x is 0.
  private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
  private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

  private static BigInteger _tmp;
  private static long parse( CPool X, Const.Format f ) {    
    int z = X.isize();          // Read the size; drop extra bits beyond size
    long x;
    if( z == -1 ) {             // Size is Huge
      x = 0;
      _tmp = X.bigint(X.i32()); // Extra size read before BigInteger
    } else if( z <= 9 ) {       // Size is Tiny to Large
      X.undo();                 // Push extra bits back
      x = X.pack64();          // Do a normal packed read
      _tmp = null;      
    } else {                    // Size is modest Huge
      BigInteger big =  X.bigint(z-1); // Already read size, just do BigInteger
      if( LONG_MIN.compareTo(big) <= 0 && big.compareTo(LONG_MAX) <= 0 ) {
        x = big.longValueExact();
        _tmp = null;
      } else {
        x = 0;
        _tmp = big;
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
    if( _tmp==null ) {
      // Already fits in a long.  Sizes >= 8 will always work.
      // Sizes < 8 need a range check.
      if( c < 8 ) {
        long rng = 1L<<(c<<3);
        if(  unsigned && x < 0 ) throw new IllegalArgumentException("illegal unsigned value: " + x);
        if(  unsigned && x >= rng ) throw bad(c,x);
        if( !unsigned && !(-(rng>>1) <= x && x < (rng>>1)) )
          throw bad(c,x);
      }
    } else {
      if(  unsigned && ((_tmp.bitLength()+7)>>3) > c ) throw bad(c,x);
      if( !unsigned && (_tmp.bitLength()>>3) + 1 > c ) throw bad(c,x);
    }
    return x;
  }
  
  public IntCon( CPool X, Const.Format f ) {
    super(parse(X,f));
    _f = f;
    _big = _tmp;
  }
  private static IllegalArgumentException bad(int c, long x) {
    return new IllegalArgumentException("value exceeds " + c + " bytes: " + (_tmp==null ? ""+x : _tmp.toString()));
  }
}
