package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;

class TernaryAST extends AST {
  TernaryAST( XClzBuilder X ) {
    super(X, 3, false);
    for( int i=0; i<_kids.length; i++ )
      _kids[i] = ast_term(X);
  }
  @Override void jmid ( SB sb, int i ) {
    if( i==0 ) sb.p(" ? ");
    if( i==1 ) sb.p(" : ");
  }
}
