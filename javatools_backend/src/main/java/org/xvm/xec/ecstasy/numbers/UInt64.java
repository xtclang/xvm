package org.xvm.xec.ecstasy.numbers;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xrun.Never;
import org.xvm.xec.ecstasy.Appenderchar;
import org.xvm.xec.ecstasy.Orderable;
import org.xvm.xec.ecstasy.Ordered;
import org.xvm.xec.ecstasy.text.Stringable;
import org.xvm.xec.ecstasy.collections.Array;

/**
     Support XTC UInt64
*/
public class UInt64 extends UIntNumber {
  public static final UInt64 GOLD = new UInt64(null);
  public UInt64(Never n ) { this(0); } // No-arg constructor
  
  private static UInt64[] CACHE = new UInt64[256];
  static {
    for( int i=0; i<CACHE.length; i++ )
      CACHE[i] = new UInt64(i);
  }
  public static UInt64 make(int x) {
    if( 0 <= x && x < CACHE.length ) return CACHE[x];
    return new UInt64(x);
  }
  public final int _i;
  
  private UInt64( int i ) { _i = i; }

  Array<Bit> toBitArray(Array.Mutability mut) { throw XEC.TODO(); }


  @Override public long hashCode( XTC x ) { throw XEC.TODO(); }
  @Override public boolean equals ( XTC x0, XTC x1 ) { throw XEC.TODO(); }
  @Override public Ordered compare( XTC x0, XTC x1 ) { throw XEC.TODO(); }

  @Override public final String toString() { return ""+_i; }
  @Override public long estimateStringLength() { return (64 - Long.numberOfLeadingZeros(_i)); }
}
