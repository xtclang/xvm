package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.util.SB;

class CallAST extends AST {
  final Const[] _retTypes;
  static CallAST make( XClzBuilder X ) {
    // Read optional array of return types (not currently used)
    int ncons = X.u31();
    Const[] retTypes = ncons==0 ? null : new Const[ncons];
    for( int i=0; i<ncons; i++ )
      retTypes[i] = X.con(X.u31());

    // Read the arguments, then the function expression.
    // Move the function to the 0th kid slot.
    int nargs = X.u31();
    AST[] kids = new AST[nargs+1];
    for( int i=0; i<nargs; i++ )
      kids[i+1] = ast_term(X); // Args in order
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
