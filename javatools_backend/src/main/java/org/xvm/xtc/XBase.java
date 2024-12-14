package org.xvm.xtc;

import java.util.HashMap;
import java.util.HashSet;
import org.xvm.util.*;
import org.xvm.xtc.cons.ParamTCon;

// Shallow Java class name, or type.
public class XBase extends XType<TVar> {
  private static XBase FREE = new XBase();
  public String _jtype;
  private XBase() { _tvars = new Ary<>(TVar.class); }
  public static XBase make( String j, boolean notNull) {
    assert j.indexOf('<') == -1; // No generics here
    FREE._jtype = j;
    FREE._notNull = notNull;
    XBase jt = (XBase)intern(FREE);
    if( jt==FREE ) FREE = new XBase();
    return jt;
  }
  @Override boolean eq(XType xt) { return _jtype.equals(((XBase)xt)._jtype);  }
  @Override long hash() { return _jtype.hashCode(); }

  @Override void _dups(VBitSet visit, VBitSet dups) { }

  @Override SB _str1( SB sb, VBitSet visit, VBitSet dups ) {
    sb.p(_jtype);
    if( S.eq(_jtype,"String") && !_notNull ) sb.p("?");
    return sb;
  }

  // Oddly, String is treated as an "array of Char"
  @Override public boolean isAry() { return this==XCons.STRING; }

  @Override public XBase nullable() { return make(_jtype,false); }

  @Override public XType e() {
    assert this==XCons.STRING;
    return XCons.CHAR;
  }
  @Override SB _clz( SB sb, ParamTCon ptc ) { return sb.p(_jtype); }
  // Because interning, if not "==" then never structurally equals and
  // because simple fully expanded types, never "isa" unless "==".
  @Override boolean _isa( XType xt ) {
    if( this==XCons.INT && xt==XCons.LONG ) return true;
    return S.eq( "String", _jtype ) && S.eq( "String", ((XBase) xt)._jtype ) && !xt._notNull;
  }
}
