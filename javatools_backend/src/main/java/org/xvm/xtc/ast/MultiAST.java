package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.xtc.*;

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
    return kid0==XType.BOOL ? kid1.box() : XType.BOOL;
  }

  @Override AST rewrite() {
    // When finding a BinOp ":", I search the _kids[0] slot for an elvis.
    //   ( ...e0 ?. e1 ... : alt)  ==>>
    //   (e0==null ? alt : (... e0.e1 ...))
    // When finding a Multi, I search the _kids[0] slot for an elvis
    //   ( ...e0 ?. e1 ... , more)  ==>>
    //   ((e0==null || (...e0.e1...)) && more)
    AST elvis = UniOpAST.find_elvis(this);
    if( elvis != null ) {
      AST par_elvis = elvis._par;
      assert par_elvis._kids[0] == elvis;
      // Make 'e0==null'
      AST vsnull = elvis._kids[0];
      BinOpAST eq0 = new BinOpAST("==","",XType.BOOL,new ConAST("null"),vsnull);
      BinOpAST or  = new BinOpAST("||","",XType.BOOL,eq0,_kids[0]);
      _kids[0] = or;
      // Drop the elvis buried inside the expression
      par_elvis._kids[0] = vsnull;
    }
    
    return this;
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
