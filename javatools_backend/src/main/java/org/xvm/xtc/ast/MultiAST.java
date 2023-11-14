package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.xtc.*;

class MultiAST extends AST {
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
    return kid0==XType.BOOL ? kid1.box() : XType.BOOL;
  }

  @Override AST rewrite() {
    // When finding a BinOp ":", I search the _kids[0] slot for an elvis.
    //   ( ...e0 ?. e1 ... : alt)  ==>>
    //   (e0==null ? alt : (... e0.e1 ...))
    // When finding a Multi, I search the _kids[0] slot for an elvis
    //   ( ...e0 ?. e1 ... , more)  ==>>
    //   ((e0==null || (...e0.e1...)) && more)
    // TODO: search all kids
    AST elvis = _kids[0];
    while( elvis!=null && elvis._kids!=null && !UniOpAST.is_elvis(elvis._kids[0]) )
      elvis = elvis._kids[0];
    if( elvis!=null && elvis._kids!=null ) {
      // Make 'e0==null'
      AST vsnull = elvis._kids[0]._kids[0];
      BinOpAST eq0 = new BinOpAST("==","",XType.BOOL,new ConAST("null"),vsnull);
      BinOpAST or  = new BinOpAST("||","",XType.BOOL,eq0,_kids[0]);
      _kids[0] = or;
      // Drop the elvis buried inside the expression
      elvis._kids[0] = vsnull;
    }
    
    return this;
  }
  
  @Override public void jpre(SB sb) {
    if( _expr ) 
      if( _kids.length > 1 )
        sb.p("(");
  }
  @Override public void jmid(SB sb, int i) {
    if( _kids.length > 1 )
      sb.p(_expr ? ") && (" : "; ");
  }
  @Override public void jpost(SB sb) {
    if( _kids.length > 1 )
      sb.unchar( _expr ? 5 : 2); // Undo ") && (" or "; " from jmid
  }
}
