package org.xvm.xtc.ast;

import org.xvm.util.S;
import org.xvm.util.SB;
import org.xvm.xtc.*;
import org.xvm.XEC;

public class MultiAST extends AST {
  final boolean _expr;
  static MultiAST make( ClzBuilder X, boolean expr) {
    int len = X.u31();
    AST[] kids = new AST[len];
    for( int i=0; i<len; i++ )
      kids[i] = expr ? ast_term(X) : ast(X);
    return new MultiAST(expr,kids);
  }
  MultiAST( boolean expr, AST... kids ) { super(kids); _expr = expr; }

  @Override XType _type() {
    XType kid0 = _kids[0]._type;
    if( _kids.length==1 ) return kid0;
    XType kid1 = _kids[1]._type;
    // Box kid1, so we can null-check it
    if( kid0==XCons.BOOL )
      return S.eq(kid1.ztype(),"0") ? kid1.box() : kid1;
    // Otherwise, we're in a multi-ast situation with lots of AND'd parts
    return XCons.BOOL;
  }

  // THIS:    ( ...e0 ?. e1..., more);
  // MAPS TO: ((e0==null || (...e0.e1...)) && more)
  AST doElvis(AST elvis, int idx) {
    BinOpAST eq0 = new BinOpAST("==","",XCons.BOOL,new ConAST("null"),elvis);
    BinOpAST or  = new BinOpAST("||","",XCons.BOOL,eq0,_kids[0]);
    _kids[idx] = or;
    // Drop the elvis buried inside the expression
    return elvis;
  }

  @Override public void jpre(SB sb) {
    if( _kids.length > 1 )
      if( _expr )
        sb.p("(");
  }
  @Override public void jmid(SB sb, int i) {
    if( _kids.length > 1 )
      if( _expr ) sb.p(") && (");
      else {
        sb.p(";").nl();
        if( i<_kids.length-1 ) sb.i();
      }
  }
  @Override public void jpost(SB sb) {
    if( _kids.length > 1 )
      if( _expr ) sb.unchar(5); // Undo ") && ("
  }
}
