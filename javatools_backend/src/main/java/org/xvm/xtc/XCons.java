package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.S;
import org.xvm.xtc.cons.Const;

import static org.xvm.XEC.TODO;

import java.util.HashMap;

public abstract class XCons {
  public static XClz XXTC    = make_java("","XTC",null);
  public static XClz XXTC_RO = XXTC.readOnly();
  public static XClz CONST   = make_java("ecstasy","Const",XXTC);
  public static XClz ENUM    = make_java("ecstasy","Enum" ,CONST);
  public static XClz SERVICE = make_java("ecstasy","Service",XXTC);

  // Java non-primitive classes
  public static XClz JBOOL    = make_java("ecstasy","Boolean",ENUM);
  public static XClz JCHAR    = make_java("ecstasy.text","Char",CONST);
  public static XClz JSTRING  = make_java("ecstasy.text","String",CONST);
  public static XClz JSTRINGN = JSTRING.nullable();
  public static XClz JOBJECT  = make_java("ecstasy","Object",null);
  public static XClz EXCEPTION= make_java("java.lang","Exception","ecstasy","Exception",CONST);

  // Java primitives or primitive classes
  public static XBase BOOL = XBase.make("boolean",false);
  public static XBase BYTE = XBase.make("byte",false);
  public static XBase CHAR = XBase.make("char",false);
  public static XBase SHORT= XBase.make("short",false);
  public static XBase INT  = XBase.make("int",false);
  public static XBase LONG = XBase.make("long",false);
  public static XBase DOUBLE = XBase.make("double",false);
  public static XBase STRING = XBase.make("String",true);
  public static XBase STRINGN= XBase.make("String",false);

  public static XBase VOID = XBase.make("void",false);
  public static XBase TRUE = XBase.make("true",false);
  public static XClz JTRUE = make_java("ecstasy.Boolean","True" ,JBOOL);
  public static XBase FALSE= XBase.make("false",false);
  public static XClz JFALSE= make_java("ecstasy.Boolean","False",JBOOL);
  public static XBase NULL = XBase.make("null",false);
  public static XClz JNULL = make_java("ecstasy","Nullable",ENUM);

  // A set of common XTC classes, and their Java replacements...
  // AND these take the default import path from "org.xvm.xec.ecstasy...".
  public static XClz APPENDER    = make_java("ecstasy","Appender","ecstasy","Appender",null,"Element",XXTC);
  public static XClz APPENDERCHAR= make_java("ecstasy","Appenderchar","ecstasy","Appenderchar",APPENDER,APPENDER,JCHAR);
  public static XClz CONSOLE     = make_java("ecstasy.io","Console",null);
  public static XClz FREEZABLE   = make_java("ecstasy","Freezable",null);
  public static XClz HASHABLE    = make_java("ecstasy.collections","Hashable",null);
  public static XClz ILLARGX     = make_java("XTC","IllegalArgument","ecstasy","IllegalArgument",EXCEPTION);
  public static XClz ILLSTATEX   = make_java("XTC","IllegalState"   ,"ecstasy","IllegalState"   ,EXCEPTION);
  public static XClz UNSUPPORTED = make_java("XTC","Unsupported"    ,"ecstasy","Unsupported"    ,EXCEPTION);
  public static XClz NOTIMPL     = make_java("XTC","NotImplemented" ,"ecstasy","NotImplemented" ,UNSUPPORTED);
  public static XClz ITERABLE    = make_java("ecstasy","Iterable","ecstasy","Iterable",(XClz)null,"Element",XXTC);
  public static XClz ITERATOR    = make_java("ecstasy","Iterator","ecstasy","Iterator",(XClz)null,"Element",XXTC);
  public static XClz MUTABILITY  = make_java("ecstasy.collections.Array","Mutability",ENUM);
  public static XClz ORDERABLE   = make_java("ecstasy","Orderable",null);
  public static XClz ORDERED     = make_java("ecstasy","Ordered",ENUM);
  public static XClz STRINGBUFFER= make_java("ecstasy.text","StringBuffer",null);
  public static XClz TYPE        = make_java("ecstasy.reflect","Type","ecstasy.reflect","Type",(XClz)null,"DataType",XXTC,"OuterType",XXTC);

  public static XClz NUMBER  = make_java("ecstasy.numbers","Number",CONST);
  public static XClz SIGNUM  = make_java("ecstasy.numbers.Number","Signum",ENUM);
  public static XClz BIT     = make_java("ecstasy.numbers","Bit",CONST);

  public static XClz INTNUM  = make_java("ecstasy.numbers","IntNumber" ,NUMBER);
  public static XClz JBYTE   = make_java("ecstasy.numbers","Int8"      ,INTNUM);
  public static XClz JINT16  = make_java("ecstasy.numbers","Int16"     ,INTNUM);
  public static XClz JINT32  = make_java("ecstasy.numbers","Int32"     ,INTNUM);
  public static XClz JLONG   = make_java("ecstasy.numbers","Int64"     ,INTNUM);
  public static XClz JINT128 = make_java("ecstasy.numbers","Int128"    ,INTNUM);
  public static XClz JINTN   = make_java("ecstasy.numbers","IntN"      ,INTNUM);
  public static XClz UINTNUM = make_java("ecstasy.numbers","UIntNumber",INTNUM);
  public static XClz JUINT8  = make_java("ecstasy.numbers","UInt8"    ,UINTNUM);
  public static XClz JUINT16 = make_java("ecstasy.numbers","UInt16"   ,UINTNUM);
  public static XClz JUINT32 = make_java("ecstasy.numbers","UInt32"   ,UINTNUM);
  public static XClz JUINT64 = make_java("ecstasy.numbers","UInt64"   ,UINTNUM);
  public static XClz JUINT128= make_java("ecstasy.numbers","UInt128"  ,UINTNUM);
  public static XClz JUINTN  = make_java("ecstasy.numbers","UIntN"    ,UINTNUM);

  public static XClz FPNUM       = make_java("ecstasy.numbers","FPNumber",NUMBER);
  public static XClz BINARYFP    = make_java("ecstasy.numbers","BinaryFPNumber",FPNUM);
  public static XClz JFLOAT      = make_java("ecstasy.numbers","Float32" ,BINARYFP);
  public static XClz JDOUBLE     = make_java("ecstasy.numbers","Float64" ,BINARYFP);
  public static XClz FLOAT128    = make_java("ecstasy.numbers","Float128",BINARYFP);

  public static XClz DECIMALFP   = make_java("ecstasy.numbers","DecimalFPNumber",FPNUM);
  public static XClz DEC64       = make_java("ecstasy.numbers","Dec64" ,DECIMALFP);
  public static XClz DEC128      = make_java("ecstasy.numbers","Dec128",DECIMALFP);

  public static XClz ROUNDING    = make_java("ecstasy.numbers.FPNumber","Rounding",ENUM);

  public static XClz RANGE       = make_java("ecstasy","AbstractRange","ecstasy", "Range"  ,CONST,"Element",CONST);
  public static XClz RANGEEE     = make_java("ecstasy","RangeEE"      ,"ecstasy","XRangeEE",RANGE,RANGE,JLONG); // No Ecstasy matching class
  public static XClz RANGEIE     = make_java("ecstasy","RangeIE"      ,"ecstasy","XRangeIE",RANGE,RANGE,JLONG); // No Ecstasy matching class
  public static XClz RANGEEI     = make_java("ecstasy","RangeEI"      ,"ecstasy","XRangeEI",RANGE,RANGE,JLONG); // No Ecstasy matching class
  public static XClz RANGEII     = make_java("ecstasy","RangeII"      ,"ecstasy","XRangeII",RANGE,RANGE,JLONG); // No Ecstasy matching class

  // This is a mixin type
  public static XClz VOLATILEVAR = make_java("ecstasy.annotations","VolatileVar","ecstasy.annotations","VolatileVar",null,"Referent",XXTC);
  public static XClz FUTUREVAR   = make_java("ecstasy.annotations","FutureVar","ecstasy.annotations","FutureVar",VOLATILEVAR,"Referent",XXTC);

  // Convert a Java primitive to the Java object version.
  static final HashMap<XBase, XClz> XBOX = new HashMap<>() {{
      put(NULL ,JNULL );
      put(BOOL ,JBOOL );
      put(BYTE ,JBYTE );
      put(CHAR ,JCHAR );
      put(SHORT,JINT16);
      put(INT,  JINT32);
      put(LONG ,JLONG );
      put(DOUBLE,JDOUBLE);
      put(TRUE ,JTRUE );
      put(FALSE,JFALSE);
      put(STRING,JSTRING);
      put(STRINGN,JSTRINGN);
    }};
  // Convert a Java wrapped primitive to the unwrapped primitive
  static final HashMap<XType, XBase> UNBOX = new HashMap<>() {{
      put(JNULL ,NULL );
      put(JBOOL ,BOOL );
      put(JBYTE ,BYTE );
      put(JCHAR ,CHAR );
      put(JINT16,SHORT);
      put(JUINT8 ,LONG);
      put(JUINT16,LONG);
      put(JUINT32,LONG);
      put(JINT32,INT  );
      put(JDOUBLE,DOUBLE);
      put(JLONG ,LONG );
      put(JTRUE ,TRUE );
      put(JFALSE,FALSE);
      put(JSTRING,STRING);
    }};

  // Generic array; element type is unknown, and could be primitives or Java Object.
  public static XClz ARRAY    = make_java("ecstasy.collections","Array","ecstasy.collections","Array",null,"Element",VOID);
  public static XClz ARRAY_RO = ARRAY.readOnly();
  // Generic Java Object[], this maps to the ecstasy.collections.Array class
  public static XClz ARYXTC   = make_java_ary("AryXTC",XXTC  );
  public static XClz ARYXTC_RO= ARYXTC.readOnly();
  // Java primitive arrays
  public static XClz ARYBOOL  = make_java_ary("Aryboolean",JBOOL  );
  public static XClz ARYCHAR  = make_java_ary("Arychar"   ,JCHAR  );
  public static XClz ARYSTRING= make_java_ary("AryString" ,JSTRING);
  public static XClz ARYLONG  = make_java_ary("Arylong"   ,JLONG  );
  public static XClz ARYUBYTE = make_java_ary("AryUInt8"  ,JUINT8 );

  // Type sig for Iterator<Int64>, which returns a non-XTC type "Iteratorlong"
  // which supports a "long next8()" as well as the expected "Int64 next()".
  // No corresponding XTC class.
  public static XClz ITERATORLONG = make_java("ecstasy.collections.Arylong",   "Iterlong",  "ecstasy","Iterator",null,"Element",JLONG  );
  public static XClz ITERSTR      = make_java("ecstasy.collections.AryString", "IterString","ecstasy","Iterator",null,"Element",JSTRING);
  public static XClz ITERATORCHAR = make_java("ecstasy",                       "Iterator",  "ecstasy","Iterator",null,"Element",CHAR   );

  // These are always expanded to some Java constant
  public static XClz INTLITERAL = make_java("ecstasy.numbers","IntLiteral","ecstasy.numbers","IntLiteral",CONST);
  public static XClz  FPLITERAL = make_java("ecstasy.numbers", "FPLiteral","ecstasy.numbers", "FPLiteral",CONST);


  public static XClz MAP = make_java("ecstasy.collections","Map","ecstasy.collections","Map",null,"Key",XXTC,"Value",XXTC);

  // Some tuples
  public static XClz TUPLE0 = make_tuple();
  public static XClz COND_CHAR = make_tuple(BOOL,CHAR);



  private static final XClz[] XCLZS = new XClz[] {
    null,                     // IntLiteral
    null,                     // Bit
    null,                     // Nybble
    JBYTE   ,
    JINT16  ,
    JINT32  ,
    JLONG   ,
    JINT128 ,
    JINTN   ,
    null,
    JUINT16 ,
    JUINT32 ,
    JUINT64 ,
    JUINT128,
    JUINTN  ,
  };
  public static XClz format_clz(Const.Format f) { return XCLZS[f.ordinal()]; }
  private static final boolean[] IPRIMS = new boolean[] {
    false,                  // IntLiteral
    false,                  // Bit
    false,                  // Nybble
    true ,                  // i8
    true ,                  // i16
    true ,                  // i32
    true ,                  // i64
    false,                  // i128
    false,                  // BigInteger
    true ,                  // u8
    true ,                  // u16
    true ,                  // u32
    true ,                  // u64
    false,                  // u128
    false,                  // UBigInteger
  };
  public static boolean format_iprims(Const.Format f) { return IPRIMS[f.ordinal()]; }



  // Made from a Java class directly; the XTC class shows up later.
  // Fields are hand-added and need to match the ClazzPart later.
  static XClz make_java( String pack, String name, XClz supr ) {
    return _make_java(pack,name,pack,name,supr,XClz.SIDES0,XClz.STR0,XType.EMPTY);
  }

  // Specify string/XTC for xts/tns.
  static XClz make_java( String jpack, String jname, String pack, String name, XClz supr ) {
    return _make_java(jpack,jname,pack,name,supr,XClz.SIDES0,XClz.STR0,XType.EMPTY);
  }
  static XClz make_java( String jpack, String jname, String pack, String name, XClz supr, String tvar, XType xt ) {
    assert tvar!=null;
    assert  xt !=null;
    return _make_java(jpack,jname,pack,name,supr,XClz.SIDES0,new String[]{tvar},new XType[]{xt});
  }
  static XClz make_java( String jpack, String jname, String pack, String name, XClz supr, String tvar0, XClz xt0, String tvar1, XClz xt1 ) {
    return _make_java(jpack,jname,pack,name,supr,XClz.SIDES0,new String[]{tvar0,tvar1},new XType[]{xt0,xt1});
  }
  static XClz make_java( String jpack, String jname, String pack, String name, XClz supr, XClz side_clz, XClz tvar_clz ) {
    return _make_java(jpack,jname,pack,name,supr,new HashMap<>(){{put(side_clz,new int[]{0});}},new String[0],new XType[]{tvar_clz});
  }

  private static XClz _make_java( String jpack, String jname, String pack, String name, XClz supr, HashMap<XClz,int[]> sides, String[] tns, XType[] xts ) {
    XClz clz = XClz.mallocBase(true, pack,"",name);
    clz._jpack = jpack;
    clz._jname = jname;
    clz._xts = xts;
    clz._tns = tns;
    clz._sides = sides;
    // XXTC root XClz has no XTC _clz
    if( pack.isEmpty() && S.eq(name,"XTC") ) {
      clz._depth = 0;
      return clz._intern();
    }

    // Walk the XTC REPO in parallel to parsing, and find the matching XTC class
    String[] packs = pack.split("\\.");
    assert packs[0].equals("ecstasy");
    ClassPart pclz = XEC.ECSTASY;
    for( int i=1; i<packs.length; i++ )
      pclz = (ClassPart)pclz.child(packs[i]);
    pclz = (ClassPart)pclz.child(name);
    // Some XClzs have no XTC equivalent
    if( pclz==null ) {
      clz._super = supr;
    } else {
      // Fill in XTC class details
      assert clz._xts.length == pclz._tcons.length;
      // Set the super
      assert supr==XClz.get_super(pclz);
      clz._super = supr;
      clz._depth = supr==null ? 0 : supr._depth+1;
      // Set mod and clz
      clz._mod = pclz.mod();
      clz._clz = pclz;
      if( S.eq(jname,name) )
        pclz._tclz = clz;         // Fill in class cache
    }
    XClz clz2 = clz._intern();
    assert clz==clz2;           // No prior versions of these java-based XClzs
    return clz;
  }

  // Java primitive array classes, no corresponding XTC class.
  // Treated as a specific instance of the generic Array class using concrete types.
  private static XClz make_java_ary( String jname, XType xelem ) {
    XClz clz = XClz.mallocBase(true, "ecstasy.collections","","Array");
    if( clz._tns!=null && clz._tns.length==1 ) clz._tns[0] = "Element";
    else clz._tns = new String[]{"Element"};
    if( clz._xts!=null && clz._xts.length==1 ) clz._xts[0] = xelem;
    else clz._xts = new XType[]{xelem};
    clz._sides = new HashMap<>(){{put(XCons.ARRAY,new int[]{0});}};
    XClz clz2 = clz._intern(XCons.ARRAY);
    assert clz==clz2;
    clz._jname = jname;
    return clz;
  }


  public static XClz make_tuple( XType... clzs ) {
    XClz clz = XClz.mallocLen(true,"ecstasy.collections","","Tuple",clzs.length);
    clz._tns = new String[clzs.length];
    for( int i=0; i<clzs.length; i++ )
      clz._tns[i] = (""+i).intern();
    clz._xts = clzs;
    XClz clz2 = clz._intern();
    if( clz2 != clz ) return clz2;
    clz._clz = XXTC._clz;
    clz._super = XXTC;
    clz._jpack = "ecstasy.collections";
    clz._jname = "Tuple";
    // TODO: Name is same but need to honor parameters
    // TODO: Name should not be same
    return clz;
  }
}
