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
    if( enclosing instanceof SwitchAST ) {
      if( _par instanceof BlockAST blk &&
          blk._par==enclosing &&
          blk._kids[blk._kids.length-1]==this )
        return sb.ip("// Fall-through");
      // Requires fall-thru semantics from the middle complex expressions which
      // amounts to a GOTO.
      throw XEC.TODO();
    }
    return sb.ip("continue");
  }
}
