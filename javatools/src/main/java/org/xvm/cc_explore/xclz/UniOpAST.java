package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.UnaryOpExprAST.Operator;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;
import java.util.HashMap;

class UniOpAST extends AST {
  static final Operator[] OPS = Operator.values();
  final String _op0;
  final String _type;

  static UniOpAST make( XClzBuilder X ) {
    AST[] kids = X.kids(1);     // One expr
    Const type = X.con();
    Operator op = OPS[X.u31()];
    return new UniOpAST(kids,op.text,type);
  }
  
  private UniOpAST( AST[] kids, String op0, Const type ) {
    super(kids);
    _op0 = op0;
    _type = type==null ? null : XClzBuilder.jtype(type,false);
  }
  @Override String type() { return _type; }
  @Override SB jcode( SB sb ) { return _kids[0].jcode(sb).p(_op0); }
}
