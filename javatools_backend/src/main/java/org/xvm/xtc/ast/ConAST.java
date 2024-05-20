package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.*;

public class ConAST extends AST {
  public final TCon _tcon;
  public String _con;
  private final ClzBuilder _X;
  ConAST( ClzBuilder X, Const con ) { this(X,(TCon)con, XValue.val(X,con), XType.xtype(con,false)); }
  ConAST( String con ) { this(null,null,con, XBase.make(con,false)); }
  ConAST( String con, XType type ) { this(null,null,con,type); }
  ConAST( ClzBuilder X, TCon tcon, String con, XType type ) {
    super(null);
    _tcon = tcon;
    _con  = con.intern();
    _type = type;
    if( _tcon instanceof IntCon itc && XCons.format_iprims(itc._f) )
      _type = XCons.LONG;       // Can be Java long
    _X = X;
  }

  @Override public AST unBox() {
    if( _con.endsWith(".GOLD") )
      return null;              // No unboxing gold instances
    return super.unBox();
  }

  @Override public AST rewrite() {
    // Embedded Lambda
    if( _con.equals("->") ) {

      MethodPart lam = (MethodPart) _tcon.part();
      // A builder for the lambda method
      ClzBuilder X2 = new ClzBuilder(_X,null);
      // All the args from the current scope visible in the lambda body, as
      // hidden extra arguments
      int nargs = lam._args.length;
      for( int i=0; i<nargs; i++ ) {
        String aname = lam._args[i]._name;
        int idx = _X._locals.find(aname);
        XType atype = idx >= 0
          ? _X._ltypes.at(idx)  // Name exists in out scope, use that type
          : XType.xtype(lam._args[i].tcon(),false);
        X2.define(aname,atype);
      }

      // Build the lambda AST body
      AST body = X2.ast(lam);
      // Shortcut: if the body is a Block of a Return of 1 return value,
      // skip the block.
      if( body instanceof BlockAST && body._kids.length==1 &&
          body._kids[0] instanceof ReturnAST ret && ret._kids!=null && ret._kids.length==1 )
        body = ret._kids[0];
      body._par = null;

      // If the parent started as a BindFunc, the BindFunc will print the
      // lambda header.  If there are no extra args, BAST skips the BindFunc,
      // but we still need a header
      if( !(_par instanceof BindFuncAST && _par._kids[0]==this) )
        body = new BindFuncAST(body,lam).doType();

      // Swap out the method constant for the AST body
      return body;
    }

    return null;
  }
  @Override XType _type() { return _type; }
  @Override public SB jcode( SB sb ) { return sb.p(_con); }
}
