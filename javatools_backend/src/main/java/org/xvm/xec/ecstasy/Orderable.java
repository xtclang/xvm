package org.xvm.xec.ecstasy;

import org.xvm.XEC;
import org.xvm.util.Ary;
import org.xvm.util.S;
import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Ordered;
import org.xvm.xtc.*;

// XTC Orderable adds 'compare'.
// Any XTC classes which implement XTC 'Orderable' should also implement Java
// 'compareTo'.
public interface Orderable extends org.xvm.xec.ecstasy.Comparable {

  // The fully dynamic compare lookup
  public static Ordered compare( XTC gold_type, XTC x0, XTC x1 ) {
    return gold_type.compare(x0,x1); // Dynamic against gold
  }

  public static Ordered compare$Orderable( XTC gold_type, XTC x0, XTC x1 ) {
    // Compare as Orderables, and both x0 and x1 have the same class as gold_type.
    // But this is the generic orderables compare.
    throw XEC.TODO();
  }
  
  // Require implementations define this, which can typically be done with a
  // default implementation.  This same signature appears in the XTC base
  // class, so we can do a v-call instead of an i-call.
  public abstract Ordered compare( XTC x0, XTC x1 );

  // If the XTC compiler knows 'this' and 'x1' are the same class it emits a
  // short-form compare call, to directly check <,<=,>,>=
  default boolean CompLt(Orderable ord) {
    return compare((XTC)this,(XTC)ord) == Ordered.Lesser;
  }

  /** Each implementation will define the above abstract equals as:
   *  {@code public Ordered compare(XTC x0, XTC x1) { return compare$CLZ((CLZ)x0,(CLZ)x1) } }
   * Each implemention will also define (commonly with a default gen'd code):
   *  {@code public Ordered compare$CLZ(CLZ x0, CLZ x1) { ... field-by-field or user-speced... } }
   *
   *  The default implementation never uses the gold argument, but user-defined
   *  equals have access to it.
  */
  
  /* Generate:
     public Ordered compare( XTC x0, XTC x1 ) { // Called by the fully dynamic lookup
       return compare$CLZ(GOLD,(CLZ)x0,(CLZ)x1);
     }
     public static Ordered compare$CLZ( XTC gold, CLZ x0, CLZ x1 ) { 
       if( x0==x1 ) return Equal;
       Ordered x;
       if( (x=x0.fld0.compare(x1.fld0)) != Equal ) return x;
       if( (x=x0.fld1.compare(x1.fld1)) != Equal ) return x;
       if( (spaceship(x0.fld2,x1.fld2)) != Equal ) return x;
       ...
       return Equal;
     }
  */
  static void make_compare_default( ClassPart clz, String clzname, SB sb ) {
    sb.ifmt("public static Ordered compare$%0( XTC gold, %0 x0,%0 x1 ) {\n",clzname).ii();
    sb.ip("if( x0==x1 ) return Ordered.Equal;\n");
    sb.ip("Ordered $x;\n");

    Ary<PropPart> pps = new Ary<>(PropPart.class);
    for( Part p : clz._name2kid.values() )
      if( p instanceof PropPart prop && S.find(clz._tnames,prop._name) == -1 && (p._nFlags & Part.STATIC_BIT)==0 )
        pps.setX(prop._order,prop);
    for( PropPart prop : pps ) {
      String fld = prop._name;
      sb.ip(  "if( ($x=");
      if( xeq(prop) ) { sb.p("Orderable.spaceship( x0.").p(fld).p(",x1.").p(fld).p(")");  ClzBuilder.add_import(XCons.ORDERABLE); }
      else            { sb.p("x0.").p(fld).p(".compare(x1.").p(fld).p(")"); }
      sb.p(") != Ordered.Equal ) return $x;\n");
    }
    sb.ip(  "return Ordered.Equal;\n").di();
    sb.ip("}\n");
  };
  // Just a default Java compare, when the user overwrites the Const default compare
  static void make_compare( String clzname, SB sb ) {
    sb.ip("// Default compare\n");
    sb.ip("public Ordered compare( XTC x0, XTC x1 ) {\n").ii();
    sb.ifmt("return compare$%0(GOLD,(%0)x0,(%0)x1);\n",clzname).di();
    sb.ip("}\n");    
  }
  private static boolean xeq(PropPart p) {
    return XType.xtype(p._con,false).is_jdk();
  }


  // Called via explicit "3 <=> 5"
  static Ordered spaceship(long x, long y) {
    if( x < y ) return Ordered.Lesser;
    if( x== y ) return Ordered.Equal;
    return Ordered.Greater;
  }

  static Ordered spaceship(String x, String y) {
    int o = x.compareTo(y);
    return o < 0 ? Ordered.Lesser
      : o > 0 ? Ordered.Greater
      : Ordered.Equal;
  }

  
  public static boolean lesser ( Ordered x ) { return x==Ordered.Lesser ; }
  public static boolean greater( Ordered x ) { return x==Ordered.Greater; }
}
