package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.Const.NodeType;
import org.xvm.util.SB;
import org.xvm.util.S;

public abstract class AST {
  public static int _uid;       // Private counter for temp names

  public AST[] _kids;           // Kids
  AST _par;                     // Parent
  // Computed type.  This varies during the rewrites as I attempt to unbox
  // things, or resolve to a sharper type.  Occasionally a COND producer wants
  // the COND as the primary answer.
  XType _type;                  // Null is unset, never valid. "void" or "int" or "Long" or "AryI64"
  // The XRuntime.COND global is used to return a 2nd argument from many calls
  // (often flagged as "conditional").  This 2nd argument can be used in nearly
  // any boolean context including If's, stacked And expressions "a && b && c",
  // as part of conditional assignments "if( Int a = condInt() ) { ...  a...}",
  // asserts at least.  Since its a singleton global, its crushed by the very
  // next COND-writer, and has to be consumed quickly.
  boolean _cond;                // True if also passing the hidden condition flag

  // Simple all-fields constructor
  AST( AST[] kids ) { _kids = kids; }

  @Override public String toString() { return jcode(new SB() ).toString(); }

  public XType type() { return _type; }

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
    return i==0 ? ast : ast._par.enclosing_loop(i-1);
  }
  boolean is_loopswitch() { return false; }
  // Adding a continue/break label, if it makes sense
  void add_label() { throw XEC.TODO(); }
  String label() { throw XEC.TODO(); }

  // Name, if it makes sense
  String name() { throw XEC.TODO(); }

  // Cast nth child from a long to an int
  void castInt(int n) {
    AST kid = _kids[n];
    // Update a long constant to an int in-place
    if( kid instanceof ConAST con ) {
      if( con._type == XCons.INT ) return; // Already an int
      assert con._type == XCons.LONG;
      con._con = con._con.substring(0,con._con.length()-1);
      con._type = XCons.INT;
      return;
    }
    throw XEC.TODO();
  }
  // Replace a constant "false" with a conditional false return
  boolean condFalse( int idx, XType zret ) {
    if( _kids[idx]._cond ) return false;
    assert _kids[idx] instanceof ConAST con && con._con.equals("false");
    _kids[idx] = new ConAST("XRuntime.False("+zret.ztype()+")");
    _kids[idx]._cond = true;
    return true;
  }


  // Set parent and first cut types in the AST
  public void doType(AST par) {
    _par = par;
    if( _kids != null )
      for( AST kid : _kids )
        if( kid != null )
          kid.doType( this );
    doType();                   // Non-recursive
  }
  AST doType() {
    XType type = _type();              // Local type computation
    assert _type==null || _type==type; // Allow type to be early set
    _type = type;
    _cond = _cond();            // Local cond value
    return this;
  }
  // Subclasses must override
  abstract XType _type();
  boolean _cond() { return false; }

  // Generic visitor pattern, allowing updates
  public  AST visit(java.util.function.UnaryOperator<AST> F, AST par) { return visit(F,par,true,0); }
  private AST visit(java.util.function.UnaryOperator<AST> F, AST par, boolean first, int cnt) {
    assert cnt<100;
    if( _par==null || _par._par==par ) _par = par;
    else assert _par==par;
    AST[] kids = _kids;
    if( kids != null )
      for( int i=0; i<kids.length; i++ ) {
        AST kid = kids[i];
        if( kid != null && (first || kid._par!=this) ) {
          kid._par = this;
          kids[i] = kid.visit(F,this,first,cnt+1);
          assert kids[i]._par==this; // Kid updates always leave parent correct
        }
      }
    assert kids==_kids;         // Children cannot replace parents base kids array
    // Apply op to AST.  Correct _par field amongst self and kids.
    // Repeat until stable.
    AST x = F.apply(this);
    return x==null ? check_par(par) : x.visit(F,par,false,cnt+1);
  }

  private AST check_par(AST par) {
    assert _par==par;
    if( _kids!=null )
      for( AST kid : _kids )
        assert kid==null || kid._par==this;
    return this;
  }
  // Unboxed name of a boxed variable
  public static String unbox_name(String box) { return ("$"+box+"_i").intern(); }

  // Rewrite some AST bits before Java
  public AST rewrite() { return null; }

  // Auto-box e.g. long to Int64 or j.l.String to xtc.String
  public AST reBox() { return null; }

  AST unBoxThis() { return new UniOpAST(new AST[]{this},null,"._i",_type.unbox());  }
  AST reBoxThis() {
    assert _type instanceof XBase;
    return new NewAST(new AST[]{this},_type.box());
  }

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
        _kids[i].jcode(sb);
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
    case DoWhileStmt  ->  DoWhileAST.make(X);
    case ForListStmt  -> ForRangeAST.make(X);
    case ForMapStmt   -> ForRangeAST.make(X);
    case ForRangeStmt -> ForRangeAST.make(X);
    case ForIterableStmt -> ForIterStmtAST.make(X);
    case ForStmt      ->  ForStmtAST.make(X);
    case Greater      ->    OrderAST.make(X,">");
    case IfElseStmt   ->       IfAST.make(X,3);
    case IfThenStmt   ->       IfAST.make(X,2);
    case InitAst      ->     InitAST.make(X);
    case InvokeExpr   ->   InvokeAST.make(X,false);
    case InvokeAsyncExpr-> InvokeAST.make(X,true );
    case Less         ->    OrderAST.make(X,"<");
    case LoopStmt     ->    WhileAST.make(X,true);
    case MapExpr      ->      MapAST.make(X);
    case MultiExpr    ->    MultiAST.make(X,true);
    case MultiStmt    ->    MultiAST.make(X,false);
    case NamedRegAlloc->   DefRegAST.make(X,true ,false);
    case NarrowedExpr ->   NarrowAST.make(X);
    case NegExpr      ->    UniOpAST.make(X,"-",null);
    case NewExpr      ->      NewAST.make(X,false);
    case NewChildExpr ->      NewAST.make(X,true );
    case NewVirtualExpr ->NewVirtAST.make(X);
    case NotExpr      ->    UniOpAST.make(X,"!",null);
    case None         ->     NoneAST.make(X);
    case NotNullExpr  ->    UniOpAST.make(X,"ELVIS",null);
    case OuterExpr    ->    OuterAST.make(X);
    case PostDecExpr  ->    UniOpAST.make(X,null,"--");
    case PostIncExpr  ->    UniOpAST.make(X,null,"++");
    case PreDecExpr   ->    UniOpAST.make(X,"--",null);
    case PreIncExpr   ->    UniOpAST.make(X,"++",null);
    case PropertyExpr -> PropertyAST.make(X);
    case RefOfExpr    ->    UniOpAST.make(X,"&",null);
    case RegAlloc     ->   DefRegAST.make(X,true ,true );
    case RelOpExpr    ->    BinOpAST.make(X,true );
    case Return0Stmt  ->   ReturnAST.make(X,0);
    case Return1Stmt  ->   ReturnAST.make(X,1);
    case ReturnNStmt  ->   ReturnAST.make(X,X.u31());
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
    case VarOfExpr    ->    UniOpAST.make(X,"&","");
    case WhileDoStmt  ->    WhileAST.make(X,false);
    case UnpackExpr   ->   UnpackAST.make(X);

    default -> throw XEC.TODO();
    };
  }
}
