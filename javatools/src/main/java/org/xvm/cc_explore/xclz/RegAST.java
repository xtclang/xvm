package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;

// Always replaced before writing out.
// E.g. XTC encoded a default arg (-4) for a call.
// Since Java has no defaults, explicitly replace.
class RegAST extends AST {
  final int _reg;
  final String _name;
  RegAST( int reg, String name ) {
    super(null);
    _reg = reg;
    _name = name;
  }
  @Override void jpre ( SB sb ) { sb.p(_name); }
}
