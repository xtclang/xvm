package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;

class TemplateAST extends AST {
  static TemplateAST make(XClzBuilder X) { return new TemplateAST(X.kids()); }
  private TemplateAST( AST[] kids ) {
    super(kids);
    _kids[0] = null;            // Toss away the StringBuilder first op
  }
  @Override String _type() { return "String"; }
  @Override void jpre ( SB sb ) { sb.p("\"\"+ "); }
  @Override void jmid ( SB sb, int i ) { sb.p("+ "); }
  @Override void jpost( SB sb ) { sb.unchar(2); }  
}
