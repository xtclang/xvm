package org.xvm.xtc.ast;

import org.xvm.xtc.*;
import org.xvm.xtc.cons.AccessTCon;
import org.xvm.xtc.cons.Const;

class NarrowAST extends AST {

  static NarrowAST make( ClzBuilder X ) {
    AST[] kids = X.kids(1);     // One expr
    Const type = X.con();
    return new NarrowAST(kids,type);
  }
  
  private NarrowAST( AST[] kids, Const type ) {
    super(kids);
    _type = type instanceof AccessTCon
      ? XType.XXTC
      : XType.xtype(type,true);
  }
  @Override XType _type() { return _type; }
  @Override String name() { return _kids[0].name(); }
}
