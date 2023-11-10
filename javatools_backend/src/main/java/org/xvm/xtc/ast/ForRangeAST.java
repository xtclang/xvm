package org.xvm.xtc.ast;

import org.xvm.xtc.*;
import org.xvm.util.SB;

class ForRangeAST extends AST {
  // _kids[0] == LHS
  // _kids[1] == RHS
  // _kids[2] == Body
  // _kids[3+] == Special Regs
  static ForRangeAST make( XClzBuilder X ) {
    AST[] kids = X.kids_bias(3);
    kids[0] = ast_term(X);     // LHS
    kids[1] = ast_term(X);     // RHS
    kids[2] = ast(X);          // Body
    return new ForRangeAST(kids);
  }
  private ForRangeAST( AST[] kids ) { super(kids); }

  @Override boolean is_loopswitch() { return true; }

  @Override XType _type() { return XType.VOID; }

  @Override public SB jcode( SB sb ) {
    if( sb.was_nl() ) sb.i();
    // for( long x : new XRange(1,100) ) {
    _kids[0].jcode(sb.p("for( "));
    _kids[1].jcode(sb.p(" : "  ));
    _kids[2].jcode(sb.p(" ) "  ));
    return sb;
  }
}
