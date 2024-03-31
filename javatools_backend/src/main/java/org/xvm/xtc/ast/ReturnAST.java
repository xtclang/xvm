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
  private final String _ztype;  // Set if this is a conditional return
  private final MethodPart _meth;
  private final ExprAST _expr;
  static ReturnAST make( ClzBuilder X, int n ) {

    String ztype=null;
    if( X._expr==null && X._meth.is_cond_ret() )
      ztype = X._meth._xrets[1].ztype();
    return new ReturnAST(ztype, X._meth, X._expr, X.kids(n) );
  }
  public ReturnAST( String ztype, MethodPart meth, ExprAST expr, AST... kids) {
    super(kids);
    _ztype= ztype;
    _meth = meth;
    _expr = expr;
  }

  @Override XType _type() {
    // Nested statement expression; kids[0] is statement and have to drill to
    // get the type.  Instead, cached here.
    if( _expr !=null )
      return _expr._type;
    // No returns
    if( _meth._xrets==null )
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
    if( _meth._xrets.length==1 )
      return _meth._xrets[0];

    // Make a Tuple return
    return org.xvm.xec.ecstasy.collections.Tuple.make_class(XCons.make_tuple(_meth._xrets));
  }

  @Override public AST rewrite() {
    // Void return functions execute the return for side effects only
    if( _meth._xrets==null && _expr==null )
      return _kids==null ? null : _kids[0];
    return this;
  }

  @Override public SB jcode( SB sb ) {
    sb.ip("return ");
    if( _kids==null ) return sb;
    // Conditional return:
    if( _ztype!=null && !_kids[0]._cond ) {
      if( _kids.length==1 ) {
        // The only two returns allowed are: MultiAST (Boolean,T) or False
        if( _kids[0] instanceof ConAST )
          return sb.p("XRuntime.SET$COND(false,").p(_ztype).p(")");
        assert _kids[0] instanceof MultiAST && _kids[0]._kids.length==2;
        sb.p("XRuntime.SET$COND(true,");
        _kids[0]._kids[1].jcode(sb);
        return sb.p(")");
      }
      // Returning two parts: (bar,isValid)
      assert _kids.length==2;
      sb.p("XRuntime.SET$COND(");
      _kids[0].jcode(sb);
      sb.p(",");
      _kids[1].jcode(sb);
      sb.p(")");
      return sb;
    }

    if( _kids.length==1 )
      return _kids[0].jcode(sb);

    XClz tup = (XClz)_type;
    tup.clz(sb.p("new ")).p("( ");
    for( AST kid : _kids )
      kid.jcode(sb).p(",");
    return sb.unchar().p(")");
  }
}
