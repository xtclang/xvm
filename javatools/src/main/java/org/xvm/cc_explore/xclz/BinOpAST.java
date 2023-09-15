package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.BiExprAST.Operator;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;

class BinOpAST extends AST {
  static final Operator[] OPS = Operator.values();
  final Operator _op;
  final String _type;
  BinOpAST( XClzBuilder X, boolean type ) {
    super(X, 2, false);
    _kids[0] = ast_term(X);
    _op = OPS[X.u31()];
    _kids[1] = ast_term(X);
    _type = type ? X.jtype_methcon_ast() : null;
  }
  @Override void jpre ( SB sb ) { }
  @Override void jmid ( SB sb, int i ) { if( i==0 ) sb.p(_op.text); }
}
