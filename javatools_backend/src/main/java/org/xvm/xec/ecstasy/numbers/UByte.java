package org.xvm.xec.ecstasy.numbers;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xrun.Never;
import org.xvm.xec.ecstasy.Appenderchar;
import org.xvm.xec.ecstasy.Orderable;
import org.xvm.xec.ecstasy.Ordered;
import org.xvm.xec.ecstasy.text.Stringable;

/**
     Support XTC UByte
*/
public class UByte extends UIntNumber {
  public static final UByte GOLD = new UByte(null);
  public UByte(Never n ) { this(0); } // No-arg constructor
  
  final byte _i;
  
  public UByte( int i ) { _i = (byte)i; }


  @Override public long hashCode( XTC x ) { throw XEC.TODO(); }
  @Override public boolean equals ( XTC x0, XTC x1 ) { throw XEC.TODO(); }
  @Override public Ordered compare( XTC x0, XTC x1 ) { throw XEC.TODO(); }

  @Override public final String toString() { return ""+_i; }
  @Override public long estimateStringLength() { return (64 - Long.numberOfLeadingZeros(_i)); }
  @Override public final Appenderchar appendTo(Appenderchar buf) {
    return buf.appendTo(toString());
  }
}
