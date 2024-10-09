package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.Ary;
import org.xvm.util.S;
import org.xvm.util.SB;
import org.xvm.xtc.*;

public class MultiAST extends ElvisAST {
  final boolean _expr;
  static MultiAST make( ClzBuilder X, boolean expr) {
    int len = X.u31();
    AST[] kids = new AST[len];
    for( int i=0; i<len; i++ )
      kids[i] = expr ? ast_term(X) : ast(X);
    return new MultiAST(expr,kids);
  }
  MultiAST( boolean expr, AST... kids ) { super(kids); _expr = expr; }

  @Override XType _type() {
    XType kid0 = _kids[0]._type;
    if( _kids.length==1 ) return kid0;
    if( _kids[1]==null ) return XCons.VOID; // Ignored 2nd
    XType kid1 = _kids[1]._type;

    // Loading bool, we're using COND.  Only a 2nd element afterwards, then its
    // conditional on that type.
    if( _kids.length==2 && kid0.isBool() )
      return _kids[1]._type;

    // Otherwise, we're in a multi-ast situation with lots of AND'd parts
    return XCons.BOOL;
  }
  @Override boolean _cond() {
    return _kids.length==2 && _kids[0]._type.isBool() && !_kids[1]._type.isBool();
  }


  // All parts are simple defs of the same type.
  // Can use Javas multi-def: "int x0=e0, x1=e1"
  private XType multiAssign() {
    if( !(_kids[0] instanceof AssignAST asg) )
      return null;
    XType xt = asg.isDef();
    if( xt==null ) return null;
    for( AST ast : _kids )
      if( !(ast instanceof AssignAST asg1) || asg1.isDef()!=xt )
        return null;
    return xt;
  }

  @Override public AST rewrite() {
    if( !_expr && _par instanceof WhileAST && _elves==null )
      return new BlockAST(_kids);
    return super.rewrite();
  }

  @Override public SB jcode(SB sb) {
    XType xt;
    if( _expr ) {
      // Only "true && expr"
      if( _cond ) {
        assert _kids[0] instanceof ConAST con && con._con.equals("true");
        return _kids[1].jcode(sb.p("XRuntime.True(")).p(")");
      }
      // A && B && C && ...
      for( AST kid : _kids )
        kid.jcode(sb).p(" && ");
      return sb.unchar(4); // Undo " && "
    }

    if( (xt=multiAssign()) != null ) {
      // This form is required for for-loops
      //   int x0=e0, x1=e1, ..., xn=en;
      xt.str(sb).p(" ");
      for( AST kid : _kids ) {
        AssignAST asg = (AssignAST)kid;
        sb.p(((DefRegAST)asg._kids[0])._name);
        sb.p(" = ");
        asg._kids[1].jcode(sb);
        sb.p(", ");
      }
      return sb.unchar(2);
    }

    // A;
    // B;
    // C;
    // Also (a, _, c, _, e) has ignored tuple parts
    for( AST kid : _kids )
      if( kid != null ) // Ignored in tuple breakouts
        kid.jcode(sb).p(";").nl().i();
    return sb;
  }
}
