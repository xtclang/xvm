package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.util.SB;

class TODOAST extends AST {
  final String _name;
  final Const[] _cons;
  static TODOAST make(XClzBuilder X ) { return new TODOAST(X.utf8(),X.consts()); }
  private TODOAST( String name, Const[] cons ) {
    super(null);
    _name = name;
    _cons = cons;
  }
  @Override void jpre ( SB sb ) { sb.p("/*").p(_name).p("*/"); }
}
