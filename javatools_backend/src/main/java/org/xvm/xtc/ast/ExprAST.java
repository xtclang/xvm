package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.Const.AsnOp;
import org.xvm.xtc.cons.Const;

public class ExprAST extends BlockAST {
  static ExprAST make( ClzBuilder X ) {
    // Exactly 1 MultiAST kid
    AST multi = ast(X);
    assert multi instanceof MultiAST;
    // Types?  Ignore the BAST types, and take last child type
    X.consts();
    // Kids are nested in an Expr "method" and not the outer method
    return new ExprAST(multi._kids);
  }

  private ExprAST( AST[] kids ) {
    super(kids);
    _type=null;
  }
  @Override XType _type() {
    // Expression type is the last entry
    return _kids[_kids.length-1]._type;
  }

  @Override public AST rewrite() {
    AST last = _kids[_kids.length-1];
    if( last instanceof ReturnAST )
      return null;
    // If the last kid is an assign, swap with the assign:
    // long year;  { int $tmp1; $tmp1=foo(); bar(); year = $tmp1; }
    // long year = { int $tmp1; $tmp1=foo(); bar(); return $tmp1; }
    if( last instanceof AssignAST asn && asn._op==AsnOp.Asn ) {
      _kids[_kids.length-1] = new ReturnAST(null,asn._kids[1]);
      return new AssignAST(asn._kids[0],this);
    }

    // If not using the last value, can we get rid of expr?
    throw XEC.TODO();
  }

  @Override public SB jcode( SB sb ) {
    String t = _type.clz();
    sb.fmt("new XExpr() { public %0 get_%0() ",t);
    super.jcode(sb);
    return sb.fmt("}.get_%0()",t);
  }
}
