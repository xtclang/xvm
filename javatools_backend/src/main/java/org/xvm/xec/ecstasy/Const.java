package org.xvm.xec.ecstasy;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.text.Stringable;
import org.xvm.util.Ary;
import org.xvm.util.SB;
import org.xvm.xtc.*;

public abstract class Const extends XTC
  implements Comparable<Const>,  // Java Comparable; XTC Comparable has equals, which is included in Java Object
             Stringable           // has appendTo
{
  @Override public int compareTo( Const o ) { return compare(o).ordinal()-1; }
  public boolean CompLt( Const that ) { return compare(that)==Ordered.Lesser; }

  
  // Make several fixed constant class methods
  public static void make_meth( ClassPart clz, String methname, SB sb ) {
    switch( methname ) {
    case "compare" : make_compare (clz,sb); break;
    case "equals"  : make_equals  (clz,sb); break;
    case "hashCode": make_hashCode(clz,sb); break;
    case "toString": make_toString(clz,sb); break;
    default: throw XEC.TODO();
    }
  }
  
  /* Generate:
     public Ordered compare( XTC that ) {
       return getClass().isInstance(that) ? compare$CLAZZ((CLAZZ)that) : that.compare(this);
     }
     public Ordered compare$CLAZZ( CLZ that ) {
       if( this==that ) return Equal;
       Ordered x;
       if( (x=fld0.compare(that.fld0)) != Equal ) return x;
       if( (x=fld1.compare(that.fld1)) != Equal ) return x;
       ...
       return Equal;
     }
  */
  static void make_compare( ClassPart clz, SB sb ) {
    String clzname = ClzBuilder.java_class_name(clz._name);
    sb.ip("// Default compare\n");
    sb.ip("public Ordered compare( XTC that ) {\n").ii();
    sb.ifmt("return getClass().isInstance(that) ? compare$%0((%0)that) : that.compare(this);",clzname).nl().di();
    sb.ip("}\n");
    
    sb.ifmt("public Ordered compare$%0( %0 that ) {\n",clzname).ii();
    sb.ip("if( this==that ) return Ordered.Equal;\n");
    sb.ip("Ordered $x;\n");

    Ary<PropPart> pps = new Ary<>(PropPart.class);
    for( Part p : clz._name2kid.values() )
      if( p instanceof PropPart prop && (p._nFlags & Part.SYNTHETIC_BIT)!=0 )
        pps.setX(prop._order,prop);
    for( PropPart prop : pps ) {
      String fld = prop._name;
      sb.ip(  "if( ($x=");
      if( xeq(prop) ) sb.p("XTC.spaceship(").p(fld).p(",that.").p(fld).p(")");
      else            sb.p(fld).p(".compare(that.").p(fld).p(")");
      sb.p(") != Ordered.Equal ) return $x;\n");
    }
    sb.ip(  "return Ordered.Equal;\n").di();
    sb.ip("}\n");
  };

  /* Generate:
     public boolean equals( Object o ) {
       return o instanceof CLAZZ that && equals$CLAZZ(that);
     }
     public final boolean equals$CLAZZ( CLAZZ that ) { 
       if( this==that ) return true;
       return fld0.equals(that.fld0) && fld1==that.fld1 && ... fldN.equals(that.fldN);
     }
  */
  static void make_equals( ClassPart clz, SB sb ) {
    String clzname = ClzBuilder.java_class_name(clz._name);
    sb.ip("// Default equals\n");
    sb.ip("public boolean equals( Object o ) {\n").ii();
    sb.ifmt("return o instanceof %0 that && equals$%0(that);\n",clzname).di();
    sb.ip("}\n");    
    sb.ifmt("public final boolean equals$%0( %0 that ) {\n",clzname).ii();
    sb.ip(  "if( this==that ) return true;\n");
    sb.ip(  "return ");
    boolean any=false;
    for( Part p : clz._name2kid.values() )
      if( p instanceof PropPart prop && (p._nFlags & Part.SYNTHETIC_BIT)!=0 && (any=true) ) {
        XType xt = XType.xtype(prop._con,false);
        xt.do_eq(sb.p(prop._name),"that."+prop._name).p(" && ");
      }
    if( any ) sb.unchar(4);
    else sb.p("true");
    sb.p(";\n").di();
    sb.ip("}\n");
  }

  private static boolean xeq(PropPart p) {
    return XType.xtype(p._con,false).is_jdk();
  }

  /* Generate:
     public long hash() {
       return fld0.hash()^fld1^...^fldN.hash();
     }
     public int hashCode() { long h = hash(); return (int)((h>>32)^h); }
  */
  static void make_hashCode( ClassPart clz, SB sb ) {
    sb.ip("// Default hashCode\n");
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
    sb.ip("public int hashCode() { long h = hash(); return (int)((h>>32)^h); }\n");
  };


  /* Generate:
     @Override public String toString() {
       SB sb = new SB().p("(");
       sb.p(fld0.toString()).p(",");
       sb.p( fld_prim      ).p(",");
       sb.p(fldN.toString()).p(");");
       return sb.toString();
     }
  */
  static void make_toString( ClassPart clz, SB sb ) {
    sb.ip("// Default toString\n");
    sb.ip("@Override public String toString() {\n").ii();
    sb.ip("StringBuilder sb = new StringBuilder().append(\"(\");\n");
    Ary<PropPart> pps = new Ary<>(PropPart.class);
    for( Part p : clz._name2kid.values() )
      if( p instanceof PropPart prop && (p._nFlags & Part.SYNTHETIC_BIT)!=0 )
        pps.setX(prop._order,prop);
    for( int i=0; i<pps._len; i++ ) {
      sb.ip("sb.append(").p(pps._es[i]._name);
      if( !xeq(pps._es[i]) ) sb.ip(".toString()");
      sb.p(").append(\"").p(i<pps._len-1 ? "," : ")").p("\");\n");
    }
    sb.ip("return sb.toString();\n").di();
    sb.ip("}\n");
  };

  // Default appendTo
  @Override public Appenderchar appendTo(Appenderchar buf) {
    return buf.appendTo(toString());
  }
}
