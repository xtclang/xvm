package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.BinaryAST.NodeType;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;

public abstract class AST {
  final AST[] _kids;
  final int _nlocals;           // Count of locals before parsing nested kids
  AST _par;

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
  // Simple all-fields constructor
  AST( AST[] kids, int nlocals ) {
    _kids = kids;
    _nlocals = nlocals;
  }

  @Override public String toString() { return jcode(new SB() ).toString(); }

  // Use the _par parent to find nearest enclosing Block for tmps
  BlockAST enclosing() {
    AST ast = this;
    while( !(ast instanceof BlockAST blk) ) ast = ast._par;
    return blk;
  }
  
  // String java Type of this expression
  String type() { throw XEC.TODO(); }
  
  // Walk, and allow AST to rewrite themselves in-place.
  // Set the _par parent.
  final AST do_rewrite() {
    if( _kids!=null )
      for( int i=0; i<_kids.length; i++ ) {
        AST kid = _kids[i], old;
        do {          
          kid._par = this;
          kid = (old=kid).rewrite();
        } while( old!=kid );
        _kids[i] = kid;
        _kids[i].do_rewrite();
      }
    return this;
  }
  
  // Rewrite some AST bits before Java
  AST rewrite() { return this; }
  
  /**
   * Dump indented pretty java code from AST.
   *
   * @param sb - dump into this SB
   */
  SB jcode( SB sb ) {
    if( sb.was_nl() ) sb.i();
    jpre(sb);
    if( _kids!=null )
      for( int i=0; i<_kids.length; i++ ) {
        if( _kids[i]==null ) continue;
        _kids[i].jcode(sb );
        jmid(sb, i);
      }
    jpost(sb);
    return sb;
  }
  
  // Pretty-print in Java overrides.  Returns a flag to do a visit over all
  // children (with jmid in between kids), or just allow jpre to do the entire
  // print.
  void jpre ( SB sb ) {}
  void jmid ( SB sb, int i ) {}
  void jpost( SB sb ) {}
  
  public static AST parse( XClzBuilder X ) { return ast(X); }

  static AST ast_term( XClzBuilder X ) {
    int iop = (int)X.pack64();
    if( iop >= 32 ) return new RegAST(X,iop-32);  // Local variable register
    if( iop == NodeType.Escape.ordinal() ) return ast(X);
    if( iop >= 0 ) return _ast(X,iop);
    if( iop > XClzBuilder.CONSTANT_OFFSET ) return new RegAST(X,iop); // "special" negative register
    // Constants from the limited method constant pool
    return new ConAST(X, X.methcon(iop) );
  }
  
  static AST ast( XClzBuilder X ) { return _ast(X,X.u8()); }
  
  private static AST _ast( XClzBuilder X, int iop ) {
    NodeType op = NodeType.valueOf(iop);
    return switch( op ) {
    case AnnoNamedRegAlloc -> new DefRegAST(X,true ,true );
    case AnnoRegAlloc -> new   DefRegAST(X,false,true );
    case Assign       -> new   AssignAST(X,true);
    case CallExpr     -> new     CallAST(X, X.consts());
    case CondOpExpr   -> new    BinOpAST(X,false);
    case ForRangeStmt -> new ForRangeAST(X);
    case IfElseStmt   -> new       IfAST(X,3);
    case IfThenStmt   -> new       IfAST(X,2);
    case InvokeExpr   -> new   InvokeAST(X, X.consts());
    case MultiExpr    -> new    MultiAST(X);
    case NamedRegAlloc-> new   DefRegAST(X,true ,false);
    case NewExpr      -> new      NewAST(X, X.methcon_ast(), X.methcon_ast());
    case NotImplYet   -> new     TODOAST(X);
    case RegAlloc     -> new   DefRegAST(X,true ,true );
    case RelOpExpr    -> new    BinOpAST(X,true );
    case Return0Stmt  -> new   ReturnAST(X,0);
    case Return1Stmt  -> new   ReturnAST(X,1);
    case StmtBlock    -> new    BlockAST(X);
    case SwitchExpr   -> new   SwitchAST(X, ast_term(X), X.pack64(), X.consts());
    case TemplateExpr -> new TemplateAST(X);
    case TernaryExpr  -> new  TernaryAST(X);
    
    case MapExpr      -> new     MapAST(X, X.methcon_ast());
    case StmtExpr     -> new    ExprAST(X);
    
    default -> throw XEC.TODO();
    };
  }
}
