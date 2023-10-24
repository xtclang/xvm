package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.util.SB;

class ListAST extends AST {
  final boolean _tuple;
  final String _type;
  
  static ListAST make( XClzBuilder X, boolean tuple ) {
    Const type = X.con();
    return new ListAST(X.kids(),type,tuple);
  }
  private ListAST( AST[] kids, Const type, boolean tuple ) {
    super(kids);
    _type = XClzBuilder.jtype(type,false);
    _tuple = tuple;
  }
  @Override String _type() { return _type; }
  
  @Override void jpre( SB sb ) {
    if( !_tuple ) throw XEC.TODO();
    sb.p("new ").p(_type).p("(");
  }
  @Override void jmid( SB sb, int i ) { sb.p(", "); }
  @Override void jpost( SB sb ) { sb.unchar(2).p(")"); }
}
