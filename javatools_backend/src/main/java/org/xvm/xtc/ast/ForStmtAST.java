package org.xvm.xtc.ast;

import org.xvm.xtc.*;
import org.xvm.util.SB;

class ForStmtAST extends AST {
  // _kids[0] == init
  // _kids[1] == cond
  // _kids[2] == update
  // _kids[3] == Body
  // _kids[4+] == Special Regs
  static ForStmtAST make( ClzBuilder X ) {
    // Count of locals
    int nlocals = X._nlocals;
    AST[] kids = X.kids_bias(4);
    kids[0] = ast(X);           // init
    kids[1] = ast_term(X);      // cond
    kids[2] = ast(X);           // update
    kids[3] = ast(X);           // Body
    X.pop_locals(nlocals);    
    return new ForStmtAST(kids);
  }
  private ForStmtAST( AST[] kids ) { super(kids); }

  @Override boolean is_loopswitch() { return true; }

  @Override XType _type() { return XType.VOID; }

  @Override public SB jcode( SB sb ) {
    if( sb.was_nl() ) sb.i();
    // for( init; cond; update ) body
    _kids[0].jcode(sb.p("for( ")); // for( init
    _kids[1].jcode(sb.p("; "   )); // for( init; cond
    _kids[2].jcode(sb.p("; "   )); // for( init; cond; update
    _kids[3].jcode(sb.p(" ) "  )); // for( init; cond; update ) body
    return sb;
  }
}
