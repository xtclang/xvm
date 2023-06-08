package org.xvm.xec.ecstasy;

import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xtc.*;

// Not-The-Java Comparable.  XTC Comparable adds 'equals' not 'compareTo'.
public interface Comparable {

  // The fully dynamic comparable lookup
  public static boolean equals( int czidx, XTC x0, XTC x1 ) {
    return XTC.GOLDS.at(czidx).equals(x0,x1); // Dynamic against gold
  }

  // Require implementations define this, which can typically be done with a
  // default implementation.  This same signature appears in the XTC base
  // class, so we can do a v-call instead of an i-call.
  public abstract boolean equals( XTC x0, XTC x1 );

  /** Each implementation will define the above abstract equals as:
   *  {@code public boolean equals(XTC x0, XTC x1) { return equals$CLZ((CLZ)x0,(CLZ)x1) } }
   * Each implemention will also define (commonly with a default gen'd code):
   *  {@code public boolean equals$CLZ(CLZ x0, CLZ x1) { ... field-by-field or user-speced... } }
  */

  /* Generate:
     public boolean equals( XTC x0, XTC x1 ) { // Called by the fully dynamic lookup
       return equals$CLZ(x0,x1);
     }
     public static boolean equals$CLZ( int clzid, CLZ x0, CLZ x1 ) { 
       if( x0==x1 ) return true;
       return x0.fld0.equals(x1.fld0) && x0.fld1==x1.fld1 && ... x0.fldN.equals(x1.fldN);
     }
  */
  static void make_equals_default( ClassPart clz, String clzname, SB sb ) {
    sb.ifmt("public static boolean equals$%0( int clzid, %0 x0, %0 x1 ) {\n",clzname).ii();
    sb.ip(  "if( x0==x1 ) return true;\n");
    sb.ip(  "return ");
    boolean any=false;
    for( Part p : clz._name2kid.values() )
      if( p instanceof PropPart prop && (p._nFlags & Part.SYNTHETIC_BIT)!=0 && (any=true) ) {
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
    sb.ifmt("return equals$%0(KID,(%0)x0,(%0)x1);\n",clzname).di();
    sb.ip("}\n");    
  }  
}