package org.xvm.xec.ecstasy;

import org.xvm.xec.XTC;
import org.xvm.xrun.Never;

/** The Java Enum class, implementing an XTC hidden internal class.
    The XTC Enum is an *XTC interface*, not an XTC class, but it has many class-like properties.
    
    The XTC Enum interface is thus treated like this Java Class.
 */
public class Enum extends Const {
  public static final Enum GOLD = new Enum();
  public Enum() {}              // Explicit no-arg-no-work constructor
  public Enum(Never n) {}       // Forced   no-arg-no-work constructor

  // --- Comparable
  public boolean equals( XTC x0, XTC x1 ) { throw org.xvm.XEC.TODO(); }
  
  public static <E extends java.lang.Enum> boolean equals$Enum( XTC gold, E       ord0, E       ord1 ) { return ord0==ord1; }
  public static <E extends java.lang.Enum> boolean equals$Enum( XTC gold, boolean ord0, boolean ord1 ) { return ord0==ord1; }
  
}
