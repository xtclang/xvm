package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;

class TemplateAST extends AST {
  TemplateAST( XClzBuilder X ) {
    super(X, X.u31());
    _kids[0] = null;            // Toss away the StringBuilder first op
  }
  @Override void jpre ( SB sb ) { sb.p("\"\"+"); }
  @Override void jmid ( SB sb, int i ) { sb.p("+"); }
  @Override void jpost( SB sb ) { sb.unchar(1); }  
}
