package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.ClzBuilder;
import org.xvm.xtc.XType;
import org.xvm.xtc.MethodPart;
import org.xvm.xtc.cons.*;
import org.xvm.util.SB;
import org.xvm.util.S;

import java.util.Arrays;

// Reflective construction
class NewVirtAST extends AST {
  final MethodPart _meth;
  static NewVirtAST make( ClzBuilder X ) {
    AST target = ast_term(X);
    MethodPart meth = (MethodPart)X.con().part();
    AST[] kids = X.kids_bias(1);
    kids[0] = target;
    return new NewVirtAST(kids,meth);
  }

  NewVirtAST( AST[] kids, MethodPart meth ) {
    super(kids);
    _meth = meth;
  }

  @Override XType _type() { return _kids[0]._type; }

  // e0.getClass().getConstructor(e1.class,e2.class).newInstance(e1,e2);
  @Override public SB jcode(SB sb) {
    _kids[0].jcode(sb);
    sb.p(".getClass().getConstructor(");
    for( int i=1; i<_kids.length; i++ )
      _kids[i]._type.clz(sb).p(",");
    if( _kids.length>1 ) sb.unchar(1);
    sb.p(").newInstance(");
    for( int i=1; i<_kids.length; i++ )
      _kids[i].jcode(sb).p(",");
    if( _kids.length>1 ) sb.unchar(1);
    return sb.p(")");
  }
}
