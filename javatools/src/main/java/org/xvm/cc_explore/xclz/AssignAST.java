package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.AssignAST.Operator;
import org.xvm.cc_explore.util.SB;

class AssignAST extends AST {
  static final Operator[] OPS = Operator.values();
  final Operator _op;
  static AssignAST make( XClzBuilder X, boolean asgn ) {
    AST[] kids = new AST[2];
    kids[0] = ast_term(X);
    Operator op = asgn ? Operator.Asn : OPS[X.u31()];
    kids[1] = ast_term(X);
    return new AssignAST(kids, op);
  }
  private AssignAST( AST[] kids, Operator op ) { super(kids); _op=op; }
  @Override void jmid( SB sb, int i ) { if( i==0 ) sb.p(' ').p(_op.text).p(' '); }
}
