package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.S;
import org.xvm.util.SB;
import org.xvm.xtc.*;

public class ReturnAST extends AST {
  final MethodPart _meth;

  static ReturnAST make( ClzBuilder X, int n ) {
    return new ReturnAST(X._meth, X.kids(n) );
  }
  public ReturnAST( MethodPart meth, AST... kids) {
    super(kids);
    _meth = meth;
  }

  @Override boolean _cond() { return _meth.is_cond_ret(); }

  @Override XType _type() {
    // Nested statement expression; kids[0] is statement and have to drill to
    // get the type.  Otherwise, the method has the official type which might
    // be lifted over the expression.
    return enclosing_block() instanceof ExprAST ? _kids[0]._type : _meth.xfun().ret();
  }

  @Override public AST rewrite() {
    // Expression returns have the wrong method (the outer method not the
    // expression), and their not really a return at all except I have to model
    // them as an inner-lambda.
    if( enclosing_block() instanceof ExprAST ) return null;
    // Void return functions execute the return for side effects only
    XFun fun = _meth.xfun();
    if( fun.ret() == XCons.VOID )
      return _kids==null ? null : _kids[0]; // Change "return void_fcn()" into "void_fcn()"

    // Conditional returns normally handled by the child
    if( fun._cond ) {
      // Flip 2 returns into a conditional Multi
      if( _kids.length == 2 ) {
        assert _kids[0]._cond || _kids[0]._type.isBool();
        _kids[1]._cond = true; // 2nd arg is conditional now
        MultiAST multi = new MultiAST(true,_kids);
        _kids = new AST[]{multi.doType()};
        //_kids[0]._cond = true;
        return this;
      }
      if( condFalse(0, fun.ret()) ) // Conditional false return
        return this;
    }

    // Flip multi-return into a tuple return
    if( _kids.length > 1 ) {
      AST nnn = new NewAST(_kids,(XClz)_type);
      AST ret = new ReturnAST(_meth,nnn);
      ret._type = _type;
      return ret;
    }
    return null;
  }

  // Box as needed
  @Override public AST reBox( ) {
    if( !(_kids[0]._type instanceof XBase) ||
        _kids[0]._type == XCons.NULL ||
        _type instanceof XBase ||
        enclosing_block() instanceof ExprAST )
      return null;
    _kids[0] = _kids[0].reBoxThis();
    return this;
  }


  @Override public void jpre( SB sb ) {
    sb.p("return ");
    if( _kids!=null && _kids.length>= 2 ) sb.p("( ");
  }
  @Override public void jmid( SB sb, int i ) { sb.p(", "); }
  @Override public void jpost( SB sb ) {
    if( _kids!=null ) {
      sb.unchar(2);
      if( _kids.length >= 2 ) sb.p(" )");
    }
  }

}
