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
  static CallAST make( ClzBuilder X ) {
    // Read optional array of return types (not currently used)
    Const[] retTypes = X.consts();
    // Read the arguments, then the function expression.
    AST[] kids = X.kids_bias(1);
    // Move the function to the 0th kid slot.
    kids[0] = ast_term(X);     // Call expression first
    XType[] rets = XType.xtypes(retTypes);
    return new CallAST(rets,X._meth._name,XFun.make(X._meth.xargs(),X._meth.xrets()),kids);
  }
  CallAST( XType[] rets, String mname, XFun fun, AST... kids ) {
    super(kids);
    // Check for a call to super: "super.call()" becomes "super.METHOD"
    if( _kids[0] instanceof RegAST reg && reg._reg== -13 )
      _kids[0] = new ConAST(null,null,"super."+mname,fun);
    _rets = rets;
    _type = _type();
  }

  @Override XType _type() {
    if( _rets==null ) return XCons.VOID;
    if( _rets.length == 1 ) return _rets[0];
    return org.xvm.xec.ecstasy.collections.Tuple.make_class(XCons.make_tuple(_rets));
  }

  @Override public AST rewrite() {
    // Try to rewrite constant calls.  This is required for e.g. "funky
    // dispatch" calls - virtual calls off of a type argument.
    if( !(_kids[0] instanceof ConAST con) ) return this; // Not a constant call, take as-is
    assert _kids[0]._type instanceof XFun;

    // Convert "funky dispatch" calls; something like "Class.equals"
    int lidx = con._con.lastIndexOf('.');
    if( lidx == -1 ) return this; // Normal call, take as-is

    // Filter for the special names
    String clz  = con._con.substring(0,lidx);
    String base = con._con.substring(lidx+1);
    AST ast = switch( base ) {
    case "hashCode" ->  _kids[2];
    case "equals"   ->  new BinOpAST("==" ,"",XCons.BOOL,_kids[2],_kids[3]);
    case "compare"  ->  new BinOpAST("<=>","",XCons.BOOL,_kids[2],_kids[3]);
    default -> null;
    };
    if( ast==null ) return this; // Not a funky dispatch

    // Primitive funky dispatch goes to Java operators
    AST k1 = _kids[1];
    AST k2 = _kids[2];
    if( k2._type instanceof XBase ) {
      if( k2._type != XCons.STRING && k2._type != XCons.STRINGN )
        return ast;
      clz = XCons.JSTRING.clz();
      ((ConAST)k1)._con = null;
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
      return this;
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
  @Override void jpre( SB sb ) {
    // Assume we need a (self!) cast, from some abstract type to a more
    // specificed local type.
    if( _type != XCons.VOID && !(_type instanceof XBase) &&
        _kids.length>1 && _kids[1] instanceof ConAST con && con._tcon instanceof ParamTCon ptc )
      _type.clz(sb.p("(")).p(")");
  }

  @Override void jmid ( SB sb, int i ) {
    if( i>0 ) { sb.p(", "); return; }
    AST kid = _kids[0] instanceof NarrowAST n ? n._kids[0] : _kids[0];
    sb.p( (kid instanceof RegAST ? ".call(": "(") );
  }
  @Override void jpost( SB sb ) {
    if( _kids.length > 1 )
      sb.unchar(2);
    sb.p(")");
  }
}
