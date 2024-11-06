package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xtc.*;

class TernaryAST extends ElvisAST {
  // Many tests AND'd; the last two kids are the results
  static TernaryAST make( ClzBuilder X) {
    AST[] kids = X.kids(3);
    XType[] xts = XType.xtypes(X.consts());
    // Either a single result type, or a conditional and the main type is in slot 1
    boolean cond = xts.length==2;
    assert xts.length==1 || cond;
    return new TernaryAST(kids, xts[cond ? 1 : 0], cond);
  }
  TernaryAST( AST[] kids, XType type, boolean cond ) {  super(kids); _type = type; _cond = cond; }

  @Override XType _type() {
    // If conditional, kids are either conditional & types match, or they're a
    // constant False
    if( _cond ) {
      int len = _kids.length;
      AST kid0 = _kids[len-2];
      AST kid1 = _kids[len-1];
      assert (kid0._cond && kid0._type.isa(_type)) || kid0._type.isBool();
      assert (kid1._cond && kid1._type.isa(_type)) || kid1._type.isBool();
    }
    return _type;
  }

  @Override boolean _cond() { return _cond; }

  @Override public AST rewrite() {
    // Ternary can be used to narrow args
    if( !_cond && _type==XCons.INT && _kids[1]._type==XCons.LONG ) {
      _type = XCons.LONG;
      return new ConvAST(XCons.INT,this);
    }
    // If conditional and either child is a bare "false" replace with a
    // conditional false constant.  Notice the "|" not "||" to side effect both
    // children.
    return _cond && (condFalse(1,_type) | condFalse(2,_type)) ? this : super.rewrite();
  }

  // Box as needed
  @Override public AST reBox( ) {
    if( _type instanceof XBase ) return null;
    return reBoxKid(_kids.length-2) | reBoxKid(_kids.length-1) ? this : null;
  }

  private boolean reBoxKid(int idx) {
    XType kt = _kids[idx]._type;
    if( kt!=XCons.NULL && kt.box()!=null && kt.box()!=kt ) {
      _kids[idx] = _kids[idx].reBoxThis();
      return true;
    }
    return false;
  }


  @Override public SB jcode( SB sb ) {
    sb.p("(");
    // Due to Elvis operators, there might be many tests ANDed together
    int len = _kids.length;
    for( int i=0; i<len-2; i++ ) {
      _kids[i].jcode(sb);
      sb.p(" && ");
    }
    sb.unchar(4).p(" ? ");
    _kids[len-2].jcode(sb).p(" : ");
    _kids[len-1].jcode(sb).p(")");
    return sb;
  }
}
