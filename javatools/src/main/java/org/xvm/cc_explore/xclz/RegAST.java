package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.cons.TCon;
import org.xvm.cc_explore.util.SB;

// Always replaced before writing out.
// E.g. XTC encoded a default arg (-4) for a call.
// Since Java has no defaults, explicitly replace.
class RegAST extends AST {
  final int _reg;
  final String _name;
  RegAST( XClzBuilder X, int reg ) {
    super(null, 0);
    assert reg < 0 || reg < X._nlocals;
    _reg = reg;
    _name = reg < 0 ? null : X._locals.get(reg);
  }
  @Override void jpre ( SB sb ) { sb.p(_name); }
}
