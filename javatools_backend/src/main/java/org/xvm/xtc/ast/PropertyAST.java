package org.xvm.xtc.ast;

import org.xvm.xtc.*;
import org.xvm.util.SB;
import org.xvm.xtc.cons.Const;

class PropertyAST extends AST {
  public String _prop;
  static PropertyAST make( ClzBuilder X ) {
    AST lhs = ast_term(X);
    Const tc = X.con();
    String prop = XValue.val(X,tc);
    XType type = XType.xtype(tc,false);
    // Property is class-local, no need for class name
    if( lhs._type==X._tmod ) lhs = null;
    // Use Java primitives if we can
    type = type.unbox();
    return new PropertyAST( lhs, type, prop);
  }

  PropertyAST( AST lhs, XType type, String prop ) {
    super(new AST[]{lhs});
    _prop = prop;
    _type = type;
  }

  @Override XType _type() { return _type; } // Already set
  @Override String name() { return _prop; }

  @Override AST prewrite() {
    // Java strings do not have any properties, just rewrite to the java name
    if( _prop.equals("size") || _prop.equals("size$get()")) {
      if( _kids[0]._type == XCons.STRING || _kids[0]._type == XCons.STRINGN )
        { _prop="length()"; _type=XCons.LONG; return this; }
      if( _kids[0]._type.isAry() )
        { _prop="_len"    ; _type=XCons.LONG; return this; }
    }
    // Prop READS use prop$get, but assigns keep the plain name
    if( _par instanceof AssignAST && _par._kids[0]==this ) {
      assert _prop.endsWith("$get()");
      _prop = _prop.substring(0,_prop.length()-6);
    }
    return this;
  }

  @Override void jpost( SB sb ) {
    if( _kids[0]!=null ) sb.p('.');
    sb.p(_prop);
  }
}
