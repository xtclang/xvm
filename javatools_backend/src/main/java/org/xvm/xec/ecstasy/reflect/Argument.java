package org.xvm.xec.ecstasy.reflect;

import org.xvm.xec.ecstasy.Const;
import org.xvm.xrun.Never;

public class Argument extends Const {
  public static final Argument GOLD = new Argument();
  public Argument(Never n) {}
  public Argument() {}
  @Override public long hash() { return 0; }  
}
