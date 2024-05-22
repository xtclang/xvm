package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xtc.*;

class TernaryAST extends AST {
  private boolean _elvis;
  static TernaryAST make( ClzBuilder X) { return new TernaryAST(X.kids(3),XType.xtypes(X.consts())[0]); }
  TernaryAST( AST[] kids, XType type ) { super(kids); _type = type; }

  @Override XType _type() { return _type; }

  RegAST doElvis( AST elvis ) {
    assert _kids[0]==null;
    _elvis = true;
    XType type = elvis._type;
    // THIS:     ( : [good_expr (elvis e0)] [bad_expr])
    // MAPS TO:  ( (  (tmp=e0) != null ) ? [good_expr tmp] : [bad_expr] )
    // MAPS TO:  ( ($t(tmp=e0) && $COND) ? [good_expr tmp] : [bad_expr] )
    String tmpname = enclosing_block().add_tmp(type);
    // Assign elvis expression e0 to tmp
    AST reg = new RegAST(tmpname,type);
    AST asn = new AssignAST(reg,elvis);
    asn._type = type;
    asn._par = this;
    _kids[0] = asn;
    // Use tmp instead of Elvis in good_expr
    return new RegAST(tmpname,type);
  }

  // Box as needed
  @Override XType reBox( AST kid ) {
    return _kids[0]==kid ? null : _type;
  }

  @Override public SB jcode( SB sb ) {
    sb.p("(").p(_elvis ? (_cond ? "$t(" : "(") : "");
    _kids[0].jcode(sb);
    sb.p(_elvis ? (_cond ? ") && XRuntime.$COND" : ")!=null") : "").p(" ? ");
    _kids[1].jcode(sb).p(" : ");
    _kids[2].jcode(sb).p(")");
    return sb;
  }
}
