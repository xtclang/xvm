package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.cons.TCon;
import org.xvm.cc_explore.util.SB;

class PropertyAST extends AST {
  public final String _lhs, _prop;
  static PropertyAST make( XClzBuilder X ) {
    AST prop = ast_term(X);
    String lhs;
    if( prop instanceof ConAST cprop ) lhs = cprop._con;
    else if( prop instanceof RegAST reg ) lhs = reg._name;
    else throw XEC.TODO();
    
    String con = XClzBuilder.value_tcon((TCon)X.con());
    return new PropertyAST(X,lhs,con);
  }
  
  private PropertyAST( XClzBuilder X, String lhs, String prop ) {
    super(null);
    // If already in the correct class, skip the leading class
    if( XClzBuilder.java_class_name( X._mod ).equals(lhs) )
      lhs = null;
    _lhs=lhs;
    _prop=prop;
  }
  
  @Override void jpre ( SB sb ) {
    if( _lhs != null )
      sb.p(_lhs).p('.');
    sb.p(_prop).p("$get()");
  }
}
