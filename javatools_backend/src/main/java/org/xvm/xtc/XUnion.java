package org.xvm.xtc;

import org.xvm.util.SB;
import org.xvm.util.VBitSet;

// A XTC union class
public class XUnion extends XType {
  private static XUnion FREE = new XUnion();
  public static XUnion make( XType u0, XType u1 ) {
    FREE._xts = new XType[]{u0,u1};
    XUnion jt = (XUnion)intern(FREE);
    if( jt==FREE ) FREE = new XUnion();
    return jt;
  }
  @Override public boolean is_prim_base() { return false; }
  @Override public SB str( SB sb, VBitSet visit, VBitSet dups, boolean clz ) {
    sb.p("Union[");
    _xts[0].str(sb,visit,dups,true).p(",");
    _xts[1].str(sb,visit,dups,true).p("]");
    return sb;
  }
  @Override boolean eq(XType xt) { return true; }
  @Override int hash() { return 0; }
}

