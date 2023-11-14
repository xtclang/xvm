package org.xvm.xtc.ast;

import org.xvm.xtc.*;
import org.xvm.xtc.cons.Const.BinOp;
import org.xvm.util.SB;

class CmpChainAST extends AST {
  final BinOp[] _ops;
  final String[] _tmps;

  static CmpChainAST make( ClzBuilder X ) {
    AST[] kids = X.kids();
    BinOp[] ops = new BinOp[kids.length-1];
    for( int i=0; i<ops.length; i++ )
      ops[i] = BinOp.OPS[X.u31()];
    return new CmpChainAST(kids,ops);
  }
  
  CmpChainAST( AST[] kids, BinOp[] ops ) {
    super(kids);
    _ops = ops;
    _tmps = new String[ops.length];
  }

  @Override XType _type() { return XType.BOOL; }

  @Override AST rewrite() {
    BlockAST blk = enclosing_block();
    for( int i=1; i<_ops.length; i++ )
      _tmps[i] = blk.add_tmp(_kids[i]._type);
    return this;
  }
  
  // _kids[0] op (tmp1=_kids[1]) &&
  // tmp1     op (tmp2=_kids[2]) &&
  // tmp2     op _kids[3];
  @Override public SB jcode( SB sb ) {
    XType xt = _kids[0]._type;
    for( int i=0; i<_ops.length; i++ ) {
      if( i==0 ) _kids[0].jcode(sb);
      else sb.p(_tmps[i]);

      if( xt.primeq() ) {
        // ... == (tmp=_kids[i])
        sb.p(" ").p(_ops[i].text).p(" ");
        if( i < _ops.length-1 ) sb.p("(").p(_tmps[i+1]).p("=");
        _kids[i+1].jcode(sb);
        if( i < _ops.length-1 ) sb.p(" )");

      } else {
        // ... .equals(tmp=_kids[i])
        sb.p(".").p(_ops[i].toString()).p("(");
        if( i < _ops.length-1 ) sb.p(_tmps[i+1]).p("=");
        _kids[i+1].jcode(sb);
        sb.p(" )");
        
      }      
      sb.p(" && ");
    }
    return sb.unchar(4);
  }

}
