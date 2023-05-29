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
     Support XTC UInt8
*/
public class UInt8 extends UIntNumber {
  public static final UInt8 GOLD = new UInt8(null);
  public UInt8(Never n ) { this(0); } // No-arg constructor
  
  private static UInt8[] CACHE = new UInt8[256];
  static {
    for( int i=0; i<CACHE.length; i++ )
      CACHE[i] = new UInt8(i - (CACHE.length>>1));
  }
  public static UInt8 make(byte x) {
    return CACHE[x&0xFF];
  }
  public final byte _i;
  
  private UInt8( int i ) { _i = (byte)i; }

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
