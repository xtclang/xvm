package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.cons.AnnotTCon;
import org.xvm.cc_explore.cons.TermTCon;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.util.SB;

class RegAST extends AST {
  final String _type, _name, _init;  
  RegAST( XClzBuilder X, boolean named, boolean initd ) {
    super(X, 0);
    if( initd ) {
      // Destination is read first and is typeaware, so read the destination type.
      AnnotTCon anno = (AnnotTCon)X.methcon_ast();
      // TODO: Handle other kinds of typed args
      TermTCon ttc = anno.con().is_generic();
      if( ttc==null ) throw XEC.TODO();
      _init = X.jvalue_ttcon(ttc);
    } else {
      _init = null;
    }
    _type = X.jtype_methcon_ast();
    _name = named ? X.jname_methcon_ast() : null;
    if( named )
      X.define(_name);
  }
  @Override void jpre ( SB sb ) {
    sb.ip(_type).p(" ").p(_name);
    if( _init != null ) sb.p(" = ").p(_init);
    sb.p(";").nl();    
  }
}
