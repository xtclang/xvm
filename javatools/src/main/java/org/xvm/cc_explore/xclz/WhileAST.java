package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;

class WhileAST extends AST {
  // _kids[0] == Condition
  // _kids[1] == Body
  // _kids[2+] == Specials
  // _kids[2+] == Declared Regs
  final int _skids;             // Number of specials
  static WhileAST make( XClzBuilder X ) {
    // Special Expr Ary
    AST[] skids = X.kids();
    int nskids = skids == null ? 0 : skids.length;
    // Declared Regs Ary, skipping space for special kids
    AST[] kids = X.kids_bias(nskids+2);
    // Copy specials in
    if( skids != null ) System.arraycopy(skids,0,kids,2,nskids);
    // Fixed 
    kids[0] = ast_term(X);      // Condition
    kids[1] = ast(X);           // BOdy
    return new WhileAST(kids,nskids);
  }
  private WhileAST( AST[] kids, int skids ) { super(kids); _skids = skids; }

  @Override boolean is_loopswitch() { return true; }

  @Override XType _type() { return XType.VOID; }

  @Override SB jcode( SB sb ) {
    if( sb.was_nl() ) sb.i();
    // while( cond ) body;
    _kids[0].jcode(sb.p("while( "));
    _kids[1].jcode(sb.p(" ) "));
    return sb;
  }
}