package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;

class PropertyAST extends AST {
  public String _prop, _type;
  static PropertyAST make( XClzBuilder X ) {
    AST lhs = ast_term(X);    
    String prop = XClzBuilder.value_tcon(X.con());
    // Property is class-local
    String type = lhs._type;
    if( lhs instanceof ConAST con && 
        con._con.equals(XClzBuilder.java_class_name(X._mod._name)) )
      lhs = null;
    return new PropertyAST( lhs,prop, type);
  }
  
  private PropertyAST( AST lhs, String prop, String type ) {
    super(new AST[]{lhs});
    _prop = prop;
    _type = type;
  }

  @Override String _type() {
    if( _prop.equals("size$get()") )
      return "long"; // Size get is a long for all types
    if( _prop.equals("console$get()") && _kids[0]==null )
      return "XConsole";
    throw XEC.TODO();
  }
  
  @Override AST rewrite() {
    // Java strings do not have any properties, just rewrite to the java name
    if( _prop.equals("size$get()") && _kids[0]._type.equals("String") )
      { _prop="length()"; _type="long"; }
    return this;
  }
  
  @Override void jpost( SB sb ) {
    if( _kids[0]!=null ) sb.p('.');
    sb.p(_prop);
  }
}
