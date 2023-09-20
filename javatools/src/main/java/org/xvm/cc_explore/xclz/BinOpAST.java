package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.BiExprAST.Operator;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.SB;

class BinOpAST extends AST {
  static final Operator[] OPS = Operator.values();
  final Operator _op;
  final String _type;

  static BinOpAST make( XClzBuilder X, boolean has_type ) {
    AST[] kids = new AST[2];
    kids[0] = ast_term(X);
    Operator op = OPS[X.u31()];
    kids[1] = ast_term(X);
    Const type = has_type ? X.con(X.u31()) : null;
    return new BinOpAST(kids,op,type);
  }
  
  private BinOpAST( AST[] kids, Operator op, Const type ) {
    super(kids);
    _op = op;
    _type = type==null ? null : XClzBuilder.jtype_tcon((TCon)type,false);
  }
  @Override String type() { return _type; }
  @Override AST rewrite() {
    // Range is not a valid Java operator, so need to change everything here
    if( _op.text.equals(".." ) ) return new NewAST(_kids,_type+"II");
    if( _op.text.equals("..<") ) return new NewAST(_kids,_type+"IE");
    return this;
  }
  @Override void jmid ( SB sb, int i ) { if( i==0 ) sb.p(_op.text); }
}
