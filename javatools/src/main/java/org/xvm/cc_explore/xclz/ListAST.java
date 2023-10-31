package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.util.SB;

class ListAST extends AST {
  final boolean _tuple;
  
  static ListAST make( XClzBuilder X, boolean tuple ) {
    Const type = X.con();
    return new ListAST(X.kids(),type,tuple);
  }
  private ListAST( AST[] kids, Const type, boolean tuple ) {
    super(kids);
    _type = XType.xtype(type,false);
    _tuple = tuple;
  }
  @Override XType _type() { return _type; }
  
  @Override void jpre( SB sb ) {
    if( !_tuple ) throw XEC.TODO();
    _type.p(sb.p("new ")).p("(");
  }
  @Override void jmid( SB sb, int i ) { sb.p(", "); }
  @Override void jpost( SB sb ) { sb.unchar(2).p(")"); }
}
