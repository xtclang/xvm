package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.XType;
import org.xvm.xtc.ClzBuilder;
import org.xvm.xtc.cons.Const.NodeType;
import org.xvm.util.SB;

public abstract class AST {
  public final AST[] _kids;     // Kids
  AST _par;                     // Parent
  XType _type;                  // Null is unset, never valid. "void" or "int" or "Long" or "AryI64"
  boolean _cond;                // True if passing the hidden condition flag
  
  // Simple all-fields constructor
  AST( AST[] kids ) { _kids = kids; }

  @Override public String toString() { return jcode(new SB() ).toString(); }

  // Use the _par parent to find nearest enclosing Block for tmps
  BlockAST enclosing_block() {
    AST ast = this;
    while( !(ast instanceof BlockAST blk) )
      ast = ast._par;
    return blk;
  }

  // Use the _par parent to find the Nth enclosing switch or loop.
  AST enclosing_loop(int i) {
    AST ast = this;
    while( !ast.is_loopswitch() )
      ast = ast._par;
    return i==0 ? ast : enclosing_loop(i-1);
  }
  boolean is_loopswitch() { return false; }

  // Name, if it makes sense
  String name() { throw XEC.TODO(); }

  // Recursively walk the AST, setting the _type field.
  public final void type( ) {
    if( _kids != null )
      for( AST kid : _kids )
        if( kid!=null )
          kid.type();
    do_type();
  }
  // Subclasses must override
  abstract XType _type();
  boolean _cond() { return false; }

  // Called after a rewrite to set the type again
  AST do_type() {
    XType type = _type();
    assert _type==null || _type==type;
    _type = type;
    _cond = _cond();
    return this;
  }

  // Walk, and allow AST to rewrite themselves in-place.
  // Set the _par parent.
  public final void do_rewrite() {
    if( _kids==null ) return;
    for( int i=0; i<_kids.length; i++ ) {
      AST kid = _kids[i];
      if( kid == null ) continue;
      while( true ) {
        kid._par = this;
        AST rwk = kid.rewrite();
        if( kid==rwk )  break; // No change
        assert rwk._type!=null;
        kid = rwk;
      }
      _kids[i] = kid;
      _kids[i].do_rewrite();
    }
  }
  
  // Rewrite some AST bits before Java
  AST rewrite() { return this; }
  
  /**
   * Dump indented pretty java code from AST.
   *
   * @param sb - dump into this SB
   */
  public SB jcode( SB sb ) {
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
  
  public static AST parse( ClzBuilder X ) { return ast(X); }

  // Magic constant for indexing into the constant pool.
  static final int CONSTANT_OFFSET = -16;
  public static AST ast_term( ClzBuilder X ) {
    int iop = (int)X.pack64();
    if( iop >= 32 )
      return new RegAST(iop-32,X);  // Local variable register
    if( iop == NodeType.Escape.ordinal() ) return ast(X);
    if( iop >= 0 ) return _ast(X,iop);
    if( iop > CONSTANT_OFFSET ) return new RegAST(iop,X); // "special" negative register
    // Constants from the limited method constant pool
    return new ConAST( X, X.con(CONSTANT_OFFSET-iop) );
  }
  
  static AST ast( ClzBuilder X ) { return _ast(X,X.u8()); }
  
  private static AST _ast( ClzBuilder X, int iop ) {
    NodeType op = NodeType.NODES[iop];
    return switch( op ) {
    case AnnoNamedRegAlloc -> DefRegAST.make(X,true ,true );
    case AnnoRegAlloc ->   DefRegAST.make(X,false,true );
    case ArrayAccessExpr -> BinOpAST.make(X,".at(",")");
    case AssertStmt   ->   AssertAST.make(X);
    case Assign       ->   AssignAST.make(X,true);
    case BinOpAssign  ->   AssignAST.make(X,false);
    case BindFunctionExpr -> BindFuncAST.make(X);
    case BindMethodExpr->BindMethAST.make(X);
    case BitNotExpr   ->    UniOpAST.make(X,"~",null);
    case BreakStmt    ->    BreakAST.make(X);
    case CallExpr     ->     CallAST.make(X);
    case CmpChainExpr -> CmpChainAST.make(X);
    case CondOpExpr   ->    BinOpAST.make(X,false);
    case ContinueStmt -> ContinueAST.make(X);
    case ConvertExpr  ->     ConvAST.make(X);
    case DivRemExpr   ->   DivRemAST.make(X);
    case ForListStmt  -> ForRangeAST.make(X);
    case ForRangeStmt -> ForRangeAST.make(X);
    case ForStmt      ->  ForStmtAST.make(X);
    case Greater      ->    OrderAST.make(X,">");
    case IfElseStmt   ->       IfAST.make(X,3);
    case IfThenStmt   ->       IfAST.make(X,2);
    case InvokeExpr   ->   InvokeAST.make(X);
    case Less         ->    OrderAST.make(X,"<");
    case MultiExpr    ->    MultiAST.make(X,true);
    case MultiStmt    ->    MultiAST.make(X,false);
    case NamedRegAlloc->   DefRegAST.make(X,true ,false);
    case NarrowedExpr ->   NarrowAST.make(X);
    case NegExpr      ->    UniOpAST.make(X,"-",null);
    case NewExpr      ->      NewAST.make(X);
    case NotExpr      ->    UniOpAST.make(X,"!",null);
    case NotNullExpr  ->    UniOpAST.make(X,"ELVIS",null);
    case OuterExpr    ->    OuterAST.make(X);
    case PostDecExpr  ->    UniOpAST.make(X,null,"--");
    case PostIncExpr  ->    UniOpAST.make(X,null,"++");
    case PreDecExpr   ->    UniOpAST.make(X,"--",null);
    case PreIncExpr   ->    UniOpAST.make(X,"++",null);
    case PropertyExpr -> PropertyAST.make(X);
    case RegAlloc     ->   DefRegAST.make(X,true ,true );
    case RelOpExpr    ->    BinOpAST.make(X,true );
    case Return0Stmt  ->   ReturnAST.make(X,0);
    case Return1Stmt  ->   ReturnAST.make(X,1);
    case StmtBlock    ->    BlockAST.make(X);
    case StmtExpr     ->     ExprAST.make(X);
    case SwitchExpr   ->   SwitchAST.make(X,true);
    case SwitchStmt   ->   SwitchAST.make(X,false);
    case TemplateExpr -> TemplateAST.make(X);
    case TernaryExpr  ->  TernaryAST.make(X);
    case ThrowExpr    ->    ThrowAST.make(X);
    case TryCatchStmt -> TryCatchAST.make(X);
    case TupleExpr    ->     ListAST.make(X,true);
    case UnaryOpExpr  ->    UniOpAST.make(X);
    case WhileDoStmt  ->    WhileAST.make(X);
    
    default -> throw XEC.TODO();
    };
  }
}
