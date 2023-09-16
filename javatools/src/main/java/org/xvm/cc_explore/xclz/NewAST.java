package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.MethodPart;
import org.xvm.cc_explore.cons.MethodCon;
import org.xvm.cc_explore.cons.TCon;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.util.SB;

class NewAST extends AST {
  MethodPart _meth;             // TODO: Constructor is Java is totally determined by argument types.  
  String _type;
  NewAST( XClzBuilder X, Const typecon, Const methcon ) {
    super(X, X.u31(), true);
    _type = XClzBuilder.jtype_tcon((TCon)typecon,false);
    _meth = (MethodPart)((MethodCon)methcon).part();
  }
  @Override void jpre ( SB sb ) {
    sb.p("new ").p(_type).p("(");
  }
  @Override void jmid ( SB sb, int i ) {
    sb.p(", ");
  }
  @Override void jpost( SB sb ) {
    if( _kids!=null )
      sb.unchar(2);
    sb.p(")");
  }
}
