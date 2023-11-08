package org.xvm.xclz;

import org.xvm.MethodPart;
import org.xvm.cons.Const;
import org.xvm.cons.MethodCon;
import org.xvm.util.SB;

class NewAST extends AST {
  final MethodPart _meth; // TODO: Constructor is Java is totally determined by argument types.  
  static NewAST make( XClzBuilder X ) {
    return new NewAST(X.con(),X.con(),X.kids());
  }
  private NewAST( Const xtype, Const meth, AST[] kids ) {
    this(kids,XType.xtype(xtype,false),(MethodPart)((MethodCon)meth).part());
  }
  NewAST( AST[] kids, XType type, MethodPart meth ) {
    super(kids_plus_clz(kids,type));
    _type = type;
    _meth = meth;
  }
  private static AST[] kids_plus_clz(AST[] kids, XType type) {
    
    // If type is generic, add the generic class explicitly
    if( !(type instanceof XType.Ary tary ) ) return kids;
    if( tary.is_prim_base() ) return kids;
    AST[] kids2 = new AST[(kids==null ? 0 : kids.length)+1];
    if( kids!=null ) System.arraycopy(kids,0,kids2,1,kids.length);
    XType gen = tary._e;
    kids2[0] = new ConAST(null,gen+".class",gen);
    return kids2;
  }
  @Override XType _type() { return _type; }

  @Override AST rewrite() {
    // Weird syntax for conversion "new long(e0)" is same as "Long.valueOf(e0)"
    if( _type==XType.LONG )
      return new InvokeAST("valueOf",XType.LONG,new ConAST("Long"),_kids[0]).do_type();
    return this;
  }
  
  @Override void jpre ( SB sb ) { _type.p(sb.p("new ")).p("("); }
  @Override void jmid ( SB sb, int i ) { sb.p(", "); }
  @Override void jpost( SB sb ) {    
    if( _kids!=null )  sb.unchar(2);
    sb.p(")");
  }
}
