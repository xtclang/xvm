package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.MethodPart;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.cons.MethodCon;
import org.xvm.cc_explore.cons.TCon;
import org.xvm.cc_explore.util.SB;

class NewAST extends AST {
  final MethodPart _meth; // TODO: Constructor is Java is totally determined by argument types.  
  static NewAST make( XClzBuilder X ) {
    return new NewAST(X.con(),X.con(),X.kids());
  }
  private NewAST( Const xtype, Const meth, AST[] kids ) {
    this(kids,XClzBuilder.jtype(xtype,false),(MethodPart)((MethodCon)meth).part());
  }
  NewAST( AST[] kids, String type, MethodPart meth ) {
    super(kids_plus_clz(kids,type));
    _type = type;
    _meth = meth;
  }
  private static AST[] kids_plus_clz(AST[] kids, String type) {
    // If type is generic, add the generic class explicitly
    int lastx = type.length()-1;
    if( type.charAt(lastx)!='>' ) return kids;
    AST[] kids2 = new AST[(kids==null ? 0 : kids.length)+1];
    if( kids!=null ) System.arraycopy(kids,0,kids2,1,kids.length);
    String gen = type.substring(type.indexOf('<')+1,lastx);
    kids2[0] = new ConAST(null,gen+".class",gen);
    return kids2;
  }
  @Override String _type() { return _type; }

  @Override AST rewrite() {
    // Weird syntax for conversion "new long(e0)" is same as "Long.valueOf(e0)"
    if( _type.equals("long") )
      return new InvokeAST("valueOf","long",new ConAST("Long"),_kids[0]).do_type();
    return this;
  }
  
  @Override void jpre ( SB sb ) { sb.p("new ").p(_type).p("("); }
  @Override void jmid ( SB sb, int i ) { sb.p(", "); }
  @Override void jpost( SB sb ) {    
    if( _kids!=null )  sb.unchar(2);
    sb.p(")");
  }
}
