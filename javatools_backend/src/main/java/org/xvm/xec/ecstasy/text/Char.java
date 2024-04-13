package org.xvm.xec.ecstasy.text;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Const;
import org.xvm.xec.ecstasy.Ordered;
import org.xvm.xrun.Never;
import org.xvm.xrun.XRuntime;

public class Char extends Const {
  public static final Char GOLD = new Char(null);
  public Char(Never n) {_i=(char)0;}
  public Char(char c) {_i=c;}
  public static Char make(char c) { return new Char(c); }
  public final char _i;
  public static <E extends Char> boolean equals$Char( XTC gold, E ord0, E ord1 ) { return ord0==ord1; }
  @Override public boolean equals ( XTC x0, XTC x1 ) { return ((Char)x0)._i == ((Char)x1)._i; }
  @Override public boolean equals( Object o ) {
    if( this==o ) return true;
    return o instanceof Char c && _i==c._i;
  }


  @Override public Ordered compare(XTC c0, XTC c1 ) { return compare$Char(GOLD,(Char)c0,(Char)c1); }
  public static Ordered compare$Char(Char gold, Char c0, Char c1 ) {
    if( c0._i==c1._i ) return Ordered.Equal;
    return c0._i < c1._i ? Ordered.Lesser : Ordered.Greater;
  }
  public static Ordered compare$Char(Char gold, char c0, char c1 ) {
    if( c0==c1 ) return Ordered.Equal;
    return c0 < c1 ? Ordered.Lesser : Ordered.Greater;
  }


  // ASCII digit check
  public long asciiDigit() {
    long x = _i-'0';
    return XRuntime.SET$COND(0 <= x && x <= 9,x);
  }
  // ASCII digit check
  public static long asciiDigit(char c) {
    long x = c-'0';
    return XRuntime.SET$COND(0 <= x && x <= 9,x);
  }
  // Unicode version of ASCII digit check
  public long decimalValue() {
    return asciiDigit();
  }

  public static String quoted(char c) {
    throw XEC.TODO();
  }

}
