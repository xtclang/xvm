package org.xvm.xec.ecstasy.text;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Const;
import org.xvm.xrun.Never;
import org.xvm.xrun.XRuntime;

public class Char extends Const {
  public static final Char GOLD = new Char(null);
  public Char(Never n) {_c=(char)0;}
  public Char(char c) {_c=c;}
  public static Char make(char c) { return new Char(c); }
  public final char _c;
  public static <E extends Char> boolean equals$Char( XTC gold, E ord0, E ord1 ) { return ord0==ord1; }
  @Override public boolean equals ( XTC x0, XTC x1 ) { return ((Char)x0)._c == ((Char)x1)._c; }

  // ASCII digit check
  public long asciiDigit() {
    long x = _c-'0';
    return XRuntime.SET$COND(0 <= x && x <= 9,x);
  }

}
