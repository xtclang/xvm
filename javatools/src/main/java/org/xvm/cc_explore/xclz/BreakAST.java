package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;

class BreakAST extends AST {
  final int _d;                 // Depth; 0 is tightest enclosing
  static BreakAST make(XClzBuilder X) { return new BreakAST(X.u31()); }
  private BreakAST( int d ) { super(null); _d = d; }
  @Override SB jcode ( SB sb ) {
    if( _d > 0 ) throw XEC.TODO();
    return sb.ip("break");
  }
}
