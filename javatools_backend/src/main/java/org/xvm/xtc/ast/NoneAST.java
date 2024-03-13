package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.XEC;
import org.xvm.xtc.*;

class NoneAST extends AST {
  static NoneAST make( ClzBuilder X) { return new NoneAST(); }
  private NoneAST() { super(null); }
  @Override XType _type() { return XCons.VOID; }
  @Override public SB jcode ( SB sb ) { return sb.p("{}"); }
}
