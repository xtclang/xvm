package org.xvm.xtc;

import org.xvm.util.SB;
import org.xvm.util.VBitSet;

// Basically a Java class as a tuple
public class XTuple extends XType {
  private static XTuple FREE = new XTuple();
  private XTuple() { }
  public static XTuple make( XType... xts ) {
    FREE._xts = xts;
    XTuple jt = (XTuple)intern(FREE);
    if( jt==FREE ) FREE = new XTuple();
    return jt;
  }
  public int nargs() { return _xts.length; }
  public XType arg(int i) { return _xts[i]; }
  @Override public boolean is_prim_base() { return false; }

  @Override public SB str( SB sb, VBitSet visit, VBitSet dups, boolean clz ) {
    if( clz )  sb.p("Tuple").p(_xts.length).p("$");
    else sb.p("( ");
    for( XType xt : _xts )
      xt.str(sb,visit,dups,clz).p( clz ? "$" : "," );
    sb.unchar();
    if( !clz ) sb.p(" )");
    return sb;
  }
  
  // Using shallow equals,hashCode, not deep, because the parts are already interned
  @Override boolean eq(XType xt) { return true; }
  @Override int hash() { return 0; }
}

