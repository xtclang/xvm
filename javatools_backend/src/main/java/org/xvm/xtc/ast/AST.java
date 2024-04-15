package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.Const.NodeType;
import org.xvm.util.SB;
import org.xvm.util.S;

public abstract class AST {
  public static int _uid;       // Private counter for temp names

  public final AST[] _kids;     // Kids
  AST _par;                     // Parent
  XType _type;                  // Null is unset, never valid. "void" or "int" or "Long" or "AryI64"
  boolean _cond;                // True if passing the hidden condition flag

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
    return i==0 ? ast : enclosing_loop(i-1);
  }
  boolean is_loopswitch() { return false; }


  // Name, if it makes sense
  String name() { throw XEC.TODO(); }

  // Cast nth child from a long to an int
  AST castInt(int n) {
    AST kid = _kids[n];
    // Update a long constant to an int in-place
    if( kid instanceof ConAST con ) {
      if( con._type == XCons.INT ) return this; // Already an int
      assert con._type == XCons.LONG;
      con._con = con._con.substring(0,con._con.length()-1);
      con._type = XCons.INT;
      return this;
    }
    throw XEC.TODO();
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
    XType type = _type();
    assert _type==null || _type==type;
    _type = type;
    _cond = _cond();
    return this;
  }
  // Subclasses must override
  abstract XType _type();
  boolean _cond() { return false; }

  // Generic visitor pattern, allowing updates
  public AST visit(java.util.function.UnaryOperator<AST> F, AST par) {
    assert _par==par;
    if( _kids != null )
      for( int i=0; i<_kids.length; i++ )
        if( _kids[i] != null )
          _kids[i] = _kids[i].visit(F,this);
    // Apply op to AST.  Correct _par field amongst self and kids.
    // Repeat until stable.
    AST old = this, x;
    while( (x=F.apply(old)) != old && x != null ) {
      x._par = par;
      if( x._kids != null )
        for( AST kid : x._kids )
          if( kid != null )
            kid._par = x;
      old = x;
    }
    return x;
  }

  // Auto-unbox e.g. Int64 to a long or xtc.String to j.l.String
  public AST unBox() {
    // Parent is narrowed, removes not-null and allows unboxing
    if( _par instanceof NarrowAST nar && !(nar._par instanceof AssignAST) &&
        nar._type instanceof XBase nbase && _type instanceof XClz ) {
      XClz nbox = nbase.box();
      AST box = this;
      if( nbox != _type ) {
        box = new ConvAST(nbox,this); _par = box;
        nar._kids[0] = box;  box._par = nar;
      }
      return new UniOpAST(new AST[]{box},null,"._i",nbase);
    }

    // Otherwise unbox if type is boxed and not immediately demanded boxed
    XType unbox = _type.unbox();
    if( unbox == _type ||       // Already unboxed
        unbox == XCons.NULL ||  // Unboxes to a "null" (TODO: use constant null)
        !_type._notNull  ||     // Maybe-null
        // LHS of assignment; BAD: "n._i = boxed_thing";
        _par instanceof AssignAST asgn0 && asgn0._kids[0] == this ||
        // Assign with no uses; BAD: "{ ...; (n = boxed)._i; ... }
        this instanceof AssignAST asgn1 && asgn1._par instanceof BlockAST ||
        this instanceof MultiAST
        )
      return this;              // Do not unbox
    // Unbox
    return new UniOpAST(new AST[]{this},null,"._i",unbox);
  }

  // Rewrite some AST bits before Java
  public AST rewrite() { return this; }

  // Auto-box e.g. long to Int64 or j.l.String to xtc.String
  public AST reBox() {
    if( _par == null )              return this; // Must have a parent that cares
    if( _par._type == XCons.VOID )  return this; // No boxing around VOID
    if(      _type == XCons.VOID )  return this;
    if( _par._type instanceof XFun) return this; // No boxing issues around functions
    // Assigns RHS and Returns LHS might need to box
    XType lhs = _par.reBox(this);
    // Desired flavor is no-change or not-boxed or already isa
    if( lhs == null || lhs instanceof XBase || _type.isa(lhs) ) return this;
    XClz rhs = _type.box();
    if( rhs == null || rhs == _type ) return this; // Always going to the box, so this is a noop
    // Never box to an interface, caller had already better be a boxed implementer
    XClz lhsc = (XClz)lhs;
    if( lhsc.iface() ) lhsc = XCons.XXTC;
    assert rhs.isa(lhsc);

    // Box 'em up!
    return (_par._kids[S.find(_par._kids,this)] = new NewAST(new AST[]{this},rhs));
  }

  XType reBox( AST par ) { return null; }

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
    case DoWhileStmt  ->  DoWhileAST.make(X);
    case ForListStmt  -> ForRangeAST.make(X);
    case ForRangeStmt -> ForRangeAST.make(X);
    case ForIterableStmt -> ForStmtAST.make(X);
    case ForStmt      ->  ForStmtAST.make(X);
    case Greater      ->    OrderAST.make(X,">");
    case IfElseStmt   ->       IfAST.make(X,3);
    case IfThenStmt   ->       IfAST.make(X,2);
    case InvokeExpr   ->   InvokeAST.make(X,false);
    case InvokeAsyncExpr-> InvokeAST.make(X,true );
    case Less         ->    OrderAST.make(X,"<");
    case MapExpr      ->      MapAST.make(X);
    case MultiExpr    ->    MultiAST.make(X,true);
    case MultiStmt    ->    MultiAST.make(X,false);
    case NamedRegAlloc->   DefRegAST.make(X,true ,false);
    case NarrowedExpr ->   NarrowAST.make(X);
    case NegExpr      ->    UniOpAST.make(X,"-",null);
    case NewExpr      ->      NewAST.make(X,false);
    case NewChildExpr ->      NewAST.make(X,true );
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
    case WhileDoStmt  ->    WhileAST.make(X);
    case UnpackExpr   ->   UnpackAST.make(X);

    default -> throw XEC.TODO();
    };
  }
}
