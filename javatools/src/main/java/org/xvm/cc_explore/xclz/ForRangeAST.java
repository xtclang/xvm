package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;

class ForRangeAST extends AST {
  // _kids[0] == LHS
  // _kids[1] == RHS
  // _kids[2] == Body
  // _kids[3+] == Special Regs
  ForRangeAST( XClzBuilder X ) {
    super(X, X.u31()+3,false);
    for( int i=3; i<_kids.length; i++ )
      _kids[i] = ast_term(X);
    _kids[0] = ast_term(X);     // LHS
    _kids[1] = ast_term(X);     // RHS
    _kids[2] = ast(X);          // Body
  }
  
  @Override SB jcode( SB sb ) {
    if( sb.was_nl() ) sb.i();
    // for( long x : new XRange(1,100) ) {
    _kids[0].jcode(sb.p("for( "));
    _kids[1].jcode(sb.p(" : "  ));
    _kids[2].jcode(sb.p(" ) "  ));
    return sb;
  }
}
