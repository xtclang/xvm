package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.UnaryOpExprAST.Operator;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.*;

// Rewrites:
// AST: ( - e0 ) -- Java: -e0 // no rewrite
// AST: ( ~ e0 ) -- Java: ~e0 // no rewrite if e0 is some integer
// AST: ( ~ e0 ) -- Java: !e0 // rewritten in make if e0 is boolean
// AST: ( ! e0 ) -- Java: !e0 // e0 NOT CONDITIONAL
// AST: ( ! e0 ) -- Java: !($t(e0) && GET$COND()) // E0 YES CONDITIONAL
// AST: ( .TRACE() e0 ) - Java: XClz.TRACE(e0) // rewrite

class UniOpAST extends AST {
  static final Operator[] OPS = Operator.values();
  final String _pre, _post;

  static UniOpAST make( XClzBuilder X ) {
    AST[] kids = X.kids(1);     // One expr
    Const type = X.con();
    Operator op = OPS[X.u31()]; // Post op by default
    return new UniOpAST(kids,null,op.text,type);
  }
  
  static UniOpAST make( XClzBuilder X, String pre, String post ) {
    AST[] kids = X.kids(1);     // One expr
    Const type = X.con();
    // XTC allows booleans "~" but Java does not.
    if( "~".equals(pre) && "boolean".equals(XClzBuilder.jtype(type,false)) )
      pre = "!";                // Use Java bang instead
    return new UniOpAST(kids,pre,post,type);
  }
  
  private UniOpAST( AST[] kids, String pre, String post, Const type ) {
    super(kids);
    _pre = pre;
    _post = post;
    // TRACE is given a type in BAST, but its not the full type - its "boolean"
    // if the kid is conditional, instead of the kids value type.  TRACE can
    // compute its type from its kid later.
    if( is_trace() ) type=null;
    _type = type==null ? null : XClzBuilder.jtype(type,false);
  }
  boolean is_elvis() { return "ELVIS".equals(_pre); }
  static boolean is_elvis(AST tern) { return tern instanceof UniOpAST btern && btern.is_elvis(); }
  boolean is_trace() { return ".TRACE()".equals(_post); }

  @Override String _type() {
    // "!" is given a type in BAST (always "boolean"), but it uses a condition
    // test if available, or the first part of a Tuple (which must be a
    // boolean).
    if( "!".equals(_pre) )
      return _type;
    // Other operators carry through from the child
    String t = _kids[0]._type;
    if( is_elvis() ) t = XClzBuilder.unbox(t);
    return t;
  }
  @Override boolean _cond() {
    // Pass-through on conditional
    if( is_trace() ) return _kids[0]._cond;
    return false;
  }

  @Override AST rewrite() {
    if( is_trace() )
      return new InvokeAST("TRACE",(String)null,new ConAST("XClz"),_kids[0]).do_type();
    return this;
  }
  
  @Override SB jcode( SB sb ) {
    // Bang "eats" the test part of {test,value} conditionals and drops the
    // value part.
    if( _kids[0]._cond ) {
      assert _type.equals("boolean");
      if( "!".equals(_pre) ) {
        _kids[0].jcode(sb.p("$t("));
        return sb.p(") && !XRuntime.GET$COND()");
      } else 
        throw XEC.TODO();
    }
    
    if( _pre !=null ) sb.p(" ").p(_pre );
    _kids[0].jcode(sb);
    if( _post!=null ) sb.p(_post).p(" ");
    return sb;
  }
}
