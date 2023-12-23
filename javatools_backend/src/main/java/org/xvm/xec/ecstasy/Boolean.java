package org.xvm.xec.ecstasy;

import org.xvm.xec.XTC;
import org.xvm.xrun.Never;

public class Boolean extends Enum {
  public static final Boolean GOLD = new Boolean();
  public Boolean(Never n) {}
  public Boolean() {}
  public static <E extends java.lang.Boolean> boolean equals$Boolean( XTC gold, E ord0, E ord1 ) { return ord0==ord1; }
}
