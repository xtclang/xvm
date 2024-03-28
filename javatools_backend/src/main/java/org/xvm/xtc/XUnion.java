package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.util.VBitSet;
import org.xvm.xtc.cons.ParamTCon;

// A XTC union class
public class XUnion extends XType {
  private static XUnion FREE = new XUnion();
  public static XUnion make( XType u0, XType u1 ) {
    FREE._xts = new XType[]{u0,u1};
    XUnion jt = (XUnion)intern(FREE);
    if( jt==FREE ) FREE = new XUnion();
    return jt;
  }
  @Override public SB str( SB sb, VBitSet visit, VBitSet dups ) {
    sb.p("Union[");
    _xts[0].str(sb,visit,dups).p(",");
    _xts[1].str(sb,visit,dups).p("]");
    return sb;
  }
  @Override SB _clz( SB sb, ParamTCon ptc, boolean print ) {
    if( ptc != null ) throw XEC.TODO();
    sb.p("Union[");
    _xts[0]._clz(sb,ptc,print).p(",");
    _xts[1]._clz(sb,ptc,print).p("]");
    return sb;
  }
  @Override boolean eq(XType xt) { return true; }
  @Override int hash() { return 0; }
  @Override boolean _isa( XType xt ) {
    throw XEC.TODO();
  }
  boolean _reverse_isa(XType xt) {
    return xt.isa(_xts[0]) || xt.isa(_xts[1]);
  }
}
