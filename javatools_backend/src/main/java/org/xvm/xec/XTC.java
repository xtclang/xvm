package org.xvm.xec;

import org.xvm.util.Ary;
import org.xvm.xrun.*;
import org.xvm.xec.ecstasy.collections.Array.Mutability;
import org.xvm.XEC;
import org.xvm.xec.ecstasy.Ordered;

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

  // Ecstasy's normal "hash" call calls the "hash" from "clz" and not a
  // subclass implementation.  This requires a runtime lookup, unless clz is a
  // constant.  This call is done in the Hashable interface, but it uses this
  // signature so can use a java virtual call instead of a java interface call.
  // This will only be called with a Hashable.
  public long hashCode( XTC x ) { throw XEC.TODO(); }
  
  
  // Default mutability
  public Mutability mutability$get() { return Mutability.Constant; }

  // Trace
  public static <X> X TRACE( X x ) { return x; }
  public static String TRACE(String x) { return x; }
  public static long TRACE(long x) { return x; }
  public static int  TRACE(int  x) { return x; }
  public static boolean TRACE(boolean x) { return x; }

  // Assert is always-on runtime test
  public static void xassert( boolean cond ) {
    if( !cond )
      throw new IllegalState("");
  }
  public static void xassert( boolean cond, String msg ) {
    if( !cond )
      throw new IllegalState(msg);
  }
  public static void xassert( ) { xassert(false); }

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

  public static class Exception extends RuntimeException {
    public Exception(String msg) {super(msg); }
    public Exception() { }
    public static Exception construct(String s) { return new Exception(s); }
    public String message$get() { return getMessage(); };
  }
  

  // XTC IllegalState mapped to Java
  public static class IllegalState extends Exception {
    public IllegalState(String msg) {super(msg); }
    public static IllegalState construct(String s) { return new IllegalState(s); }
  }

  // XTC IllegalArgument mapped to Java
  public static class IllegalArgument extends Exception {
    public IllegalArgument(String s) { super(s); }
    public static IllegalArgument construct(String s) { return new IllegalArgument(s); }
  }

  // XTC ReadOnlyException mapped to Java
  public static class ReadOnlyException extends Exception { }

  // XTC NotImplemented mapped to Java
  public static class NotImplemented extends Exception {}
}
