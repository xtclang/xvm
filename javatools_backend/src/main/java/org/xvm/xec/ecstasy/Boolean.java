package org.xvm.xec.ecstasy;

import org.xvm.xec.XTC;
import org.xvm.xrun.Never;

public class Boolean extends Enum {
  public static final Boolean GOLD = new Boolean(false);
  public Boolean(Never n) {_i=false;}
  private Boolean(boolean b) {_i=b;}
  public final boolean _i;
  public static Boolean TRUE = new Boolean(true), FALSE = new Boolean(false);
  public static <E extends java.lang.Boolean> boolean equals$Boolean( XTC gold, E ord0, E ord1 ) { return ord0==ord1; }
  public static Boolean make(boolean b) { return b ? TRUE : FALSE; }
  public static Boolean construct(boolean b) { return b ? TRUE : FALSE; }
  @Override public boolean equals ( XTC x0, XTC x1 ) { return x0==x1; } // Both are Boolean, interned
}
