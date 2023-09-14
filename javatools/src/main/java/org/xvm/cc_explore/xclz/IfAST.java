package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;

class IfAST extends AST {
  IfAST( XClzBuilder X, int n ) {
    super(X, n, false);
    _kids[0] = ast_term(X);
    _kids[1] = ast(X);
    if( n==3 )
      _kids[2] = ast(X);
  }
  @Override void jpre ( SB sb ) {
    sb.ip("if( ");
  }
  @Override void jmid( SB sb, int i ) {
    if( i==0 ) sb.p(" ) {").nl().ii();
    else if( _kids.length==3 ) sb.di().ip("} else {").ii().nl();
  }
  @Override void jpost( SB sb ) {
    sb.di().ip("}").nl();
  }
}
