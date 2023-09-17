package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.MethodPart;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.cons.MethodCon;
import org.xvm.cc_explore.util.SB;

class InvokeAST extends AST {
  final String _meth, _target, _type;
  final int _rnum;
  InvokeAST( XClzBuilder X, Const[] retTypes ) {
    super(X, X.u31(), false);
    if( _kids!=null )
      for( int i=0; i<_kids.length; i++ )
        _kids[i] = ast_term(X);
    // Calling target.method(args)
    MethodPart meth = (MethodPart)((MethodCon)X.methcon_ast()).part();
    _meth = meth._name;
    _rnum = X.u31() - 32;       // Register number for the LHS
    _target = X._locals.get(_rnum);
    _type   = X._ltypes.get(_rnum);
    
    // Replace default args with their actual default values
    if( _kids!=null )
      for( int i=0; i<_kids.length; i++ ) {
        if( _kids[i] instanceof RegAST reg ) {
          assert reg._reg == -4;  // Default
          // Swap in the default
          _kids[i] = new ConAST(X,meth._args[i]._def);
        }
      }
  }
  
  InvokeAST( String meth, String target, String type, int rnum, AST kid ) {
    super(null,1,false);
    _meth = meth;
    _type = type;
    _rnum = rnum;
    _target=target;
    _kids[0] = kid;
  }
  
  @Override AST rewrite() {
    // Cannot invoke directly on java primitives
    if( _type.equals("long") && _meth.equals("toString") )
      return new InvokeAST(_meth,"Long","Long",_rnum,new RegAST(_rnum,_target));
    return this;
  }
  @Override void jpre ( SB sb ) { sb.p(_target).p('.').p(_meth).p("("); }
  @Override void jmid ( SB sb, int i ) { sb.p(", "); }
  @Override void jpost( SB sb ) {
    if( _kids!=null ) sb.unchar(2);
    sb.p(")");
  }
}
