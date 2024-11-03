package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.util.SB;
import org.xvm.xtc.cons.Const;

public class ExprAST extends AST {
  static ExprAST make( ClzBuilder X ) {

    // Kids are nested in an Expr "method" and not the outer method
    assert X._expr==null;            // No double nesting
    ExprAST expr = X._expr = new ExprAST();
    // Exactly 1 MultiAST kid
    expr._kids[0] = ast(X);
    assert expr._kids[0] instanceof MultiAST;
    // Done with the nested expr "method"
    X._expr = null;
    // Types?  Ignore the BAST types, and take 1st child type
    X.consts();
    return expr;
  }

  private ExprAST( ) { super(new AST[1]); }
  @Override XType _type() {
    // Expression type is the last Multi entry
    AST multi = _kids[0];
    return multi._kids[multi._kids.length-1]._type;
  }

  void insertDef( DefRegAST def ) {
    BlockAST blk = enclosing_block();
    blk.add_tmp(def._type, def._name);
  }

  @Override void jpre( SB sb ) {
    sb.p("new XExpr() { public ");
    _type.clz(sb).p(" get_");
    _type.clz(sb).p("() {").ii().nl();
  }
  @Override void jmid( SB sb, int i ) { }
  @Override void jpost( SB sb ) {
    sb.di().ip("}}.get_");
    _type.clz(sb).p("()");
  }
}
