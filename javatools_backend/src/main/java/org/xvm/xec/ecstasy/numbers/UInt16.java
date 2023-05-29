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
     Support XTC UInt16
*/
public class UInt16 extends UIntNumber {
  public static final UInt16 GOLD = new UInt16(null);
  public UInt16(Never n ) { this(0); } // No-arg constructor
  
  private static UInt16[] CACHE = new UInt16[256];
  static {
    for( int i=0; i<CACHE.length; i++ )
      CACHE[i] = new UInt16(i);
  }
  public static UInt16 make(char x) {
    if( 0 <= x && x < CACHE.length ) return CACHE[x];
    return new UInt16(x);
  }
  public final char _i;
  
  private UInt16( int i ) { _i = (char)i; }

  Array<Bit> toBitArray(Array.Mutability mut) { throw XEC.TODO(); }


  @Override public long hashCode( XTC x ) { throw XEC.TODO(); }
  @Override public boolean equals ( XTC x0, XTC x1 ) { throw XEC.TODO(); }
  @Override public Ordered compare( XTC x0, XTC x1 ) { throw XEC.TODO(); }

  @Override public final String toString() { return ""+_i; }
  @Override public long estimateStringLength() { return (64 - Long.numberOfLeadingZeros(_i)); }
  @Override public final Appenderchar appendTo(Appenderchar buf) {
    return buf.appendTo(toString());
  }
}
