package org.xvm.xec.ecstasy;

import org.xvm.util.S;
import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xtc.*;

// Not-The-Java Comparable.  XTC Comparable adds 'equals' not 'compareTo'.
public interface Comparable {

  // The fully dynamic equals lookup, based on the given compile-time class
  static boolean equals( XTC gold_type, XTC x0, XTC x1 ) {
    return gold_type.equals(x0,x1); // Dynamic against gold
  }

  // Require implementations define this, which can typically be done with a
  // default implementation.  This same signature appears in the XTC base
  // class, so we can do a v-call instead of an i-call.
  boolean equals( XTC x0, XTC x1 );

  // If the XTC compiler knows 'this' and 'x1' are the same class it emits a
  // short-form equals call in which dispatch to either side is ok.  
  default boolean equals( XTC x1 ) { return equals((XTC)this,x1); }
  
  /** Each implementation will define the above abstract equals as:
   *  {@code public boolean equals(XTC x0, XTC x1) { return equals$CLZ((CLZ)x0,(CLZ)x1) } }
   * Each implemention will also define (commonly with a default gen'd code):
   *  {@code public boolean equals$CLZ(CLZ x0, CLZ x1) { ... field-by-field or user-speced... } }
   *
   *  The default implementation never uses the gold argument, but user-defined
   *  equals have access to it.
   */

  /* Generate:
     public boolean equals( XTC x0, XTC x1 ) { // Called by the fully dynamic lookup
       return equals$CLZ(GOLD,(CLZ)x0,(CLZ)x1);
     }
     public static boolean equals$CLZ( XTC gold, CLZ x0, CLZ x1 ) { 
       if( x0==x1 ) return true;
       return x0.fld0.equals(x1.fld0) && x0.fld1==x1.fld1 && ... x0.fldN.equals(x1.fldN);
     }
  */
  static void make_equals_default( ClassPart clz, String clzname, SB sb ) {
    sb.ifmt("public static boolean equals$%0( XTC gold, %0 x0, %0 x1 ) {\n",clzname).ii();
    sb.ip(  "if( x0==x1 ) return true;\n");
    sb.ip(  "return ");
    boolean any=false;
    for( Part p : clz._name2kid.values() )
      if( p instanceof PropPart prop && S.find(clz._tnames,prop._name) == -1 && (p._nFlags & Part.STATIC_BIT)==0 && (any=true) ) {
        XType xt = XType.xtype(prop._con,false);
        xt.do_eq(sb.p("x0.").p(prop._name),"x1."+prop._name).p(" && ");
      }
    if( any ) sb.unchar(4);
    else sb.p("true");
    sb.p(";\n").di();
    sb.ip("}\n");
  }
  // Just a default Java equals, when the user overwrites the Const default equals
  public static void make_equals( String clzname, SB sb ) {
    sb.ip("// Default equals\n");
    sb.ip("public boolean equals( XTC x0, XTC x1 ) {\n").ii();
    sb.ifmt("return equals$%0(GOLD,(%0)x0,(%0)x1);\n",clzname).di();
    sb.ip("}\n");    
  }
}
