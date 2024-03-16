package org.xvm.xec.ecstasy.reflect;

import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Const;
import org.xvm.xrun.Never;

public class Type extends Const {
  public static final Type GOLD = new Type();
  public Type(Never n) {}
  public Type() {}

  // --- Comparable
  public static boolean equals$Type( XTC gold, XTC gold0, XTC gold1 ) { return gold0==gold1; }
  @Override public boolean equals( XTC x0, XTC x1 ) { throw org.xvm.XEC.TODO(); }  
}
