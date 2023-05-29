package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.ParamTCon;
import org.xvm.util.SB;
import org.xvm.xtc.cons.Const.BinOp;

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
      return XCons.BOOL;
    throw XEC.TODO();
  }

  @Override public SB jcode(SB sb) {
    if( _op.equals("is") ) {
      if( _kids[1] instanceof ConAST con && con._tcon instanceof ParamTCon ) {
        _kids[1].jcode(sb);
        sb.p(".getClass().isInstance(");
        _kids[0].jcode(sb);
        sb.p(")");
        return sb;
        
      } else {
        _kids[0].jcode(sb);
        sb.p(" instanceof ");
        _kids[1].jcode(sb);
        return sb;
      }
      
    } else
      throw XEC.TODO();
  }
}
