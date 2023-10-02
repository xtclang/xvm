package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.cons.TCon;
import org.xvm.cc_explore.util.SB;

class PropertyAST extends AST {
  public final String _prop;
  static PropertyAST make( XClzBuilder X ) {
    AST lhs = ast_term(X);    
    String prop = XClzBuilder.value_tcon((TCon)X.con());
    if( lhs instanceof ConAST con && 
        con._con.equals(XClzBuilder.java_class_name(X._mod)) )
      lhs = null;
    return new PropertyAST( lhs,prop);
  }
  
  private PropertyAST( AST lhs, String prop ) {
    super(new AST[]{lhs});
    _prop=prop;
  }
  
  @Override void jpost( SB sb ) {
    if( _kids[0]!=null ) sb.p('.');
    sb.p(_prop);
  }
}
