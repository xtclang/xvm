package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;

class MultiAST extends AST {
  static MultiAST make(XClzBuilder X) {
    int len = X.u31();
    AST[] kids = new AST[len];
    for( int i=0; i<len; i++ )
      kids[i] = ast_term(X);
    return new MultiAST(kids);
  }
  MultiAST( AST[] kids ) { super(kids); }
  @Override void jpre ( SB sb ) { sb.p("("); }
  @Override void jmid ( SB sb, int i ) { sb.p(", "); }
  @Override void jpost( SB sb ) { sb.unchar(2).p(")"); }
}
