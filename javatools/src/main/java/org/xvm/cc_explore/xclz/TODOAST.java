package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.util.SB;

class TODOAST extends AST {
  final String _name;
  final Const[] _cons;
  TODOAST( XClzBuilder X ) {
    super(X,0);
    _name = X.utf8();
    _cons = X.consts();
  }
  @Override void jpre ( SB sb ) { sb.ip("// ").p(_name).nl(); }
  @Override void jpost( SB sb ) { }
}
