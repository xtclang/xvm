package org.xvm.xtc.ast;

import org.xvm.xtc.*;
import org.xvm.xtc.MethodPart;
import org.xvm.xtc.cons.Const;
import org.xvm.xtc.cons.MethodCon;
import org.xvm.xtc.cons.Const.BinOp;
import org.xvm.util.SB;

class CmpChainAST extends AST {
  final BinOp[] _ops;
  final String[] _tmps;
  final MethodPart _cmp;

  static CmpChainAST make( ClzBuilder X ) {
    AST[] kids = X.kids();
    BinOp[] ops = new BinOp[kids.length-1];
    for( int i=0; i<ops.length; i++ )
      ops[i] = BinOp.OPS[X.u31()];
    Const con = X.con();
    MethodPart cmp = (MethodPart)con.part();
    return new CmpChainAST(kids,ops,cmp);
  }

  CmpChainAST( AST[] kids, BinOp[] ops, MethodPart cmp ) {
    super(kids);
    _ops = ops;
    _cmp = cmp;
    _tmps = new String[ops.length];
  }

  @Override XType _type() { return XCons.BOOL; }

  @Override AST postwrite() {
    BlockAST blk = enclosing_block();
    for( int i=1; i<_ops.length; i++ )
      _tmps[i] = blk.add_tmp(_kids[i]._type);
    return this;
  }

  // _kids[0] op (tmp1=_kids[1]) &&
  // tmp1     op (tmp2=_kids[2]) &&
  // tmp2     op _kids[3];
  //   LHS.unBox && RHS.isBox ? box(e0) : e0;
  //   both_prims ? op : assert_EQ, .equals;
  //   LHS.isBox && RHS.unBox ? box : _;
  //   openParen;
  //   last ? e1 : tmp_assign(e1); // note: temp holds primitive is possible
  //   closeParen;
  @Override public SB jcode( SB sb ) {
    for( int i=0; i<_ops.length; i++ ) {
      XType lhst = _kids[i  ]._type;
      XType rhst = _kids[i+1]._type;
      boolean lhs = lhst.primeq();
      boolean rhs = rhst.primeq();
      if( lhs && !rhs ) {
        make(sb,lhst).p("(");
        tmp(sb,i);
        sb.p(")");
      } else {
        tmp(sb,i);
      }

      if( lhs && rhs ) sb.p(_ops[i].text);
      else sb.p(".").p(_ops[i].toString());
      sb.p("(");

      if( !lhs && rhs ) make(sb,rhst).p("(");

      if( i < _ops.length-1 )
        sb.p(_tmps[i+1]).p("=");
      _kids[i+1].jcode(sb);

      if( !lhs && rhs )  sb.p(")");
      sb.p(") && ");
    }
    return sb.unchar(4);
  }

  private SB make(SB sb, XType xt) { return sb.p(xt.box().clz_bare()).p(".make"); }

  private SB tmp(SB sb, int i) { return i==0 ? _kids[0].jcode(sb) : sb.p(_tmps[i]); }
}
