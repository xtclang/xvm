package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xtc.*;

class ThrowAST extends AST {
  
  static ThrowAST make( ClzBuilder X ) {
    AST kid = ast_term(X);           // Throwable
    AST msg = X.u31()!=0 ? ast_term(X) : null;
    return new ThrowAST(kid,msg);
  }
  private ThrowAST( AST... kids ) { super(kids); }

  @Override XType _type() { return XType.VOID; }
  
  @Override public SB jcode( SB sb ) {
    return _kids[0].jcode(sb.ip("throw "));
  }
}
