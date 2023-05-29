package org.xvm.xtc.ast;

import org.xvm.xtc.*;
import org.xvm.util.SB;

class ForRangeAST extends AST {
  String _tmp;                  // For exploded iterator
  // _kids[0] == LHS
  // _kids[1] == RHS
  // _kids[2] == Body
  // _kids[3+] == Special Regs
  static ForRangeAST make( ClzBuilder X ) {
    int nlocals = X.nlocals(); // Count of locals
    AST[] kids = X.kids_bias(3);
    kids[0] = ast_term(X);     // LHS
    kids[1] = ast_term(X);     // RHS
    kids[2] = ast(X);          // Body
    X.pop_locals(nlocals);     // Pop scope-locals at end of scope
    return new ForRangeAST(kids);
  }
  private ForRangeAST( AST[] kids ) { super(kids); }

  @Override boolean is_loopswitch() { return true; }

  @Override XType _type() { return XCons.VOID; }

  @Override AST prewrite() {
    // Primitive iterator?
    // Get a tmp
    if( _kids[0]._type.primeq() )
      _tmp = enclosing_block().add_tmp(_kids[1]._type);
    return this;
  }

  
  @Override public SB jcode( SB sb ) {
    if( sb.was_nl() ) sb.i();
    if( _tmp == null ) {
      // for( long x : new XRange(1,100) ) {
      _kids[0].jcode(sb.p("for( "));
      _kids[1].jcode(sb.p(" : "  ));
      sb.p(" ) ");
    } else {
      // Range tmp = new XRange(1,100);
      // for( long x = tmp._lo; x < tmp._hi; x++ )
      sb.p(_tmp).p(" = ");
      _kids[1].jcode(sb).p(";").nl();
      DefRegAST def = (DefRegAST)_kids[0];
      def._type.clz(sb.i().p("for( "));
      sb.fmt(" %0 = %1._lo; %0 < %1._hi; %0++ ) ",def._name,_tmp);
    }
    // Body
    _kids[2].jcode(sb);
    return sb;
  }
}
