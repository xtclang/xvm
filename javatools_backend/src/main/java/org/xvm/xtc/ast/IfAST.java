package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.xtc.*;

class IfAST extends AST {
  static IfAST make( ClzBuilder X, int n ) {
    int nlocals0 = X.nlocals();  // Count of locals
    AST[] kids = new AST[n];
    kids[0] = ast_term(X);
    int nlocals1 = X.nlocals();  // Count of locals
    kids[1] = ast(X);
    X.pop_locals(nlocals1);      // Pop scope-locals at end of scope
    if( n==3 )
      kids[2] = ast(X);
    X.pop_locals(nlocals0);     // Pop scope-locals at end of scope
    return new IfAST(kids);
  }
  IfAST( AST... kids ) { super(kids); }

  @Override XType _type() { return XCons.VOID; }

  @Override public SB jcode( SB sb ) {
    _kids[0].jcode(sb.ip("if( ")).p(") ");
    if( _kids[1] instanceof BlockAST ) {
      // if( pred ) {
      //   BLOCK;
      // }<<<---- Cursor here
      _kids[1].jcode(sb);
      if( _kids.length==2 ) return sb;
      // if( pred ) {
      //   BLOCK;
      // } <<<---- Cursor here
      sb.p(" ");
    } else {
      // if( pred )
      //   STMT<<<---- Cursor here
      sb.ii().nl();
      _kids[1].jcode(sb);
      sb.di();
      if( _kids.length==2 ) return sb;
      // if( pred )
      //   STMT;
      // <<<---- Cursor here
      sb.p(";").nl().i();
    }

    sb.p("else ");

    if( _kids[2] instanceof BlockAST ) {
      _kids[2].jcode(sb);
    } else {
      sb.ii().nl();
      _kids[2].jcode(sb);
      sb.di();
    }
    return sb;
  }
}
