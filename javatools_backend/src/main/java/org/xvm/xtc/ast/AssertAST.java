package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xtc.*;

class AssertAST extends ElvisAST {
  static AssertAST make( ClzBuilder X ) {
    int flags = X.u31();
    AST cond = (flags&1)!=0 ? ast_term(X) : new ConAST("false",XCons.FALSE);
    AST intv = (flags&2)!=0 ? ast_term(X) : null;
    AST mesg = (flags&4)!=0 ? ast_term(X) : null;
    return new AssertAST(cond,intv,mesg);
  }
  private AssertAST( AST... kids ) { super(kids); }
  @Override XType _type() {
    int len = _kids.length;
    for( int i=0; i<len-2; i++ )
      assert _kids[i]._cond || _kids[i]._type.isBool();
    return XCons.VOID;
  }

  @Override public SB jcode( SB sb ) {
    sb.p("XTC.xassert(");
    int len = _kids.length;
    for( int i=0; i<len-2; i++ ) {
      if( _kids[i]._cond ) _kids[i].jcode(sb.p("$t(")).p(") && XRuntime.$COND && ");
      else _kids[i].jcode(sb).p(" && ");
    }
    sb.unchar(4);
    if( _kids[len-2] != null )
      throw XEC.TODO();         // intv
    if( _kids[len-1] != null )
      _kids[len-1].jcode(sb.p(", "));   // String mesg
    return sb.p(")");
  }
}
