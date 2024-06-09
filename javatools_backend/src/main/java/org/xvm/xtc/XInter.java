package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.util.VBitSet;
import org.xvm.xtc.cons.ParamTCon;

// A XTC inter class
public class XInter extends XType {
  private static XInter FREE = new XInter();
  public static XInter make( XType u0, XType u1 ) {
    FREE._xts = new XType[]{u0,u1};
    XInter jt = (XInter)intern(FREE);
    if( jt==FREE ) FREE = new XInter();
    return jt;
  }
  @Override public SB str( SB sb, VBitSet visit, VBitSet dups ) {
    sb.p("Inter[");
    _xts[0].str(sb,visit,dups).p(",");
    _xts[1].str(sb,visit,dups).p("]");
    return sb;
  }
  @Override SB _clz( SB sb, ParamTCon ptc ) {
    if( ptc != null ) throw XEC.TODO();
    sb.p("Inter[");
    _xts[0]._clz(sb,ptc).p(",");
    _xts[1]._clz(sb,ptc).p("]");
    return sb;
  }
  @Override boolean eq(XType xt) { return true; }
  @Override int hash() { return 0; }
  @Override boolean _isa( XType xt ) {
    throw XEC.TODO();
  }
}
