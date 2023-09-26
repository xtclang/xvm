package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.cons.TCon;
import org.xvm.cc_explore.util.SB;

class PropertyAST extends AST {
  public final String _clz, _prop;
  static PropertyAST make( XClzBuilder X ) {
    ConAST prop = (ConAST)ast_term(X); // TODO: Not sure always a constant here
    String con = XClzBuilder.value_tcon((TCon)X.con());
    return new PropertyAST(X,prop._con,con);
  }
  
  private PropertyAST( XClzBuilder X, String clz, String prop ) {
    super(null);
    // If already in the correct class, skip the leading class
    if( XClzBuilder.java_class_name( X._mod ).equals(clz) )
      clz = null;
    _clz=clz;
    _prop=prop;
  }
  
  @Override void jpre ( SB sb ) {
    if( _clz != null )
      sb.p(_clz).p('.');
    sb.p(_prop).p("$get()");
  }
}
