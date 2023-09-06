package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.util.SB;

class RegAST extends AST {

  final String _type, _name;
  
  RegAST( XClzBuilder X, boolean named ) {
    super(X, 0);
    _type = X.jtype_methcon_ast();
    _name = named ? X.jname_methcon_ast() : null;
  }
  @Override void jpre ( SB sb ) {
    sb.ip(_type).p(" ").p(_name).p(";").nl();
  }
  @Override void jpost( SB sb ) { }
}

