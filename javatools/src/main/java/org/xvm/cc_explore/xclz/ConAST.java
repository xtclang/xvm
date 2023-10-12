package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.cons.*;
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
  @Override String type() {
    if( _tcon instanceof StringCon ) return "String";
    if( _tcon instanceof EnumCon econ )
      return econ.part()._par._name;
    return _con;
  }
  @Override SB jcode( SB sb ) { return sb.p(_con); }
}
