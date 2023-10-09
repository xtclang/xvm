package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.AssignAST.Operator;
import org.xvm.cc_explore.XEC;
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
  @Override AST rewrite() {
    // Assign of a non-primitive array
    if( _kids[0] instanceof BinOpAST bin &&
        // Replace with "ary.set(idx,val)"
        bin._op0.equals(".at(") )
      return new InvokeAST("set",bin._kids[0],bin._kids[1],_kids[1]);
    // Conditional assign; the conditional is passed through XRuntime.$COND and
    // the def happens at the same time.  Hoist the java def to the enclosing block.
    if( _op == Operator.AsnIfNotFalse )
      enclosing_block().add_tmp(_kids[0].type(),((DefRegAST)_kids[0])._name);
    return this;
  }
  @Override SB jcode( SB sb ) {
    if( _op == Operator.AsnIfNotFalse ) {
      // Expression result is the boolean conditional value,
      // and also the var is defined.
      // $t(var = init) & XRuntime.GET$COND()
      sb.p("$t(");
      sb.p(((DefRegAST)_kids[0])._name);
      sb.p(" = ");
      _kids[1].jcode(sb);
      sb.p(") & XRuntime.GET$COND()");
    } else {
      _kids[0].jcode(sb);
      sb.p(" ").p(_op.text).p(" ");
      _kids[1].jcode(sb);
    }
    return sb;
  }
}
