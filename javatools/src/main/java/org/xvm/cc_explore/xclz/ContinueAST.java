package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;

class ContinueAST extends AST {
  final int _d;                 // Depth; 0 is tightest enclosing
  static ContinueAST make(XClzBuilder X) { return new ContinueAST(X.u31()); }
  private ContinueAST( int d ) { super(null); _d = d; }
  @Override SB jcode ( SB sb ) {
    if( _d > 0 ) throw XEC.TODO();
    AST enclosing = enclosing_loop(_d);
    if( enclosing instanceof SwitchAST )
      throw XEC.TODO(); // Requires fall-thru semantics from the middle complex
                        // expressions which amounts to a GOTO.
    return sb.ip("continue");
  }
}
