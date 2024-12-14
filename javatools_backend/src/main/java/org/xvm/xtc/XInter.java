package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.util.VBitSet;
import org.xvm.xtc.cons.ParamTCon;

// A XTC inter class
public class XInter extends XType {
  private static XInter FREE = new XInter();
  public static XInter make( XType u0, XType u1 ) {
    //FREE._xts = new XType[]{u0,u1};
    //XInter jt = (XInter)intern(FREE);
    //if( jt==FREE ) FREE = new XInter();
    //return jt;
    throw XEC.TODO();
  }
  @Override boolean eq(XType xt) { return true; }
  @Override long hash() { return 0; }
  @Override public SB _str1( SB sb, VBitSet visit, VBitSet dups ) {
    sb.p("Inter[");
    xt(0)._str0(sb,visit,dups).p(",");
    xt(1)._str0(sb,visit,dups).p("]");
    return sb;
  }

  @Override SB _clz( SB sb, ParamTCon ptc ) {
    if( ptc != null ) throw XEC.TODO();
    sb.p("Inter_");
    xt(0)._clz(sb,ptc).p("_");
    xt(1)._clz(sb,ptc).p("");
    return sb;
  }
  @Override boolean _isa( XType xt ) {
    throw XEC.TODO();
  }
  @Override public XInter readOnly() {
    XType xt0 = xt(0).readOnly();
    XType xt1 = xt(1).readOnly();
    return xt0==xt(0) && xt1 == xt(1) ? this : make(xt0,xt1);
  }
}
