package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.Const;

class TernaryAST extends AST {
  static TernaryAST make( ClzBuilder X) { return new TernaryAST(X.kids(3),XType.xtypes(X.consts())[0]); }
  TernaryAST( AST[] kids, XType type ) { super(kids); _type = type; }

  @Override XType _type() { return _type; }

  @Override void jmid ( SB sb, int i ) {
    if( i==0 ) sb.p(" ? ");
    if( i==1 ) sb.p(" : ");
  }
}
