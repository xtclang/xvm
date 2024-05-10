package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.Ary;
import org.xvm.util.S;
import org.xvm.util.SB;
import org.xvm.xtc.*;

public class MultiAST extends AST {
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


  // General Elvis Rule
  // MultiAST
  //   e0 && e1 && e2 ... && en

  // Suppose e1 is AST tree with ELVIS(evar).  e1 is typed boolean.
  // "evar" is some expression with side effects.

  // If ELVIS is not a boolean, we need to insert a test and do e1 afterwards:
  //   e0 && (tmp=evar)!=null && e1[tmp/evar] && e2 ... && en
  // Replacing evar with a tmp to avoid repeating side effects.
  // If e1 has several ELVIS's, they all need to have their tests hoisted:
  //   e1 === foo(ELVIS(v1),ELVIS(v2),ELVIS(v3))
  // Then we get:
  //   e0 && (tmp1=v1)!=null && (tmp2=v2)!=null && (tmp3=v3)!=null && foo(tmp1,tmp2,tmp3) && e2 .... && en

  // If ELVIS typed boolean, then it must be directly being tested in Multi.
  // The test e1 below might include {narrowing, TRACE, negate} at least.
  //   e0 && e1(ELVIS(evar)) && e2 && ...
  // We need to replace with a null/zero test inline:
  //   e0 && e1(evar!=null) && e2 ... && en
  // In this case no tmp and no seperate test expression.

  private Ary<Elf> _elves;      // Elvis variable list
  private static class Elf {    // Elvis tests to be inserted
    int _idx; AST _var; String _tmp;
    Elf(int idx, AST var, String tmp) { _idx=idx; _var=var; _tmp=tmp; }
  }

    // Collect an Elvis test to be inserted
  AST doElvis( AST elvis, AST old ) {
    if( _elves==null ) _elves = new Ary<>(Elf.class);
    // Drop the elvis buried inside the expression and return a tmp
    String tmp = enclosing_block().add_tmp(elvis._type);
    _elves.push(new Elf(S.find(_kids,old),elvis,tmp));
    return new RegAST(-1,tmp,elvis._type);
  }

  @Override public AST rewrite() {
    // Insert all Elvis tests
    if( _elves != null ) {
      AST[] kids = new AST[_kids.length+_elves._len];
      int i=0, j=0;
      for( Elf elf : _elves ) {
        while( i<elf._idx )  kids[j++] = _kids[i++];
        // Insert Elvis expression:
        //    A && (expr(elvis)) && C
        //    A && ((tmp=elvis)!=null) && expr(tmp) && C
        AST reg = new RegAST(-1,elf._tmp,elf._var._type);
        AST asg = new AssignAST(reg,elf._var);   reg._par = elf._var._par = asg;
        ConAST con = new ConAST("null");
        BinOpAST eq = new BinOpAST("!=","",XCons.BOOL,asg,con);   con._par = asg._par = eq;
        kids[j++] = eq;
      }
      while( i<_kids.length )  kids[j++] = _kids[i++];
      return new MultiAST(_expr,kids);
    }

    return this;
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
