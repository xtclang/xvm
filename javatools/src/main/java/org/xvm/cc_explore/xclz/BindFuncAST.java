package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.cons.TCon;
import org.xvm.cc_explore.XEC;

class BindFuncAST extends AST {
  // 0..<nargs - arg#N
  // nargs   - target
  final int[] _idxs;
  final String _type;

  static BindFuncAST make( XClzBuilder X ) {
    AST target = ast_term(X);
    int nargs = X.u31();
    AST[] kids = new AST[nargs+1];
    int[] idxs = new int[nargs];
    for( int i=0; i<nargs; i++ ) {
      idxs[i] = X.u31();
      kids[i] = ast_term(X);
    }
    kids[nargs] = target;
    Const type = X.con(X.u31());
    return new BindFuncAST( X, kids, idxs, type );
  }
    
  private BindFuncAST( XClzBuilder X, AST[] kids, int[] idxs, Const type ) {
    super(kids);
    _idxs = idxs;
    _type = XClzBuilder.jtype_tcon((TCon)type,false);    
  }
  @Override SB jcode( SB sb ) {
    throw XEC.TODO();
  }
}
