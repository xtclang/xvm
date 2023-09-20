package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;

class ReturnAST extends AST {
  static ReturnAST make( XClzBuilder X, int n ) { return new ReturnAST(X.kids(n));  }
  ReturnAST( AST[] kids ) { super(kids); }
  @Override void jpre( SB sb ) { sb.p("return "); }
}
