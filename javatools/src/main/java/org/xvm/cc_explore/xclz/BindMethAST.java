package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.MethodPart;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.XEC;

class BindMethAST extends AST {
  final MethodPart _meth;
  static BindMethAST make( XClzBuilder X ) {
    AST target = ast_term(X);
    Const meth = X.con();
    Const type = X.con();
    return new BindMethAST( (MethodPart)((MethodCon)meth).part(), XType.xtype(type,false), target );
  }
    
  private BindMethAST( MethodPart meth, XType type, AST... kids ) {
    super(kids);
    _meth = meth;
    _type = type;
  }

  @Override XType _type() { return _type; }
  @Override String name() { return _meth.jname(); }

  @Override void jpost( SB sb ) {
    sb.p(".").p(_meth._name);
  }
}
