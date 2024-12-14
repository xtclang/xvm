package org.xvm.xtc;

import java.util.Arrays;
import java.util.HashSet;
import org.xvm.XEC;
import org.xvm.util.*;
import org.xvm.xtc.ast.AST;
import org.xvm.xtc.cons.ParamTCon;
import org.xvm.xtc.cons.SigCon;

// Basically a Java class as a function.  Arg 0 is the return.
public class XFun extends XType {
  private static XFun FREE = new XFun();
  public boolean _cond;   // Conditional return; ALSO sets global XRuntime.COND

  XFun() { _tvars = new Ary<>(TVar.class); }
  // Used by e.g. CallAST
  public static XFun make( XFun free ) {
    assert free==FREE;
    XFun jt = (XFun)intern(FREE);
    if( jt==FREE ) FREE = new XFun();
    return jt;
  }

  // used by MethodPart
  public static XFun make( boolean cond, XType ret, XType... args ) {
    FREE._cond = cond;
    assert FREE._tvars.isEmpty();
    FREE._tvars.add(TVar.make(ret));
    if( args!=null )
      for( XType xt : args )
        FREE._tvars.add(TVar.make(xt));
    return make(FREE);
  }

  // Make a function type from a Call signature.  The Call's return is the
  // XFun's return.  Arg 0 is ignored (generally the Call name as a constant).
  // Args are either from kids 1&2 or kids 1&2&3.  If either kid 2 or kid 3 is
  // boxed, then both are.
  public static XFun makeCall( AST call ) {
    assert FREE._tvars.isEmpty();
    FREE._cond = false;
    Ary<TVar> tvs = FREE._tvars;
    tvs.add(TVar.make(call.type()));
    XType k1t = call._kids[1].type();
    XType k2t = call._kids[2].type();
    tvs.add(TVar.make(k1t));
    tvs.add(TVar.make(k2t));

    if( call._kids.length==4 ) {
      XType k3t = call._kids[3].type();
      tvs.add(TVar.make(k3t));
      // If both are primitives, take it.
      // Otherwise, box them both.
      if( !(k2t instanceof XBase && k3t instanceof XBase) ) {
        if( k2t.box() != null ) tvs.set(2,TVar.make(k2t.box()));
        if( k3t.box() != null ) tvs.set(3,TVar.make(k3t.box()));
      }
    }
    return make(FREE);
  }

  // Using shallow equals&hashCode, not deep, because the parts are already interned
  // Using shallow equals&hashCode, not deep, because the parts are already interned
  @Override boolean eq(XType xt) { return _cond==((XFun)xt)._cond; }
  @Override long hash() { return _cond ? 512 : 0; }
  @Override SB _str1( SB sb, VBitSet visit, VBitSet dups ) {
    sb.p("{ ");
    for( int i=1; i<len(); i++ )
      xt(i)._str0(sb,visit,dups).p(", ");
    if( len() > 1 )  sb.unchar(2);
    sb.p(" -> ");
    if( _cond ) sb.p("!");
    return xt(0)._str0(sb,visit,dups).p(" }");
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
    XClz clz = clazz.xclz();
    int len = clz.len(), j=len;
    // Also get their stated args
    if( args != null ) len += args.length;
    // Nested inner classes get the outer class as an arg.
    ClassPart outer = clazz.isNestedInnerClass();
    if( outer!=null ) len++;

    // Build up the arguments
    assert FREE._tvars.isEmpty();
    FREE._cond = isCondRet;
    Ary<TVar> tvs = FREE._tvars;
    tvs.add(TVar.make(ret));
    // Class type args for constructor
    for( TVarZ tv : clz._tvars )
      tvs.add(tv);

    // Add outer class next
    if( outer!=null )
      tvs.add(TVarZ.make(outer.xclz(),"$outer"));
    // Copy/type actual args
    if( args != null )
      for( XType arg : args )
        tvs.add(TVar.make(arg));
    // Intern
    return make(FREE);
  }

  public int nargs() { return len()-1; }
  public XType arg(int i) { return xt(i+1); }
  public XType ret() { return xt(0); }
  public boolean cond() { return _cond; }

  public boolean hasUnboxedArgs() {
    for( int i=1; i<len(); i++ )
      if( xt(i).isUnboxed() )
        return true;
    return false;
  }

  @Override SB _clz( SB sb, ParamTCon ptc ) {
    if( ptc != null ) {
      XClz xargs = (XClz)xtype(ptc._parms[0],true);
      if( xargs.len()!=nargs() ) throw XEC.TODO();
    }
    sb.p("Fun").p(nargs());
    for( int i=1; i<len(); i++ ) {
      if(  xt(i)==XCons.JSTRING )   sb.p("XString$");
      else xt(i).clz_bare(sb).p("$");
    }
    if( _cond ) throw XEC.TODO(); // Hack name to include $COND
    return sb.unchar();
  }

  @Override boolean _isa( XType xt ) {
    XFun fun = (XFun)xt;        // Invariant
    if( len() != fun.len() ) return false;
    for( int i=1; i<len(); i++ )
      if( !xt(i).isa(fun.xt(i)) )
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
    if( len()==0 ) return this;


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
