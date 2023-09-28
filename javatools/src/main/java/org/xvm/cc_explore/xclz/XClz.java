package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.xrun.*;

// Some kind of base class for a Java class that implements an XTC Module
public abstract class XClz {
  // Default mutability
  public Mutability mutability$get() { return Mutability.Constant; }

  // Trace
  public final <T> T TRACE() {
    return (T)this;
  }

  // Assert is always-on runtime test
  public static void xassert( boolean cond ) {
    if( !cond ) throw new AssertionError();
  }
  
  public enum Mutability {
    Constant,                   // Deeply immutable
    Persistant,                 // Odd name, but shallow immutable
    Fixed;                      // Tuples and arrays are fixed length, but mutable
    static final Mutability[] VALUES = values();
  }

}
