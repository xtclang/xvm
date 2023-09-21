package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.BinaryAST.NodeType;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;

public abstract class AST {
  final AST[] _kids;
  AST _par;
  
  // Simple all-fields constructor
  AST( AST[] kids ) { _kids = kids; }

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
        if( kid != null ) {
          do {          
            kid._par = this;
            kid = (old=kid).rewrite();
          } while( old!=kid );
          _kids[i] = kid;
          _kids[i].do_rewrite();
        }
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

  // Magic constant for indexing into the constant pool.
  static final int CONSTANT_OFFSET = -16;
  static AST ast_term( XClzBuilder X ) {
    int iop = (int)X.pack64();
    if( iop >= 32 ) return new RegAST(iop-32,X);  // Local variable register
    if( iop == NodeType.Escape.ordinal() ) return ast(X);
    if( iop >= 0 ) return _ast(X,iop);
    if( iop > CONSTANT_OFFSET ) return new RegAST(iop); // "special" negative register
    // Constants from the limited method constant pool
    return new ConAST( X.con(CONSTANT_OFFSET-iop) );
  }
  
  static AST ast( XClzBuilder X ) { return _ast(X,X.u8()); }
  
  private static AST _ast( XClzBuilder X, int iop ) {
    NodeType op = NodeType.valueOf(iop);
    return switch( op ) {
    case ArrayAccessExpr -> BinOpAST.make(X,"[","]");
    case AnnoNamedRegAlloc -> DefRegAST.make(X,true ,true );
    case AnnoRegAlloc ->   DefRegAST.make(X,false,true );
    case Assign       ->   AssignAST.make(X,true);
    case BindFunctionExpr -> BindFuncAST.make(X);
    case CallExpr     ->     CallAST.make(X);
    case CondOpExpr   ->    BinOpAST.make(X,false);
    case ForListStmt  -> ForRangeAST.make(X);
    case ForRangeStmt -> ForRangeAST.make(X);
    case IfElseStmt   ->       IfAST.make(X,3);
    case IfThenStmt   ->       IfAST.make(X,2);
    case InvokeExpr   ->   InvokeAST.make(X);
    case MultiExpr    ->    MultiAST.make(X);
    case NamedRegAlloc->   DefRegAST.make(X,true ,false);
    case NewExpr      ->      NewAST.make(X);
    case NotImplYet   ->     TODOAST.make(X);
    case RegAlloc     ->   DefRegAST.make(X,true ,true );
    case RelOpExpr    ->    BinOpAST.make(X,true );
    case Return0Stmt  ->   ReturnAST.make(X,0);
    case Return1Stmt  ->   ReturnAST.make(X,1);
    case StmtBlock    ->    BlockAST.make(X);
    case SwitchExpr   ->   SwitchAST.make(X);
    case TemplateExpr -> TemplateAST.make(X);
    case TernaryExpr  ->  TernaryAST.make(X);
    
    //case MapExpr      -> new     MapAST(X, X.methcon_ast());
    //case StmtExpr     -> new    ExprAST(X);
    
    default -> throw XEC.TODO();
    };
  }
}
