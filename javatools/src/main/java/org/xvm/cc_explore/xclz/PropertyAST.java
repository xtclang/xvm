package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.cons.TCon;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;

class PropertyAST extends AST {
  public String _prop, _type;
  static PropertyAST make( XClzBuilder X ) {
    AST lhs = ast_term(X);    
    String prop = XClzBuilder.value_tcon(X.con());
    if( lhs instanceof ConAST con && 
        con._con.equals(XClzBuilder.java_class_name(X._mod._name)) )
      lhs = null;
    return new PropertyAST( lhs,prop);
  }
  
  private PropertyAST( AST lhs, String prop ) {
    super(new AST[]{lhs});
    _prop=prop;
  }
  @Override String type() {
    if( _type == null ) throw XEC.TODO();
    return _type;
  }

  @Override AST rewrite() {
    // Java strings do not have any properties, just rewrite to the java name
    if( _prop.equals("size$get()") && _kids[0].type().equals("String") )
      { _prop="length()"; _type="long"; }
    return this;
  }
  
  @Override void jpost( SB sb ) {
    if( _kids[0]!=null ) sb.p('.');
    sb.p(_prop);
  }
}
