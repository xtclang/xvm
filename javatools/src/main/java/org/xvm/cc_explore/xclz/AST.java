package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.BinaryAST.NodeType;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;

public abstract class AST {
  final AST[] _kids;
  final int _nlocals;
  AST( XClzBuilder X, int n ) {
    _nlocals = X==null ? 0 : X._nlocals;
    if( n == 0 ) { _kids=null; return; }
    _kids = new AST[n];
    for( int i=0; i<n; i++ )
      _kids[i] = ast(X);
    while( _nlocals < X._nlocals )
      X._locals.remove(--X._nlocals);
  }
  final void jcode(SB sb) {
    jpre(sb);
    if( _kids!=null )
      for( AST ast : _kids ) {
        ast.jcode(sb);
        jmid(sb);
      }
    jpost(sb);
  }
  abstract void jpre ( SB sb );
  void jpost( SB sb ) {}
  void jmid ( SB sb ) {}

  // Parse the AST, skipping it all if we cannot parse
  private static final RuntimeException EXCEPT = new RuntimeException();
  public static AST parse( XClzBuilder X ) { return ast(X); }

  private static AST ast( XClzBuilder X ) {
    int iop = X.ast_op();
    // Constants get a tighter AST encoding
    if( iop <= 0 )
      return iop <= XClzBuilder.CONSTANT_OFFSET ? new ConAST(X, X.methcon(iop) ) : new SpecialAST(iop);
    
    NodeType op = NodeType.valueOf(iop);
    return switch( op ) {
    case AnnoNamedRegAlloc -> new RegAST(X,true ,true );
    case     NamedRegAlloc -> new RegAST(X,true ,false);
    case          RegAlloc -> new RegAST(X,false,false);
    case Return0Stmt   -> new ReturnAST(X,0);
    case StmtBlock     -> new  BlockAST(X);
    case NotImplYet    -> new   TODOAST(X);
    case InvokeExpr    -> new InvokeAST(X, X.consts());

    default -> throw XEC.TODO();
    };
  }
}
