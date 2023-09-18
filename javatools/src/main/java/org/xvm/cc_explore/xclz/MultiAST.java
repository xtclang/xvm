package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;

class MultiAST extends AST {
  MultiAST( XClzBuilder X ) { super(X, X.u31()); }
  @Override void jpre ( SB sb ) { sb.p("("); }
  @Override void jmid ( SB sb, int i ) { sb.p(", "); }
  @Override void jpost( SB sb ) { sb.unchar(2).p(")"); }
}
