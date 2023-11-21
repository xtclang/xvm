package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.Const;
import org.xvm.util.SB;

public class CallAST extends AST {
  final XType[] _rets;
  static CallAST make( ClzBuilder X ) {
    // Read optional array of return types (not currently used)
    Const[] retTypes = X.consts();
    // Read the arguments, then the function expression.
    AST[] kids = X.kids_bias(1);
    // Move the function to the 0th kid slot.
    kids[0] = ast_term(X);     // Call expression first
    return new CallAST(kids,retTypes,X._meth);
  }
  private CallAST( AST[] kids, Const[] retTypes, MethodPart meth ) {
    super(kids);
    // Check for a call to super: "super.call()" becomes "super.METHOD"
    if( _kids[0] instanceof RegAST reg && reg._reg== -13 )
      _kids[0] = new ConAST(null,"super."+meth._name,reg._type);
    _rets = XType.xtypes(retTypes);
  }
  
  @Override XType _type() {
    if( _rets==null ) return XType.VOID;
    if( _rets.length == 1 ) return _rets[0];
    throw XEC.TODO();
  }

  private static final String[] CMPS = new String[]{"equals","compare"};
  @Override AST rewrite( ) {
    // Try to rewrite constant calls to the XTC special equals.
    if( _kids[0] instanceof ConAST con ) {
      for( String s : CMPS ) {
        if( con._con.endsWith("."+s) ) {
          // Convert CLZ.equals    (class,x0,x1)
          // to      CLZ.equals$CLZ(class,x0,x1)
          String clz = con._con.substring(0,con._con.length()-s.length()-1);
          con._con += "$"+clz;
          return this;
        }
      }
    }
    return this;
  }
  
  @Override void jmid ( SB sb, int i ) {
    sb.p( i==0 ? (_kids[0] instanceof RegAST ? ".call(": "(") : ", " );
  }
  @Override void jpost( SB sb ) {
    if( _kids.length > 1 )
      sb.unchar(2);
    sb.p(")");
  }
}
