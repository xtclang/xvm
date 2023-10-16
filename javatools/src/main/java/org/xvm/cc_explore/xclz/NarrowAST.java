package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.UnaryOpExprAST.Operator;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.ClassPart;
import java.util.HashMap;

class NarrowAST extends AST {
  final String _type;

  static NarrowAST make( XClzBuilder X ) {
    AST[] kids = X.kids(1);     // One expr
    Const type = X.con();
    return new NarrowAST(kids,type);
  }
  
  private NarrowAST( AST[] kids, Const type ) {
    super(kids);
    _type = type instanceof AccessTCon access
      ? "Object"
      : XClzBuilder.jtype((TCon)type,false);
  }

  @Override String type() { return _type; }  
}
