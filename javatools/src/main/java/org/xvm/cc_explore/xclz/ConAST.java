package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.cons.TCon;
import org.xvm.cc_explore.util.SB;

class ConAST extends AST {
  final String _con;
  ConAST( Const con ) { super(null,0); _con = XClzBuilder.value_tcon((TCon)con); }
  @Override void jpre ( SB sb ) { sb.p(_con); }
}
