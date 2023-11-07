package org.xvm.cc_explore.xrun;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.Ary;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.xclz.XClz;
import org.xvm.cc_explore.xclz.XType;
import org.xvm.cc_explore.xclz.XClzBuilder;

public abstract class XConst extends XClz implements Comparable<XConst> {

  abstract public Ordered compare( XConst that );
  @Override public int compareTo( XConst o ) { return compare(o).ordinal()-1; }
  public boolean CompLt( XConst that ) { return compare(that)==Ordered.Lesser; }

  
  // Make several fixed constant class methods
  public static void make_meth( ClassPart clz, String methname, SB sb ) {
    switch( methname ) {
    case "equals"  : make_equals  (clz,sb); break;
    case "compare" : make_compare (clz,sb); break;
    case "hashCode": make_hashCode(clz,sb); break;
    default: throw XEC.TODO();
    }
  }

  /* Generate:
     public boolean equals( Object o ) {
       if( this==o ) return true;
       if( !(o instanceof CLAZZ that) ) return false;
       return fld0.equals(that.fld0) && fld1==that.fld1 && ... fldN.equals(that.fldN);
     }
  */
  static void make_equals( ClassPart clz, SB sb ) {
    String clzname = XClzBuilder.java_class_name(clz._name);    
    sb.ip("// Default equals").nl();
    sb.ip("public boolean equals( Object o ) {").nl().ii();
    sb.ip(  "if( this==o ) return true;").nl();
    sb.ip(  "if( !(o instanceof ").p(clzname).p(" that) ) return false;").nl();
    sb.ip(  "return ");
    boolean any=false;
    for( Part p : clz._name2kid.values() )
      if( p instanceof PropPart prop && (p._nFlags & Part.SYNTHETIC_BIT)!=0 && (any=true) ) {
        XType xt = XType.xtype(prop._con,false);
        xt.do_eq(sb.p(prop._name),"that."+prop._name).p(" && ");
      }
    if( any ) sb.unchar(4);
    else sb.p("true");
    sb.p(";").nl().di();
    sb.ip("}").nl();
  }

  private static boolean xeq(PropPart p) {
    return XType.xtype(p._con,false).primeq();
  }

  
  /* Generate:
     public Ordered compare( CLZ that ) {
       if( this==that ) return Equal;
       Ordered x;
       if( (x=fld0.compare(that.fld0)) != Equal ) return x;
       if( (x=fld1.compare(that.fld1)) != Equal ) return x;
       ...
       return Equal;
     }
  */
  static void make_compare( ClassPart clz, SB sb ) {
    String clzname = XClzBuilder.java_class_name(clz._name);    
    sb.ip("// Default compare").nl();
    sb.ip("public Ordered compare( XConst o ) {").nl().ii();
    sb.ip("if( this==o ) return Ordered.Equal;").nl();
    sb.ip(clzname).p(" that = (").p(clzname).p(")o;").nl();
    sb.ip("Ordered $x;").nl();

    Ary<PropPart> pps = new Ary<>(PropPart.class);
    for( Part p : clz._name2kid.values() )
      if( p instanceof PropPart prop && (p._nFlags & Part.SYNTHETIC_BIT)!=0 )
        pps.setX(prop._order,prop);
    for( PropPart prop : pps ) {
      String fld = prop._name;
      sb.ip(  "if( ($x=");
      if( xeq(prop) ) sb.p("XClz.spaceship(").p(fld).p(",that.").p(fld).p(")");
      else            sb.p(fld).p(".compare(that.").p(fld).p(")");
      sb.p(") != Ordered.Equal ) return $x;").nl();
    }
    sb.ip(  "return Ordered.Equal;").nl().di();
    sb.ip("}").nl();
  };

  /* Generate:
     public long hash() {
       return fld0.hash()^fld1^...^fldN.hash();
     }
     public int hashCode() { long h = hash(); return (int)((h>>32)^h); }
  */
  static void make_hashCode( ClassPart clz, SB sb ) {
    sb.ip("// Default hashCode").nl();
    sb.ip("public long hash() {").nl().ii();
    sb.ip(  "return ");
    boolean any=false;
    for( Part p : clz._name2kid.values() )
      if( p instanceof PropPart prop && (p._nFlags & Part.SYNTHETIC_BIT)!=0 && (any=true) )
        sb.p(prop._name).p(xeq(prop) ? "" : ".hash()").p(" ^ ");
    if( any ) sb.unchar(3);
    else sb.p("0xDEADBEEF");
    sb.p(";").nl().di();
    sb.ip("}").nl();
    sb.ip("public int hashCode() { long h = hash(); return (int)((h>>32)^h); }").nl();
  };

}
