package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.cons.TCon;
import org.xvm.cc_explore.util.SB;

class ConAST extends AST {
  final TCon _tcon;
  final String _con;
  ConAST( Const con ) {
    super(null);
    _tcon = (TCon)con;
    _con = XClzBuilder.value_tcon(_tcon);
  }
  ConAST( String con ) {
    super(null);
    _tcon = null;
    _con = con;
  }
  @Override SB jcode( SB sb ) { return sb.p(_con); }
}