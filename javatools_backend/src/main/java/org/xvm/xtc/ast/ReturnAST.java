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
  final MethodPart _meth;
  final ExprAST _expr;

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
      AST ret = new ReturnAST(_meth,_expr,nnn);
      ret._type = _type;
      return ret;
    }
    if( _cond ) {
      if( _kids.length==2 ) {
        MultiAST mult = new MultiAST(true,_kids);
        mult._type = _type;
        AST ret = new ReturnAST(_meth,null,mult);
        ret._cond = true;
        return ret;
      }
      assert _kids.length==1;
      cond_rewrite(this,0,_meth.xret(1).ztype());
    }
    return null;
  }

  // Rewrite children to directly call the XRuntime.COND backdoors.
  private static void cond_rewrite(AST par, int idx, String ztype) {
    AST ast = par._kids[idx];
    switch( ast ) {
    case ConAST con:
      ast._cond = true;
      assert S.eq(con._con,"false");
      con._con = "XRuntime.False(" + ztype + ")";
      break;
    case MultiAST mult:
      ast._cond = true;
      assert mult._kids[0] instanceof ConAST con && S.eq(con._con,"true");
      CallAST call = CallAST.make(mult._type,"XRuntime","True",mult._kids[1]);  mult._kids[1]._par = call;
      par._kids[idx] = call;  call._par = par;
      break;
    case TernaryAST tern:
      ast._cond = true;
      cond_rewrite(tern,1,ztype);
      cond_rewrite(tern,2,ztype);
      break;
    case InvokeAST invk:  break;
    case CallAST call2:   break;

    default: throw XEC.TODO();
    }
  }


  // Box as needed
  @Override XType reBox( AST kid ) { return _kids[0]==kid ? _type : null; }

  @Override public void jpre( SB sb ) { sb.p("return "); }
}
