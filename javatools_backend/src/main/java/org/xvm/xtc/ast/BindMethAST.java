package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.*;

class BindMethAST extends AST {
  final MethodPart _meth;
  static BindMethAST make( ClzBuilder X ) {
    AST target = ast_term(X);
    Const meth = X.con();
    Const type = X.con();
    return new BindMethAST( (MethodPart) meth.part(), XType.xtype(type,false), target );
  }

  private BindMethAST( MethodPart meth, XType type, AST... kids ) {
    super(kids);
    _meth = meth;
    _type = type;
  }

  @Override XType _type() { return _type; }
  @Override String name() { return _meth.jname(); }

  @Override void jpost( SB sb ) {
    sb.p("::").p(_meth._name);
  }
}
