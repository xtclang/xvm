package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;

class TernaryAST extends AST {
  TernaryAST( XClzBuilder X ) { super(X, 3); }
  @Override void jmid ( SB sb, int i ) {
    if( i==0 ) sb.p(" ? ");
    if( i==1 ) sb.p(" : ");
  }
}
