package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.MethodPart;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.cons.MethodCon;
import org.xvm.cc_explore.util.SB;

class InvokeAST extends AST {
  final String _target, _meth;
  InvokeAST( XClzBuilder X, Const[] retTypes ) {
    super(X, X.u31());
    if( retTypes != null ) 
      throw XEC.TODO();
    // Calling target.method(args)
    MethodPart meth = (MethodPart)((MethodCon)X.methcon_ast()).part();
    int target = X.u31() - 32;  // Register number for the LHS
    _target = X._locals.get(target);
    _meth = meth._name;
    // Replace default args with their actual default values
    for( int i=0; i<_kids.length; i++ )
      if( _kids[i] instanceof RegAST reg ) {
        assert reg._reg == -4;  // Default
        // Swap in the default
        _kids[i] = new ConAST(X,meth._args[i]._def);
      }
  }
  @Override void jpre ( SB sb ) { sb.ip(_target).p(".").p(_meth).p("("); }
  @Override void jmid ( SB sb, int i ) { sb.p(", "); }
  @Override void jpost( SB sb ) { sb.unchar(2).p(");").nl(); }
}
