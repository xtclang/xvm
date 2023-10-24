package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.MethodPart;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.XEC;

class BindFuncAST extends AST {
  // 0..<nargs - arg#N
  // nargs   - target
  final int[] _idxs;
  final String _type;
  final String[] _args;

  static BindFuncAST make( XClzBuilder X ) {
    AST target = ast_term(X);
    int nargs = X.u31();
    AST[] kids = new AST[nargs+1];
    int[] idxs = new int[nargs];
    for( int i=0; i<nargs; i++ ) {
      idxs[i] = X.u31();
      kids[i] = ast_term(X);
    }
    kids[nargs] = target;       // Target is last _kids
    Const type = X.con();
    return new BindFuncAST( X, kids, idxs, type );
  }
    
  private BindFuncAST( XClzBuilder X, AST[] kids, int[] idxs, Const type ) {
    super(kids);
    _idxs = idxs;
    _type = XClzBuilder.jtype(type,false);
    // Embedded Lambda
    int nargs = kids.length-1;
    if( _kids[nargs] instanceof ConAST con && con._con.equals("->") ) {
      MethodPart lam = (MethodPart)((MethodCon)con._tcon).part();
      // A builder for the lambda method
      XClzBuilder X2 = new XClzBuilder(new SB());
      // All the args from the current scope visible in the lambda body, as hidden extra arguments
      for( int i=0; i<nargs; i++ ) {
        RegAST reg = (RegAST)_kids[i];
        assert lam._args[i]._name.equals(reg._name);
        long idx = X.name2idx(reg._name);
        X2.define(reg._name,X._ltypes.get(idx));
      }
      // All the explicit lambda args
      _args = new String[lam._args.length-nargs];
      for( int i=nargs; i<lam._args.length; i++ ) {
        String name = lam._args[i]._name;
        String atype = XClzBuilder.jtype(lam._args[i]._con,false);
        _args[i-nargs] = name;
        X2.define(name,atype);
      }
      // Build the lambda AST body
      AST body = X2.ast(lam);
      // Shortcut: if the body is a Block of a Return of 1 return value,
      // skip the block.
      if( body instanceof BlockAST && body._kids.length==1 &&
          body._kids[0] instanceof ReturnAST ret && ret._kids!=null && ret._kids.length==1 )
        body = ret._kids[0];
      // Swap out the method constant for the AST body
      _kids[nargs] = body;
      
    } else
      throw XEC.TODO();
  }

  @Override String _type() { return "->"; } // Local lamdba type

  @Override SB jcode( SB sb ) {
    for( String arg : _args )
      sb.p(arg).p(" ");
    sb.p("-> ");
    AST body = _kids[_kids.length-1];
    body.jcode(sb);
    return sb;
  }
}
