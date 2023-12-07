package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xtc.ClzBuilder;
import org.xvm.xtc.MethodPart;
import org.xvm.xtc.XType;
import org.xvm.xtc.XValue;
import org.xvm.xtc.cons.*;

public class ConAST extends AST {
  public final TCon _tcon;
  public String _con;
  private final ClzBuilder _X;
  ConAST( ClzBuilder X, Const con ) { this(X,(TCon)con, XValue.val(con), XType.xtype(con,false)); }
  ConAST( String con ) { this(null,null,con, XType.Base.make(con)); }
  ConAST( ClzBuilder X, TCon tcon, String con, XType type ) {
    super(null);
    _tcon = tcon;
    _con  = con ;
    _type = type;
    _X = X;
  }
  @Override AST rewrite() {
    
    if( _con.equals("->") ) {
      // Embedded Lambda
      MethodPart lam = (MethodPart)((MethodCon)_tcon).part();
      // A builder for the lambda method
      ClzBuilder X2 = new ClzBuilder(_X,null);
      // All the args from the current scope visible in the lambda body, as
      // hidden extra arguments
      int nargs = lam._args.length;
      for( int i=0; i<nargs; i++ ) {
        String aname = lam._args[i]._name;
        XType atype = XType.xtype(lam._args[i]._con,false);
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
