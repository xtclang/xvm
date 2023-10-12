package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.MethodPart;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.cons.MethodCon;
import org.xvm.cc_explore.util.SB;

class InvokeAST extends AST {
  final String _meth;

  static InvokeAST make( XClzBuilder X ) {
    Const[] retTypes = X.consts(); // Return types
    AST[] kids = X.kids_bias(1);   // Call arguments
    Const methcon = X.con();       // Method constant, name
    kids[0] = ast_term(X);         // Method expression in kids[0]
    return new InvokeAST(X,kids,retTypes,methcon);
  }
  
  private InvokeAST( XClzBuilder X, AST[] kids, Const[] retTypes, Const methcon ) {
    super(kids);
    // Calling target.method(args)
    MethodPart meth = (MethodPart)((MethodCon)methcon).part();
    _meth = meth._name;
    
    // Replace default args with their actual default values
    for( int i=1; i<_kids.length; i++ ) {
      if( _kids[i] instanceof RegAST reg &&
          reg._reg == -4/*Op.A_DEFAULT*/ ) {    // Default reg
        // Swap in the default from method defaults
        _kids[i] = new ConAST(meth._args[i-1]._def);
      }
    }
  }
  
  InvokeAST( String meth, AST... kids ) {
    super(kids);
    _meth = meth;
  }
  
  @Override AST rewrite() {
    // Cannot invoke directly on java primitives
    if( _meth.equals("toString") && _kids[0].type().equals("long") )
      return new InvokeAST(_meth,new ConAST("Long"),_kids[0]);
    if( _meth.equals("toInt") && _kids[0] instanceof ConAST con )
      return new InvokeAST("valueOf",new ConAST("Long"),_kids[0]);
    if( _meth.equals("toInt64") ) { // Cast to a Long
      if( "long".equals(_kids[0].type()) || "Long".equals(_kids[0].type()))
        return _kids[0];
      // Actually needs a cast
      throw XEC.TODO();
    }
    return this;
  }
  @Override void jmid ( SB sb, int i ) {
    if( i==0 ) sb.p('.').p(_meth).p("(");
    else sb.p(", ");
  }
  @Override void jpost( SB sb ) {
    if( _kids.length>1 && _kids[1]!=null ) sb.unchar(2);
    sb.p(")");
  }
}
