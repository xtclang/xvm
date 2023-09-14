package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.AssignAST.Operator;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;

class AssignAST extends AST {
  static final Operator[] OPS = Operator.values();
  final Operator _op;
  AssignAST( XClzBuilder X, boolean asgn ) {
    super(X, 2, false);
    _kids[0] = ast_term(X);
    _op = asgn ? Operator.Asn : OPS[X.u31()];
    _kids[1] = ast_term(X);
  }
  @Override void jpre ( SB sb ) { }
  @Override void jmid ( SB sb, int i ) { sb.p(' ').p(_op.text).p(' '); }
}
