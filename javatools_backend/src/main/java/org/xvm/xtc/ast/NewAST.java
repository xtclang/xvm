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
  static NewAST make( ClzBuilder X, boolean isChild ) {
    AST outer = isChild ? ast_term(X) : null;

    Const type = X.con();
    XClz xt = (XClz)XType.xtype(type,true);

    MethodPart meth = (MethodPart)X.con().part();
    assert (meth.clz().isNestedInnerClass()!=null) == isChild;

    // Argument orders:
    // - 0 or 1 isChild outer class arg
    // - N type args
    // - L external lambda args; in meth._args, never defaults, not in kids
    // - M method args passed; already in both kids and meth._args, minus any unknown defaults from kids

    // Outer class in slot 0 always.  Args start in slot 1.
    int typeBias = isChild ? 1 : 0;

    // See if there are any type parameters needing adding.
    // Java mirrors have type arg baked in already
    int nTypes = xt.printsTypeParm() ? xt.localLen() : 0;
    int argBias = typeBias + nTypes;

    // Parse arg trees
    AST[] kids = X.kids_bias(argBias);
    // Fill in child
    if( isChild )
      kids[0] = outer;
    // Fill in types
    if( nTypes > 0 )
      addTypeArgs(kids,xt,X,type,typeBias);

    // Replace default args with their actual default values
    if( meth._args != null )
      for( int i=0; i<meth._args.length; i++ ) {
        if( kids!=null && i+argBias < kids.length ) {
          if( kids[i+argBias] instanceof RegAST reg &&
              reg._reg == -4/*Op.A_DEFAULT*/ ) {    // Default reg
            // Swap in the default from method defaults
            TCon con = meth._args[i]._def;
            kids[i+argBias] = con==null
              ? new ConAST("0",meth.xfun().arg(i))
              : new ConAST(null,con);
          }
        } else {
          // Missing default arg, only allowed 1 on the end
          if( kids==null ) kids = new AST[1];
          else kids = Arrays.copyOf(kids,kids.length+1);
          kids[kids.length-1] = new ConAST(null,meth._args[meth._args.length-1]._def);
        }
      }

    return new NewAST(kids,xt);
  }

  // Normal constructor, also used for e.g. auto-boxing
  NewAST( AST[] kids, XClz xt ) {
    super(kids);
    _type = xt;
    if( xt.needs_import(true) )
      ClzBuilder.add_import(xt);
  }

  // Basically, when passing types-as-values, they are hidden in the ParamTCon
  // "as-if" they are types... but they're actually values in registers.  I have
  // to special case picking out the register from the ParamTCon and call out a
  // "new RegAST".
  private static void addTypeArgs(AST[] kids, XClz xt, ClzBuilder X, Const type, int typeBias) {

    // Type parameters can be constants or can be function arguments passed in.
    // Function argument names are hidden in the ParamTCon.
    ParamTCon ptc = type instanceof ParamTCon ptc0 ? ptc0 : (ParamTCon)((DepTCon)type)._par;
    for( int i=0; i<xt.len(); i++ ) {
      if( xt.at(i).local() ) {
        if( ptc._parms!=null && i < ptc._parms.length && ptc._parms[i] instanceof TermTCon ttc && ttc.part() instanceof ParmPart parm ) {
          // Type parameter comes from the method arguments.
          // Do a name lookup.
          int reg = X._locals.find(parm._name);
          kids[i+typeBias] = new RegAST(reg,parm._name,X._ltypes.at(reg));

        } else {
          // Type parameter is a constant; get the golden instance ".GOLD" from
          // the types boxed variant.
          XType gen = xt.xt(i);
          XClz box = gen.box();
          if( box!=null ) { ClzBuilder.add_import(box); gen=box; }
          kids[i+typeBias] = new ConAST(null,null,gen.clz()+".GOLD",gen);
        }
      }
    }
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
