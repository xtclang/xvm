package org.xvm.xclz;

import org.xvm.cons.Const.UniOp;
import org.xvm.cons.AccessTCon;
import org.xvm.cons.Const;

class NarrowAST extends AST {

  static NarrowAST make( XClzBuilder X ) {
    AST[] kids = X.kids(1);     // One expr
    Const type = X.con();
    return new NarrowAST(kids,type);
  }
  
  private NarrowAST( AST[] kids, Const type ) {
    super(kids);
    _type = type instanceof AccessTCon
      ? XType.OBJECT
      : XType.xtype(type,false);
  }
  @Override XType _type() { return _type; }
  @Override String name() { return _kids[0].name(); }
}
