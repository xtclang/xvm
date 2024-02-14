package org.xvm.xec.ecstasy.collections;

import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xtc.*;

public interface Hashable extends org.xvm.xec.ecstasy.Comparable {

  // The fully dynamic hash lookup
  public static long hashCode$Hashable( XTC gold_type, Hashable x ) {
    return gold_type.hashCode((XTC)x); // Dynamic against gold
  }
  
  // The fully dynamic hash lookup
  public static boolean equals$Hashable( XTC gold_type, Hashable x0, Hashable x1 ) {
    return gold_type.equals((XTC)x0,(XTC)x1); // Dynamic against gold
  }


  // Require implementations define this, which can typically be done with a
  // default implementation.  This same signature appears in the XTC base
  // class, so we can do a v-call instead of an i-call.
  public abstract long hashCode( XTC x );

  /* Generate:
     public long hash(XTC x0) { // Called by the fully dynamic lookup
       return hash$CLZ(GOLD,(CLZ)x0);
     }
     public static long hash$CLZ( XTC gold, CLZ x ) {
       return x.fld0.hash()^x.fld1^...^x.fldN.hash();
     }
  */
  static void make_hashCode_default( ClassPart clz, String clzname, SB sb ) {
    sb.ifmt("public static long hashCode$%0(XTC gold, %0 x) {\n",clzname).ii();
    sb.ip(    "return ");
    boolean any=false;
    for( Part p : clz._name2kid.values() )
      // TODO: Probably static not synthetic
      if( p instanceof PropPart prop && p.isSynthetic() && !p.isStatic() && (any=true) ) {
        sb.p("x.").p(prop._name);
        XType xt = XType.xtype(prop._con,false);
        if( !xt.zero() )        // Numbers as themselves for hash
          sb.p(".hashCode()");  // Others call hashCode
        sb.p(" ^ ");
      }
    if( any ) sb.unchar(3);
    else sb.p("0xDEADBEEF");
    sb.p(";\n").di();
    sb.ip("}\n");
  };
  static void make_hashCode( String clzname, SB sb ) {
    sb.ip("// Default hash\n");
    sb.ifmt("public long hashCode( XTC x ) { return hashCode$%0(GOLD,(%0)x); }\n",clzname);
  }

  // Java 32-bit hashCode() wrapping XTC 64-bit hash()
  //public int hashCode() { long h = hash(); return (int)((h>>32)^h); }
}
