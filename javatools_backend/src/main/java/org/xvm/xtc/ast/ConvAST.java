package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.Const;
import org.xvm.xtc.cons.MethodCon;
import java.util.HashMap;

class ConvAST extends AST {
  final MethodPart _meth;

  static ConvAST make( XClzBuilder X ) {
    AST[] kids = X.kids(1);     // One expr
    Const[] types = X.consts();
    Const[] convs = X.sparse_consts(types.length);
    return new ConvAST(kids,types,convs);
  }
  
  private ConvAST( AST[] kids, Const[] types, Const[] convs) {
    super(kids);
    // Expecting exactly 2 types; first is boolean for a COND.
    // Expecting exactly 1 conversion method.
    assert types.length==2 && XType.xtype(types[0],false)==XType.BOOL;
    assert convs.length==2 && convs[0]==null;
    _type = XType.xtype(types[1],false);
    _meth = (MethodPart)((MethodCon)convs[1]).part();
  }

  @Override XType _type() { return _type; }
  
  @Override public SB jcode( SB sb ) {
    _type.p(sb.p("(")).p(")");
    _kids[0].jcode(sb);
    return sb;    
  }
}
