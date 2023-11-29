package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.Const;
import org.xvm.xtc.cons.MethodCon;
import org.xvm.util.SB;

class InvokeAST extends AST {
  final String _meth;
  final XType[] _rets;

  static InvokeAST make( ClzBuilder X ) {
    Const[] retTypes = X.consts(); // Return types
    AST[] kids = X.kids_bias(1);   // Call arguments
    Const methcon = X.con();       // Method constant, name
    kids[0] = ast_term(X);         // Method expression in kids[0]
    return new InvokeAST( kids,retTypes,methcon);
  }
  
  private InvokeAST( AST[] kids, Const[] retTypes, Const methcon ) {
    super(kids);
    // Calling target.method(args)
    MethodPart meth = (MethodPart)((MethodCon)methcon).part();
    _meth = meth.jname();
    // Returns
    _rets = XType.xtypes(retTypes);
    
    // Replace default args with their actual default values
    for( int i=1; i<_kids.length; i++ ) {
      if( _kids[i] instanceof RegAST reg &&
          reg._reg == -4/*Op.A_DEFAULT*/ ) {    // Default reg
        // Swap in the default from method defaults
        _kids[i] = new ConAST(meth._args[i-1]._def);
      }
    }
  }
  
  InvokeAST( String meth, XType ret, AST... kids ) {
    this(meth, ret==null ? null : new XType[]{ret}, kids);
  }
  InvokeAST( String meth, XType[] rets, AST... kids ) {
    super(kids);
    _meth = meth;
    _rets = rets;
    _type = _type();
  }

  @Override XType _type() {
    if( _rets==null || _rets.length==0 ) return XType.VOID;
    if( _rets.length == 1 ) return _rets[0];
    if( _rets.length == 2 && _rets[0]==XType.BOOL )
      return _rets[1];          // Conditional
    throw XEC.TODO();
  }
  @Override boolean _cond() {
    if( "TRACE".equals(_meth) ) return _kids[1]._cond; // TRACE passes condition thru
    return _rets!=null && _rets.length == 2 && _rets[0]==XType.BOOL;
  }
  
  @Override AST rewrite() {
    if( _kids[0]._type == XType.JLONG ) {
      if( _meth.equals("toString") )
        return _kids[0] instanceof ConAST ? this : new InvokeAST(_meth,XType.STRING,new ConAST("Long"),_kids[1]).do_type();
      if( _meth.equals("toInt64") ) // Cast long to a Long
        return _kids[0];            // Autoboxing in Java
      if( _meth.equals("valueOf") )
        return this;
      // Actually needs a cast
      throw XEC.TODO();
    }
    if( _kids[0]._type == XType.STRING ) {
      if( _meth.equals("toCharArray") )
        return new NewAST(_kids,XType.ARYCHAR,null);
      if( _meth.equals("equals") )
        return this;
      // Invert the call for String; FROM "abc".appendTo(sb) TO sb.appendTo("abc")
      if( _meth.equals("appendTo") ) {
        AST tmp = _kids[0]; _kids[0] = _kids[1]; _kids[1] = tmp;
        return this;
      }
      throw XEC.TODO();
    }
    
    if( !(_kids[0]._type instanceof XType.Base jt) ) return this;
    // Cannot invoke directly on java primitives
    switch( jt._jtype ) {

    case "long": {
      if( _meth.equals("toString") )
        return new InvokeAST(_meth,XType.STRING,new ConAST("Long"),_kids[0]).do_type();
      if( _meth.equals("toInt64") ) // Cast long to a Long
        return _kids[0];            // Autoboxing in Java
      // Invert the call for String; FROM 123L.appendTo(sb) TO sb.appendTo(123L)
      if( _meth.equals("appendTo") ) {
        AST tmp = _kids[0]; _kids[0] = _kids[1]; _kids[1] = tmp;
        return this;
      }
      // Actually needs a cast
      throw XEC.TODO();
    }

    case "char":
    case "Character":
      if( _meth.equals("asciiDigit") )
        return new InvokeAST(_meth,XType.COND_CHAR,new ConAST("XRuntime"),_kids[0]).do_type();
      throw XEC.TODO();      
      
    default:
      return this;
    }
  }
  @Override void jmid ( SB sb, int i ) {
    if( i==0 ) sb.p('.').p(_meth).p("(");
    else sb.p(", ");
  }
  @Override void jpost( SB sb ) {
    if( _kids.length>1 && _kids[1]!=null ) sb.unchar(2);
    sb.p(")");
  }
}
