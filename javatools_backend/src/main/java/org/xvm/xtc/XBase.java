package org.xvm.xtc;

import org.xvm.util.SB;
import org.xvm.util.VBitSet;
import org.xvm.xtc.cons.ParamTCon;

// Shallow Java class name, or type. 
public class XBase extends XType {
  private static XBase FREE = new XBase();
  public String _jtype;
  private XBase() {}
  public static XBase make( String j) {
    assert j.indexOf('<') == -1; // No generics here
    FREE._jtype = j;
    XBase jt = (XBase)intern(FREE);
    if( jt==FREE ) FREE = new XBase();
    return jt;
  }
  @Override public SB str( SB sb, VBitSet visit, VBitSet dups ) { return sb.p(_jtype); }
  @Override SB _clz( SB sb, ParamTCon ptc ) { return sb.p(_jtype); }
  @Override boolean eq(XType xt) { return _jtype.equals(((XBase)xt)._jtype);  }
  @Override int hash() { return _jtype.hashCode(); }
  @Override public XType e() {
    assert this==XCons.STRING;
    return XCons.CHAR;
  }

  @Override boolean _isa( XType xt ) { return false; }
}

