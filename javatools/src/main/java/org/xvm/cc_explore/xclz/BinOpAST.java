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
    Const type = has_type ? X.con() : null;
    return new BinOpAST(kids,op.text,"",type);
  }
  
  static BinOpAST make( XClzBuilder X, String op0, String op1 ) {
    AST[] kids = new AST[2];
    kids[0] = ast_term(X);
    kids[1] = ast_term(X);
    String t0 = kids[0].type();
    // Array lookup on a non-array.
    if( op0.equals("[") && t0.charAt(t0.length()-1)!=']' ) {
      // Swap ary[idx] to ary.at(idx);
      op0 = ".at(";
      op1 = ")";
    }
    return new BinOpAST(kids,op0,op1,null);
  }
  
  private BinOpAST( AST[] kids, String op0, String op1, Const type ) {
    super(kids);
    _op0 = op0;
    _op1 = op1;
    _type = type==null ? null : XClzBuilder.jtype(type,false);
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
    return this;
  }

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
    if( "String".equals(_type) ) { sb.p(" "); wrap=true; }
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
    }};
  private boolean prec(String op, String ex) {
    Integer ii0 = PRECS.get(op);
    Integer ii1 = PRECS.get(ex);
    if( ii0==null ) System.err.println("Missing \""+op+"\" from BinOpAST");
    if( ii1==null ) System.err.println("Missing \""+ex+"\" from BinOpAST");
    return ii0 < ii1;
  }
}
