package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.util.SB;
import org.xvm.xtc.cons.Const;

class PropertyAST extends AST {
  public String _prop;
  static PropertyAST make( XClzBuilder X ) {
    AST lhs = ast_term(X);
    Const tc = X.con();
    String prop = XClzBuilder.value_tcon(tc);
    XType type = XType.xtype(tc,false);
    // Property is class-local, no need for class name
    if( lhs._type==X._tmod ) lhs = null;
    return new PropertyAST( lhs, type, prop);
  }
  
  private PropertyAST( AST lhs, XType type, String prop ) {
    super(new AST[]{lhs});
    _prop = prop;
    _type = type;
  }

  @Override XType _type() { return _type; } // Already set
  @Override String name() { return _prop; }
  
  @Override AST rewrite() {
    // Java strings do not have any properties, just rewrite to the java name
    if( _prop.equals("size") ) {
      if( _kids[0]._type instanceof XType.Ary )
        { _prop="_len"; _type=XType.LONG; }
      if( _kids[0]._type == XType.STRING )
        { _prop="length()"; _type=XType.LONG; }
    } else if( !(_par instanceof AssignAST) ) {
      // Prop READS use prop$get, but assigns keep the plain name
      _prop = _prop+"$get()";
    }
    return this;
  }
  
  @Override void jpost( SB sb ) {
    if( _kids[0]!=null ) sb.p('.');
    sb.p(_prop);
  }
}
