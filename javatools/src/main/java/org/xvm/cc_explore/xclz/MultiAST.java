package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;
import java.util.Arrays;

class MultiAST extends AST {
  static MultiAST make(XClzBuilder X, boolean expr) {
    int len = X.u31();
    AST[] kids = new AST[len];
    for( int i=0; i<len; i++ )
      kids[i] = expr ? ast_term(X) : ast(X);
    return new MultiAST(kids);
  }
  MultiAST( AST... kids ) { super(kids); }
  
  @Override String _type() {
    String kid0 = _kids[0]._type;
    if( _kids.length==1 ) return kid0;
    String kid1 = _kids[1]._type;
    if( kid0.equals("Boolean" ) )
      return "?"+kid1;
    return "boolean";
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
      BinOpAST eq0 = new BinOpAST("==","","boolean",new ConAST("null"),vsnull);
      BinOpAST or = new BinOpAST("||","","boolean",eq0,_kids[0]);
      _kids[0] = or;
      // Drop the elvis buried inside the expression
      elvis._kids[0] = vsnull;
    }
    
    return this;
  }
  
  @Override public void jpre(SB sb) {
    if( _kids.length > 1 )
      sb.p("(");
  }
  @Override public void jmid(SB sb, int i) {
    if( _kids.length > 1 )
      sb.p(") && (");
  }
  @Override public void jpost(SB sb) {
    if( _kids.length > 1 )
      sb.unchar(5);
  }
}
