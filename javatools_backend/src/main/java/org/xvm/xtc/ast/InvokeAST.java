package org.xvm.xtc.ast;

import java.util.Arrays;
import org.xvm.XEC;
import org.xvm.util.S;
import org.xvm.util.SB;
import org.xvm.xec.ecstasy.AbstractRange;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.Const;

public class InvokeAST extends AST {
  String _meth, _slice_tmp;
  final boolean _async;
  XFun _fun;
  final XType _ret;

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
    MethodPart meth = (MethodPart)methcon.part();
    _meth = meth.jname();
    _async = async;
    _fun = meth.xfun();
    // Return types are sharpened from the method, based on local type info
    _ret = XFun.ret(XType.xtypes(retTypes));

    // Replace default args with their actual default values
    for( int i=1; i<_kids.length; i++ ) {
      if( _kids[i] instanceof RegAST reg &&
          reg._reg == -4/*Op.A_DEFAULT*/ ) {    // Default reg
        // Swap in the default from method defaults
        _kids[i] = new ConAST(null,meth._args[i-1]._def);
      }
    }
  }

  public InvokeAST( String meth, XType ret, AST... kids ) {
    super(kids);
    _meth = meth;
    _async = false;
    _fun = null;
    _ret = ret;
    _type = _type();
  }

  @Override XType _type() { return _ret; }
  @Override boolean _cond() {
    // TRACE is a special for asserts; its the one function XEC understands
    // passes-through the condition flag.
    if( "TRACE".equals(_meth) ) return _kids[1]._cond; // TRACE passes condition thru
    // Otherwise, take the condition flag from the function being called
    return _fun!=null && _fun._cond;
  }

  @Override public AST rewrite() {
    XType k0t = _kids[0]._type;
    // Handle all the Int/Int64/Intliteral to "long" calls
    if( k0t == XCons.JLONG || k0t == XCons.LONG || k0t == XCons.INT ) {
      return switch( _meth ) {
      case "toString" -> _kids[0] instanceof ConAST ? null : new InvokeAST(_meth,XCons.STRING,new ConAST("Long",XCons.JLONG),_kids[0]).doType();
      case "toInt64", "toInt" ->  _kids[0]; // Autoboxing in Java
      case "toChar"  -> new ConvAST(XCons.CHAR ,_kids[0]);
      case "toInt8"  -> new ConvAST(XCons.BYTE ,_kids[0]);
      case "toInt16" -> new ConvAST(XCons.SHORT,_kids[0]);
      case "toInt32" -> new ConvAST(XCons.INT  ,_kids[0]);
      // Invert the call for String; FROM 123L.appendTo(sb) TO sb.appendTo(123L)
      case "appendTo" -> { S.swap(_kids,0,1); yield this; }
      case "toUInt8"  -> new BinOpAST( "&", "", XCons.LONG, _kids[0], new ConAST(       "0xFFL",XCons.LONG ));
      case "toUInt16" -> new BinOpAST( "&", "", XCons.LONG, _kids[0], new ConAST(     "0xFFFFL",XCons.LONG ));
      case "toUInt32" -> new BinOpAST( "&", "", XCons.LONG, _kids[0], new ConAST( "0xFFFFFFFFL",XCons.LONG ));
      case "eq"  -> this; //new BinOpAST( "==","", XCons.LONG, _kids );
      case "add" -> llbin( "+" );
      case "sub" -> llbin( "-" );
      case "mul" -> llbin( "*" );
      case "div" -> llbin( "/" );
      case "mod" -> llbin( "%" );
      case "and" -> llbin( "&" );
      case "or"  -> llbin( "|" );
      case "xor" -> llbin( "^" );
      case "shiftLeft"    -> llbin( "<<"  );
      case "shiftRight"   -> llbin( ">>"  );
      case "shiftAllRight"-> llbin( ">>>" );
      case "to"   -> BinOpAST.do_range( _kids, XCons.RANGEII );
      case "toEx" -> BinOpAST.do_range( _kids, XCons.RANGEIE );
      case "exTo" -> BinOpAST.do_range( _kids, XCons.RANGEEI );
      case "exToEx"->BinOpAST.do_range( _kids, XCons.RANGEEE );
      case "valueOf", "equals", "toInt128", "estimateStringLength", "abs" ->
        new InvokeAST(_meth,_ret,new ConAST("org.xvm.xec.ecstasy.numbers.IntNumber",XCons.INTNUM),_kids[0]);
      case "toDec64" -> new NewAST(new AST[]{_kids[0]},XCons.DEC64);

      default -> throw XEC.TODO(_meth);
      };
    }

    // Handle the Float64 calls
    if( k0t == XCons.JDOUBLE || k0t == XCons.DOUBLE ) {
      return switch( _meth ) {
      case "add" -> ddbin( "+" );
      default -> throw XEC.TODO(_meth);
      };
    }

    // Handle all the Char to "char" calls
    if( k0t == XCons.CHAR ) {
      return switch( _meth ) {
      case "add" -> new BinOpAST( "+", "", XCons.LONG, _kids );
      case "sub" -> new BinOpAST( "-", "", XCons.LONG, _kids );
      case "toInt64" -> _kids[0]; // no-op cast
      case "asciiDigit" -> {
        InvokeAST inv = new InvokeAST(_meth,_ret,new ConAST(XEC.XCLZ+".ecstasy.text.Char",XCons.JCHAR),_kids[0]);
        inv._cond = true;
        yield inv;
      }
      case "decimalValue", "quoted" ->
        new InvokeAST(_meth,_ret,new ConAST(XEC.XCLZ+".ecstasy.text.Char",XCons.JCHAR),_kids[0]);
      default -> throw XEC.TODO(_meth);
      };
    }

    // XTC String calls mapped to Java String calls
    if( k0t == XCons.STRING || k0t == XCons.STRINGN ) {
      return switch( _meth ) {
      case "toCharArray" -> _par._type== XCons.ARYCHAR ? new NewAST(_kids,XCons.ARYCHAR) : null;
      case "appendTo" -> {
        // Invert the call for String; FROM "abc".appendTo(sb) TO sb.appendTo("abc")
        AST tmp = _kids[0]; _kids[0] = _kids[1]; _kids[1] = tmp;
        yield null;
      }
      case "append", "add" -> new BinOpAST("+","", XCons.STRING, _kids);
      // Change "abc".quoted() to e.text.String.quoted("abc")
      case "quoted", "iterator" ->
        new InvokeAST(_meth,_ret,new ConAST("org.xvm.xec.ecstasy.text.String",XCons.JSTRING),_kids[0]);
      // The existing _fun has XTC.String arguments; we're using the Java
      // function of the same name which expects bare Java.String, so we want
      // to remove any "arguments need boxing", which is based on the _fun.
      case "equals", "endsWith", "startsWith" -> { _fun = null; yield null; }
      case "substring" -> {
        _fun = null;
        // Force offset math to be an integer
        yield castInt(1) ? this : null;
      }

      // Conditional and long index return
      case "indexOf" -> {
        AST call = new InvokeAST(_meth,_ret,new ConAST(XEC.XCLZ+".ecstasy.text.String",XCons.JSTRING),_kids[0],_kids[1],_kids[2]);
        call._cond = true;
        yield call;
      }
      case "slice" -> {
        _slice_tmp = enclosing_block().add_tmp(ClzBuilder.add_import(XCons.RANGE));
        yield null;
      }
      case "split" ->
        new InvokeAST(_meth,_ret,new ConAST(XEC.XCLZ+".ecstasy.text.String",XCons.JSTRING),_kids[0],_kids[1],_kids[2],_kids[3]);
      default -> throw XEC.TODO();
      };
    }

    // Use fast primitive iterator
    if( k0t.isa(XCons.ITERATOR) && _meth.equals("next") ) {
      XType elem = k0t._xts[0];
      if(      elem.isa(XCons.INTNUM ) )  _meth = "next8";
      else if( elem.isa(XCons.JCHAR  ) )  _meth = "next2";
      else if( elem.isa(XCons.JSTRING) )  _meth = "nextStr";
      else return null;
      return this;
    }

    if( k0t instanceof XClz clz && clz.isTuple() ) {
      switch( _meth ) {
      case "slice":
        BlockAST blk = enclosing_block();
        _slice_tmp = blk.add_tmp(_kids[0]._type);
        break;
      case "TRACE": break;
      case "add": break;
      default: throw XEC.TODO();
      }
    }

    // Unbox boxed arguments as needed
    AST progress = null;
    if( _fun != null )
      for( int i=0; i<_fun.nargs(); i++ )
        if( _fun.arg(i) instanceof XBase && !(_kids[i+1]._type instanceof XBase) )
          progress = _kids[i+1] = _kids[i+1].unBoxThis();
    if( progress != null ) return this;

    return null;
  }

  private BinOpAST llbin(String op) {
    if( _type == XCons.INT ) {
      castInt(0);
      castInt(1);
      return new BinOpAST( op, "", XCons.INT, _kids );
    }
    return _type==XCons.LONG ? new BinOpAST( op, "", XCons.LONG, _kids ) : null;
  }
  private BinOpAST ddbin(String op) {
    return _type==XCons.DOUBLE ? new BinOpAST( op, "", XCons.DOUBLE, _kids ) : null;
  }

  @Override public AST reBox( ) {
    if( _fun==null ) return null; // Baked-in, should be good already
    // Internal Java-implemented mirror type; these
    // all have primitive versions, no need to reBox.
    if( _kids[0]._type instanceof XClz xclz && !xclz._jname.isEmpty() )
      return null;
    AST progress=null;
    for( int i = 1; i < _kids.length; i++ ) {
      if( _kids[i]._type instanceof XBase kbase && kbase != XCons.NULL &&
          !(_fun.arg(i-1) instanceof XBase) )
        progress = _kids[i] = _kids[i].reBoxThis();
    }
    return progress==null ? null : this;
  }

  @Override public SB jcode( SB sb ) {
    if( sb.was_nl() ) sb.i();

    // Sharp tuple slices are special in all kinds of ways.
    // I have to explode the fields directly.
    if( _meth.equals("slice") && _type instanceof XClz clz && clz.isTuple() ) {
      assert _kids[1] instanceof NewAST && _kids[1]._type.isa(XCons.RANGE);
      AbstractRange rng = AbstractRange.range(_kids[1]);
      // new Tuple((tmp=kid0)._f2,tmp._f3,tmp._f4);
      sb.p("new ");
      clz.clz(sb);
      sb.p("((").p(_slice_tmp).p(" = ");
      _kids[0].jcode(sb);
      sb.p(")");
      for( long i=rng._start; i<rng._end; i++ )
        sb.p("._f").p(i).p(",").p(_slice_tmp);
      sb.unchar(_slice_tmp.length()).unchar(1);
      sb.p(")");
      return sb;
    }

    // Print "str.slice( RangeExpr )" as
    // "str.substring((tmp=RangeExpr)._start,tmp._end)"
    if( _meth.equals("slice") && _type==XCons.STRING ) {
      _kids[0].jcode(sb).p(".substring((int)(").p(_slice_tmp).p("=");
      _kids[1].jcode(sb).p(")._start,(int)").p(_slice_tmp).p("._end)");
      return sb;
    }

    // Print the instance before method - except for "this"
    // which can be assumed
    if( !(_kids[0] instanceof NarrowAST n &&
          n._kids[0] instanceof RegAST reg &&
          reg._reg== -5) )      // Special "this" register
      _kids[0].jcode(sb).p(".");

    // Service calls wrap
    if( _kids[0]._type instanceof XClz clz && clz.isa(XCons.SERVICE) &&
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
