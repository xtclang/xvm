package org.xvm.xec;

import org.xvm.xrun.*;
import org.xvm.XEC;

import java.io.IOException;

// The base "ecstasy.x" module - as a Java class.
//
// Why "XTC" and not "ecstasy"?
//
// Since Java does not allow a class to be named the same as a package, and a
// package is really just a directory - naming this class "ecstasy" conflicts
// with the package/directory of the same name.

public abstract class XTC {
  // Everybody gets a container injected.
  // TODO: This is probably too expensive, and needs to be just for X code
  // which @Injects.
  public final Container _container;
  public XTC( ) { this(null); }
  public XTC( Container container ) { _container = container; }


  // --------------------------------------------------------------------------
  // A bunch of classes and functions that are always available (e.g. TRACE
  // from asserts), or defined in ecstasy.x, or needed for the Java port.

  // Ecstasy's normal "equals" call calls the "equals" from "clz" and not a
  // subclass implementation.  This requires a runtime lookup of equals, if
  // there are several.


  // Theory on a "free" fast version.
  // Classes always define a "equals$CLZ" along with a custom "equals" (note: Const defines a custom equals)
  //     class Base extends XTC {
  //       boolean x0.equals$Base(Base x1);
  //     }
  // If the calling type is a constant, use it directly:
  //     x0.equals$CONSTANT_CLZ(x1);
  public static boolean equals( Class clz, XTC x0, XTC x1 ) {
    throw XEC.TODO();
  }

  // Ecstasy's normal "compare" call calls the "compare" from "clz" and not a
  // subclass implementation.  This requires a runtime lookup of compare, if
  // there are several.
  public static Ordered compare( Class clz, XTC x0, XTC x1 ) {
    throw XEC.TODO();
  }

  // Default mutability
  public Mutability mutability$get() { return Mutability.Constant; }

  // Trace
  public static <X extends XTC> X TRACE( X x ) { return x; }
  public static Ordered TRACE( Ordered x ) { return x; }
  public static String TRACE(String x) { return x; }
  public static long TRACE(long x) { return x; }
  public static int  TRACE(int  x) { return x; }

  // Assert is always-on runtime test
  public static void xassert( boolean cond ) {
    if( !cond ) throw new IllegalStateX();
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

  public static Ordered spaceship(String x, String y) {
    int o = x.compareTo(y);
    return o < 0 ? Ordered.Lesser
      : o > 0 ? Ordered.Greater
      : Ordered.Equal;
  }
  
  public static Ordered spaceship( XTC x, XTC y ) {
    return x.getClass().isInstance(y) ? x.compare(y) : y.compare(x);
  }

  public Ordered compare(XTC that) {
    if( this==that ) return Ordered.Equal;
    throw new IllegalArgumentException("comparing non-orderables");
  }
    
  /** --------------------------------------------------------------------------
      <ul>
      <li>XTC {@code IllegalStateException} is thrown by {@code assert} and by {@code close}.</li>

      <li>Java {@code assert} throws {@link AssertionError}.</li>

      <li>Java {@code close} throws {@link IOException}.</li>

      <li>I don't see/expect any catches of assert exceptions, except for demo
      purposes, and possibly a few times in some testing harness. </li>

      <li>I DO see exactly 2 catches of IllegalState (in the JSONDB Catalog.x).
      I might expect more from users, used to catching close fails.</li>
      
      <li>Since the exception throw by XTC {@code assert} and {@code close} are
      the same thing, and can be caught by the same thing, I need to force the
      matching Java exceptions to map to some same thing, so they can be caught
      by whatever I map XTC's {@code IllegalStateException} to.</li>

      <li>This means I need to merge Java's {@link IOException} and Java's
      {@link AssertionError}.</li>

      <li>That means I need to catch every Java {@code close} call or every
      Java {@code assert} call (or both), and remap the exception.</li>

      <li>Right now, every XTC {@code assert} is mapped to a Java {@code assert};
      and this means I might wrap (in Java) for every XTC {@code
      assert}... <em>OR</em></li>

      <li>when I call the Java mapping for every XTC {@code close}, I'll have
      to catch Java's obvious {@link IOException} and map it.</li>

      <li>There's a lot less wrapping of exceptions going on, if we can
      unbundle XTC assert's {@code IllegalStateException} from XTC close's
      {@code IllegalStateException}.</li>
      
      </ul>
      So I am Once Again, asking for a language change: make the XTC assert
      throw e.g. AssertionError instead of IllegalStateException.
  */
  // XTC IllegalStateException mapped to Java
  public static class IllegalStateX extends RuntimeException {}

  // XTC ReadOnlyException mapped to Java
  public class ReadOnlyException extends RuntimeException {}
  
}
