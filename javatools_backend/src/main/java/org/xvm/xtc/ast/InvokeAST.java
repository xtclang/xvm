package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.Const;
import org.xvm.xtc.cons.MethodCon;
import org.xvm.xec.ecstasy.AbstractRange;
import org.xvm.util.S;
import org.xvm.util.SB;

public class InvokeAST extends AST {
  String _meth, _slice_tmp;
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
    MethodPart meth = (MethodPart)methcon.part();
    _meth = meth.jname();
    _async = async;
    _args = meth.xargs();
    _rets = XType.xtypes(retTypes);

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
    return XCons.make_tuple(_rets);
  }
  @Override boolean _cond() {
    if( "TRACE".equals(_meth) ) return _kids[1]._cond; // TRACE passes condition thru
    return _rets!=null && _rets.length == 2 && _rets[0]==XCons.BOOL;
  }

  @Override public AST unBox() {
    // Unbox primitive iterators
    if( _kids[0]._type instanceof XClz clz && S.eq(clz._name,"Iterator") ) {
      XType xt = clz._xts[0];
      switch( _meth ) {
      case "next":              // Use the primitive iterator
        if(      clz._xts[0].isa(XCons.INTNUM ) )  _meth = "next8";
        else if( clz._xts[0].isa(XCons.JCHAR  ) )  _meth = "next2";
        else if( clz._xts[0].isa(XCons.JSTRING) )  _meth = "nextStr";
        break;
      case
        "concat",
        "forEach",
        "knownEmpty",
        "knownSize",
        "limit",
        "take",
        "untilAny",
        "whileEach",
        "ZZZZZZ":               // Just to end the sorted list
        break;
      default: throw XEC.TODO();
      };
    }
    return null;
  }

  @Override public AST rewrite() {
    XType k0t = _kids[0]._type;
    // Handle all the Int/Int64/Intliteral to "long" calls
    if( k0t == XCons.JLONG || k0t == XCons.LONG ) {
      return switch( _meth ) {
      case "toString" -> _kids[0] instanceof ConAST ? null : new InvokeAST(_meth,XCons.STRING,new ConAST("Long",XCons.JLONG),_kids[0]).doType();
      case "toChar", "toInt8", "toInt16", "toInt32", "toInt64", "toInt" ->  _kids[0]; // Autoboxing in Java
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
      case "exToEx"->BinOpAST.do_range( _kids, XCons.RANGEEE );
      case "valueOf", "equals", "toInt128", "estimateStringLength", "abs" ->
        new InvokeAST(_meth,_rets,new ConAST("org.xvm.xec.ecstasy.numbers.IntNumber",XCons.INTNUM),_kids[0]);
      case "toDec64" -> new NewAST(new AST[]{_kids[0]},XCons.DEC64);

      default -> throw XEC.TODO(_meth);
      };
    }

    // Handle all the Char to "char" calls
    if( k0t == XCons.CHAR ) {
      return switch( _meth ) {
      case "add" -> new BinOpAST( "+", "", XCons.CHAR, _kids );
      case "sub" -> new BinOpAST( "-", "", XCons.CHAR, _kids );
      case "toInt64" -> _kids[0]; // no-op cast
      case "asciiDigit" -> {
        InvokeAST inv = new InvokeAST(_meth,_rets,new ConAST("org.xvm.xec.ecstasy.text.Char",XCons.JCHAR),_kids[0]);
        inv._cond = true;
        yield inv;
      }
      case "decimalValue", "quoted" ->  new InvokeAST(_meth,_rets,new ConAST("org.xvm.xec.ecstasy.text.Char",XCons.JCHAR),_kids[0]);
      default -> throw XEC.TODO(_meth);
      };
    }

    // XTC String calls mapped to Java String calls
    if( k0t == XCons.STRING ) {
      return switch( _meth ) {
      case "toCharArray" -> _par._type== XCons.ARYCHAR ? new NewAST(_kids,XCons.ARYCHAR) : null;
      case "appendTo" -> {
        // Invert the call for String; FROM "abc".appendTo(sb) TO sb.appendTo("abc")
        AST tmp = _kids[0]; _kids[0] = _kids[1]; _kids[1] = tmp;
        yield null;
      }
      case "append", "add" -> new BinOpAST("+","", XCons.STRING, _kids);
      // Change "abc".quoted() to e.text.String.quoted("abc")
      case "quoted", "iterator" ->  new InvokeAST(_meth,_rets,new ConAST("org.xvm.xec.ecstasy.text.String",XCons.JSTRING),_kids[0]);
      case "equals", "split", "endsWith", "startsWith" -> null;
      case "indexOf" -> {
        castInt(2);                         // Force index to be an int not a long
        if( _type!=XCons.BOOL ) yield null; // Return int result
        // Request for the boolean result instead of int result
        _type = XCons.LONG;   // Back to producing an int result
        // But insert compare to -1 for boolean
        yield new BinOpAST( "!=", "", XCons.BOOL, this, new ConAST( "-1", XCons.LONG ) );
      }
      default -> throw XEC.TODO();
      };
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

    return null;
  }

  private BinOpAST llbin(String op) {
    return _type==XCons.LONG ? new BinOpAST( op, "", XCons.LONG, _kids ) : null;
  }

  @Override XType reBox( AST kid ) {
    if( _args==null ) return kid._type;
    if( kid instanceof NewAST ) return null; // Already boxed
    switch( _kids[0]._type ) {
    case XBase  base : return null;
    case XInter in   : return null;
    case XClz clz when !clz._jname.isEmpty(): return null; // "this" ptr is a Java special, will have primitive arg version
    default: break;
    }
    int idx = S.find(_kids,kid);
    if( idx==0 ) return null; // The "this" ptr never boxes
    // Expected args type vs provided kids[idx]._type
    return _args[idx-1];
  }

  @Override public SB jcode( SB sb ) {
    if( sb.was_nl() ) sb.i();

    // Sharp tuple slices are special in all kinds of ways.
    // I have to explode the fields directly.
    if( _type instanceof XClz clz && clz.isTuple() && _meth.equals("slice") ) {
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
