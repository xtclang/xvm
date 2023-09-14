package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.BiExprAST.Operator;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;

class BinOpAST extends AST {
  final Operator _op;
  static final Operator[] OPS = Operator.values();
  BinOpAST( XClzBuilder X ) {
    super(X, 2, false);
    _kids[0] = ast_term(X);
    _op = OPS[X.u31()];
    _kids[1] = ast_term(X);
  }
  @Override void jpre ( SB sb ) { }
  @Override void jmid ( SB sb, int i ) { if( i==0 ) sb.p(_op.text); }
}
