package org.xvm.xtc.ast;

import org.xvm.xtc.*;
import org.xvm.xtc.cons.Const;
import org.xvm.xtc.cons.MethodCon;
import org.xvm.xtc.cons.ParamTCon;
import org.xvm.xtc.cons.TermTCon;
import org.xvm.util.SB;

class NewAST extends AST {
  final MethodPart _meth;
  static NewAST make( ClzBuilder X ) {
    return new NewAST(X, X.con(),(MethodCon)X.con(),X.kids());
  }
  private NewAST( ClzBuilder X, Const type, MethodCon meth, AST[] kids ) {
    this(kids,(XClz)XType.xtype(type,true), X, type, meth);
  }
  NewAST( AST[] kids, XClz xt ) {
    this(kids,xt,null,null,null);
  }
  NewAST( AST[] kids, XClz xt, ClzBuilder X, Const type, MethodCon meth ) {
    super(kids_plus_clz(kids,xt,X,type));
    _type = xt;
    //_meth = meth==null ? null : (MethodPart)meth.part();
    _meth=null;
    if( xt.needs_import() )
      ClzBuilder.add_import(xt);
  }

  // Basically, when passing types-as-values, they are hidden in the ParamTCon
  // "as-if" they are types... but their actually values in registers.  I have
  // to special case picking out the register from the ParamTCon and call out a
  // "new RegAST".
  private static AST[] kids_plus_clz(AST[] kids, XClz xt, ClzBuilder X, Const type) {
    // See if there are any type parameters needing adding
    int nTypeParms = xt.nTypeParms();
    if( nTypeParms == 0 || !xt._jparms )
      return kids;

    // Type parameters can be constants or can be function arguments passed in.
    // Function argument names are hidden in the ParamTCon.
    ParamTCon ptc = (ParamTCon)type;
    assert ptc._parms.length==nTypeParms;
    AST[] kids2 = new AST[(kids==null ? 0 : kids.length)+nTypeParms];
    if( kids!=null ) System.arraycopy(kids,0,kids2,nTypeParms,kids.length);
    for( int i=0; i<nTypeParms; i++ ) {
      if( ptc._parms[i] instanceof TermTCon ttc && ttc.part() instanceof ParmPart parm ) {
        // Type parameter comes from the method arguments.
        // Do a name lookup.
        int reg = X._locals.find(parm._name);
        kids2[i] = new RegAST(reg,parm._name,X._ltypes.at(reg));
        
      } else {
        // Type parameter is a constant; get the golden instance ".GOLD" from
        // the types boxed variant.
        //assert ptc._parms[i] instanceof ParamTCon;
        XType gen = xt.typeParm(i);
        XClz box = gen.box();
        kids2[i] = new ConAST(null,null,(box==null ? gen : box).clz()+".GOLD",gen);
      }
    }
    return kids2;
  }
  
  @Override XType _type() { return _type; }

  @Override AST prewrite() {
    // Array of XTC requires reflection; type in _kids[0]
    if( _type == XCons.ARRAY ) {
      // Array needs reflection
      AST[] kids = new AST[_kids.length+1];
      kids[0] = new ConAST("Array");
      System.arraycopy(_kids,0,kids,1,_kids.length);
      return new InvokeAST("$new",_type,kids);
    }
    return this;
  }
  
  
  @Override void jpre ( SB sb ) { _type.clz(sb.p("new ")).p("("); }
  @Override void jmid ( SB sb, int i ) { sb.p(", "); }
  @Override void jpost( SB sb ) {    
    if( _kids!=null )  sb.unchar(2);
    sb.p(")");
  }
}
