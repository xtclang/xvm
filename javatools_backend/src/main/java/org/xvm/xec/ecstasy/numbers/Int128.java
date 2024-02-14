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

  static final Int128 ZERO = new Int128(0,0);
  public final long _lo, _hi; // low, high

  public Int128(long lo, long hi) { _lo=lo; _hi=hi; }
  public Int128(long lo) { _lo=lo; _hi=0; }

  public static Int128 construct(long lo) { return lo==0 ? ZERO : new Int128(lo); }

  public Int128 add( long x ) { throw XEC.TODO(); }
  public Int128 sub( long x ) { throw XEC.TODO(); }
  public Int128 mul( long x ) { throw XEC.TODO(); }
  public Int128 div( long x ) { throw XEC.TODO(); }
  public Int128 add( Int128 x ) { throw XEC.TODO(); }
  public Int128 sub( Int128 x ) { throw XEC.TODO(); }
  public Int128 mul( Int128 x ) {
    long lo = _lo*x._lo;
    long hi = _hi*x._hi + Math.multiplyHigh(_lo,x._lo) + _lo*x._hi + _hi*x._lo;
    return new Int128(lo,hi);
  }
  public Int128 div( Int128 x ) { throw XEC.TODO(); }
  public boolean eq( Int128 x ) { throw XEC.TODO(); }
  public boolean gt( Int128 x ) { return _hi != x._hi ? (_hi >  x._hi) : (_lo >  x._lo); }
  public boolean ge( Int128 x ) { return _hi != x._hi ? (_hi >= x._hi) : (_lo >= x._lo); }
  
  Array<Bit> toBitArray(Array.Mutability mut) { throw XEC.TODO(); }
  public Dec128 toDec128() { throw XEC.TODO(); }
  
  @Override public long hashCode( XTC x ) { throw XEC.TODO(); }
  @Override public boolean equals ( XTC x0, XTC x1 ) { throw XEC.TODO(); }
  @Override public Ordered compare( XTC x0, XTC x1 ) { throw XEC.TODO(); }

  @Override public final String toString() {
    if( _hi != (_lo>>63) )
      throw XEC.TODO();
    return Long.toString(_lo);
  }
  @Override public long estimateStringLength() { return 128; }
  @Override public final Appenderchar appendTo(Appenderchar buf) {
    return buf.appendTo(toString());
  }
}
