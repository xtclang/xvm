package org.xvm.xec;

import org.xvm.util.Ary;
import org.xvm.xrun.*;
import org.xvm.xec.ecstasy.Orderable.Ordered;
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
  public XTC( Never n ) {}      // No arg constructor
  public XTC() {}               // No arg constructor

  // --------------------------------------------------------------------------
  // Every XTC class has a unique integer Klass ID - a KID.  This is used for
  // dynamic lookups for {equals,compare,hashCode } at least.
  // Convert KID to a "Golden" blank instance of an XTC class.
  // The Golden instance can make virtual calls, but its fields are all 0/null.
  public static final Ary<XTC> GOLDS = new Ary<>(XTC.class);
  public static int KID_CNT = 1; // Unique KID counter
  abstract public int kid();     // Virtual call to get the KID from an instance
  /** Generated in all classes
     static final int KID = GET_KID();
     public int kid() { return KID; }
   */
  public static int GET_KID( XTC gold ) {
    int kid = KID_CNT++;
    GOLDS.setX(kid,gold);
    return kid;
  }

  
  // --------------------------------------------------------------------------
  // A bunch of classes and functions that are always available (e.g. TRACE
  // from asserts), or defined in ecstasy.x, or needed for the Java port.

  // Ecstasy's normal "equals" call calls the "equals" from "clz" and not a
  // subclass implementation.  This requires a runtime lookup, unless clz is a
  // constant.  This call is done in the Comparable interface, but it uses this
  // signature so can use a java virtual call instead of a java interface call.
  // This will only be called with two Comparables.
  public boolean equals( XTC x0, XTC x1 ) { throw XEC.TODO(); }

  // Ecstasy's normal "compare" call calls the "compare" from "clz" and not a
  // subclass implementation.  This requires a runtime lookup, unless clz is a
  // constant.  This call is done in the Orderable interface, but it uses this
  // signature so can use a java virtual call instead of a java interface call.
  // This will only be called with two Orderables.
  public Ordered compare( XTC x0, XTC x1 ) { throw XEC.TODO(); }

  // TODO: this should move into e.g. Const, but Java doesn't understand
  //                GOLDS[Enum.idx].equals(...);
  // TODO: Instead: Enum.GOLD      .equals(...)  should work
  public boolean equals(Ordered x0, Ordered x1) { return x0==x1; }
  
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
