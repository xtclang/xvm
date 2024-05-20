package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.xtc.*;

class TernaryAST extends AST {
  static TernaryAST make( ClzBuilder X) { return new TernaryAST(X.kids(3),XType.xtypes(X.consts())[0]); }
  TernaryAST( AST[] kids, XType type ) { super(kids); _type = type; }

  @Override XType _type() { return _type; }

  RegAST doElvis( AST elvis ) {
    assert _kids[0]==null;
    XType type = elvis._type;
    // THIS:     ( : [good_expr (elvis var)] [bad_expr])
    // MAPS TO:  ( ((tmp=var)!=null) ? [good_expr tmp] [bad_expr] )
    String tmpname = enclosing_block().add_tmp(type);
    // Assign the tmp to predicate
    AST reg = new RegAST(tmpname,type);
    AST asgn = new AssignAST(reg,elvis);
    asgn._type = type;
    // Zero/null if primitive
    AST zero = new ConAST("null");
    // Null check it
    AST nchk = new BinOpAST("!=","",XCons.BOOL,asgn, zero );
    // Insert missing predicate into Ternary
    _kids[0] = nchk;
    nchk._par = this;
    // Use tmp instead of Elvis in good_expr
    return new RegAST(tmpname,type);
  }

  // Box as needed
  @Override XType reBox( AST kid ) {
    return _kids[0]==kid ? null : _type;
  }

  @Override void jpre( SB sb ) { sb.p("("); }
  @Override void jmid ( SB sb, int i ) {
    if( i==0 ) sb.p(" ? ");
    if( i==1 ) sb.p(" : ");
  }
  @Override void jpost( SB sb ) { sb.p(")"); }
}
