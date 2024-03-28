package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.S;
import org.xvm.xtc.cons.Const;
import org.xvm.xtc.cons.MethodCon;
import org.xvm.xtc.*;
import org.xvm.util.SB;

class BindFuncAST extends AST {
  // Which arguments are being bound here.  This is basically reverse-currying
  // by any other name.
  final int[] _idxs;            // Which args are being bound
  String[] _args;               // Remaining args
  private final ClzBuilder _X;
  private MethodPart _lam;  // Avoid double-rewrite on nested BindFunc/Con-> setups (nested lambda expressions)

  static BindFuncAST make( ClzBuilder X ) {
    AST target = ast_term(X);
    int nargs = X.u31();
    AST[] kids = new AST[nargs+1];
    kids[0] = target;       // Target is _kids[0]
    int[] idxs = new int[nargs];
    for( int i=0; i<nargs; i++ ) {
      idxs[i] = X.u31();
      kids[i+1] = ast_term(X);
    }
    Const type = X.con();
    return new BindFuncAST( X, kids, idxs, type );
  }

  BindFuncAST( ClzBuilder X, AST[] kids, int[] idxs, Const type ) {
    super(kids);
    _idxs = idxs;
    _X = X;
  }

  @Override XType _type() {
    int nargs = _kids.length-1;
    if( _kids[0] instanceof ConAST con && con._con.equals("->") ) {
      MethodPart lam = (MethodPart)(con._tcon).part();
      // All the explicit lambda args
      _args = new String[lam._args.length-nargs];
      XType[] xargs = new XType[lam._args.length-nargs];
      for( int i=nargs; i<lam._args.length; i++ ) {
        String name = lam._args[i]._name;
        XType atype = XType.xtype(lam._args[i].tcon(),false);
        _args[i-nargs] = name;
        xargs[i-nargs] = atype;
      }
      return XFun.make(xargs,XType.xtypes(lam._rets));

      // Currying: pre-binding some method args
    } else {
      assert nargs==_idxs.length; // Every
      return _kids[0]._type;
    }
  }

  @Override AST prewrite() {
    if( _kids[0] instanceof ConAST con && con._con.equals("->") )
      _lam = (MethodPart) con._tcon.part();
    if( _lam != null ) {
      // Check for other exposed names being effectively final
      for( int i=1; i<_kids.length; i++ )
        if( _kids[i] instanceof RegAST reg )
          make_effectively_final(reg,_lam);
      return this;
    }

    XFun lam = (XFun)_type;
    int nargs = _kids.length-1;
    // The idx[] args are pre-defined; the remaining args are passed along.
    // Example: foo( Int x, String s ) { ...body... }
    // The "1th" arg is predefined here, the 0th arg is passed along.
    //     foo2 = &foo(s="abc")   ===>>>
    //     foo2(long x) = x -> foo(x,"abc");
    //
    // All the explicit lambda args: all the args minus the given (curried) args
    _args = new String[lam.nargs()-nargs];

    // Recycle the kids array for the InvokeAST
    AST[] ikids = new AST[lam.nargs()+1];

    // Fill in the args, leaving slot 0 open
    int j=0;
    for( int i=0; i<lam.nargs(); i++ )
      ikids[i + 1] = j < nargs && _idxs[j]==i
        ? _kids[1 + j++] // The jth curried arg
        : new RegAST(i-j,_args[i-j] = "x"+i,lam.arg(i)); // The ith arg e.g. "x0"

    // If kid0 is a BindMeth, then called as "expr.call(args)"
    // else                        called as "this.fun (args)"
    String fname = _kids[0] instanceof BindMethAST ? _kids[0].name()   : "call";
    ikids[0]     = _kids[0] instanceof BindMethAST ? new RegAST(-5,_X) : _kids[0];
    // Update this BindFunc to just call with the curried arguments
    _kids[0] = new InvokeAST(fname,lam.rets(),ikids);
    return this;
  }

  // Make this register Java-effectively-final here
  private void make_effectively_final(RegAST reg, MethodPart lam) {
    if( isEF(_par,this,reg) ) return;
    // New final temp name
    String s = _par.enclosing_block().add_final(reg);
    // Change parameter name in inlined lambda
    for( Parameter p : lam._args )
      if( S.eq(p._name,reg._name) )
        { p._name = s; break; }
  }

  private boolean isEF(AST par, AST kid, RegAST reg) {
    if( par==null ) return true;
    int i=0; while( i< par._kids.length && par._kids[i] != kid )
      if( isRedefTree(par._kids[i++],reg) )
        return false;
    return isEF(par._par,par,reg);
  }

  private boolean isRedefTree(AST ast,RegAST reg) {
    if( ast instanceof DefRegAST def && def._reg==reg._reg ) {
      if( def._par instanceof ForRangeAST )
        return def._type instanceof XBase;
      // TODO: Not correct, need some kind of final-field indication
      if( def._par instanceof BlockAST && def._init!=null )
        return false;
      // Not a parent ForRange, need to look more
      throw XEC.TODO();
    }
    return false;
  }

  @Override public SB jcode( SB sb ) {
    sb.p("( ");
    if( _args != null )
      for( String arg : _args )
        sb.p(arg).p(",");
    sb.unchar(1).p(") -> ");
    AST body = _kids[0];
    body.jcode(sb);
    return sb;
  }
}
