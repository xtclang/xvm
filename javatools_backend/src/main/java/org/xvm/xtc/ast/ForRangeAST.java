package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.util.SB;

class ForRangeAST extends ForAST {
  private String _prim_iter;    // For exploded primitive iterator
  // _kids[0] == Var
  // _kids[1] == Iter
  // _kids[2] == Body
  // _kids[3+] == Special Regs
  static ForRangeAST make( ClzBuilder X ) {
    int nlocals = X.nlocals(); // Count of locals
    AST[] kids = X.kids_bias(3);
    kids[0] = ast_term(X);     // Var
    kids[1] = ast_term(X);     // Iter
    kids[2] = ast(X);          // Body
    X.pop_locals(nlocals);     // Pop scope-locals at end of scope
    return new ForRangeAST(kids);
  }
  private ForRangeAST( AST[] kids ) { super(kids,3); }

  @Override public AST rewrite() {
    super.rewrite();
    // Primitive iterator?
    AST k0 = _kids[0], k1 = _kids[1];
    // Get a tmp
    if( k0._type.zero() && !(k0 instanceof MultiAST) ) {
      if( k1._type instanceof XClz xclz )
        ClzBuilder.add_import(xclz);
      _prim_iter = enclosing_block().add_tmp(k1._type);
    }
    // AryString primitive iterator
    if( k0._type.primeq() && k1._type.isa(XCons.ARYSTRING) ) {
      _kids[1] = new InvokeAST("iterStr",XCons.ITERSTR,k1);
      return this;
    }

    return null;
  }

  @Override public SB jcode( SB sb ) {
    if( _prim_iter == null ) {
      // for( long x : new XRange(1,100) ) {
      if( _label!=null ) sb.p(_label).p(":").nl().i();
      _kids[0].jcode(sb.p("for( "));
      _kids[1].jcode(sb.p(" : "  ));
      sb.p(" ) ");
    } else if( _kids[0] instanceof DefRegAST def ) {
      // Range tmp = new XRange(1,100);
      // Label: for( long x = tmp._lo; x < tmp._hi; x++ )
      sb.p(_prim_iter).p(" = ");
      _kids[1].jcode(sb).p(";").nl().i();
      if( _label!=null ) sb.p(_label).p(":  ");
      def._type.clz(sb.p("for( "));
      sb.fmt(" %0 = %1._start; %1.in(%0); %0 += %1._incr ) ",def._name,_prim_iter);
    } else {
      throw XEC.TODO();
    }
    // Body
    _kids[2].jcode(sb);
    return sb;
  }
}
