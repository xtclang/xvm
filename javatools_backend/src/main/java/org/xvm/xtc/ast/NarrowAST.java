package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.AccessTCon;
import org.xvm.xtc.cons.Const;

// Sharpen or lift a type, after a test:
//   if( x instanceof Y ) { Y xy = (Y)x; .... }
// This is a free up-cast after test.
class NarrowAST extends AST {
  private final XType _xt;
  static NarrowAST make( ClzBuilder X ) {
    AST[] kids = X.kids(1);     // One expr
    Const type = X.con();
    XType xt = XType.xtype(type,true); // Does not unbox for free
    return new NarrowAST(kids,xt);
  }

  private NarrowAST( AST[] kids, XType xt ) {
    super(kids);
    _xt = xt;
  }
  @Override XType _type() {
    return _kids[0]._type instanceof XBase && !(_xt instanceof XBase)
      ? _xt.unbox()          // Preserve unboxed kid, but upcast to unboxed _xt
      : _xt;
  }
  @Override String name() { return _kids[0].name(); }
}
