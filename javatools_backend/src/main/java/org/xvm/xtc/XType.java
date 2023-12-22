package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.xtc.cons.*;
import org.xvm.util.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;

// Concrete java types from XTC types.  All instances are interned so deep
// compares are free/shallow.  
@SuppressWarnings("StaticInitializerReferencesSubClass")
public abstract class XType {
  // The intern table
  static final HashMap<XType,XType> INTERN = new HashMap<>();

  private static int CNT=1;
  final int _uid = CNT++;       // Unique id, for cycle printing
  
  // Children, if any
  XType[] _xts;

  // Interning support
  static XType intern( XType free ) {
    free._hash = 0;
    XType jt = INTERN.get(free);
    if( jt!=null ) return jt;
    INTERN.put(jt=free,free);
    return jt;
  }

  // --------------------------------------------------------------------------

  // Fancy print, handlng cycles and dups.  Suitable for the debugger.
  @Override public final String toString() { return str_init(false); }
  // Alternative Class flavored fancy print
  public final String clz() { return str_init(true); }

  // Collect dups and a visit bit
  private String str_init(boolean clz) {
    VBitSet visit = new VBitSet(), dups = new VBitSet();
    _dups(visit,dups);
    return _str(new SB(),visit.clr(),dups,true ).toString();
  }
  private void _dups(VBitSet visit, VBitSet dups) {
    if( visit.tset(_uid) )
      dups.set(_uid);
    else if( _xts!=null )
      for( XType xt : _xts )
        xt._dups(visit,dups);
  }

  // Default fancy print, stops at cycles and dups
  public SB _str( SB sb, VBitSet visit, VBitSet dups, boolean clz ) {
    if( dups!=null && dups.test(_uid) ) {
      sb.p("V").p(_uid);
      if( visit.tset(_uid) )  return sb;
      sb.p(":");
    }
    return str(sb,visit,dups,clz);
  }

  // Internal specialized print, given dups and string/class flag
  abstract SB str( SB sb, VBitSet visit, VBitSet dups, boolean clz );

  // Non-fancy print.  Dies on cycles and dups.  Suitable for flat or simple types.
  public SB str( SB sb ) { return _str(sb,null,null,false); }
  public SB clz( SB sb ) { return _str(sb,null,null,true ); }

  // --------------------------------------------------------------------------

  abstract public boolean is_prim_base();
  public boolean needs_import() { return false; }

  abstract boolean eq( XType xt );
  @Override public boolean equals(Object o) {
    if( this==o ) return true;
    if( this.getClass() != o.getClass() ) return false;
    if( !Arrays.equals(_xts,((XType)o)._xts) ) return false;
    return eq((XType)o);
  }
    
  int _hash;
  abstract int hash();
  @Override final public int hashCode() {
    if( _hash!=0 ) return _hash;
    long hash = hash();
    if( _xts!=null )
      for( XType xt : _xts )
        hash = (hash<<25) | (hash>>(64-25)) ^ xt._uid;
    int ihash = (int)(hash ^ (hash>>32));
    if( ihash==0 ) ihash = 0xdeadbeef;
    return (_hash=ihash);
  }

  // --------------------------------------------------------------------------

  public static XClz XXTC    = new XClz("","XTC",null);
  public static XClz CONST   = new XClz("ecstasy","Const",XXTC);
  public static XClz ENUM    = new XClz("ecstasy","Enum",CONST);
  
  // Java non-primitive classes
  public static XClz  JNULL  = new XClz("ecstasy","Nullable",null);
  public static XClz  JUBYTE = new XClz("ecstasy.numbers","UByte",null);
  public static XClz  JBOOL  = new XClz("ecstasy","Boolean",ENUM);
  public static XClz  JCHAR  = new XClz("ecstasy.text","Char",null);
  public static XClz  STRING = new XClz("java.lang","String",null);
  public static XClz  EXCEPTION = new XClz("java.lang","Exception",null);

  // Java primitives or primitive classes
  public static XBase BOOL = XBase.make("boolean");
  public static XBase CHAR = XBase.make("char");
  public static XBase BYTE = XBase.make("byte");
  public static XBase LONG = XBase.make("long");
  public static XBase INT  = XBase.make("int");
  
  public static XBase FALSE= XBase.make("false");
  public static XBase NULL = XBase.make("null");
  public static XBase TRUE = XBase.make("true");
  public static XBase VOID = XBase.make("void");
  public static XClz JTRUE = new XClz("Boolean","True" ,JBOOL);
  public static XClz JFALSE= new XClz("Boolean","False",JBOOL);

  public static XAry ARY    = XAry.make(XXTC);
  public static XAry ARYBOOL= XAry.make(BOOL);    // No Ecstasy matching class
  public static XAry ARYCHAR= XAry.make(CHAR);    // No Ecstasy matching class
  public static XAry ARYUBYTE=XAry.make(JUBYTE);  // No Ecstasy matching class
  public static XAry ARYLONG= XAry.make(LONG);    // No Ecstasy matching class
  public static XAry ARYSTRING = XAry.make(STRING); // No Ecstasy matching class
  public static XTuple COND_CHAR = XTuple.make(BOOL,CHAR);
  

  
  // A set of common XTC classes, and their Java replacements.
  // These are NOT parameterized.
  // These will NOT have XTC-prefixed imports.
  static final HashMap<String, XType> BASE_XJMAP = new HashMap<>() {{
      put("Boolean+ecstasy/Boolean.x",BOOL);
      put("Char+ecstasy/text/Char.x",CHAR);
      put("Exception+ecstasy/Exception.x",EXCEPTION);
      put("Int64+ecstasy/numbers/Int64.x",LONG);
      put("IntLiteral+ecstasy/numbers/IntLiteral.x",LONG);
      put("Object+ecstasy/Object.x",XXTC);
      put("String+ecstasy/text/String.x",STRING);      
      put("UInt8+ecstasy/numbers/UInt8.x",JUBYTE);
    }};
  
  // A set of common XTC classes, and their Java replacements...
  // AND these take the default import path from "org.xvm.xec.ecstasy...".
  public static XClz APPENDERCHAR= new XClz("ecstasy","Appenderchar",null);
  public static XClz CONSOLE     = new XClz("ecstasy.io","Console",null);
  public static XClz ILLARGX     = new XClz("XTC","IllegalArgument",null);
  public static XClz ILLSTATEX   = new XClz("XTC","IllegalState",null);
  public static XClz HASHABLE    = new XClz("ecstasy.collections","Hashable",null);
  public static XClz ITER64      = new XClz("ecstasy","Iterablelong",null);
  public static XClz MUTABILITY  = new XClz("ecstasy.collections.Array","Mutability",ENUM);
  public static XClz ORDERABLE   = new XClz("ecstasy","Orderable",null);
  public static XClz ORDERED     = new XClz("ecstasy","Ordered",ENUM);
  public static XClz RANGE       = new XClz("ecstasy","Range",null);
  public static XClz RANGEIE     = new XClz("ecstasy","RangeIE",RANGE); // No Ecstasy matching class
  public static XClz RANGEII     = new XClz("ecstasy","RangeII",RANGE); // No Ecstasy matching class
  public static XClz SERVICE     = new XClz("ecstasy","Service",null);
  public static XClz STRINGBUFFER= new XClz("ecstasy.text","StringBuffer",null);
  public static XClz ARGUMENT    = new XClz("ecstasy.reflect","Argument",null);
  public static XClz TYPE        = new XClz("ecstasy.reflect","Type",null);
  public static XClz UNSUPPORTEDOPERATION = new XClz("ecstasy","UnsupportedOperation",null);

  public static XClz NUMBER      = new XClz("ecstasy.numbers","Number",CONST);
  
  public static XClz INTNUM      = new XClz("ecstasy.numbers","IntNumber",NUMBER);
  public static XClz UINTNUM     = new XClz("ecstasy.numbers","UIntNumber",INTNUM);
  public static XClz JLONG       = new XClz("ecstasy.numbers","Int64",INTNUM);
  
  public static XClz FPNUM       = new XClz("ecstasy.numbers","FPNumber",NUMBER);
  public static XClz DECIMALFP   = new XClz("ecstasy.numbers","DecimalFPNumber",FPNUM);
  public static XClz DEC64       = new XClz("ecstasy.numbers","Dec64",DECIMALFP);
  
  public static XClz ROUNDING    = new XClz("ecstasy.numbers.FPNumber","Rounding",ENUM);

  static final HashMap<String, XClz> IMPORT_XJMAP = new HashMap<>() {{
      put("Boolean+ecstasy/Boolean.x",JBOOL);      
      put("Char+ecstasy/text/Char.x",JCHAR);
      put("Console+ecstasy/io/Console.x",CONSOLE);
      put("Const+ecstasy/Const.x",CONST);
      put("Dec64+ecstasy/numbers/Dec64.x",DEC64);
      put("DecimalFPNumber+ecstasy/numbers/DecimalFPNumber.x",DECIMALFP);
      put("Enum+ecstasy/Enum.x",ENUM);
      put("FPNumber+ecstasy/numbers/FPNumber.x",FPNUM);
      put("Hashable+ecstasy/collections/Hashable.x", HASHABLE);
      put("IllegalArgument+ecstasy.x",ILLARGX);
      put("IllegalState+ecstasy.x",ILLSTATEX);
      put("Int64+ecstasy/numbers/Int64.x",JLONG);
      put("IntNumber+ecstasy/numbers/IntNumber.x",INTNUM);
      put("Iterable+ecstasy/Iterable.x",ITER64);
      put("Mutability+ecstasy/collections/Array.x",MUTABILITY);
      put("Number+ecstasy/numbers/Number.x",NUMBER);
      put("Orderable+ecstasy/Orderable.x",ORDERABLE);
      put("Ordered+ecstasy.x",ORDERED);
      put("Range+ecstasy/Range.x",RANGE);
      put("Rounding+ecstasy/numbers/FPNumber.x",ROUNDING);
      put("StringBuffer+ecstasy/text/StringBuffer.x",STRINGBUFFER);
      put("Type+ecstasy/reflect/Argument.x",ARGUMENT);
      put("Type+ecstasy/reflect/Type.x",TYPE);
      put("UIntNumber+ecstasy/numbers/UIntNumber.x",UINTNUM); // TODO: Java prim 'long' is not unsigned
      put("UnsupportedOperation+ecstasy.x",UNSUPPORTEDOPERATION);
    }};
  static String xjkey(ClassPart clz) { return clz._name + "+" + clz._path._str; }

  static final HashSet<XClz> SPECIAL_RENAMES = new HashSet<>() {{
      add(ITER64);              // Remapped ecstasy.Iterable to ecstasy.Iterablelong
      add(STRING);              // Remapped to java.lang.String
      add(EXCEPTION);           // Remapped to java.lang.Exception
      add(ILLSTATEX); // Remapped to avoid collision with java.lang.IllegalStateException
      add(ILLARGX);   // Remapped to avoid collision with java.lang.IllegalArgumentException
    }};
  

  // Convert a Java primitive to the Java object version.
  private static final HashMap<XBase, XClz> XBOX = new HashMap<>() {{
      put(BOOL,JBOOL);
      put(CHAR,JCHAR);
      put(LONG,JLONG);
      put(NULL,JNULL);
      put(TRUE,JTRUE);
      put(FALSE,JFALSE);
    }};
  public XClz box() {
    if( this instanceof XClz clz ) return clz;
    return XBOX.get(this);
  }
  // Convert a Java wrapped primitive to the unwrapped primitive
  static final HashMap<XType, XBase> UNBOX = new HashMap<>() {{
      put(JBOOL,BOOL);
      put(JCHAR,CHAR);
      put(JLONG,LONG);
      put(JNULL,NULL);
      put(JTRUE ,TRUE);
      put(JFALSE,TRUE);
    }};
  public XType unbox() {
    XBase jt = UNBOX.get(this);
    return jt==null ? this : jt;
  } 
  public boolean primeq() { return XBOX.containsKey(this); }
  public String ztype() { return primeq() ? "0" : "null"; }
  public boolean is_jdk() { return primeq() || this==STRING; }
  
  // Either "==name" or ".equals(name)"
  public SB do_eq(SB sb, String name) {
    return primeq() ? sb.p("==").p(name) : sb.p(".equals(").p(name).p(")");
  }

  // Only valid for Ary, Clz (Tuple???).
  // Always arrays have 1 type parameter, the element type.
  // Clzs mostly have 0, sometimes have 1 (e.g. Hashable<Value>), rarely have 2 or more (e.g. Map<Key,Value>)
  public int nTypeParms() { throw XEC.TODO(); }
  public XType typeParm(int i) { return _xts[i]; }
  
  // --------------------------------------------------------------------------

  
  // Convert an array of Const to an array of XType
  public static XType[] xtypes( Const[] cons ) {
    if( cons==null ) return null;
    XType[] xts = new XType[cons.length];
    for( int i=0; i<cons.length; i++ )
      xts[i] = xtype(cons[i],false);
    return xts;
  }
  
  // Convert an array of Parameter._con to an array of XType
  public static XType[] xtypes( Parameter[] parms ) {
    if( parms==null ) return null;
    XType[] xts = new XType[parms.length];
    for( int i=0; i<parms.length; i++ )
      xts[i] = xtype(parms[i]._con,false);
    return xts;
  }
  
  // Produce a java type from a TermTCon
  public static XType xtype( Const tc, boolean boxed ) {
    return switch( tc ) {
    case TermTCon ttc -> {
      if( ttc.part() instanceof ClassPart clz ) {
        if( clz._path==null ) {
          if( clz._name.equals("Null" ) ) yield NULL;
          if( clz._name.equals("True" ) ) yield TRUE;
          if( clz._name.equals("False") ) yield FALSE;
          throw XEC.TODO();
        }
        // Check the common base classes
        String xjkey = xjkey(clz);
        XType val = BASE_XJMAP.get(xjkey);
        if( val!=null ) yield boxed ? val.box() : val;
        // Again, common classes with imports
        XClz xclz = IMPORT_XJMAP.get(xjkey);
        if( xclz!=null ) {
          xclz.set(clz);
          yield ClzBuilder.add_import(xclz);
        }
        // Make one
        yield XClz.make(clz);
      }
      
      if( ttc.part() instanceof ParmPart ) {
        if( ttc.id() instanceof TParmCon tpc ) {
          yield ((XClz)xtype(tpc.parm()._con,boxed));
        } else if( ttc.id() instanceof DynFormalCon dfc ) {
          yield xtype(dfc.type(),false);
        } else 
          throw XEC.TODO();
      }

      // Hidden extra XTC type argument (GOLD instance of the class whos hash
      // implementation to use)
      if( ttc.part() instanceof PropPart prop )
        yield XType.xtype(prop._con,false);
      
      throw XEC.TODO();
    }

    case ParamTCon ptc -> {
      ClassPart clz = ((ClzCon)ptc._con).clz();

      // These XTC classes are all intercepted and directly implemented in Java
      if( clz._name.equals("Array") && clz._path._str.equals("ecstasy/collections/Array.x") ) {
        XType telem = ptc._parms==null ? null : xtype(ptc._parms[0],false);
        XAry xt = null;
        if( telem == BOOL  ) xt = ARYBOOL ; // Java ArrayList specialized to boolean
        if( telem == CHAR  ) xt = ARYCHAR ; // Java ArrayList specialized to char
        if( telem == JUBYTE) xt = ARYUBYTE; // Java ArrayList specialized to unsigned bytes
        if( telem == LONG  ) xt = ARYLONG ; // Java ArrayList specialized to int64
        if( telem == STRING) xt = ARYSTRING;// Java ArrayList specialized to String
        if( xt==null ) xt = XAry.make(telem);
        yield ClzBuilder.add_import(xt);
      }

      if( clz._name.equals("Function") && clz._path._str.equals("ecstasy/reflect/Function.x") ) {
        XType[] args = xtypes(((ParamTCon)ptc._parms[0])._parms);
        XType[] rets = xtypes(((ParamTCon)ptc._parms[1])._parms);
        yield XFun.make(args, rets).make_class();
      }

      XType telem = ptc._parms==null ? null : xtype(ptc._parms[0],true);
      
      // All the long-based ranges, intervals and iterators are just Ranges now.
      if( clz._name.equals("Range"   ) && clz._path._str.equals("ecstasy/Range.x"   ) ||
          clz._name.equals("Interval") && clz._path._str.equals("ecstasy/Interval.x") ) {
        if( telem== LONG || telem== JLONG )
          yield ClzBuilder.add_import(RANGE); // Shortcut class
        throw XEC.TODO();
      }

      if( clz._name.equals("Iterator") && clz._path._str.equals("ecstasy/Iterator.x") ) {
        if( telem== LONG || telem== JLONG )
          yield ClzBuilder.add_import(ITER64); // Shortcut class
        throw XEC.TODO();
      }

    //  if( clz._name.equals("List") && clz._path._str.equals("ecstasy/collections/List.x") )
    //    yield "Ary<"+telem+">"; // Shortcut class

      if( clz._name.equals("Tuple") && clz._path._str.equals("ecstasy/collections/Tuple.x") )
        //yield org.xvm.xec.ecstasy.collections.Tuple.make_class(ClzBuilder.XCLASSES, xtype(ptc._parms));
        throw XEC.TODO();

    //  if( clz._name.equals("Map") && clz._path._str.equals("ecstasy/collections/Map.x") )
    //    yield XMap.make_class(ClzBuilder.XCLASSES, ptc._parms);
    //  
      // Attempt to use the Java class name
      if( clz._name.equals("Type") && clz._path._str.equals("ecstasy/reflect/Type.x") )
        yield telem;

      if( clz._name.equals("Appender") && clz._path._str.equals("ecstasy/Appender.x") ) {
        if( telem == CHAR || telem== JCHAR )
          yield ClzBuilder.add_import(APPENDERCHAR);
        throw XEC.TODO();
      }

      // No intercept, so just the class
      yield XClz.make(clz);
    }

    case ImmutTCon itc ->
      xtype(itc.icon(),boxed); // Ignore immutable for now

    // Generalized union types gonna wait awhile.
    // Right now, allow null unions only
    case UnionTCon utc -> {
      if( ((ClzCon)utc._con1).clz()._name.equals("Nullable") )
        yield xtype(utc._con2,true);
      XType u1 = xtype(utc._con1,false);
      XType u2 = xtype(utc._con2,false);
      yield XUnion.make(u1,u2);
    }

    case IntCon itc -> {
      if( itc._f == Const.Format.Int64 )
        yield boxed ? JLONG : LONG;
      throw XEC.TODO();
    }

    case CharCon cc ->
      boxed ? JCHAR : CHAR;
    
    case StringCon sc ->
      STRING;

    case EnumCon econ -> {
      // The enum instance as a ClassPart
      ClassPart eclz = (ClassPart)econ.part();
      // The Enum class itself, not the enum
      XClz xclz = XClz.make(eclz._super);
      //// The enum
      //XClz e = new XClz(xclz._name,eclz._name,xclz);
      //yield e.unbox();
      // XTC True/False are enums, return the unboxed java primitive true,false.
      // Other enums just return themselves.
      yield xclz.unbox();
    }

    case LitCon lit -> {
      if( lit._f==Const.Format.IntLiteral )
        yield boxed ? JLONG : LONG;
      if( lit._f==Const.Format.FPLiteral )
        yield DEC64;
      throw XEC.TODO();
    }

    case AryCon ac ->
      xtype(ac.type(),false);

    case MethodCon mcon ->
      XFun.make((MethodPart)mcon.part());

    // Property constant
    case PropCon prop ->
      xtype(((PropPart)prop.part())._con,false);

    case SingleCon con0 -> {
      if( con0.part() instanceof ModPart mod )
        yield XClz.make(mod);
      if( con0.part() instanceof PropPart prop )
        yield XBase.make(PropBuilder.jname(prop));
      throw XEC.TODO();
    }

    case Dec64Con dcon ->
      DEC64;

    case ClassCon ccon ->
      XClz.make(ccon.clz());

    case ServiceTCon service ->
      SERVICE;

      case VirtDepTCon virt ->
        xtype(virt._par,false);
    
    default -> throw XEC.TODO();
    };
  }

  private static XTuple xtype( TCon[] cons ) {
    int N = cons==null ? 0 : cons.length;
    XType[] clzs = new XType[N];
    for( int i=0; i<N; i++ )
      clzs[i] = XType.xtype(cons[i],false);
    return XTuple.make(clzs);
  }
  
}
