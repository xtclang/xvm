package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.util.VBitSet;

import java.util.Arrays;

// Basically a Java class as a function
public class XFun extends XType {
  private static XFun FREE = new XFun();
  private static final XType[] EMPTY = new XType[0];
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
  
  @Override public boolean is_prim_base() { return false; }
  @Override public SB str( SB sb, VBitSet visit, VBitSet dups, boolean clz ) {
    if( clz ) sb.p("Fun").p(_nargs);
    else      sb.p("{ ");
    for( int i=0; i<_nargs; i++ )
      _xts[i].str(sb,visit,dups,clz).p(clz ? "$" : ",");
    sb.unchar();
    
    if( !clz ) {
      sb.p(" -> ");
      for( int i=_nargs; i<_xts.length; i++ )
        _xts[i].str(sb,visit,dups,clz).p(",");
      sb.unchar().p(" }");        
    }
    return sb;
  }
  
  // Using shallow equals,hashCode, not deep, because the parts are already interned
  @Override boolean eq(XType xt) { return _nargs == ((XFun)xt)._nargs; }
  @Override int hash() { return _nargs; }

  // Make a callable interface with a particular signature
  public XFun make_class( ) {
    if( ClzBuilder.XCLASSES==null ) return this;
    String tclz = clz();
    if( ClzBuilder.XCLASSES.containsKey(tclz) )
      return this;
    /* Gotta build one.  Looks like:
       interface Fun2$long$String {
         long call(long l, String s);
       }
    */
    SB sb = new SB();
    sb.p("interface ").p(tclz).p(" {").nl().ii();
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
    ClzBuilder.XCLASSES.put(tclz,sb.toString());
    return this;
  }
}

