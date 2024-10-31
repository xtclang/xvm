package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.util.VBitSet;
import org.xvm.xtc.cons.ParamTCon;
import org.xvm.xtc.cons.SigCon;
import org.xvm.xtc.ast.AST;

import java.util.Arrays;

// Basically a Java class as a function.  Arg 0 is the return.
public class XFun extends XType {
  private static XFun FREE = new XFun();
  public boolean _cond;   // Conditional return; ALSO sets global XRuntime.COND

  // Used by e.g. CallAST
  public static XFun make( XType[] xts, boolean cond ) {
    FREE._cond = cond;
    FREE._xts = xts;
    XFun jt = (XFun)intern(FREE);
    if( jt==FREE ) FREE = new XFun();
    return jt;
  }

  // used by MethodPart
  public static XFun make( boolean cond, XType ret, XType[] args ) {
    int alen = args==null ? 0 : args.length;
    XType[] xts = FREE._xts!=null && FREE._xts.length==alen+1 ? FREE._xts : new XType[alen+1];
    xts[0] = ret;
    for( int i=0; i<alen; i++ )
      xts[i+1] = args[i];
    return make(xts,cond);
  }

  // Make a function type from a Call signature.  The Call's return is the
  // XFun's return.  Arg 0 is ignored (generally the Call name as a constant).
  // Args are either from kids 1&2 or kids 1&2&3.  If either kid 2 or kid 3 is
  // boxed, then both are.
  public static XFun makeCall( AST call ) {
    XType[] args = new XType[call._kids.length];
    args[0] = call.type();      // Return
    args[1] = call._kids[1].type();
    args[2] = call._kids[2].type();
    if( args.length==4 ) {
      args[3] = call._kids[3].type();
      // If both are primitives, take it.
      // Otherwise, box them both.
      if( !(args[2] instanceof XBase && args[3] instanceof XBase) ) {
        if( args[2].box() != null ) args[2] = args[2].box();
        if( args[3].box() != null ) args[3] = args[2].box();
      }
    }
    return make(args,false);
  }


  // Return type; always exactly one return value:
  // - No given rets: VOID
  // - Exactly one: rets[0]
  // - Two, but first is a boolean: fconditional of the 2nd
  // - Three, or two and not condition: return a tuple
  public static XType ret(XType[] rets) {
    if( rets==null || rets.length==0 )
      return XCons.VOID;
    if( rets.length==1 )
      return rets[0];
    // Treat as conditional
    if( isCondRet(rets) )
      return rets[1]; // Use the non-conditional return
    // Tuplize
    XClz ret = XCons.make_tuple(rets);
    org.xvm.xec.ecstasy.collections.Tuple.make_class(ret);
    return ret;
  }
  private static boolean isCondRet( XType[] rets ) {
    return rets!=null && rets.length==2 && rets[0]==XCons.BOOL;
  }

  // Make an XFun from parts; this works for e.g. SigCon's which do not have a
  // MethodPart and indeed are trying to find one.

  // The args are broken into sets:
  // - Parent xargs/types, one explicit type argument per parent type var
  // - Normal args, mapping to _args
  // - Nested inner classes get the outer class as an arg
  public static XFun make( ClassPart clazz, boolean constructor, XType[] args, XType[] rets ) {
    boolean isCondRet = isCondRet(rets);
    XType ret = ret(rets);

    // Non-constructors just walk the args
    if( !constructor )
      // Don't box privates or operators, DO box args to others
      return make(isCondRet, ret, args );

    // Constructors get all the type args from their class
    XClz clz = clazz.tclz();
    int len = clz._tns.length, j=len;
    // Also get their stated args
    if( args != null ) len += args.length;
    // Nested inner classes get the outer class as an arg.
    ClassPart outer = clazz.isNestedInnerClass();
    if( outer!=null ) len++;

    // Extend as needed
    XType[] zts = new XType[len+1/*return*/];
    zts[0] = ret;
    System.arraycopy(clz._xts,0,zts,1,clz._xts.length);

    // Add outer class next
    if( outer!=null )
      zts[1+j++] = outer.tclz();
    // Copy/type actual args
    if( args != null )
      for( XType arg : args )
        zts[1+j++] = arg;

    return XFun.make(zts,isCondRet);
  }

  public int nargs() { return _xts.length-1; }
  public XType arg(int i) { return _xts[i+1]; }
  public XType ret() { return _xts[0]; }
  public boolean cond() { return _cond; }

  public boolean hasUnboxedArgs() {
    for( int i=1; i<_xts.length; i++ )
      if( _xts[i].isUnboxed() )
        return true;
    return false;
  }

  @Override public SB str( SB sb, VBitSet visit, VBitSet dups ) {
    sb.p("{ ");
    for( int i=1; i<_xts.length; i++ )
      _xts[i].str(sb,visit,dups).p(", ");
    if( _xts.length > 1 )  sb.unchar(2);
    sb.p(" -> ");
    if( _cond ) sb.p("!");
    return _xts[0].str(sb,visit,dups).p(" }");
  }

  @Override SB _clz( SB sb, ParamTCon ptc ) {
    if( ptc != null ) {
      XClz xargs = (XClz)xtype(ptc._parms[0],true);
      if( xargs._xts.length!=nargs() ) throw XEC.TODO();
    }
    sb.p("Fun").p(nargs());
    for( int i=1; i<_xts.length; i++ ) {
      if( _xts[i]==XCons.JSTRING )   sb.p("XString$");
      else _xts[i].clz_bare(sb).p("$");
    }
    if( _cond ) throw XEC.TODO(); // Hack name to include $COND
    return sb.unchar();
  }

  // Using shallow equals&hashCode, not deep, because the parts are already interned
  @Override boolean eq(XType xt) { return _cond==((XFun)xt)._cond; }
  @Override int hash() { return _cond ? 512 : 0; }
  @Override boolean _isa( XType xt ) {
    XFun fun = (XFun)xt;        // Invariant
    if( _xts.length != fun._xts.length ) return false;
    for( int i=1; i<_xts.length; i++ )
      if( !_xts[i].isa(fun._xts[i]) )
        return false;
    // TODO: Need to check covariant returns?
    return true;
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
    makeImports(sb);
    sb.p("public interface ").p(tclz).p(" {").nl().ii();
    sb.ip("abstract ");
    // Return
    ret().clz(sb);
    sb.p(" call( ");
    int nargs = nargs();
    if( nargs>0 )
      for( int i=0; i<nargs; i++ )
        arg(i).clz(sb).p(" x").p(i).p(",");
    sb.unchar().p(");").nl();
    // Class end
    sb.di().ip("}").nl();
    sb.p("// ---------------------------------------------------------------").nl();
    ClzBldSet.add(qual,sb.toString());
    return this;
  }

}
