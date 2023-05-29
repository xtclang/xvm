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
     Support XTC UInt32
*/
public class UInt32 extends UIntNumber {
  public static final UInt32 GOLD = new UInt32(null);
  public UInt32(Never n ) { this(0); } // No-arg constructor
  
  private static UInt32[] CACHE = new UInt32[256];
  static {
    for( int i=0; i<CACHE.length; i++ )
      CACHE[i] = new UInt32(i);
  }
  public static UInt32 make(int x) {
    if( 0 <= x && x < CACHE.length ) return CACHE[x];
    return new UInt32(x);
  }
  public final int _i;
  
  private UInt32( int i ) { _i = i; }

  Array<Bit> toBitArray(Array.Mutability mut) { throw XEC.TODO(); }


  @Override public long hashCode( XTC x ) { throw XEC.TODO(); }
  @Override public boolean equals ( XTC x0, XTC x1 ) { throw XEC.TODO(); }
  @Override public Ordered compare( XTC x0, XTC x1 ) { throw XEC.TODO(); }

  @Override public final String toString() { return ""+_i; }
  @Override public long estimateStringLength() { return (32 - Long.numberOfLeadingZeros(_i)); }
  @Override public final Appenderchar appendTo(Appenderchar buf) {
    return buf.appendTo(toString());
  }
}
