package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.MethodPart;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.cons.MethodCon;
import org.xvm.cc_explore.util.SB;

class InvokeAST extends AST {
  final String _meth, _target, _type;
  final int _rnum;

  static InvokeAST make( XClzBuilder X ) {
    Const[] retTypes = X.consts(); // Return types
    AST[] kids = X.kids();         // Call arguments
    Const methcon = X.con();       // Method constant
    int rnum = X.u31() - 32;       // Register number for the LHS
    return new InvokeAST(X,kids,retTypes,methcon,rnum);
  }
  
  private InvokeAST( XClzBuilder X, AST[] kids, Const[] retTypes, Const methcon, int rnum ) {
    super(kids);
    // Calling target.method(args)
    MethodPart meth = (MethodPart)((MethodCon)methcon).part();
    _meth = meth._name;
    _rnum = rnum;       // Register number for the LHS
    _target = X._locals.get(rnum);
    _type   = X._ltypes.get(rnum);
    
    // Replace default args with their actual default values
    if( _kids!=null )
      for( int i=0; i<_kids.length; i++ ) {
        if( _kids[i] instanceof RegAST reg ) {
          assert reg._reg == -4;  // Default
          // Swap in the default
          _kids[i] = new ConAST(meth._args[i]._def);
        }
      }
  }
  
  InvokeAST( String meth, String target, String type, int rnum, AST kid ) {
    super(new AST[]{kid});
    _meth = meth;
    _type = type;
    _rnum = rnum;
    _target=target;
  }
  
  @Override AST rewrite() {
    // Cannot invoke directly on java primitives
    if( _type.equals("long") && _meth.equals("toString") )
      return new InvokeAST(_meth,"Long","Long",_rnum,new RegAST(_rnum,_target,"long"));
    return this;
  }
  @Override void jpre ( SB sb ) { sb.p(_target).p('.').p(_meth).p("("); }
  @Override void jmid ( SB sb, int i ) { sb.p(", "); }
  @Override void jpost( SB sb ) {
    if( _kids!=null ) sb.unchar(2);
    sb.p(")");
  }
}
