package org.xvm.xclz;

import org.xvm.cons.*;
import org.xvm.util.SB;

class ConAST extends AST {
  final TCon _tcon;
  final String _con;
  ConAST( Const con ) { this((TCon)con, XClzBuilder.value_tcon(con), XType.xtype(con,false)); }
  ConAST( String con ) { this(null,con, XType.Base.make(con)); }
  ConAST( TCon tcon, String con, XType type ) {
    super(null);
    _tcon = tcon;
    _con  = con ;
    _type = type;
  }
  @Override XType _type() { return _type; }
  @Override SB jcode( SB sb ) { return sb.p(_con); }
}
