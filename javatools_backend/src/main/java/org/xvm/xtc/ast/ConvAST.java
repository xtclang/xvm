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
  
  @Override public SB jcode( SB sb ) {
    _type.p(sb.p("(")).p(")");
    _kids[0].jcode(sb);
    return sb;    
  }
}
