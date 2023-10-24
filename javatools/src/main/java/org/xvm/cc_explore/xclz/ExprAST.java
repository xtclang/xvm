package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.cons.Const;

class ExprAST extends AST {
  static ExprAST make( XClzBuilder X ) {
    
    // Parse kids in order as stmts not exprs
    AST kid = ast(X);
    // Types?
    Const[] types = X.consts();
    String type = XClzBuilder.jtype(types[0],false);
    return new ExprAST(type,kid);
  }
  
  ExprAST( String type, AST... kids ) { super(kids);  _type = type; }
  @Override String _type() { return _type; }

  // new Runnable() { void run() { ....; } }.run()
  @Override void jpre( SB sb ) {
    sb.p("new XExpr() { public ").p(_type).p(" get_").p(_type).p("() {").ii().nl();
  }
  @Override void jmid( SB sb, int i ) { sb.p(";").nl(); }
  @Override void jpost( SB sb ) {
    sb.di().ip("}}.get_").p(_type).p("()");
  }
}
