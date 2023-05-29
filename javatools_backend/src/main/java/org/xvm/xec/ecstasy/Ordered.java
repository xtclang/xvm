package org.xvm.xec.ecstasy;

import org.xvm.xec.ecstasy.Enum;

public enum Ordered {
  Lesser,
  Equal, 
  Greater;
  public static final Ordered[] VALUES = values();
  public static final Enum GOLD = Enum.GOLD; // Dispatch against Ordered class same as Enum class
}
