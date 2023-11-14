package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.xtc.*;

class TemplateAST extends AST {
  static TemplateAST make( ClzBuilder X) { return new TemplateAST(X.kids()); }
  private TemplateAST( AST[] kids ) {
    super(kids);
    _kids[0] = null;            // Toss away the StringBuilder first op
  }
  @Override XType _type() { return XType.STRING; }
  @Override void jpre ( SB sb ) { sb.p("\"\"+ "); }
  @Override void jmid ( SB sb, int i ) { sb.p("+ "); }
  @Override void jpost( SB sb ) { sb.unchar(2); }  
}
