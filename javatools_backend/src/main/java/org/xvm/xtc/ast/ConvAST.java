package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.XEC;
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
    if( types.length != 1 ) {
      assert types.length==2 && XType.xtype(types[0],false)==XCons.BOOL;
      assert convs.length==2 && convs[0]==null;
    }
    _type = XType.xtype(types[idx],false);
    _meth = (MethodPart)convs[idx].part();
  }

  @Override XType _type() { return _type; }

  @Override AST postwrite() {
    // Converting from a Java primitive will always need some kind of conversion call
    if( _kids[0]._type.is_jdk() )
      return new NewAST(_kids,(XClz)_type);
    if( !_kids[0]._cond && _type==XCons.LONG &&
        // TODO: this needs to handle all flavors
        (_kids[0]._type==XCons.JUINT8 ||
         _kids[0]._type==XCons.JUINT32 ) )
      return new UniOpAST(new AST[]{_kids[0]},null,"._i",_type);
    return this;
  }
  
  @Override public SB jcode( SB sb ) {
    _type.clz(sb.p("(")).p(")(");
    return _kids[0].jcode(sb).p(")");
  }
}
