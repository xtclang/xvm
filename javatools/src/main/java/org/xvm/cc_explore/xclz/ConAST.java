package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.cons.TCon;
import org.xvm.cc_explore.util.SB;

class ConAST extends AST {
  final String _con;
  ConAST( XClzBuilder X, Const con ) { super(X, 0); _con = X.value_tcon((TCon)con); }
  @Override void jpre ( SB sb ) { sb.p(_con); }
}
