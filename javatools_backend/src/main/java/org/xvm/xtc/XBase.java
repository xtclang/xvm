package org.xvm.xtc;

import org.xvm.util.SB;
import org.xvm.util.VBitSet;

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
  @Override public boolean is_prim_base() { return primeq(); }
  @Override public SB str( SB sb, VBitSet visit, VBitSet dups, boolean clz ) { return sb.p(_jtype); }
  @Override boolean eq(XType xt) { return _jtype.equals(((XBase)xt)._jtype);  }
  @Override int hash() { return _jtype.hashCode(); }
}

