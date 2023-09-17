package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;

class ReturnAST extends AST {
  ReturnAST( XClzBuilder X, int n ) {
    super(X, n, false);
    if( _kids != null )
      for( int i=0; i<_kids.length; i++ )
        _kids[i] = ast_term(X);
  }
  @Override void jpre( SB sb ) { sb.p("return "); }
}
