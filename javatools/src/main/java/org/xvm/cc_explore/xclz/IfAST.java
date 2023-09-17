package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;

class IfAST extends AST {
  IfAST( XClzBuilder X, int n ) {
    super(X, n, false);
    _kids[0] = ast_term(X);
    _kids[1] = ast(X);
    if( n==3 )
      _kids[2] = ast(X);
  }
  @Override void jpre ( SB sb ) { sb.p("if( "); }
  @Override void jmid( SB sb, int i ) {
    if( i==0 ) {
      sb.p(" ) ");
      //   if( pred )
      //     S1; // Split line down
      // VS
      //   if( pred ) S1;
      // If a Block, no need:
      //   if( pred ) { // Block will split line
      //     S1;
      //   }
      if( !(_kids[1] instanceof BlockAST) ) sb.ii().nl();
    } else if( i==1 ) {
      // If not a block, split again 
      if( !(_kids[1] instanceof BlockAST) ) sb.di();
      if( _kids.length==3 ) {
        sb.ip(" else ");
        if( !(_kids[1] instanceof BlockAST) ) sb.nl();
      }
    }
  }
}
