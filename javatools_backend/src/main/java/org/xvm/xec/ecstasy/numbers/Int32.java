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
     Support XTC Int32
*/
public class Int32 extends IntNumber {
  public static final Int32 GOLD = new Int32((Never)null);
  private static Int32[] CACHE = new Int32[128];
  static {
    for( int i=0; i<CACHE.length; i++ )
      CACHE[i] = new Int32(i - (CACHE.length>>1));
  }
  public static Int32 make(long x) {
    long y = x + (CACHE.length>>1);
    if( 0 <= y && y < CACHE.length ) return CACHE[(int)y];
    return new Int32(x);
  }
  public Int32(Never n ) { this(0); } // No-arg constructor
  
  public final int _i;
  
  public Int32(String s) { this(Integer.valueOf(s)); }
  public Int32(org.xvm.xec.ecstasy.text.String s) { this(s._i); }
  public Int32( long i ) { _i = (int)i; if( _i != i ) throw XEC.TODO(); }

  Array<Bit> toBitArray(Array.Mutability mut) { throw XEC.TODO(); }


  // All the XTC types here are guaranteed to be Int32
  @Override public long hashCode(XTC x) { return ((Int32)x)._i; }
  @Override public boolean equals ( XTC x0, XTC x1 ) { return ((Int32)x0)._i == ((Int32)x1)._i; }
  @Override public Ordered compare( XTC x0, XTC x1 ) {
    int i0 = ((Int32)x0)._i;
    int i1 = ((Int32)x1)._i;
    if( i0==i1 ) return Ordered.Equal;
    return i0<i1 ? Ordered.Lesser : Ordered.Greater;
  }

  @Override public final String toString() { return ""+_i; }
  @Override public long estimateStringLength() { return (32 - Long.numberOfLeadingZeros(_i)); }
  @Override public final Appenderchar appendTo(Appenderchar buf) {
    return buf.appendTo(toString());
  }
}
