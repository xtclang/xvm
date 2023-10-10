package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.BiExprAST.Operator;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;
import java.util.HashMap;

class BinOpAST extends AST {
  static final Operator[] OPS = Operator.values();
  final String _op0, _op1;
  final String _type;

  static BinOpAST make( XClzBuilder X, boolean has_type ) {
    AST[] kids = new AST[2];
    kids[0] = ast_term(X);
    Operator op = OPS[X.u31()];
    kids[1] = ast_term(X);
    String type = has_type
      ? XClzBuilder.jtype(X.con(),false) // Type from the AST file
      : (op==Operator.CompOrd ? "Ordered" : "boolean" ); // Must be one of the ordering operators
    return new BinOpAST(kids,op.text,"",type);
  }
  
  static BinOpAST make( XClzBuilder X, String op0, String op1 ) {
    AST[] kids = new AST[2];
    kids[0] = ast_term(X);
    kids[1] = ast_term(X);
    return new BinOpAST(kids,op0,op1,null);
  }
  
  private BinOpAST( AST[] kids, String op0, String op1, String type ) {
    super(kids);
    _op0 = op0;
    _op1 = op1;
    _type = type;
  }
  @Override String type() { return _type; }

  @Override AST rewrite() {
    // Range is not a valid Java operator, so need to change everything here
    if( _op0.equals(".." ) ) return new NewAST(_kids,_type+"II",null);
    if( _op0.equals("..<") ) return new NewAST(_kids,_type+"IE",null);
    // Generally Java needs to use equals for refs and == is only for prims
    if( _op0.equals("==") ) {
      if( needs_equals(_kids[0]) || needs_equals(_kids[1]) )
        return new InvokeAST("equals",_kids[0],_kids[1]);
    }
    if( _op0.equals("<=>") )
      return new InvokeAST("spaceship",new ConAST("XClz"),_kids[0],_kids[1]);

    // This is a ternary null-check or elvis operator.  _kids[0] has, deep
    // within it, the matching predicate test.  I need to restructure this tree:
    //   (:  (invokes...(?(pred), args) ) alt)
    // into this tree:
    //   (?: (tmp=pred) (invokes...(tmp, args)) alt)
    if( _op0.equals(":") ) {
      AST elvis = _kids[0], pred=null;
      while( elvis instanceof InvokeAST && !is_elvis(pred=elvis._kids[0]) )
        elvis = pred;
      if( !(elvis instanceof InvokeAST) )
        // Elvis use not recognized
        throw XEC.TODO();       // TODO: Elvis for loads & stores      
      String type = pred._kids[0].type();
      String tmp = enclosing_block().add_tmp(type);
      elvis._kids[0] = new RegAST(-1,tmp,type); // Read the non-null temp instead of pred
      // Assign the tmp to predicate
      AST asgn = new AssignAST(new RegAST(-1,tmp,type),pred._kids[0]);
      // Zero/null if primitive (if boxing changes type)
      AST zero = new ConAST(XClzBuilder.box(type).equals(type) ? "null" : "0");
      // Null check it
      AST nchk = new BinOpAST(new AST[]{asgn, zero}, "!=","","boolean");
      String x = nchk.toString();
      // ((tmp=pred)!=null) ? alt : (invokes...(tmp,args))
      return new TernaryAST( nchk, _kids[0], _kids[1]);
    }
    return this;
  }
  private static boolean is_elvis(AST tern) { return tern instanceof UniOpAST btern && btern._pre.equals("ELVIS"); }
  
  // Boxed types (Long vs long) needs equals.
  private static boolean needs_equals(AST k) {
    String t = k.type();        // Constants dont give a proper type
    return !(k instanceof ConAST) && XClzBuilder.box(t).equals(t);
  }
  
  @Override SB jcode( SB sb ) {
    expr(sb,_kids[0]).p(_op0);
    expr(sb,_kids[1]).p(_op1);
    return sb;
  }

  // Print 1+(2+3) as "1+2+3"
  // Print 1*(2+3) as "1*(2+3)"
  SB expr( SB sb, AST ast ) {
    boolean wrap = ast instanceof BinOpAST bin &&
            !    _op1.equals(")") &&
            !bin._op1.equals(")") &&
            prec( _op0, bin._op0 );
    if( ast instanceof AssignAST ) wrap=true;
    if( "String".equals(_type) && _kids[1]==ast &&
        (ast instanceof BinOpAST || ast instanceof TernaryAST || (ast instanceof SwitchAST sast && sast._kids[0] instanceof MultiAST ) ) ) {
      sb.p(" "); wrap=true;
    }
    if( wrap ) sb.p("(");
    ast.jcode(sb);
    if( wrap ) sb.p(")");
    return sb;
  }

  // Java precedence table
  private static final HashMap<String,Integer> PRECS = new HashMap<>(){{
      put("[" , 2);
      
      put("*" , 3);
      put("/" , 3);
      put("%" , 3);
      
      put("+" , 4);
      put("-" , 4);

      put("<<", 5);
      put(">>", 5);
      put(">>>", 5);

      put("<" , 6);
      put(">" , 6);
      put("<=", 6);
      put(">=", 6);
      
      put("==", 7);
      put("!=", 7);
      
      put("&" , 8);
      put("^" , 9);
      put("|" ,10);

      put("ELSE",11);
    }};
  private boolean prec(String op, String ex) {
    Integer ii0 = PRECS.get(op);
    Integer ii1 = PRECS.get(ex);
    if( ii0==null ) System.err.println("Missing \""+op+"\" from BinOpAST");
    if( ii1==null ) System.err.println("Missing \""+ex+"\" from BinOpAST");
    return ii0 < ii1;
  }
}
