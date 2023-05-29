package org.xvm.xec.ecstasy.numbers;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Appenderchar;
import org.xvm.xec.ecstasy.Const;
import org.xvm.xec.ecstasy.Sequential;
import org.xvm.xrun.Never;
import org.xvm.xec.ecstasy.collections.Hashable;
import org.xvm.xec.ecstasy.text.Stringable;

/**
     Support XTC Bit
*/
public class Bit extends Const implements Sequential, IntConvertible {
  public static final Bit GOLD = new Bit(null);
  public Bit(Never n ) { this(0); } // No-arg constructor

  final boolean _b;

  public Bit(long x) { _b = x==0 ? false : true; }
  
  @Override public long hashCode( XTC x ) { throw XEC.TODO(); }
  @Override public boolean equals ( XTC x0, XTC x1 ) { throw XEC.TODO(); }

  @Override public final String toString() { return _b ? "1" : "0"; }
  @Override public long estimateStringLength() { return 1; }
  @Override public final Appenderchar appendTo(Appenderchar buf) {
    return buf.appendTo(toString());
  }
}
