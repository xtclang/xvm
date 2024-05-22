package org.xvm.xtc.ast;

import org.xvm.xtc.*;
import org.xvm.util.SB;
import org.xvm.XEC;

class ContinueAST extends AST {
  final int _d;                 // Depth; 0 is tightest enclosing
  static ContinueAST make( ClzBuilder X) { return new ContinueAST(X.u31()); }
  private ContinueAST( int d ) { super(null); _d = d; }

  @Override XType _type() { return XCons.VOID; }

  @Override public AST rewrite() {
    if( _d > 0 )
      enclosing_loop(_d).add_label(); // Needs a named GOTO
    return null;
  }

  @Override public SB jcode ( SB sb ) {
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
    sb.ip("continue");
    if( _d > 0 ) sb.p(" ").p(enclosing.label());
    return sb;
  }
}
