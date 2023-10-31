package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.MethodPart;
import org.xvm.cc_explore.Parameter;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.XEC;
import java.util.Arrays;

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
      XClzBuilder X2 = new XClzBuilder(X._mod,null);
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
      _type = XType.JFunType.make(xargs,XType.xtypes(lam._rets));
      
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
      XType.JFunType lam = (XType.JFunType)_kids[0]._type;
      int lamnargs = lam.nargs();
        
      // The idx[] args are pre-defined; the remaining args are passed along.
      // Example: foo( Int x, String s ) { ...body... }
      //     &foo(s="abc")   ===>>>
      //      foo2(long x) { return foo(x,"abc"); }  OR
      //                       x -> foo(x,"abc")
      // 
      // A builder for the lambda method
      XClzBuilder X2 = new XClzBuilder(X._mod,null);
      // All the explicit lambda args
      _args = new String[lamnargs-nargs];
      
      // Recycle the _kids array for the InvokeAST
      AST[] ikids = new AST[lamnargs+1];
      ikids[0] = new RegAST(-5,X);
      
      // Fill in the args
      int j=0;
      for( int i=0; i<lamnargs; i++ ) {
        if( idxs[j]==i ) {
          ikids[i+1] = kids[1+j++];
        } else {
          String name = "TODO";//lam._args[i]._name;
          XType atype = lam._args[i];
          X2.define(name,atype);
          _args[X2._nlocals-1] = name;
          ikids[i+1] = new RegAST(X2._nlocals-1,X2);
        }
      }

      // Returns
      Parameter[] prets = null; // bind._meth._rets;
      XType[] rets = XType.xtypes(prets);

      kids[0] = new InvokeAST("TODO"/*bind._meth._name*/,rets,ikids);
    } 
  }

  @Override XType _type() { return _type; } // Local lamdba type

  @Override SB jcode( SB sb ) {
    sb.p("(");
    for( String arg : _args )
      sb.p(arg).p(",");
    sb.unchar(1).p(") -> ");
    AST body = _kids[0];
    body.jcode(sb);
    return sb;
  }
}
