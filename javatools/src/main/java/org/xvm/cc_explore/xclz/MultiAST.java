package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;

class MultiAST extends AST {
  static MultiAST make(XClzBuilder X, boolean expr) {
    int len = X.u31();
    AST[] kids = new AST[len];
    for( int i=0; i<len; i++ )
      kids[i] = expr ? ast_term(X) : ast(X);
    return new MultiAST(kids);
  }
  MultiAST( AST[] kids ) { super(kids); }
  @Override void jpre(SB sb) {
    sb.nl().ii().i();
    if( _kids.length > 1 ) throw XEC.TODO(); // Blocks need "{}", but how exprs?
  }
  @Override void jpost(SB sb) { sb.di(); }
}
