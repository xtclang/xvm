package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.UnaryOpExprAST.Operator;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;
import java.util.HashMap;

class UniOpAST extends AST {
  static final Operator[] OPS = Operator.values();
  final String _pre, _post;
  final String _type;

  static UniOpAST make( XClzBuilder X ) {
    AST[] kids = X.kids(1);     // One expr
    Const type = X.con();
    Operator op = OPS[X.u31()]; // Post op by default
    return new UniOpAST(kids,null,op.text,type);
  }
  
  static UniOpAST make( XClzBuilder X, String pre, String post ) {
    AST[] kids = X.kids(1);     // One expr
    Const type = X.con();
    return new UniOpAST(kids,pre,post,type);
  }
  
  private UniOpAST( AST[] kids, String pre, String post, Const type ) {
    super(kids);
    _pre = pre;
    _post = post;
    _type = type==null ? null : XClzBuilder.jtype(type,false);
  }
  @Override String type() { return _type; }
  @Override SB jcode( SB sb ) {
    if( _pre !=null ) sb.p(_pre );
    _kids[0].jcode(sb);
    if( _post!=null ) sb.p(_post);
    return sb;
  }
}
