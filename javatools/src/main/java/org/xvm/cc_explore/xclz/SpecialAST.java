package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.cons.TCon;
import org.xvm.cc_explore.util.SB;

// Always replaced before writing out.
// E.g. XTC encoded a default arg (-4) for a call.
// Since Java has no defaults, explicitly replace.
class SpecialAST extends AST {
  final int _reg;
  SpecialAST( int reg ) { super(null, 0); _reg = reg;  }
  @Override void jpre ( SB sb ) { throw XEC.TODO(); }
  @Override void jpost( SB sb ) { }
}
