package org.xvm.xec.ecstasy;

import org.xvm.XEC;
import org.xvm.util.Ary;
import org.xvm.util.SB;
import org.xvm.xrun.Never;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.collections.Hashable;
import org.xvm.xec.ecstasy.text.Stringable;
import org.xvm.xtc.*;

public abstract class Const extends XTC
  implements org.xvm.xec.ecstasy.Orderable, // XTC comparable adds compare,equals
             Hashable,                      // XTC hashCode
             Stringable                     // has appendTo
{
  public Const(Never n) {}
  public Const() {}
  
  /* Generate:
     @Override public String toString() {
       SB sb = new SB().p("(");
       sb.p(fld0.toString()).p(",");
       sb.p( fld_prim      ).p(",");
       sb.p(fldN.toString()).p(");");
       return sb.toString();
     }
  */
  public static void make_toString( ClassPart clz, SB sb ) {
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

  private static boolean xeq(PropPart p) {
    return XType.xtype(p._con,false).is_jdk();
  }
  
  // --- Default Implementations ----------------------------------------------
  // Default appendTo
  @Override public Appenderchar appendTo(Appenderchar buf) {
    return buf.appendTo(toString());
  }
  
}
