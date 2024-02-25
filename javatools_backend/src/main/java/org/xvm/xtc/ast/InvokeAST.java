package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.Const;
import org.xvm.xtc.cons.MethodCon;
import org.xvm.util.S;
import org.xvm.util.SB;

public class InvokeAST extends AST {
  String _meth;
  final boolean _async;
  final XType[] _args;
  final XType[] _rets;

  static InvokeAST make( ClzBuilder X, boolean async ) {
    Const[] retTypes = X.consts(); // Return types
    AST[] kids = X.kids_bias(1);   // Call arguments
    Const methcon = X.con();       // Method constant, name
    kids[0] = ast_term(X);         // Method expression in kids[0]
    return new InvokeAST( kids,retTypes,methcon,async);
  }
  
  private InvokeAST( AST[] kids, Const[] retTypes, Const methcon, boolean async ) {
    super(kids);
    // Calling target.method(args)
    MethodPart meth = (MethodPart)((MethodCon)methcon).part();
    _meth = meth.jname();
    _async = async;
    if( meth._args != null && meth._xargs==null )   meth._xargs = XType.xtypes(meth._args);
    if( meth._rets != null && meth._xrets==null )   meth._xrets = XType.xtypes(retTypes);
    _args = meth._xargs;
    _rets = meth._xrets;
    
    // Replace default args with their actual default values
    for( int i=1; i<_kids.length; i++ ) {
      if( _kids[i] instanceof RegAST reg &&
          reg._reg == -4/*Op.A_DEFAULT*/ ) {    // Default reg
        // Swap in the default from method defaults
        _kids[i] = new ConAST(null,meth._args[i-1]._def);
      }
    }
  }
  
  InvokeAST( String meth, XType ret, AST... kids ) {
    this(meth, ret==null ? null : new XType[]{ret}, kids);
  }
  public InvokeAST( String meth, XType[] rets, AST... kids ) {
    super(kids);
    _meth = meth;
    _async = false;
    _args = null;
    _rets = rets;
    _type = _type();
  }

  @Override XType _type() {
    if( _rets==null || _rets.length==0 ) return XCons.VOID;
    if( _rets.length == 1 ) return _rets[0];
    if( _rets.length == 2 && (_rets[0]==XCons.BOOL || _rets[0]==XCons.JBOOL) )
      return _rets[1];          // Conditional
    return XClz.make_tuple(_rets);
  }
  @Override boolean _cond() {
    if( "TRACE".equals(_meth) ) return _kids[1]._cond; // TRACE passes condition thru
    return _rets!=null && _rets.length == 2 && _rets[0]==XCons.BOOL;
  }
  
  @Override AST prewrite() {
    XType k0t = _kids[0]._type;
    // Handle all the Int/Int64/Intliteral to "long" calls
    if( k0t == XCons.JLONG || k0t == XCons.INTLITERAL || k0t == XCons.LONG ) {
      return switch( _meth ) {
      case "toString" -> _kids[0] instanceof ConAST ? this : new InvokeAST(_meth,XCons.STRING,new ConAST("Long"),_kids[0]).do_type();
      case "toInt8", "toInt16", "toInt32", "toInt64", "toInt" ->  _kids[0]; // Autoboxing in Java
      // Invert the call for String; FROM 123L.appendTo(sb) TO sb.appendTo(123L)
      case "appendTo" -> { S.swap(_kids,0,1); yield this; }
      case "toUInt8"  -> new BinOpAST( "&", "", XCons.LONG, _kids[0], new ConAST(       "0xFFL" ));
      case "toUInt16" -> new BinOpAST( "&", "", XCons.LONG, _kids[0], new ConAST(     "0xFFFFL" ));
      case "toUInt32" -> new BinOpAST( "&", "", XCons.LONG, _kids[0], new ConAST( "0xFFFFFFFFL" ));
      case "add" -> new BinOpAST( "+", "", XCons.LONG, _kids );
      case "sub" -> new BinOpAST( "-", "", XCons.LONG, _kids );
      case "mul" -> new BinOpAST( "*", "", XCons.LONG, _kids );
      case "div" -> new BinOpAST( "/", "", XCons.LONG, _kids );
      case "mod" -> new BinOpAST( "%", "", XCons.LONG, _kids );
      case "and" -> new BinOpAST( "&", "", XCons.LONG, _kids );
      case "or"  -> new BinOpAST( "|", "", XCons.LONG, _kids );
      case "xor" -> new BinOpAST( "^", "", XCons.LONG, _kids );
      case "shiftLeft"  -> new BinOpAST( "<<", "", XCons.LONG, _kids );
      case "shiftRight" -> new BinOpAST( ">>", "", XCons.LONG, _kids );
      case "shiftAllRight" -> new BinOpAST( ">>>", "", XCons.LONG, _kids );
      case "to"   -> BinOpAST.do_range( _kids, XCons.RANGEII );
      case "toEx" -> BinOpAST.do_range( _kids, XCons.RANGEIE );
      case "exToEx"->BinOpAST.do_range( _kids, XCons.RANGEEE );
      case "valueOf", "equals", "toInt128", "estimateStringLength" ->
        new InvokeAST(_meth,_rets,new ConAST("org.xvm.xec.ecstasy.numbers.IntNumber"),_kids[0]);
      
      default -> throw XEC.TODO(_meth);
      };
    }

    // Handle all the Int/Int64/Intliteral to "long" calls
    if( k0t == XCons.JCHAR || k0t == XCons.CHAR ) {
      return switch( _meth ) {
      case "add" -> new BinOpAST( "+", "", XCons.INT, _kids );
      case "sub" -> new BinOpAST( "-", "", XCons.INT, _kids );
      case "asciiDigit" -> this;
      case "quoted" ->  new InvokeAST(_meth,_rets,new ConAST("org.xvm.xec.ecstasy.text.Char"),_kids[0]);
      default -> throw XEC.TODO(_meth);
      };
    }

    // XTC String calls mapped to Java String calls
    if( k0t == XCons.STRING )
      return switch( _meth ) {
      case "toCharArray" -> new NewAST(_kids,XCons.ARYCHAR);
      case "appendTo" -> {
        // Invert the call for String; FROM "abc".appendTo(sb) TO sb.appendTo("abc")
        AST tmp = _kids[0]; _kids[0] = _kids[1]; _kids[1] = tmp;
        yield this;
      }
      case "append", "add" -> new BinOpAST("+","", XCons.STRING, _kids);
      // Change "abc".quoted() to e.text.String.quoted("abc")
      case "quoted" ->  new InvokeAST("quoted",_rets,new ConAST("org.xvm.xec.ecstasy.text.String"),_kids[0]);
      case "equals", "split", "indexOf" -> this;
      default -> throw XEC.TODO();
      };

    if( k0t instanceof XClz clz && S.eq(clz._name,"Iterator") ) {
      if( _meth.equals( "next" ) ) {
        _meth = "next8";        // Use the primitive long iterator
        return this;
      }
      throw XEC.TODO();
    }

    // Auto-box arguments for non-internal calls
    if( _args!=null && (k0t==XClz.XXTC || !(k0t instanceof XClz clz) || clz._jname.isEmpty()) )
      for( int i=0; i<_args.length; i++ )
        autobox(i+1, _args[i]);

    return this;
  }


  @Override public SB jcode( SB sb ) {
    if( sb.was_nl() ) sb.i();
    _kids[0].jcode(sb).p(".");
    // Service calls wrap
    if( _kids[0]._type instanceof XClz clz && clz.isa(XClz.SERVICE) &&
        // Except internal self-to-self
        ClzBuilder.CCLZ._tclz != clz ) {
      sb.p("$");               // Calling the     blocking service entry flavor
      if( _async ) sb.p("$");  // Calling the non-blocking service entry flavor
    } else assert !_async;     // Async is for services
    sb.p(_meth).p("(");
    boolean once=false;
    for( int i=1; i<_kids.length; i++ )
      if( _kids[i] != null )
        { once=true; _kids[i].jcode(sb).p(", "); }
    if( once ) sb.unchar(2);
    return sb.p(")");
  }
}
