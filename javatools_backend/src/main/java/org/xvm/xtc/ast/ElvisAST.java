package org.xvm.xtc.ast;

import java.util.Arrays;
import java.util.HashMap;
import org.xvm.XEC;
import org.xvm.util.Ary;
import org.xvm.util.S;
import org.xvm.util.SB;
import org.xvm.xtc.*;

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

// If ELVIS typed boolean, then it must be directly being tested.
// The test e1 below might include {narrowing, TRACE, negate} at least.
//   e0 && e1(ELVIS(evar)) && e2 && ...
// We need to replace with a null/zero test inline:
//   e0 && e1(evar!=null) && e2 ... && en
// In this case no tmp and no separate test expression.

abstract class ElvisAST extends AST {

  static class Elf {            // Elvis tests to be inserted
    int _idx; AST _var; String _tmp;
    Elf(int idx, AST var, String tmp) { _idx=idx; _var=var; _tmp=tmp; }
    public AST test() {
      // Insert Elvis expression:
      //    A && (expr(elvis)) && C
      //    A && ((tmp=elvis)!=null) && expr(tmp) && C
      AST reg = new RegAST(_tmp,_var._type);
      AST asg = new AssignAST(reg,_var);
      return new UniOpAST(new AST[]{asg},
                          _var._cond ? "$t(" : "(",
                          _var._cond ? ") && XRuntime.$COND" : ")!=null",
                          XCons.BOOL);
    }
  }
  Ary<Elf> _elves;              // Elvis variable list

  ElvisAST(AST[]kids) { super(kids); }

  // Some kind of expression: "expr0?.expr+expr.expr..."
  // if( tmp=expr0)!=null ) tmp.expr+expr.expr....
  AST doElvis( AST elvis, AST old ) {
    if( _elves==null ) _elves = new Ary<>(Elf.class);
    // Drop the elvis buried inside the expression and return a tmp
    String tmp = enclosing_block().add_tmp(elvis._type);
    _elves.push(new Elf(S.find(_kids,old),elvis,tmp));
    return new RegAST(tmp,elvis._type);
  }

  @Override public AST rewrite() {
    if( _elves == null ) return null;
    // Replacing: "...&& elvis(e0) && ..."
    // With:      "...&& e0!=null  && ..."
    // OR - needs an extra child slot -
    // Replacing: "...&& (e1 elvis(e0)) && ..."
    // With:      "...&& (tmp=e0)!=null && (e1 tmp) && ..."
    //
    int xtra = 0;
    for( Elf elf : _elves )
      if( elf._var._par != _kids[elf._idx] )
        xtra++;
    if( xtra==0 )
      throw XEC.TODO();

    AST[] kids = new AST[_kids.length+xtra];
    int i=0, j=0;
    for( Elf elf : _elves ) {
      // Copy, sliding over for extra slot
      while( i<elf._idx )  kids[j++] = _kids[i++];
      kids[j++] = elf.test();   // (tmp=e0)!=null
    }
    // Copy trailing kids
    while( i<_kids.length )  kids[j++] = _kids[i++];
    _kids = kids;
    _elves = null;  // Been there, done that
    return this;
  }

}
