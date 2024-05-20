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
    XType kid1 = _kids[1]._type;
    // Box kid1, so we can null-check it
    if( kid0==XCons.BOOL )
      return S.eq(kid1.ztype(),"0") ? kid1.box() : kid1;
    // Otherwise, we're in a multi-ast situation with lots of AND'd parts
    return XCons.BOOL;
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

  @Override public SB jcode(SB sb) {
    XType xt;
    if( _expr ) {
      // A && B && C && ...
      for( AST kid : _kids )
        kid.jcode(sb).p(" && ");
      return sb.unchar(4); // Undo " && "

    } else if( (xt=multiAssign()) != null ) {
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

    } else {
      // A;
      // B;
      // C;
      // ...
      for( AST kid : _kids )
        kid.jcode(sb).p(";").nl();
      return sb;
    }
  }
}
