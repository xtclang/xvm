package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;

// Always replaced before writing out.
// E.g. XTC encoded a default arg (-4) for a call.
// Since Java has no defaults, explicitly replace.
class RegAST extends AST {
  final int _reg;
  final String _name, _type;
  RegAST( int reg, String name, String type ) {
    super(null);
    _reg  = reg ;
    _name = name;
    _type = type;
  }
  RegAST( int reg, XClzBuilder X ) {
    this(reg,X._locals.get(reg),X._ltypes.get(reg));
  }
  RegAST( int reg ) { this(reg,null,null); }
  @Override String type() { return _type; }
  @Override void jpre ( SB sb ) { sb.p(_name); }
}
