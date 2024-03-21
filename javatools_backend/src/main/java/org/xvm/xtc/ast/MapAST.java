package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.XEC;
import org.xvm.xtc.cons.Const;
import org.xvm.xtc.*;

class MapAST extends AST {
  static MapAST make( ClzBuilder X) {
    Const type = X.con();
    XClz tmap = (XClz)XType.xtype(type,false);
    AST[] keys = X.kids();
    AST[] vals = X.kids();
    AST[] kids = new AST[keys.length<<1];
    for( int i=0; i<keys.length; i++ ) {
      kids[2*i+0] = keys[i];
      kids[2*i+1] = vals[i];
    }
    return new MapAST(tmap,kids);
  }
  private MapAST(XClz tmap, AST[] kids) {
    super(kids);
    _type = tmap;
    ClzBuilder.add_import(XCons.MAP);
  }
  @Override XType _type() { return _type; }
  @Override public SB jcode( SB sb ) {
    sb.p("new Map<>() {{").nl().ii();
    for( int i=0; i<_kids.length; i+= 2 ) {
      sb.ip("put(");
      _kids[i+0].jcode(sb);
      sb.p(",");
      _kids[i+1].jcode(sb);
      sb.p(");").nl();
    }
    return sb.di().ip("}}");
  }
}
