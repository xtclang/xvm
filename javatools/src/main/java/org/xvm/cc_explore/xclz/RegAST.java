package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;

// Always replaced before writing out.
// E.g. XTC encoded a default arg (-4) for a call.
// Since Java has no defaults, explicitly replace.
class RegAST extends AST {
  final int _reg;
  final String _name;
  RegAST( int reg, String name, XType type ) {
    super(null);
    _reg  = reg ;
    _name = name;
    _type = type;
  }
  RegAST( int reg, XClzBuilder X ) {
    super(null);
    _reg  = reg ;
    _name = switch( reg ) {
    case -4 ->  "default";  // A_DEFAULT
    case -5 ->  "this";     // A_THIS
    case -10 -> "this";     // A_STRUCT: this as a struct
    default -> X._locals.get(reg);
    };
    _type = switch( reg ) {
    case -4 ->  XType.VOID;  // A_DEFAULT
    case -5 ->  XClzBuilder.MOD_TYPE;                     // A_THIS
    case -10 -> XType.Base.make(X._meth._par._par._name); // A_STRUCT
    default -> X._ltypes.get(reg);
    };
    
  }
  @Override String name() { return _name; }
  @Override XType _type() { return _type; }
  @Override void jpre ( SB sb ) { sb.p(_name); }
}
