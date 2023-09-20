package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.util.SB;

class CallAST extends AST {
  final Const[] _retTypes;
  static CallAST make( XClzBuilder X ) {
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
    _retTypes = retTypes;
  }
  @Override void jmid ( SB sb, int i ) { sb.p( i==0 ? "(" : ", " ); }
  @Override void jpost( SB sb ) {
    if( _kids.length > 1 )
      sb.unchar(2);
    sb.p(")");
  }
}
