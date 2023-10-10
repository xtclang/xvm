package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;

class TernaryAST extends AST {
  static TernaryAST make(XClzBuilder X) { return new TernaryAST(X.kids(3)); }
  TernaryAST( AST... kids ) { super(kids); }
  @Override void jmid ( SB sb, int i ) {
    if( i==0 ) sb.p(" ? ");
    if( i==1 ) sb.p(" : ");
  }
}
