package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.xrun.*;

// Some kind of base class for a Java class that implements an XTC Module
public abstract class XClz {
  // Default mutability
  public Mutability mutability$get() { return Mutability.Constant; }

  // Trace
  public static <X extends XClz> X TRACE(X x) { return x; }
  public static long TRACE(long x) { return x; }
  public static int  TRACE(int  x) { return x; }

  // Assert is always-on runtime test
  public static void xassert( boolean cond ) {
    if( !cond ) throw new AssertionError();
  }
  public static void xassert( ) { xassert(false); }
  
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
