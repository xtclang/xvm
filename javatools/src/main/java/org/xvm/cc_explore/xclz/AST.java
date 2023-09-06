package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.BinaryAST.NodeType;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;

public abstract class AST {
  final AST[] _kids;
  AST( XClzBuilder X, int n ) {
    if( n == 0 ) { _kids=null; return; }
    _kids = new AST[n];
    for( int i=0; i<n; i++ )
      _kids[i] = ast(X);
  }
  void jcode(SB sb) {
    jpre(sb);
    if( _kids!=null )
      for( AST ast : _kids )
        ast.jcode(sb);
    jpost(sb);
  }
  abstract void jpre ( SB sb );
  abstract void jpost( SB sb );

  // Parse the AST, skipping it all if we cannot parse
  private static final RuntimeException EXCEPT = new RuntimeException();
  public static AST parse( XClzBuilder X ) { return ast(X); }

  private static AST ast( XClzBuilder X ) {
    NodeType op = NodeType.valueOf(X.u8());
    return switch( op ) {
    case NamedRegAlloc -> new    RegAST(X,true );
    case      RegAlloc -> new    RegAST(X,false);
    case Return0Stmt   -> new ReturnAST(X,0);
    case StmtBlock     -> new  BlockAST(X);
    case NotImplYet    -> new   TODOAST(X);

    default -> throw XEC.TODO();
    };
  }
}
