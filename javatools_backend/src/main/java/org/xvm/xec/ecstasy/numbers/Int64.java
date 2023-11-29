package org.xvm.xec.ecstasy.numbers;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xrun.Never;
import org.xvm.xec.ecstasy.Appenderchar;
import org.xvm.xec.ecstasy.Orderable;
import org.xvm.xec.ecstasy.text.Stringable;
import org.xvm.xec.ecstasy.Orderable.Ordered;

/**
     Support XTC Int64
*/
public class Int64 extends IntNumber {
  static final int KID = GET_KID(new Int64(null));
  public int kid() { return KID; }
  public Int64(Never n ) { this(0); } // No-arg constructor
  
  final long _i;
  
  public Int64( long i ) { _i = i; }


  @Override public long hash() { return _i; }
  @Override public boolean equals( XTC x0, XTC x1 ) { throw XEC.TODO(); }
  @Override public Ordered compare( XTC x0, XTC x1 ) { throw XEC.TODO(); }

  @Override public final String toString() { return ""+_i; }
  @Override public long estimateStringLength() { return (64 - Long.numberOfLeadingZeros(_i)); }
  @Override public final Appenderchar appendTo(Appenderchar buf) {
    return buf.appendTo(toString());
  }
}
