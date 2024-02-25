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
     Support XTC Int64
*/
public class Int64 extends IntNumber {
  public static final Int64 GOLD = new Int64((Never)null);
  private static Int64[] CACHE = new Int64[128];
  static {
    for( int i=0; i<CACHE.length; i++ )
      CACHE[i] = new Int64(i - (CACHE.length>>1));
  }
  public static Int64 make(long x) {
    long y = x + (CACHE.length>>1);
    if( 0 <= y && y < CACHE.length ) return CACHE[(int)y];
    return new Int64(x);
  }
  public Int64(Never n ) { this(0); } // No-arg constructor
  
  public final long _i;
  
  public Int64(String s) { this(Long.valueOf(s)); }
  public Int64(org.xvm.xec.ecstasy.text.String s) { this(s._i); }
  public Int64( long i ) { _i = i; }

  public static Int64 construct( String s ) { return new Int64(s); }
  public static Int64 construct( long i ) { return new Int64(i); }
  
  Array<Bit> toBitArray(Array.Mutability mut) { throw XEC.TODO(); }

  // All the XTC types here are guaranteed to be Int64
  @Override public long hashCode(XTC x) { return ((Int64)x)._i; }
  @Override public boolean equals ( XTC x0, XTC x1 ) { return ((Int64)x0)._i == ((Int64)x1)._i; }
  @Override public Ordered compare( XTC x0, XTC x1 ) {
    long i0 = ((Int64)x0)._i;
    long i1 = ((Int64)x1)._i;
    if( i0==i1 ) return Ordered.Equal;
    return i0<i1 ? Ordered.Lesser : Ordered.Greater;
  }

  @Override public final String toString() { return ""+_i; }
  @Override public long estimateStringLength() { return (64 - Long.numberOfLeadingZeros(_i)); }
}
