package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.XEC;
import org.xvm.xtc.*;

class BreakAST extends AST {
  final int _d;                 // Depth; 0 is tightest enclosing
  static BreakAST make( ClzBuilder X) { return new BreakAST(X.u31()); }
  private BreakAST( int d ) { super(null); _d = d; }
  @Override XType _type() { return XCons.VOID; }
  @Override public SB jcode ( SB sb ) {
    return sb.p("break");
  }
}
