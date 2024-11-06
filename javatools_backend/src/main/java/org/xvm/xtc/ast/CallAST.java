package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.Const;
import org.xvm.xtc.cons.ParamTCon;
import org.xvm.util.SB;
import org.xvm.util.S;

import java.util.Arrays;

public class CallAST extends AST {
  final XType[] _rets;
  final String _mixin_tname;    // Call to a mixin-super
  static CallAST make( ClzBuilder X ) {
    // Read optional array of return types (not currently used)
    Const[] retTypes = X.consts();
    // Read the arguments, then the function expression.
    AST[] kids = X.kids_bias(1);
    // Move the function to the 0th kid slot.
    kids[0] = ast_term(X);     // Call expression first
    XType[] rets = XType.xtypes(retTypes);
    return new CallAST(rets,X._meth,kids);
  }
  CallAST( XType[] rets, MethodPart meth, AST... kids ) {
    super(kids);
    // Check for a call to super: "super(args)" becomes "super.METHOD(args)".
    // If inside an interface (so _meth is a default method), the super
    // also needs the correct super interface named.
    if( _kids[0] instanceof RegAST reg && reg._reg== -13 ) {
      XFun fun = XFun.make(meth.xargs(),meth.xrets());
      _kids[0] = new ConAST(null,null,"super."+meth._name,fun);
      _mixin_tname = meth.clz()._f==Part.Format.MIXIN ? meth.clz()._tnames[0] : null;
    } else _mixin_tname = null;
    _rets = rets;
    _type = _type();
  }
  CallAST( XType[] rets, AST... kids ) {
    super(kids);
    assert !(_kids[0] instanceof RegAST);
    _rets = rets;
    _type = _type();
    _mixin_tname = null;
  }
  static CallAST make(XType ret, String clzname, String methname, AST kid) {
    XType[] rets = new XType[]{ret};
    XFun fun = XFun.make(new XType[]{kid._type},rets);
    ConAST con = new ConAST(clzname+"."+methname,fun);
    CallAST call = new CallAST(rets,con,kid);
    con._par = call;
    call._type = ret;
    return call;
  }

  @Override XType _type() {
    if( _rets==null ) return XCons.VOID;
    if( _rets.length == 1 ) return _rets[0];
    return org.xvm.xec.ecstasy.collections.Tuple.make_class(XCons.make_tuple(_rets));
  }

  @Override public AST unBox() {
    // If call's generic type returns boxed, but call is known to return
    // unboxed (because BAST is promoting a generic to a boxed primitive, but
    // expression using the Call extends unboxed), then unbox.
    if( _kids[0]._type instanceof XFun fun && fun.nrets()==1 &&
        fun.ret() != _type && _type instanceof XBase ) {
      // Change to: Call to Uni(Cast(Call))
      AST cast = new ConvAST(_type.box(),this);
      AST unbx = new UniOpAST(new AST[]{cast},null,"._i",_type);
      _type = fun.ret();
      return unbx;
    }
    return super.unBox();
  }

  // True if the named argument is boxed
  boolean boxedArg(AST arg) {
    int idx = S.find(_kids,arg);
    XFun fun = (XFun)_kids[0]._type;
    return fun._xts[idx-1].box()==fun._xts[idx-1];
  }


  @Override public AST rewrite() {
    // Try to rewrite constant calls.  This is required for e.g. "funky
    // dispatch" calls - virtual calls off of a type argument.
    if( !(_kids[0] instanceof ConAST con) ) return null; // Not a constant call, take as-is
    assert _kids[0]._type instanceof XFun;

    // Convert "funky dispatch" calls; something like "Class.equals"
    int lidx = con._con.lastIndexOf('.');
    if( lidx == -1 ) return null; // Normal call, take as-is

    // Filter for the special names
    String clz  = con._con.substring(0,lidx);
    String base = con._con.substring(lidx+1);
    AST ast = switch( base ) {
    case "hashCode" ->  _kids.length!=3 ? null : _kids[2];
    case "equals"   ->  _kids.length!=4 ? null : new BinOpAST("==" ,"",XCons.BOOL,_kids[2],_kids[3]);
    case "compare"  ->  _kids.length!=4 ? null : new BinOpAST("<=>","",XCons.BOOL,_kids[2],_kids[3]);
    default -> null;
    };
    if( ast==null ) return null; // Not a funky dispatch

    // Primitive funky dispatch goes to Java operators
    AST k1 = _kids[1];
    AST k2 = _kids[2];
    if( k2._type instanceof XBase ) {
      if( k2._type != XCons.STRING && k2._type != XCons.STRINGN )
        return ast;
      clz = XCons.JSTRING.clz();
      ((ConAST)k1)._con = "null";
      ((ConAST)k1)._type= XCons.NULL;
    }

    if( k1 instanceof ConAST ) {
      // XTC String got force-expanded to not conflict with j.l.String, recompress to the bare name
      if( clz.equals("org.xvm.xec.ecstasy.text.String") ) clz = "String";
      if( clz.equals("org.xvm.xec.ecstasy.Object"     ) ) clz = "Object";
      // Convert CLZ.equal/cmp(GOLD,arg1,arg2) to their static variant: CLZ.equal/cmp$CLZ(GOLD,arg1,arg2);
      // Convert CLZ.hashCode (GOLD,arg1     ) to their static variant: CLZ.hashCode$CLZ (GOLD,arg1,arg2);
      con._con += "$"+clz;
      // Sharpen the function type
      con._type = XFun.makeCall(this);
      return null;
    }

    // Dynamic variant
    // Convert CLZ.equal/cmp(arg1,arg2) to Java dynamic: GOLD.equal/cmp(arg1,arg2);
    // Convert CLZ.hashCode (arg1     ) to Java dynamic: GOLD.hashCode (arg1     );
    MethodPart meth = (MethodPart) con._tcon.part();
    ClassPart clazz = meth.clz();
    ClzBuilder.add_import(clazz);
    return new InvokeAST(base,_rets,Arrays.copyOfRange(_kids,1,_kids.length));
  }

  // Box as needed
  @Override XType reBox( AST kid ) {
    if( _kids[0]==kid ) return null; // Called method not boxed
    XFun fun = (XFun)_kids[0]._type;
    return fun.arg(S.find(_kids,kid)-1);
  }

  // If the called function takes a type parameter, its return type can be as
  // precise as the type parm; XTC does this much type inference.
  // XTC  : "Foo<Int> foo = create(stuff);"
  // BAST : "Assign(DefReg Foo<Int> is = Call("create",Int,stuff)"
  // The Java expects a strongly typed result:
  // Java : "Foo<Int> foo = create(INT64.GOLD,stuff)"
  // However, the create call returns generic Foos:
  // XTC: "<T> Foo create( stuff ) { ... }"
  // So the Java has to return a generic Foo
  // Java: "Foo create( int len )"
  //

  @Override public SB jcode( SB sb ) {

    // Assume we need a (self!) cast, from some abstract type to a more
    // specified local type.
    if( _type != XCons.VOID && !(_type instanceof XBase) &&
        _kids.length>1 && _kids[1] instanceof ConAST con && con._tcon instanceof ParamTCon ptc )
      _type.clz(sb.p("(")).p(")");
    _kids[0].jcode(sb);
    AST kid = _kids[0] instanceof NarrowAST n ? n._kids[0] : _kids[0];
    sb.p( (kid instanceof RegAST ? ".call(" : "(") );
    for( int i=1; i<_kids.length; i++ ) {
      // If this is a super of a mixin, all mixed args need to cast to the base
      // type not mixin type.  In general, I've no way to track this; could be
      // any combo of fields and locals - but the mixin can only call "supers"
      // of some sort or another.
      if( _mixin_tname !=null )
        sb.p("($").p(_mixin_tname).p(")");
      _kids[i].jcode(sb).p(", ");
    }
    if( _kids.length > 1 )
      sb.unchar(2);
    return sb.p(")");
  }

}
