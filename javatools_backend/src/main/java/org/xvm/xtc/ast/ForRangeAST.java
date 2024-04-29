package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.util.SB;

class ForRangeAST extends AST {
  String _label;
  String _tmp;                  // For exploded iterator
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
  private ForRangeAST( AST[] kids ) { super(kids); }

  @Override boolean is_loopswitch() { return true; }

  @Override XType _type() { return XCons.VOID; }

  @Override public AST unBox() {
    AST k0 = _kids[0], k1 = _kids[1];
    if( !k0._type.primeq() )  return this;
    if( !k1._type.isa(XCons.ARYSTRING) )  return this;
    k1._par = _kids[1] = new InvokeAST("iterStr",XCons.ITERSTR,k1);
    _kids[1]._par = this;
    return this;
  }

  @Override public AST rewrite() {
    // Primitive iterator?
    // Get a tmp
    if( _kids[0]._type.zero() ) {
      if( _kids[1]._type instanceof XClz xclz )
        ClzBuilder.add_import(xclz);
      _tmp = enclosing_block().add_tmp(_kids[1]._type);
    }
    return this;
  }
  @Override void add_label() { if( _label==null ) _label = "label"; }
  @Override String label() { return _label; }

  @Override public SB jcode( SB sb ) {
    if( sb.was_nl() ) sb.i();
    if( _tmp == null ) {
      // for( long x : new XRange(1,100) ) {
      if( _label!=null ) sb.p(_label).p(":").nl().i();
      _kids[0].jcode(sb.p("for( "));
      _kids[1].jcode(sb.p(" : "  ));
      sb.p(" ) ");
    } else {
      // Range tmp = new XRange(1,100);
      // for( long x = tmp._lo; x < tmp._hi; x++ )
      sb.p(_tmp).p(" = ");
      _kids[1].jcode(sb).p(";").nl().i();
      if( _label!=null ) sb.p(_label).p(":  ");
      DefRegAST def = (DefRegAST)_kids[0];
      def._type.clz(sb.p("for( "));
      sb.fmt(" %0 = %1._start; %1.in(%0); %0 += %1._incr ) ",def._name,_tmp);
    }
    // Body
    _kids[2].jcode(sb);
    return sb;
  }
}
