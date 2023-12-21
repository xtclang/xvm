package org.xvm.xtc.ast;

import org.xvm.xtc.*;
import org.xvm.util.SB;
import org.xvm.xtc.cons.Const;

public class ExprAST extends AST {
  static ExprAST make( ClzBuilder X ) {

    // Kids are nested in an Expr "method" and not the outer method
    assert X._expr==null;            // No double nesting
    ExprAST expr = X._expr = new ExprAST();
    
    // Parse kids in order as stmts not exprs
    expr._kids[0] = ast(X);
    // Done with the nested expr "method"
    X._expr = null;

    // Types?
    Const[] types = X.consts();
    expr._type = XType.xtype(types[0],false);
    return expr;
  }
  
  private ExprAST( ) { super(new AST[1]); }
  @Override XType _type() { return _type; }

  @Override void jpre( SB sb ) {
    sb.p("new XExpr() { public ");
    _type.clz(sb).p(" get_");
    _type.clz(sb).p("() {").ii().nl();
  }
  @Override void jmid( SB sb, int i ) { sb.p(";").nl(); }
  @Override void jpost( SB sb ) {
    sb.di().ip("}}.get_");
    _type.clz(sb).p("()");
  }
}
