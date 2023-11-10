package org.xvm.xtc.ast;

import org.xvm.xtc.*;
import org.xvm.util.SB;
import org.xvm.xtc.cons.Const;

class ExprAST extends AST {
  static ExprAST make( XClzBuilder X ) {
    
    // Parse kids in order as stmts not exprs
    AST kid = ast(X);
    // Types?
    Const[] types = X.consts();
    XType type = XType.xtype(types[0],false);
    return new ExprAST(type,kid);
  }
  
  ExprAST( XType type, AST... kids ) { super(kids);  _type = type; }
  @Override XType _type() { return _type; }

  // new Runnable() { void run() { ....; } }.run()
  @Override void jpre( SB sb ) {
    sb.p("new XExpr() { public ");
    _type.p(sb).p(" get_");
    _type.p(sb).p("() {").ii().nl();
  }
  @Override void jmid( SB sb, int i ) { sb.p(";").nl(); }
  @Override void jpost( SB sb ) {
    sb.di().ip("}}.get_");
    _type.p(sb).p("()");
  }
}
