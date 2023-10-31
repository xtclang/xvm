package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.UnaryOpExprAST.Operator;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.ClassPart;
import java.util.HashMap;

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
