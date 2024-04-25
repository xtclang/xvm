package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.S;
import org.xvm.util.SB;
import org.xvm.xtc.ClzBuilder;
import org.xvm.xtc.MethodPart;
import org.xvm.xtc.XClz;
import org.xvm.xtc.XCons;
import org.xvm.xtc.XType;

public class ReturnAST extends AST {
  private final MethodPart _meth;
  private final ExprAST _expr;
  boolean _cond_true;

  static ReturnAST make( ClzBuilder X, int n ) {
    return new ReturnAST(X._meth, X._expr, X.kids(n) );
  }
  public ReturnAST( MethodPart meth, ExprAST expr, AST... kids) {
    super(kids);
    _meth = meth;
    _expr = expr;
  }

  @Override boolean _cond() { return _meth.is_cond_ret(); }

  @Override XType _type() {
    // Nested statement expression; kids[0] is statement and have to drill to
    // get the type.  Instead, cached here.
    if( _expr !=null )
      return _expr._type;
    // No returns
    if( _meth.xrets()==null )
      return XCons.VOID;
    // Conditional returns.  The Java flavor takes the from the 2nd tuple argument.
    // Note the method might be flagged as having only 1 return type.
    if( _meth.is_cond_ret() ) {
      // Conditional return, always false, no other returned type
      if( _kids.length==1 )
        return XCons.VOID;
      assert _kids.length==2;
      // Report the non-boolean type
      if( _kids[0] instanceof MultiAST )
        return _kids[0]._kids[1]._type;
      // False returns have no other value
      assert _kids[0] instanceof ConAST con && S.eq(con._con,"true");
      return _kids[1]._type;
    }

    // Single normal return
    if( _meth.xrets().length==1 )
      return _meth.xret(0);

    // Make a Tuple return
    return org.xvm.xec.ecstasy.collections.Tuple.make_class(XCons.make_tuple(_meth.xrets()));
  }

  @Override public AST rewrite() {
    // Void return functions execute the return for side effects only
    if( _meth.xrets()==null && _expr==null )
      return _kids==null ? null : _kids[0];
    // Flip multi-return into a tuple return
    if( !_cond && _kids.length>1 ) {
      AST nnn = new NewAST(_kids,(XClz)_type);
      for( AST kid : _kids ) kid._par = nnn;
      AST ret = new ReturnAST(_meth,_expr,nnn);
      ret._type = _type;
      return ret;
    }
    if( _cond ) {
      // cond_true is set by MultiAST
      if( _kids.length==1 && !_cond_true ) {
        assert _kids[0] instanceof ConAST con && con._con.equals("false");
      } else {
        if( _kids.length==2 ) throw XEC.TODO(); // Maybe a complex conditional return?
        _cond_true = true;                      //
      }
    }
    return this;
  }

  // Box as needed
  @Override XType reBox( AST kid ) { return _kids[0]==kid ? _type : null; }

  @Override public SB jcode( SB sb ) {
    sb.ip("return ");
    if( _kids==null ) return sb;
    if( _cond ) {
      // Conditional return; first part in the global XRuntime.COND
      sb.p("XRuntime.SET$COND(");
      if( _cond_true ) _kids[0].jcode(sb.p("true,"));
      else             sb.p("false,").p(_meth.xret(1).ztype());
      return sb.p(")");
    }
    return _kids[0].jcode(sb);
  }
}
