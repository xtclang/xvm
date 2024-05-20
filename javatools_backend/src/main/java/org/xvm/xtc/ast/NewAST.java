package org.xvm.xtc.ast;

import org.xvm.xtc.*;
import org.xvm.xtc.cons.*;
import org.xvm.util.SB;
import org.xvm.util.S;

import java.util.Arrays;

class NewAST extends AST {
  final MethodPart _meth;
  final boolean _isChild;
  static NewAST make( ClzBuilder X, boolean isChild ) {
    AST outer = isChild ? ast_term(X) : null;
    Const type = X.con();
    MethodPart meth = (MethodPart)X.con().part();
    AST[] kids = X.kids();
    if( isChild ) {
      kids = Arrays.copyOf(kids,kids.length+1);
      kids[kids.length-1] = outer;
    }
    return new NewAST(kids,(XClz)XType.xtype(type,true),X,type,meth,isChild);
  }
  // For internal constructors like auto-boxing
  NewAST( AST[] kids, XClz xt ) {
    this(kids,xt,null,null,null,false);
  }
  NewAST( AST[] kids, XClz xt, ClzBuilder X, Const type, MethodPart meth, boolean isChild ) {
    super(kids_plus_clz(kids,xt,X,type));
    _isChild = isChild;
    _type = xt;
    _meth = meth;
    if( xt.needs_import(true) )
      ClzBuilder.add_import(xt);
  }

  // Basically, when passing types-as-values, they are hidden in the ParamTCon
  // "as-if" they are types... but their actually values in registers.  I have
  // to special case picking out the register from the ParamTCon and call out a
  // "new RegAST".
  private static AST[] kids_plus_clz(AST[] kids, XClz xt, ClzBuilder X, Const type) {
    // See if there are any type parameters needing adding
    if( xt.noTypeParms(null,false) || type==null )
      return kids;

    // Type parameters can be constants or can be function arguments passed in.
    // Function argument names are hidden in the ParamTCon.
    ParamTCon ptc = type instanceof ParamTCon ptc0 ? ptc0 : (ParamTCon)((VirtDepTCon)type)._par;
    int N = xt._xts.length;
    assert ptc._parms.length==N;
    AST[] kids2 = new AST[(kids==null ? 0 : kids.length)+N];
    if( kids!=null ) System.arraycopy(kids,0,kids2,N,kids.length);
    for( int i=0; i<N; i++ ) {
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

  @Override public AST rewrite() {
    // Array of XTC requires reflection; type in _kids[0]
    if( _type == XCons.ARRAY ) {
      // Array needs reflection
      AST[] kids = new AST[_kids.length+1];
      kids[0] = new ConAST("Array");
      System.arraycopy(_kids,0,kids,1,_kids.length);
      return new InvokeAST("$new",_type,kids);
    }
    return null;
  }

  @Override XType reBox( AST kid ) {
    if( _meth==null ) return null; // Internal made News always good
    int idx = S.find(_kids,kid);
    return _meth.xarg(idx);
  }


  @Override void jpre ( SB sb ) { sb.p(((XClz)_type).clz_bare()).p(".construct("); }
  @Override void jmid ( SB sb, int i ) { sb.p(", "); }
  @Override void jpost( SB sb ) {
    if( _kids!=null )  sb.unchar(2);
    sb.p(")");
  }
}
