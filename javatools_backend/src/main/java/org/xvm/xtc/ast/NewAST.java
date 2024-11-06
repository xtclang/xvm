package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.*;
import org.xvm.util.SB;
import org.xvm.util.S;

import java.util.Arrays;


// New objects.
// The constructor gets some explicit arguments.
// If this is a nested inner class, it also gets the outer class.
// If this class has some type arguments, it also gets explicit type objects.
class NewAST extends AST {
  final MethodPart _meth;
  static NewAST make( ClzBuilder X, boolean isChild ) {
    AST outer = isChild ? ast_term(X) : null;
    Const type = X.con();
    MethodPart meth = (MethodPart)X.con().part();
    assert (meth.clz().isNestedInnerClass()!=null) == isChild;
    AST[] kids = X.kids_bias(isChild ? 1 : 0);
    if( isChild )
      kids[0] = outer;
    return new NewAST(kids,(XClz)XType.xtype(type,true),X,type,meth,isChild);
  }
  // For internal constructors like auto-boxing
  NewAST( AST[] kids, XClz xt ) {
    this(kids,xt,null,null,null,false);
  }
  NewAST( AST[] kids, XClz xt, ClzBuilder X, Const type, MethodPart meth, boolean isChild ) {
    super(kids_plus_clz(kids,xt,X,type));
    _type = xt;
    _meth = meth;

    // Replace default args with their actual default values
    if( _kids != null )
      for( int i=1; i<_kids.length; i++ )
        if( _kids[i] instanceof RegAST reg &&
            reg._reg == -4/*Op.A_DEFAULT*/ ) {    // Default reg
          // Swap in the default from method defaults
          TCon con = meth._args[i-1]._def;
          _kids[i] = con==null
            ? new ConAST("0",meth.xfun().arg(i))
            : new ConAST(null,con);
          }

    if( meth!=null && kids!=null && (meth._args==null?0:meth._args.length)+(isChild?1:0) != kids.length ) {
      int len = kids.length;
      assert len+1==meth._args.length; // more default args
      _kids = Arrays.copyOf(_kids,meth._args.length);
      _kids[len] = new ConAST(null,meth._args[len]._def);
    }
    if( xt.needs_import(true) )
      ClzBuilder.add_import(xt);
  }

  // Basically, when passing types-as-values, they are hidden in the ParamTCon
  // "as-if" they are types... but their actually values in registers.  I have
  // to special case picking out the register from the ParamTCon and call out a
  // "new RegAST".
  private static AST[] kids_plus_clz(AST[] kids, XClz xt, ClzBuilder X, Const type) {
    // See if there are any type parameters needing adding
    int N = xt._tns.length;
    if( N==0 || type==null ) return kids;
    if( !xt.printsTypeParm() ) return kids; // Java mirrors have type arg baked in already

    // Type parameters can be constants or can be function arguments passed in.
    // Function argument names are hidden in the ParamTCon.
    ParamTCon ptc = type instanceof ParamTCon ptc0 ? ptc0 : (ParamTCon)((DepTCon)type)._par;
    AST[] kids2 = new AST[(kids==null ? 0 : kids.length)+N];
    // Slide normal args over to make room for N type args
    if( kids!=null ) System.arraycopy(kids,0,kids2,N,kids.length);
    for( int i=0; i<N; i++ ) {
      if( ptc._parms!=null && i < ptc._parms.length && ptc._parms[i] instanceof TermTCon ttc && ttc.part() instanceof ParmPart parm ) {
        // Type parameter comes from the method arguments.
        // Do a name lookup.
        int reg = X._locals.find(parm._name);
        kids2[i] = new RegAST(reg,parm._name,X._ltypes.at(reg));

      } else {
        // Type parameter is a constant; get the golden instance ".GOLD" from
        // the types boxed variant.
        XType gen = xt._xts[i];
        XClz box = gen.box();
        if( box!=null ) ClzBuilder.add_import(box);
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

  @Override void jpre ( SB sb ) { _type.clz_bare(sb).p(".construct("); }
  @Override void jmid ( SB sb, int i ) { sb.p(", "); }
  @Override void jpost( SB sb ) {
    if( _kids!=null )  sb.unchar(2);
    sb.p(")");
  }
}
