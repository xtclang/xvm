package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.LanguageAST.NodeType;
import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;

public abstract class AST {
  final AST[] _kids;
  AST( CPool X, int n ) {
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
  public static AST parse( CPool X ) {
    int ast_len = X.u31();
    int ast_start = X.cur_pos();
    AST ast = null;
    if( ast_len>0 ) {
      try { ast = ast(X); }
      catch( RuntimeException ignore ) { }
    }
    X.set_pos(ast_start + ast_len);
    return ast;
  }

  static AST ast( CPool X ) {
    NodeType op = NodeType.valueOf(X.u8());
    return switch( op ) {
    case NamedRegAlloc  -> new NamedRegAST(X);
    case RETURN_STMT    -> new   ReturnAST(X);
    case STMT_BLOCK     -> new    BlockAST(X);
    case ExprNotImplYet -> new     TODOAST(X);
    case StmtNotImplYet -> new     TODOAST(X);

    case RegAlloc ->throw EXCEPT;           
    case Assign ->throw EXCEPT;             
    case BinOpAssign ->throw EXCEPT;        
    case RegisterExpr ->throw EXCEPT;       
    case InvokeExpr ->throw EXCEPT;         
    case ASSIGN_EXPR ->throw EXCEPT;        
    case LIT_EXPR ->throw EXCEPT;
    case SWITCH_EXPR ->throw EXCEPT;
    case MULTI_COND ->throw EXCEPT;         
    case NOT_COND ->throw EXCEPT;           
    case NOT_NULL_COND ->throw EXCEPT;      
    case NOT_FALSE_COND ->throw EXCEPT;     
    case ArrayAccessExpr ->throw EXCEPT;    
    case MatrixAccessExpr ->throw EXCEPT;   
    case RelOpExpr ->throw EXCEPT;          
    case DivRemExpr ->throw EXCEPT;         
    case IsExpr ->throw EXCEPT;             
    case CondOpExpr ->throw EXCEPT;         
    case CmpChainExpr ->throw EXCEPT;       
    case UnaryOpExpr ->throw EXCEPT;        
    case NotExpr ->throw EXCEPT;            
    case NotNullExpr ->throw EXCEPT;        
    case TernaryExpr ->throw EXCEPT;        
    case TemplateExpr ->throw EXCEPT;
    case ThrowExpr ->throw EXCEPT;
    case AssertStmt ->throw EXCEPT;
    case CONSTANT_EXPR ->throw EXCEPT;
    case LIST_EXPR ->throw EXCEPT;
    case MAP_EXPR ->throw EXCEPT;
    case IF_THEN_STMT ->throw EXCEPT;       
    case IF_ELSE_STMT ->throw EXCEPT;       
    case SWITCH_STMT ->throw EXCEPT;        
    case LOOP_STMT ->throw EXCEPT;          
    case WHILE_DO_STMT ->throw EXCEPT;      
    case DO_WHILE_STMT ->throw EXCEPT;      
    case FOR_STMT ->throw EXCEPT;           
    case FOR_ITERATOR_STMT ->throw EXCEPT;  
    case FOR_RANGE_STMT ->throw EXCEPT;     
    case FOR_LIST_STMT ->throw EXCEPT;      
    case FOR_MAP_STMT ->throw EXCEPT;       
    case FOR_ITERABLE_STMT ->throw EXCEPT;  
    case CONTINUE_STMT ->throw EXCEPT;      
    case BREAK_STMT ->throw EXCEPT;         
    case EXPR_STMT ->throw EXCEPT;          
    case TRY_CATCH_STMT ->throw EXCEPT;     
    case TRY_FINALLY_STMT ->throw EXCEPT;   
    case REG_DECL_STMT ->throw EXCEPT;
    case REG_STORE_STMT ->throw EXCEPT;     
    case ANY_STORE_STMT ->throw EXCEPT;
    case ASSIGN_STMT ->throw EXCEPT;        

    default -> throw XEC.TODO();
    };
  }
}
