package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.xrun.*;

// Some kind of base class for a Java class that implements an XTC Module
public abstract class XClz<X extends XClz<X>> {
  // Default mutability
  public Mutability mutability$get() { return Mutability.Constant; }

  // Trace
  public final X TRACE() {
    return (X)this;
  }

  // Assert is always-on runtime test
  public static void xassert( boolean cond ) {
    if( !cond ) throw new AssertionError();
  }
  
  public enum Mutability {
    Constant,                   // Deeply immutable
    Persistent,                 // Odd name, but shallow immutable
    Fixed;                      // Tuples and arrays are fixed length, but mutable
    public static final Mutability[] VALUES = values();
  }

  
  public enum Ordered {
    Lesser,
    Equal, 
    Greater;
    public static final Ordered[] VALUES = values();
  }

  public static Ordered spaceship(long x, long y) {
    if( x < y ) return Ordered.Lesser;
    if( x== y ) return Ordered.Equal;
    return Ordered.Greater;
  }

}
