package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.util.SB;

class NamedRegAST extends AST {
  
  NamedRegAST( CPool X ) {
    super(X, 0);
    Const t = X.xget();
    Const n = X.xget();
  }
  @Override void jpre ( SB sb ) {  throw XEC.TODO();  }
  @Override void jpost( SB sb ) {  throw XEC.TODO();  }
}

