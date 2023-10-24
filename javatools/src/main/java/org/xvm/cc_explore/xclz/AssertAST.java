package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.AssignAST.Operator;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;

class AssertAST extends AST {
  static AssertAST make( XClzBuilder X ) {
    int flags = X.u31();
    AST cond = (flags&1)!=0 ? ast_term(X) : null;
    AST intv = (flags&2)!=0 ? ast_term(X) : null;
    AST mesg = (flags&4)!=0 ? ast_term(X) : null;
    return new AssertAST(new ConAST("XClz"), cond,intv,mesg);
  }
  private AssertAST( AST... kids ) { super(kids); }
  @Override String _type() { return "void"; }

  @Override AST rewrite() {
    return new InvokeAST("xassert",(String)null,_kids).do_type();
  }
  @Override void jmid ( SB sb, int i ) {
    if( i==0 ) sb.p(".xassert(");
    else sb.p(", ");
  }
  @Override void jpost( SB sb ) {
    if( _kids[0]!=null ) sb.unchar(2);
    sb.p(")");
  }
}
