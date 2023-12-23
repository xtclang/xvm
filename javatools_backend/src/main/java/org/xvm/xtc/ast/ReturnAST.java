package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.XEC;
import org.xvm.xtc.ClzBuilder;
import org.xvm.xtc.MethodPart;
import org.xvm.xtc.XType;

public class ReturnAST extends AST {
  private final String _ztype;  // Set if this is a conditional return
  private final MethodPart _meth;
  private final ExprAST _expr;
  static ReturnAST make( ClzBuilder X, int n ) {

    String ztype=null;
    if( X._expr==null && X._meth.is_cond_ret() )
      ztype = X._meth._xrets[1].ztype();
    return new ReturnAST(X.kids(n), ztype, X._meth, X._expr);
  }
  ReturnAST( AST[] kids, String ztype, MethodPart meth, ExprAST expr) {
    super(kids);
    _ztype= ztype;
    _meth = meth;
    _expr = expr;
  }

  @Override XType _type() {
    if( _expr !=null )
      return _expr._type;
    if( _ztype==null )
      return _kids==null ? XType.VOID : _meth._xrets[0];
    // Conditional, report the non-boolean type
    if( _kids[0] instanceof MultiAST cond )
      return cond._kids[1]._type;
    // Conditional, always false, no other returned type
    return XType.VOID;
  }

  @Override AST rewrite() {
    autobox(0,_type);
    return this;
  }

  @Override public SB jcode( SB sb ) {
    if( _kids==null ) return sb.ip("return");
    if( _ztype!=null ) {
      assert _kids.length==1;
      // The only two returns allowed are: MultiAST (Boolean,T) or False
      if( _kids[0] instanceof ConAST ) {
        return sb.ip("return XRuntime.SET$COND(false,").p(_ztype).p(")");
      } else {
        assert _kids[0] instanceof MultiAST && _kids[0]._kids.length==2;
        sb.ip("return XRuntime.SET$COND(true,");
        _kids[0]._kids[1].jcode(sb);
        return sb.p(")");
      }      
    }
    if( _kids.length==1 )
      return _kids[0].jcode(sb.ip("return "));
    throw XEC.TODO();
  }
}
