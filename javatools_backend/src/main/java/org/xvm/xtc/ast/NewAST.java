package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.Const;
import org.xvm.util.SB;

class NewAST extends AST {
  static NewAST make( ClzBuilder X ) {
    return new NewAST(X.con(),X.con(),X.kids());
  }
  private NewAST( Const type, Const meth, AST[] kids ) {
    this(kids,XType.xtype(type,true));
  }
  NewAST( AST[] kids, XType type ) {
    super(kids_plus_clz(kids,type));
    _type = type;
    if( type.needs_import() )
      ClzBuilder.add_import((XClz)type);
  }
  private static AST[] kids_plus_clz(AST[] kids, XType type) {
    // Add type parameters, if any
    int nTypeParms = type.nTypeParms();
    if( nTypeParms == 0 ) return kids;
    // Primitive base arrays have a fixed known type parameter, no need to pass
    if( type instanceof XAry ary && !ary.generic() )
      return kids;
    
    AST[] kids2 = new AST[(kids==null ? 0 : kids.length)+nTypeParms];
    if( kids!=null ) System.arraycopy(kids,0,kids2,nTypeParms,kids.length);
    for( int i=0; i<nTypeParms; i++ ) {
      XType gen = type.typeParm(i);
      XClz box = gen.box();
      kids2[i] = new ConAST(null,null,(box==null ? gen : box).clz()+".GOLD",gen);
    }
    return kids2;
  }
  @Override XType _type() { return _type; }

  @Override void jpre ( SB sb ) { _type.clz(sb.p("new ")).p("("); }
  @Override void jmid ( SB sb, int i ) { sb.p(", "); }
  @Override void jpost( SB sb ) {    
    if( _kids!=null )  sb.unchar(2);
    sb.p(")");
  }
}
