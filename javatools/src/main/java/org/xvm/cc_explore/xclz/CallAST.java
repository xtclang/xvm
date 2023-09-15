package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.MethodPart;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.cons.MethodCon;
import org.xvm.cc_explore.util.SB;

class CallAST extends AST {
  final Const[] _retTypes;
  CallAST( XClzBuilder X, Const[] retTypes ) {
    super(X, X.u31()+1, false);
    _retTypes = retTypes;
    for( int i=1; i<_kids.length; i++ )
      _kids[i] = ast_term(X);
    _kids[0] = ast_term(X);
  }
  @Override void jpre ( SB sb ) { }
  @Override void jmid ( SB sb, int i ) { sb.p( i==0 ? "(" : ", " ); }
  @Override void jpost( SB sb ) {
    if( _kids.length > 1 )
      sb.unchar(2);
    sb.p(")");
  }
}
