package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.*;

class OuterAST extends AST {
  final int _depth;

  static OuterAST make( XClzBuilder X ) {
    AST[] kids = X.kids(1);     // One expr
    Const type = X.con();
    int depth = X.u31();
    return new OuterAST(kids,type,depth);
  }
  
  private OuterAST( AST[] kids, Const type, int depth ) {
    super(kids);
    _type = XType.xtype(type,false);
    _depth = depth;
  }

  @Override XType _type() { return _type; }

  @Override SB jcode( SB sb ) {
    // TODO: This can be legit but not in Const classes
    //for( int i=0; i<_depth; i++ )
    //  sb.p("outer.");
    return _kids[0].jcode(sb);
  }
}