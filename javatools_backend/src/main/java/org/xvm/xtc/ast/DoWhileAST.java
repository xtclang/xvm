package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.xtc.*;
import org.xvm.XEC;

class DoWhileAST extends AST {
  // _kids[0] == Condition
  // _kids[1] == Body
  // _kids[2+] == Specials
  static DoWhileAST make( ClzBuilder X ) {
    int nlocals = X.nlocals();  // Count of locals
    // Specials Expr Ary, with room for conditon & body
    AST[] kids = X.kids_bias(2);
    kids[1] = ast(X);           // Body
    kids[0] = ast_term(X);      // Condition
    X.pop_locals(nlocals);      // Pop scope-locals at end of scope
    return new DoWhileAST(kids);
  }
  private DoWhileAST( AST[] kids ) { super(kids); }

  @Override boolean is_loopswitch() { return true; }

  @Override XType _type() { return XCons.VOID; }

  @Override public SB jcode( SB sb ) {
    if( sb.was_nl() ) sb.i();
    sb.p("do {").ii().nl();
    _kids[1].jcode(sb);
    sb.di().ip("} while(");
    _kids[0].jcode(sb);
    return sb.p(")");
  }
}
