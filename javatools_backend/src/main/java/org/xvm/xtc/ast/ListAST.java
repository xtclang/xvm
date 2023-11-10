package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.cons.Const;
import org.xvm.util.SB;
import org.xvm.xtc.*;

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
