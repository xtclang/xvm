package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.xtc.*;

class AssertAST extends AST {
  static AssertAST make( ClzBuilder X ) {
    int flags = X.u31();
    AST cond = (flags&1)!=0 ? ast_term(X) : null;
    AST intv = (flags&2)!=0 ? ast_term(X) : null;
    AST mesg = (flags&4)!=0 ? ast_term(X) : null;
    return new AssertAST(new ConAST("XTC"), cond,intv,mesg);
  }
  private AssertAST( AST... kids ) { super(kids); }
  @Override XType _type() {
    // If I have a conditional child, I want the conditional from the child
    if( _kids[1] != null && _kids[1]._cond )
      _kids[1]._type = XCons.BOOL;
    return XCons.VOID;
  }

  @Override AST prewrite() {
    return new InvokeAST("xassert",(XType)null,_kids).do_type();
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
