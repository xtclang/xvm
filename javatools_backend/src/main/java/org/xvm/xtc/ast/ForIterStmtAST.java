package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.util.SB;

class ForIterStmtAST extends ForAST {
  // _kids[0] == var
  // _kids[1] == iter
  // _kids[2] == Body
  // _kids[3+] == Special Regs
  static ForIterStmtAST make( ClzBuilder X ) {
    // Count of locals
    AST[] kids = X.kids_bias(3);
    kids[0] = ast(X);           // var
    kids[1] = ast_term(X);      // iter
    kids[2] = ast(X);           // body
    return new ForIterStmtAST(kids);
  }
  private ForIterStmtAST( AST[] kids ) { super(kids,3); }

  @Override public AST rewrite( ) {
    super.rewrite();
    if( _kids[1]._type == XCons.STRING || _kids[1]._type == XCons.STRINGN ) {
      _kids[1] = new InvokeAST("toCharArray",XBase.make("char[]",true),_kids[1]);
      return this;
    }
    return null;
  }

  @Override public SB jcode( SB sb ) {
    if( _label!=null ) sb.p(_label).p(":").nl().i();
    // for( init; cond; update ) body
    _kids[0].jcode(sb.p("for( ")); // for( var
    _kids[1].jcode(sb.p(": "   )); // for( var : iter )
    _kids[2].jcode(sb.p(" ) "  )); // for( var : iter ) body
    return sb;
  }
}
