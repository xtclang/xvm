package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;

class TemplateAST extends AST {
  TemplateAST( XClzBuilder X ) {
    super(X, X.u31(), false);
    for( int i=0; i<_kids.length; i++ )
      _kids[i] = ast_term(X);
  }
  @Override void jpre ( SB sb ) { throw XEC.TODO(); }
}
