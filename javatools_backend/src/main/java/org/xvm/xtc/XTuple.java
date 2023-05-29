package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.util.VBitSet;
import org.xvm.xtc.cons.ParamTCon;

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

  @Override public SB str( SB sb, VBitSet visit, VBitSet dups ) {
    sb.p("( ");
    for( XType xt : _xts )
      xt.str(sb,visit,dups).p( "," );
    return sb.unchar().p(" )");
  }
  @Override SB _clz( SB sb, ParamTCon ptc ) {
    if( ptc != null ) throw XEC.TODO();
    sb.p("Tuple").p(_xts.length).p("$");
    for( XType xt : _xts )
      xt._clz(sb,ptc).p("$");
    return sb.unchar();
  }
  
  // Using shallow equals,hashCode, not deep, because the parts are already interned
  @Override boolean eq(XType xt) { return true; }
  @Override int hash() { return 0; }

  @Override boolean _isa( XType xt ) {
    throw XEC.TODO();
  }
}

