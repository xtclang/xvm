package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.XEC;
import org.xvm.xtc.*;

class UnpackAST extends AST {
  static UnpackAST make( ClzBuilder X) { return new UnpackAST(X.kids(1)); }
  private UnpackAST(AST[] kids) { super(kids); }
  @Override XType _type() { return XCons.VOID; }
  @Override public SB jcode ( SB sb ) { return _kids[0].jcode(sb); }
}
