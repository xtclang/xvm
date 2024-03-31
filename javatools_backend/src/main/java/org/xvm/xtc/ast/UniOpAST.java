package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.util.S;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.*;
import org.xvm.xtc.cons.Const.UniOp;

// Rewrites:
// AST: ( - e0 ) -- Java: -e0 // no rewrite
// AST: ( ~ e0 ) -- Java: ~e0 // no rewrite if e0 is some integer
// AST: ( ~ e0 ) -- Java: !e0 // rewritten in make if e0 is boolean
// AST: ( ! e0 ) -- Java: !e0 // e0 NOT CONDITIONAL
// AST: ( ! e0 ) -- Java: !($t(e0) && GET$COND()) // E0 YES CONDITIONAL
// AST: ( .TRACE() e0 ) - Java: XTC.TRACE(e0) // rewrite

class UniOpAST extends AST {
  final String _pre, _post;

  static UniOpAST make( ClzBuilder X ) {
    AST[] kids = X.kids(1);     // One expr
    Const type = X.con();
    UniOp op = UniOp.OPS[X.u31()]; // Post op by default
    return new UniOpAST(kids,null,op.text,type);
  }

  static UniOpAST make( ClzBuilder X, String pre, String post ) {
    AST[] kids = X.kids(1);     // One expr
    Const type = X.con();
    // XTC allows booleans "~" but Java does not.
    if( S.eq("~",pre) && XType.xtype(type,false)==XCons.BOOL )
      pre = "!";                // Use Java bang instead
    return new UniOpAST(kids,pre,post,type);
  }

  UniOpAST( AST[] kids, String pre, String post, Const type ) {
    this(kids,pre,post,
         // TRACE is given a type in BAST, but it's not the full type - its
         // "boolean" if the kid is conditional, instead of the kids value
         // type.  TRACE can compute its type from its kid later.
         (is_trace(post) || type==null) ? null : XType.xtype(type,false));
  }
  UniOpAST( AST[] kids, String pre, String post, XType type ) {
    super(kids);
    _pre = pre;
    _post = post;
    _type = type;
  }

  boolean is_elvis() { return is_elvis(_pre ); }
  boolean is_trace() { return is_trace(_post); }
  static boolean is_elvis(String s) { return S.eq("ELVIS"   ,s); }
  static boolean is_trace(String s) { return S.eq(".TRACE()",s); }

  @Override XType _type() {
    // "!" is given a type in BAST (always "boolean"), but it uses a condition
    // test if available, or the first part of a Tuple (which must be a
    // boolean).
    if( S.eq("!",_pre) )  return _type;
    if( is_elvis() )      return _type;
    if( S.eq("&",_pre) )  return _type;
    // Other operators carry through from the child
    return _kids[0]._type;
  }

  @Override boolean _cond() {
    // Pass-through on conditional
    return is_trace() && _kids[0]._cond;
  }

  @Override public AST rewrite() {
    if( is_trace() )
      return new InvokeAST("TRACE",_type,new ConAST("XTC"),_kids[0]).doType();
    if( is_elvis() ) {
      // Find the "elvis top" - the point where we make the subexpression
      // conditional on the "elvis" existing.
      for( AST par = _par, old = this; true; old = par, par = par._par )
        switch( par ) {
        case TernaryAST ttop: return ttop.doElvis(_kids[0]);
        case MultiAST   mtop: return mtop.doElvis(_kids[0],S.find(mtop._kids,old));
        case AssertAST  asrt: return asrt.doElvis(_kids[0]);
        default: break;
        }
    } // End of Elvis


    // Invert the bang directly and remove from AST
    if( S.eq("!",_pre) && _kids[0] instanceof OrderAST ord ) {
      ord._op = switch( ord._op ) {
      case ">" -> "<=";
      case "<" -> ">=";
      default -> throw XEC.TODO();
      };
      return ord;
    }
    if( S.eq("!",_pre) && _kids[0] instanceof BinOpAST bin ) {
      if( S.eq("!=",bin._op0) ) { bin._op0="=="; return bin; }
      if( S.eq("==",bin._op0) ) { bin._op0="!="; return bin; }
    }

    return this;
  }

  @Override public SB jcode( SB sb ) {
    // Bang "eats" the test part of {test,value} conditionals and drops the
    // value part.
    if( _kids[0]._cond ) {
      if( S.eq("!",_pre) ) {
        _kids[0].jcode(sb.p("$t("));
        return sb.p(") && !XRuntime.GET$COND()");
      } else
        return sb.p("throw XEC.TODO()");
    }

    if( _pre !=null ) {
      if( sb.was_nl() ) sb.i();
      else sb.p(" ");
      sb.p(_pre );
    }
    if( _kids[0] instanceof BinOpAST ) sb.p('(');
    _kids[0].jcode(sb);
    if( _kids[0] instanceof BinOpAST ) sb.p(')');
    if( _post!=null ) sb.p(_post).p(" ");
    return sb;
  }
}
