package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.Const;
import org.xvm.xtc.cons.MethodCon;

class ConvAST extends AST {
  final MethodPart _meth;

  static ConvAST make( ClzBuilder X ) {
    AST[] kids = X.kids(1);     // One expr
    Const[] types = X.consts();
    Const[] convs = X.sparse_consts(types.length);
    return new ConvAST(kids,types,convs);
  }
  
  private ConvAST( AST[] kids, Const[] types, Const[] convs) {
    super(kids);
    // Expecting exactly 2 types; first is boolean for a COND.
    // Expecting exactly 1 conversion method.
    int idx = types.length == 1 ? 0 : 1;
    if( types.length==1 ) {
    } else {
      assert types.length==2 && XType.xtype(types[0],false)==XType.BOOL;
      assert convs.length==2 && convs[0]==null;
    }
    _type = XType.xtype(types[idx],false);
    _meth = (MethodPart)((MethodCon)convs[idx]).part();
  }

  @Override XType _type() { return _type; }
  
  @Override AST rewrite() {
    if( !(_type.is_prim_base() && _kids[0]._type.is_prim_base()) )
      // Need a converting constructor; e.g. "new Dec64(ary.at(i))"
      return new NewAST(_kids,(XClz)_type);
    // Use a normal explicit Java cast; e.g. "(long)ary.at(i)"
    return this;
  }
  
  
  @Override public SB jcode( SB sb ) {
    _type.clz(sb.p("(")).p(")");
    return _kids[0].jcode(sb);
  }
}
