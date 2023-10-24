package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;

class ReturnAST extends AST {
  private final String _ztype;
  static ReturnAST make( XClzBuilder X, int n ) {

    String ztype=null;
    if( X._meth.is_cond_ret() ) {
      String type = XClzBuilder.jtype(X._meth._rets[1]._con,false);
      ztype = XClzBuilder.box(type).equals(type) ? "null" : "0";
    }    
    return new ReturnAST(X.kids(n), ztype);
  }
  ReturnAST( AST[] kids, String ztype) { super(kids);  _ztype=ztype; }

  @Override String _type() {
    if( _ztype==null )
      return _kids==null ? "void" : _kids[0]._type;
    // Conditional, report the non-boolean type
    if( _kids[0] instanceof MultiAST cond )
      return cond._kids[1]._type;
    // Conditional, always false, no other returned type
    return "void";
  }

  @Override SB jcode( SB sb ) {
    if( _kids==null ) return sb.ip("return");
    if( _ztype!=null ) {
      assert _kids.length==1;
      // The only two returns allowed are: MultiAST (Boolean,T) or False
      if( _kids[0] instanceof ConAST con ) {
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
