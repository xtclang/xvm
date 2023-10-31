package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;

class PropertyAST extends AST {
  public String _prop;
  static PropertyAST make( XClzBuilder X ) {
    AST lhs = ast_term(X);    
    String prop = XClzBuilder.value_tcon(X.con());
    // Property is class-local
    if( lhs._type==X._type )
      lhs = null;
    return new PropertyAST( lhs, prop);
  }
  
  private PropertyAST( AST lhs, String prop ) {
    super(new AST[]{lhs});
    _prop = prop;
    if( lhs != null ) _type = lhs._type;
  }

  @Override XType _type() {
    if( _prop.equals("size$get()") ) {
      _type=null;
      return XType.LONG; // Size get is a long for all types
    }
    if( _prop.equals("console$get()") && _kids[0]==null )
      return XType.XCONSOLE;
    throw XEC.TODO();
  }
  
  @Override AST rewrite() {
    // Java strings do not have any properties, just rewrite to the java name
    if( _prop.equals("size$get()") && _kids[0]._type==XType.STRING )
      { _prop="length()"; _type=XType.LONG; }
    return this;
  }
  
  @Override void jpost( SB sb ) {
    if( _kids[0]!=null ) sb.p('.');
    sb.p(_prop);
  }
}
