package org.xvm.xec.ecstasy.reflect;

import org.xvm.xec.ecstasy.Const;
import org.xvm.xrun.Never;
import org.xvm.xec.XTC;

public class Argument extends Const {
  public static final Argument GOLD = new Argument();
  public Argument(Never n) {}
  public Argument() {}
  // --- Comparable
  @Override public boolean equals( XTC x0, XTC x1 ) { throw org.xvm.XEC.TODO(); }  
}
