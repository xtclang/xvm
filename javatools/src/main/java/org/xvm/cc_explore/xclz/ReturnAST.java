package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;

class ReturnAST extends AST {
  ReturnAST( XClzBuilder X, int n ) { super(X, n); }
  @Override void jpre( SB sb ) { sb.p("return "); }
}
