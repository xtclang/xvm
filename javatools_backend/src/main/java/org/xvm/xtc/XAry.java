package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.util.VBitSet;

// Basically a Java class as an array
public class XAry extends XType {
  private static XAry FREE = new XAry();
  private XAry() {}
  public static XAry make( XType e ) {
    FREE._xts = new XType[]{e};
    XAry jt = (XAry)intern(FREE);
    if( jt==FREE ) FREE = new XAry();
    return jt;
  }
  public XType e() { return _xts[0]; }
  @Override public boolean is_prim_base() { return _xts[0].is_prim_base(); }
  public boolean generic() {
    XType e = e();
    if( e instanceof XBase ) return false;
    if( e==JUBYTE ) return false;
    if( e==STRING ) return false;
    return true;
  }

  // Number of type parameters
  @Override public int nTypeParms() { return 1; }
  
  @Override public SB str( SB sb, VBitSet visit, VBitSet dups, boolean clz ) {
    XType e = e();
    // Primitives print as "Arylong" or "Arychar" classes
    // Generics as "Ary<String>"
    boolean generic = generic();
    if( generic ) sb.p("Array<");
    else sb.p("Ary");
    e.str(sb,visit,dups,true);
    if( generic ) sb.p(">");
    return sb;
  }
  
  public String import_name() {
    SB sb = new SB();
    sb.p(XEC.XCLZ).p(".ecstasy.collections.");
    if( generic() ) sb.p("Array");
    else str(sb,null,null,false);
    return sb.toString();
  }

  @Override boolean eq(XType xt) { return true; }
  @Override int hash() { return 0; }
}
