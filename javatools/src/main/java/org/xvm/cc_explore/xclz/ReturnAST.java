package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;

class ReturnAST extends AST {
  ReturnAST( XClzBuilder X ) { super(X, X.u31()); }
  @Override void jpre ( SB sb ) {
    sb.ip("return");
    if( _kids!=null )
      throw XEC.TODO();
    sb.p(";").nl();
  }  
  @Override void jpost( SB sb ) { }
}

