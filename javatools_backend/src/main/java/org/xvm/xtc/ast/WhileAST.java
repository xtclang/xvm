package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.xtc.*;

class WhileAST extends AST {
  // _kids[0] == Condition
  // _kids[1] == Body
  // _kids[2+] == Specials
  // _kids[2+] == Declared Regs
  final int _skids;             // Number of specials
  static WhileAST make( ClzBuilder X ) {
    // Special Expr Ary
    AST[] skids = X.kids();
    int nskids = skids == null ? 0 : skids.length;
    // Declared Regs Ary, skipping space for special kids
    AST[] kids = X.kids_bias(nskids+2);
    // Copy specials in
    if( skids != null ) System.arraycopy(skids,0,kids,2,nskids);
    // Fixed 
    kids[0] = ast_term(X);      // Condition
    
    int nlocals = X.nlocals();  // Count of locals
    kids[1] = ast(X);           // Body
    X.pop_locals(nlocals);      // Pop scope-locals at end of scope
    return new WhileAST(kids,nskids);
  }
  private WhileAST( AST[] kids, int skids ) { super(kids); _skids = skids; }

  @Override boolean is_loopswitch() { return true; }

  @Override XType _type() { return XCons.VOID; }

  @Override public SB jcode( SB sb ) {
    if( sb.was_nl() ) sb.i();
    // while( cond ) body;
    _kids[0].jcode(sb.p("while( "));
    _kids[1].jcode(sb.p(" ) ").nl().ii()).di();
    return sb;
  }
}
