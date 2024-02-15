package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.util.VBitSet;
import org.xvm.xtc.cons.ParamTCon;

import java.util.Arrays;

// Basically a Java class as a function
public class XFun extends XType {
  private static XFun FREE = new XFun();
  int _nargs;
  public static XFun make( XType[] args, XType[] rets ) {
    if( args==null ) args = EMPTY;
    if( rets==null ) rets = EMPTY;
    FREE._nargs = args.length;
    FREE._xts = Arrays.copyOf(args,args.length+rets.length);
    System.arraycopy(rets,0,FREE._xts,args.length,rets.length);
    XFun jt = (XFun)intern(FREE);
    if( jt==FREE ) FREE = new XFun();
    return jt;
  }
  public static XFun make( MethodPart meth ) {
    return make(xtypes(meth._args),xtypes(meth._rets));
  }
  public int nargs() { return _nargs; }
  public int nrets() { return _xts.length-_nargs; }
  public XType arg(int i) { return _xts[i]; }
  
  public XType[] rets() {
    return nrets()==0 ? null : Arrays.copyOfRange(_xts,_nargs,_xts.length);
  }
  
  @Override public SB str( SB sb, VBitSet visit, VBitSet dups ) {
    sb.p("{ ");
    for( int i=0; i<_nargs; i++ )
      _xts[i].str(sb,visit,dups).p(",");
    sb.unchar().p(" -> ");
    for( int i=_nargs; i<_xts.length; i++ )
      _xts[i].str(sb,visit,dups).p(",");
    return sb.unchar().p(" }");        
  }
  @Override SB _clz( SB sb, ParamTCon ptc ) {
    if( ptc != null ) {
      XClz xargs = (XClz)xtype(ptc._parms[0],true);
      if( xargs.nTypeParms()!=_nargs ) throw XEC.TODO();
      if( _nargs!=0 ) throw XEC.TODO();
    }
    sb.p("Fun").p(_nargs);
    for( int i=0; i<_nargs; i++ )
      _xts[i]._clz(sb,ptc).p("$");
    return sb.unchar();
  }
  
  // Using shallow equals,hashCode, not deep, because the parts are already interned
  @Override boolean eq(XType xt) { return _nargs == ((XFun)xt)._nargs; }
  @Override int hash() { return _nargs; }
  @Override boolean _isa( XType xt ) {
    throw XEC.TODO();
  }

  // Make a callable interface with a particular signature
  public XFun make_class( ) {
    String tclz = clz();
    String qual = (XEC.XCLZ+"."+tclz).intern();
    ClzBuilder.add_import(qual);
    if( ClzBldSet.find(qual) )
      return this;
    // The no-arg-no-ret version already exists, essentially a Java "Callable"
    if( _xts.length==0 ) return this;
    

    /* Gotta build one.  Looks like:
       interface Fun2$long$String {
         long call(long l, String s);
       }
    */
    SB sb = new SB();
    sb.p("// ---------------------------------------------------------------").nl();
    sb.p("// Auto Generated by XFun from ").p(tclz).nl().nl();
    sb.p("package ").p(XEC.XCLZ).p(";").nl().nl();
    sb.p("public interface ").p(tclz).p(" {").nl().ii();
    sb.ip("abstract ");
    // Return
    int nrets = nrets();
    if( nrets==0 ) sb.p("void");
    else if( nrets==1 ) _xts[nargs()].str(sb);
    else throw XEC.TODO();
    sb.p(" call( ");
    int nargs = nargs();
    if( nargs>0 )
      for( int i=0; i<nargs; i++ )
        _xts[i].clz(sb).p(" x").p(i).p(",");
    sb.unchar().p(");").nl();
    // Class end
    sb.di().ip("}").nl();
    sb.p("// ---------------------------------------------------------------").nl();
    ClzBldSet.add(qual,sb.toString());
    return this;
  }
  
}
