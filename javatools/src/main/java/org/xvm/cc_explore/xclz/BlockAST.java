package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;

class BlockAST extends AST {
  BlockAST( XClzBuilder X ) { super(X, X.u31()); }
  @Override void jpre ( SB sb ) {  sb.ip("{").ii().nl();  }
  @Override void jpost( SB sb ) {  sb.di().ip("}").nl();  }
}
