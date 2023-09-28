package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.UnaryOpExprAST.Operator;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;
import java.util.HashMap;

class NarrowAST extends AST {
  final AccessTCon _access;

  static NarrowAST make( XClzBuilder X ) {
    AST[] kids = X.kids(1);     // One expr
    Const type = X.con();
    return new NarrowAST(kids,type);
  }
  
  private NarrowAST( AST[] kids, Const type ) {
    super(kids);
    if( type instanceof AccessTCon access ) {
      _access = access;
    } else
      throw XEC.TODO();
  }
}
