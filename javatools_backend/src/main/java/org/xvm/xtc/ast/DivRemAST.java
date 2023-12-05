package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.util.SB;
import org.xvm.xtc.cons.Const.BinOp;
import java.util.HashMap;

class DivRemAST extends AST {
  final String _op;
  final XType[] _rets;

  static DivRemAST make( ClzBuilder X ) {
    AST[] kids = new AST[2];
    kids[0] = ast_term(X);
    BinOp op = BinOp.OPS[X.u31()];
    kids[1] = ast_term(X);
    XType[] rets = XType.xtypes(X.consts());
    return new DivRemAST(op.text,rets,kids);
  }
  
  DivRemAST( String op, XType[] rets, AST... kids ) {
    super(kids);
    _op = op;
    _rets = rets;
  }

  @Override XType _type() {
    if( _op.equals("is") )
      return XType.BOOL;
    throw XEC.TODO();
  }

  @Override AST rewrite() {
    //throw XEC.TODO();
    return this;
  }
  @Override void jmid(SB sb, int i) {
    if( _op.equals("is") ) {
      if( i==0 ) sb.p(" instanceof ");
    } else
      throw XEC.TODO();
  }
}
