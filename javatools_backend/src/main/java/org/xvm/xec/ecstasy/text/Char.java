package org.xvm.xec.ecstasy.text;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Const;
import org.xvm.xrun.Never;

public class Char extends Const {
  public static final Char GOLD = new Char();
  public Char(Never n) {}
  public Char() {}
  public static <E extends Char> boolean equals$Char( XTC gold, E ord0, E ord1 ) { return ord0==ord1; }
  @Override public long hash() { throw XEC.TODO(); }
}
