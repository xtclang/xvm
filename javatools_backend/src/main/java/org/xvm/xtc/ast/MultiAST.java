package org.xvm.xtc.ast;

import org.xvm.util.S;
import org.xvm.util.SB;
import org.xvm.xtc.*;
import org.xvm.XEC;

public class MultiAST extends AST {
  final boolean _expr;
  private AST[] _elves;         // Elvis index
  private String[] _etmps;      // Elvis temps
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

  // THIS:    ( ...e0 ?. e1..., more);
  // MAPS TO: ((e0==null || (...e0.e1...)) && more)
  AST doElvis( AST elvis, AST old ) {
    if( _elves==null ) { _elves = new AST[_kids.length]; _etmps = new String[_kids.length]; }
    int idx = S.find(_kids,old);
    _elves[idx] = elvis;
    // Drop the elvis buried inside the expression and return a clone
    _etmps[idx] = enclosing_block().add_tmp(elvis._type);
    return new RegAST(-1,_etmps[idx],elvis._type);
  }

  @Override public AST rewrite() {
    if( _elves != null )
      for( int i=0; i<_elves.length; i++ )
        if( _elves[i] != null ) {
          // Replace Elvis expression:
          //    A && (expr(elvis)) && C
          //    A && ( ((tmp=elvis)==null) || expr(tmp)) && C
          AST reg = new RegAST(-1,_etmps[i],_elves[i]._type);
          AST asg = new AssignAST(reg,_elves[i]);   reg._par = _elves[i]._par = asg;
          ConAST con = new ConAST("null");
          BinOpAST eq = new BinOpAST("==","",XCons.BOOL,con,asg);   con._par = asg._par = eq;
          BinOpAST or = new BinOpAST("||","",XCons.BOOL,eq,_kids[i]); eq._par = _kids[i]._par = or;
          return or;
        }
    return this;
  }

  @Override public SB jcode(SB sb) {
    if( _expr ) {
      // A && B && C && ...
      for( AST kid : _kids )
        kid.jcode(sb).p(" && ");
      return sb.unchar(4); // Undo " && "

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
