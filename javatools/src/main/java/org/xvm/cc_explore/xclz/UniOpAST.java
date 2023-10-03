package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.UnaryOpExprAST.Operator;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.*;
import java.util.HashMap;

class UniOpAST extends AST {
  static final Operator[] OPS = Operator.values();
  final String _pre, _post;
  final String _type;
  final Const _conv;

  static UniOpAST make( XClzBuilder X, boolean is_conv ) {
    AST[] kids = X.kids(1);     // One expr
    Const type = X.con();
    Operator op = OPS[X.u31()]; // Post op by default
    Const conv = is_conv ? X.con() : null;
    return new UniOpAST(kids,null,op.text,type,conv);
  }
  
  static UniOpAST make( XClzBuilder X, String pre, String post ) {
    AST[] kids = X.kids(1);     // One expr
    Const type = X.con();
    // XTC allows booleans "~" but Java does not.
    if( "~".equals(pre) && "boolean".equals(XClzBuilder.jtype(type,false)) )
      pre = "!";                // Use Java bang instead
    return new UniOpAST(kids,pre,post,type,null);
  }
  
  private UniOpAST( AST[] kids, String pre, String post, Const type, Const conv ) {
    super(kids);
    _pre = pre;
    _post = post;
    _type = type==null ? null : XClzBuilder.jtype(type,false);
    _conv = conv;
  }
  @Override AST rewrite() {
    if( _conv!=null ) {
      MethodPart meth = (MethodPart)((MethodCon)_conv).part();
      String k0type = _kids[0].type();
      //return new InvokeAST(
      throw XEC.TODO();
    }
    return this;
  }
  
  @Override String type() { return _type; }
  @Override SB jcode( SB sb ) {
    if( _pre !=null ) sb.p(" ").p(_pre );
    _kids[0].jcode(sb);
    if( _post!=null ) sb.p(_post).p(" ");
    return sb;
  }
}
