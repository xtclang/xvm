package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.*;

public class ConAST extends AST {
  public final TCon _tcon;
  public String _con;
  private final ClzBuilder _X;
  ConAST( ClzBuilder X, Const con ) { this(X,(TCon)con, XValue.val(X,con), XType.xtype(con,false)); }
  ConAST( String con ) { this(null,null,con, XBase.make(con,false)); }
  ConAST( ClzBuilder X, TCon tcon, String con, XType type ) {
    super(null);
    _tcon = tcon;
    _con  = con.intern();
    _type = type;
    if( _tcon instanceof IntCon )
      _type = XCons.LONG;

    _X = X;
  }

  @Override public AST unBox() {
    if( _con.endsWith(".GOLD") )
      return this;              // No unboxing gold instances
    return super.unBox();
  }

  @Override public AST rewrite() {
    // Embedded Lambda
    if( _con.equals("->") ) {
      // If the parent started as a BindFunc, the BindFunc will print the
      // lambda header.  If there are no extra args, BAST skips the BindFunc,
      // but we still need a header
      if( !(_par instanceof BindFuncAST && _par._kids[0]==this) ) {
        BindFuncAST bind = new BindFuncAST(null,new AST[]{this},null,null);
        bind.doType();
        return bind;
      }

      MethodPart lam = (MethodPart) _tcon.part();
      // A builder for the lambda method
      ClzBuilder X2 = new ClzBuilder(_X,null);
      // All the args from the current scope visible in the lambda body, as
      // hidden extra arguments
      int nargs = lam._args.length;
      for( int i=0; i<nargs; i++ ) {
        String aname = lam._args[i]._name;
        XType atype = XType.xtype(lam._args[i].tcon(),false);
        X2.define(aname,atype);
      }

      // Build the lambda AST body
      AST body = X2.ast(lam);
      // Shortcut: if the body is a Block of a Return of 1 return value,
      // skip the block.
      if( body instanceof BlockAST && body._kids.length==1 &&
          body._kids[0] instanceof ReturnAST ret && ret._kids!=null && ret._kids.length==1 )
        body = ret._kids[0];

      // Swap out the method constant for the AST body
      return body;
    }

    return this;
  }
  @Override XType _type() { return _type; }
  @Override public SB jcode( SB sb ) { return sb.p(_con); }
}
