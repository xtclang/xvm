package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;

class BindFuncAST extends AST {
  // 0..<nargs - arg#N
  // nargs   - target
  final int[] _idxs;
  final String _type;
  BindFuncAST( XClzBuilder X, AST target, int nargs ) {
    super(X, nargs+1, false);
    _idxs = new int[nargs];
    for( int i=0; i<nargs; i++ ) {
      _idxs[i] = X.u31();
      _kids[i] = ast_term(X);
    }
    _kids[nargs] = target;
    _type = X.jtype_methcon_ast();
    
  }
  @Override void jpre( SB sb ) { throw XEC.TODO(); }
}
