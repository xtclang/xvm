package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.util.SB;

class ForStmtAST extends AST {
  // _kids[0] == init
  // _kids[1] == cond
  // _kids[2] == next
  // _kids[3] == body
  // _kids[4+] == Special Regs
  static ForStmtAST make( ClzBuilder X ) {
    // Special regs read first
    AST[] kids = X.kids_bias(4);
    kids[0] = ast(X);           // init
    kids[1] = ast_term(X);      // cond
    kids[2] = ast(X);           // next
    kids[3] = ast(X);           // body
    return new ForStmtAST(kids);
  }
  private ForStmtAST( AST[] kids ) { super(kids); }

  @Override boolean is_loopswitch() { return true; }

  @Override XType _type() { return XCons.VOID; }

  @Override public SB jcode( SB sb ) {
    if( sb.was_nl() ) sb.i();
    for( int i=4; i<_kids.length; i++ )
      _kids[i].jcode(sb).nl().i(); // Special regs
    // for( init; cond; update ) body
    _kids[0].jcode(sb.p("for( ")); // for( init
    _kids[1].jcode(sb.p("; "   )); // for( init; cond
    _kids[2].jcode(sb.p("; "   )); // for( init; cond; next
    _kids[3].jcode(sb.p(" ) "  )); // for( init; cond; next ) body
    return sb;
  }
}
