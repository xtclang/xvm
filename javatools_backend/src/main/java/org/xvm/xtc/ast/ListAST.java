package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.cons.Const;
import org.xvm.util.SB;
import org.xvm.xtc.*;

class ListAST extends AST {
  final boolean _tuple;

  static ListAST make( ClzBuilder X, boolean tuple ) {
    Const type = X.con();
    return new ListAST(X.kids(),XType.xtype(type,false),tuple);
  }
  private ListAST( AST[] kids, XType type, boolean tuple ) {
    super(kids);
    _type = type;
    _tuple = tuple;
  }
  @Override XType _type() { return _type; }

  @Override public AST rewrite() {
    if( _type == XCons.TUPLE0 && _kids!=null && _kids.length>0 && _kids[0]._type == XCons.VOID )
      return new ListAST(null,_type,_tuple); // Replace void with no-kids
    return null;
  }

  @Override void jpre( SB sb ) {
    if( !_tuple ) throw XEC.TODO();
    _type.clz(sb.p("new ")).p("(  ");
  }
  @Override void jmid( SB sb, int i ) { sb.p(", "); }
  @Override void jpost( SB sb ) { sb.unchar(2).p(")"); }
}
