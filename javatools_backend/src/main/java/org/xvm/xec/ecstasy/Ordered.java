package org.xvm.xec.ecstasy;

import org.xvm.xec.ecstasy.Enum;

public enum Ordered {
  Lesser,
  Equal, 
  Greater;
  public static final Ordered[] VALUES = values();
  public static final Enum GOLD = Enum.GOLD; // Dispatch against Ordered class same as Enum class
  public static boolean equals$Ordered( Enum gold, Ordered o0, Ordered o1 ) { return o0==o1; }
}
