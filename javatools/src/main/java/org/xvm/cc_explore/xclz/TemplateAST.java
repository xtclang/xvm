package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;

class TemplateAST extends AST {
  TemplateAST( XClzBuilder X ) {
    super(X, X.u31()-1, false);
    AST ast = ast_term(X);      // Toss away the StringBuilder first op
    for( int i=0; i<_kids.length; i++ )
      _kids[i] = ast_term(X);
  }
  @Override void jpre ( SB sb ) { sb.p("\"\"+");  }
  @Override void jmid ( SB sb, int i ) { sb.p("+"); }
  @Override void jpost( SB sb ) { sb.unchar(1); }  
}
