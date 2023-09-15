package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.BinaryAST.NodeType;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;

public abstract class AST {
  final AST[] _kids;
  final int _nlocals;           // Count of locals before parsing nested kids

  // Parse kids in order, save/restore locals around kids
  AST( XClzBuilder X, int n ) { this(X,n,true); }
  AST( XClzBuilder X, int n, boolean kids ) {
    _nlocals = X==null ? 0 : X._nlocals;
    if( n == 0 ) { _kids=null; return; }    
    _kids = new AST[n];
    if( !kids ) return;
    // Parse kids in order
    for( int i=0; i<n; i++ )
      _kids[i] = ast(X);
    
    // Pop locals at end of scope
    X.pop_locals(_nlocals);
  }

  @Override public String toString() { return jcode(new SB()).toString(); }

  
  final SB jcode(SB sb) {
    jpre(sb);
    if( _kids!=null )
      for( int i=0; i<_kids.length; i++ ) {
        if( _kids[i]==null ) continue;
        _kids[i].jcode(sb);
        jmid(sb, i);
      }
    jpost(sb);
    return sb;
  }
  abstract void jpre ( SB sb );
  void jpost( SB sb ) {}
  void jmid ( SB sb, int i ) {}

  public static AST parse( XClzBuilder X ) { return ast(X); }

  static AST ast_term( XClzBuilder X ) {
    int iop = (int)X.pack64();
    if( iop >= 32 ) return new RegAST(X,iop-32);  // Local variable register
    if( iop == NodeType.Escape.ordinal() ) return ast(X);
    if( iop == NodeType.NamedRegAlloc.ordinal() ) return new DefRegAST(X,true ,false);
    if( iop == NodeType.     RegAlloc.ordinal() ) return new DefRegAST(X,false,false);
    if( iop > 0 ) throw XEC.TODO();
    if( iop > XClzBuilder.CONSTANT_OFFSET ) return new RegAST(X,iop); // "special" negative register
    // Constants from the limited method constant pool
    return new ConAST(X, X.methcon(iop) );
  }
  
  static AST ast( XClzBuilder X ) {
    NodeType op = NodeType.valueOf(X.u8());
    return switch( op ) {
    case AnnoNamedRegAlloc -> new DefRegAST(X,true ,true );
    case AnnoRegAlloc -> new   DefRegAST(X,false,true );
    case Assign       -> new   AssignAST(X,true);
    case CallExpr     -> new     CallAST(X, X.consts());
    case CondOpExpr   -> new    BinOpAST(X,false);
    case IfElseStmt   -> new       IfAST(X,3);
    case IfThenStmt   -> new       IfAST(X,2);
    case InvokeExpr   -> new   InvokeAST(X, X.consts());
    case NotImplYet   -> new     TODOAST(X);
    case RelOpExpr    -> new    BinOpAST(X,true );
    case Return0Stmt  -> new   ReturnAST(X,0);
    case Return1Stmt  -> new   ReturnAST(X,1);
    case StmtBlock    -> new    BlockAST(X);
    case TernaryExpr  -> new  TernaryAST(X);
    case TemplateExpr -> new TemplateAST(X);
    
    case MapExpr      -> new     MapAST(X, X.methcon_ast());
    case StmtExpr     -> new    ExprAST(X);
    
    default -> throw XEC.TODO();
    };
  }
}
