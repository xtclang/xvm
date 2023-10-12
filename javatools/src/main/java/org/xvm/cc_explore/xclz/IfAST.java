package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;

class IfAST extends AST {
  static IfAST make( XClzBuilder X, int n ) {
    AST[] kids = new AST[n];
    kids[0] = ast_term(X);
    kids[1] = ast(X);
    if( n==3 )
      kids[2] = ast(X);
    return new IfAST(kids);
  }  
  private IfAST( AST[] kids ) { super(kids); }

  BlockAST true_blk() {
    BlockAST blk;
    if( _kids[1] instanceof BlockAST blk0 ) {
      AST[] kids = new AST[blk0._kids.length+1];
      System.arraycopy(blk0._kids,0,kids,1,blk0._kids.length);
      blk = new BlockAST(kids);
    } else {
      blk = new BlockAST(null,_kids[1]);
    }
    return (BlockAST)(_kids[1] = blk);
  }


  @Override SB jcode( SB sb ) {
    _kids[0].jcode(sb.ip("if( ")).p(") ");
    if( _kids[1] instanceof BlockAST ) {
      // if( pred ) {
      //   BLOCK;
      // }<<<---- Cursor here
      _kids[1].jcode(sb);
      if( _kids.length==2 ) return sb;
      // if( pred ) {
      //   BLOCK;
      // } <<<---- Cursor here
      sb.p(" ");
    } else {
      // if( pred )
      //   STMT<<<---- Cursor here
      sb.ii().nl();
      _kids[1].jcode(sb);
      sb.di();
      if( _kids.length==2 ) return sb;
      // if( pred )
      //   STMT;
      // <<<---- Cursor here
      sb.p(";").nl().i();
    }
    
    sb.p("else ");
    
    if( _kids[2] instanceof BlockAST ) {
      _kids[2].jcode(sb);
    } else {
      sb.ii().nl();
      _kids[2].jcode(sb);
      sb.di();
    }
    return sb;
  }
}
