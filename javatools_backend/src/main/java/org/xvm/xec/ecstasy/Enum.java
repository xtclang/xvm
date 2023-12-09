package org.xvm.xec.ecstasy;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xrun.Never;
import org.xvm.xec.ecstasy.Orderable.Ordered;

public class Enum extends Const {
  public static final Enum GOLD = new Enum();
  public Enum(Never n) {}
  public Enum() {}
  public static <E extends java.lang.Enum> boolean equals$Enum( XTC gold, E ord0, E ord1 ) { return ord0==ord1; }
  @Override public long hash() { return 0; }
}
