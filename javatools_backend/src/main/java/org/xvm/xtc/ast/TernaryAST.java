package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.xtc.*;

class TernaryAST extends AST {
  static TernaryAST make( ClzBuilder X) { return new TernaryAST(X.kids(3)); }
  TernaryAST( AST... kids ) { super(kids); }

  @Override XType _type() {
    XType s1 = _kids[1]._type;
    XType s2 = _kids[2]._type;
    if( s1==s2 ) return s1;
    // Allow equals modulo boxing and nullable
    assert s1.box().nullable()==s2.box().nullable();
    return s1;
  }
  
  @Override void jmid ( SB sb, int i ) {
    if( i==0 ) sb.p(" ? ");
    if( i==1 ) sb.p(" : ");
  }
}
