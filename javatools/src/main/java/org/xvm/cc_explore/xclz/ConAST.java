package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.SB;

class ConAST extends AST {
  final TCon _tcon;
  final String _con;
  ConAST( Const con ) { this((TCon)con, XClzBuilder.value_tcon(con), XClzBuilder.jtype(con,false)); }
  ConAST( String con ) { this(null,con,con); }
  ConAST( TCon tcon, String con, String type ) {
    super(null);
    _tcon = tcon;
    _con  = con ;
    _type = type;
  }
  @Override String _type() { return _type; }
  @Override SB jcode( SB sb ) { return sb.p(_con); }
}
