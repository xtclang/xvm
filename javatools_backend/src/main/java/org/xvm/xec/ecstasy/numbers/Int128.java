package org.xvm.xec.ecstasy.numbers;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Appenderchar;
import org.xvm.xec.ecstasy.Orderable;
import org.xvm.xec.ecstasy.Ordered;
import org.xvm.xec.ecstasy.collections.Array;
import org.xvm.xec.ecstasy.numbers.Dec128;
import org.xvm.xec.ecstasy.text.Stringable;
import org.xvm.xrun.Never;

/**
     Support XTC Int128
*/
public class Int128 extends IntNumber {
  public static final Int128 GOLD = new Int128(null);
  public Int128(Never n ) { this(0,0); } // No-arg constructor

  public final long _i0, _i1; // low, high

  public Int128(long l0, long l1) { _i0=l0; _i1=l1; }
  public Int128(long lo) { _i0=lo; _i1=0; }

  public static Int128 construct(long lo) { return new Int128(lo); }

  public Int128 add( long x ) { throw XEC.TODO(); }
  public Int128 mul( long x ) { throw XEC.TODO(); }
  public Int128 div( long x ) { throw XEC.TODO(); }
  public Int128 add( Int128 x ) { throw XEC.TODO(); }
  public Int128 sub( Int128 x ) { throw XEC.TODO(); }
  public Int128 mul( Int128 x ) { throw XEC.TODO(); }
  
  Array<Bit> toBitArray(Array.Mutability mut) { throw XEC.TODO(); }
  public Dec128 toDec128() { throw XEC.TODO(); }
  
  @Override public long hashCode( XTC x ) { throw XEC.TODO(); }
  @Override public boolean equals ( XTC x0, XTC x1 ) { throw XEC.TODO(); }
  @Override public Ordered compare( XTC x0, XTC x1 ) { throw XEC.TODO(); }

  @Override public final String toString() { throw XEC.TODO(); }
  @Override public long estimateStringLength() { return 128; }
  @Override public final Appenderchar appendTo(Appenderchar buf) {
    return buf.appendTo(toString());
  }
}
