package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;

class TernaryAST extends AST {
  static TernaryAST make(XClzBuilder X) { return new TernaryAST(X.kids(3)); }
  TernaryAST( AST... kids ) { super(kids); }

  @Override String _type() {
    String s1 = _kids[1]._type;
    String s2 = _kids[2]._type;
    if( s1.equals("long") && s2.equals("Long") ) return s2;
    if( s1.equals("Long") && s2.equals("long") ) return s1;
    // TODO: other java prims escape hatch
    assert s1.equals(s2);
    return s1;
  }
  
  @Override void jmid ( SB sb, int i ) {
    if( i==0 ) sb.p(" ? ");
    if( i==1 ) sb.p(" : ");
  }
}
