package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;

class TODOAST extends AST {
  final String _name;
  TODOAST( XClzBuilder X ) {
    super(X,0);
    _name = X.utf8();
    int len = X.u31();
    if( len!=0 ) throw XEC.TODO();
  }
  @Override void jpre ( SB sb ) { sb.ip("// ").p(_name).nl(); }
  @Override void jpost( SB sb ) { }
}
