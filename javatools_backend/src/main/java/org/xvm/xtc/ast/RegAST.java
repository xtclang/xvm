package org.xvm.xtc.ast;

import org.xvm.xtc.ClassPart;
import org.xvm.xtc.XType;
import org.xvm.xtc.ClzBuilder;
import org.xvm.util.SB;

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
  RegAST( int reg, ClzBuilder X ) {
    super(null);
    _reg  = reg ;
    _name = switch( reg ) {
    case -4 ->  "default";  // A_DEFAULT
    case -5 ->  "this";     // A_THIS
    case -10 -> "this";     // A_STRUCT: this as a struct
    default -> X._locals.at(reg);
    };
    _type = switch( reg ) {
    case -4 ->  XType.VOID;  // A_DEFAULT
    case -5 ->  X._tclz;     // A_THIS
    case -10 -> XType.Clz.make((ClassPart)X._meth._par._par); // A_STRUCT
    default -> X._ltypes.at(reg);
    };    
    assert _type!=null;
  }
  @Override String name() { return _name; } // Can be null for 'this'?
  @Override XType _type() { return _type; }
  @Override void jpre ( SB sb ) { sb.p(_name); }
}
