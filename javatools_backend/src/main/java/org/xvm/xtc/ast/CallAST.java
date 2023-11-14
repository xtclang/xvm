package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.Const;
import org.xvm.util.SB;

class CallAST extends AST {
  final XType[] _rets;
  static CallAST make( ClzBuilder X ) {
    // Read optional array of return types (not currently used)
    Const[] retTypes = X.consts();
    // Read the arguments, then the function expression.
    AST[] kids = X.kids_bias(1);
    // Move the function to the 0th kid slot.
    kids[0] = ast_term(X);     // Call expression first
    return new CallAST(kids,retTypes);
  }
  private CallAST( AST[] kids, Const[] retTypes ) {
    super(kids);
    _rets = XType.xtypes(retTypes);
  }
  
  @Override XType _type() {
    if( _rets==null ) return XType.VOID;
    if( _rets.length == 1 ) return _rets[0];
    throw XEC.TODO();
  }
  
  @Override void jmid ( SB sb, int i ) {
    sb.p( i==0 ? (_kids[0] instanceof RegAST ? ".call(": "(") : ", " );
  }
  @Override void jpost( SB sb ) {
    if( _kids.length > 1 )
      sb.unchar(2);
    sb.p(")");
  }
}
