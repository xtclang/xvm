package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.XEC;
import org.xvm.xtc.*;

class InitAST extends AST {
  static InitAST make( ClzBuilder X) { return new InitAST(); }
  private InitAST() {super(null);}
  @Override XType _type() { return XCons.VOID; }
  @Override public SB jcode ( SB sb ) {
    return sb;
  }
}
