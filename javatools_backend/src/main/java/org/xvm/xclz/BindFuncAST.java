package org.xvm.xclz;

import org.xvm.util.SB;
import org.xvm.MethodPart;
import org.xvm.cons.*;

class BindFuncAST extends AST {
  // Which arguments are being bound here.  This is basically reverse-currying
  // by any other name.
  final int[] _idxs;            // Which args are being bound
  final String[] _args;         // Remaining args

  static BindFuncAST make( XClzBuilder X ) {
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
    
  private BindFuncAST( XClzBuilder X, AST[] kids, int[] idxs, Const type ) {
    super(kids);
    _idxs = idxs;
    int nargs = kids.length-1;
    if( kids[0] instanceof ConAST con && con._con.equals("->") ) {
      // Embedded Lambda
      MethodPart lam = (MethodPart)((MethodCon)con._tcon).part();
      // A builder for the lambda method
      XClzBuilder X2 = new XClzBuilder(null);
      // All the args from the current scope visible in the lambda body, as
      // hidden extra arguments
      for( int i=0; i<nargs; i++ ) {
        String aname = lam._args[i]._name;
        XType atype = XType.xtype(lam._args[i]._con,false);
        X2.define(aname,atype);
      }
      // All the explicit lambda args
      _args =        new String[lam._args.length-nargs];
      XType[] xargs = new XType[lam._args.length-nargs];
      for( int i=nargs; i<lam._args.length; i++ ) {
        String name = lam._args[i]._name;
        XType atype = XType.xtype(lam._args[i]._con,false);
        _args[i-nargs] = name;
        xargs[i-nargs] = atype;
        X2.define(name,atype);
      }
      _type = XType.Fun.make(xargs,XType.xtypes(lam._rets));
      
      // Build the lambda AST body
      AST body = X2.ast(lam);
      // Shortcut: if the body is a Block of a Return of 1 return value,
      // skip the block.
      if( body instanceof BlockAST && body._kids.length==1 &&
          body._kids[0] instanceof ReturnAST ret && ret._kids!=null && ret._kids.length==1 )
        body = ret._kids[0];
      // Swap out the method constant for the AST body
      kids[0] = body;

      
      // Currying: pre-binding some method args
    } else {
      XType.Fun lam = (XType.Fun)kids[0]._type;
      assert nargs==idxs.length; // Every 
        
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
        ikids[i + 1] = j < nargs && idxs[j]==i
          ? kids[1 + j++] // The jth curried arg
          : new RegAST(i-j,_args[i-j] = "x"+i,lam._args[i]); // The ith arg e.g. "x0"

      // If kid0 is a BindMeth, then called as "expr.call(args)"
      // else                        called as "this.fun (args)"
      String fname = kids[0] instanceof BindMethAST ? kids[0].name()   : "call";
      ikids[0]     = kids[0] instanceof BindMethAST ? new RegAST(-5,X) : kids[0];
      // Update this BindFunc to just call with the curried arguments
      kids[0] = new InvokeAST(fname,lam._rets,ikids);
    } 
  }

  @Override XType _type() { return _type; } // Local lamdba type

  @Override SB jcode( SB sb ) {
    sb.p("( ");
    for( String arg : _args )
      sb.p(arg).p(",");
    sb.unchar(1).p(") -> ");
    AST body = _kids[0];
    body.jcode(sb);
    return sb;
  }
}
