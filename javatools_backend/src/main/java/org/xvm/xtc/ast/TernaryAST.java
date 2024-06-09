package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xtc.*;

class TernaryAST extends ElvisAST {
  static TernaryAST make( ClzBuilder X) { return new TernaryAST(X.kids(3),XType.xtypes(X.consts())[0]); }
  TernaryAST( AST[] kids, XType type ) { super(kids); _type = type; }

  @Override XType _type() { return _type; }

  // Box as needed
  @Override XType reBox( AST kid ) {
    return _kids[_kids.length-2]==kid || _kids[_kids.length-1]==kid ? _type : null;
  }

  @Override public SB jcode( SB sb ) {
    sb.p("(");
    // Due to Elvis operators, there might be many tests ANDed together
    int len = _kids.length;
    for( int i=0; i<len-2; i++ ) {
      _kids[i].jcode(sb);
      sb.p(" && ");
    }
    sb.unchar(4).p(" ? ");
    _kids[len-2].jcode(sb).p(" : ");
    _kids[len-1].jcode(sb).p(")");
    return sb;
  }
}
