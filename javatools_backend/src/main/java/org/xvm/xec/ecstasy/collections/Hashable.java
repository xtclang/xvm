package org.xvm.xec.ecstasy.collections;

import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xtc.*;

public interface Hashable extends org.xvm.xec.ecstasy.Comparable {

  abstract long hash();
  
  /* Generate:
     public long hash() {
       return fld0.hash()^fld1^...^fldN.hash();
     }
     public static long hash( Class<CLZ> clz, CLZ x ) { return x.hash(); }
  */
  static void make_hashCode_default( ClassPart clz, String clzname, SB sb ) {
    sb.ip("public long hash() {\n").ii();
    sb.ip(  "return ");
    boolean any=false;
    for( Part p : clz._name2kid.values() )
      if( p instanceof PropPart prop && (p._nFlags & Part.SYNTHETIC_BIT)!=0 && (any=true) ) {
        sb.p(prop._name);
        XType xt = XType.xtype(prop._con,false);
        if( xt.primeq() ) ; // Numbers as themselves for hash
        else if( xt.is_jdk() ) sb.p(".hashCode()");
        else sb.p(".hash()");
        sb.p(" ^ ");
      }
    if( any ) sb.unchar(3);
    else sb.p("0xDEADBEEF");
    sb.p(";\n").di();
    sb.ip("}\n");
  };
  static void make_hashCode( String clzname, SB sb ) {
    // Static XTC inverted hashCode
    sb.ip("// Default hashCode\n");
    sb.ifmt("public static long hash( %0 x ) { return x.hash(); }\n",clzname);
  }

  // Java 32-bit hashCode() wrapping XTC 64-bit hash()
  //public int hashCode() { long h = hash(); return (int)((h>>32)^h); }

}
