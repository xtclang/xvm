package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.S;
import org.xvm.xtc.cons.Const;

import java.util.HashMap;

public abstract class XCons {
  public static XClz XXTC    = make_java("","XTC",null);
  public static XClz CONST   = make_java("ecstasy","Const",XXTC);
  public static XClz ENUM    = make_java("ecstasy","Enum" ,CONST);
  public static XClz SERVICE = make_java("ecstasy","Service",XXTC);
  
  // Java non-primitive classes
  public static XClz JBOOL  = make_java("ecstasy","Boolean",ENUM);
  public static XClz JCHAR  = make_java("ecstasy.text","Char",null);
  public static XClz JSTRING= make_java("ecstasy.text","String",CONST);
  public static XClz JOBJECT= make_java("ecstasy","Object",null);
  public static XClz EXCEPTION = make_java("java.lang","Exception",false,"ecstasy","Exception",CONST);

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
  public static XClz JNULL = make_java("ecstasy","Nullable",null);
  
  // A set of common XTC classes, and their Java replacements...
  // AND these take the default import path from "org.xvm.xec.ecstasy...".
  public static XClz APPENDERCHAR= make_java("ecstasy","Appenderchar",false,"ecstasy","Appender",null,"Element",JCHAR);
  public static XClz CONSOLE     = make_java("ecstasy.io","Console",null);
  public static XClz HASHABLE    = make_java("ecstasy.collections","Hashable",null);
  public static XClz ILLARGX     = make_java("XTC","IllegalArgument",false,"ecstasy","IllegalArgument",null);
  public static XClz ILLSTATEX   = make_java("XTC","IllegalState"   ,false,"ecstasy","IllegalState"   ,null);
  public static XClz NOTIMPL     = make_java("XTC","NotImplemented" ,false,"ecstasy","NotImplemented" ,null);
  public static XClz ITERABLE    = make_java("ecstasy","Iterable",null,"Element",XXTC);
  public static XClz ITERATOR    = make_java("ecstasy","Iterator",null,"Element",XXTC);
  public static XClz MUTABILITY  = make_java("ecstasy.collections.Array","Mutability",ENUM);
  public static XClz ORDERABLE   = make_java("ecstasy","Orderable",null);
  public static XClz ORDERED     = make_java("ecstasy","Ordered",ENUM);
  public static XClz RANGE       = make_java("ecstasy","Range",false,"ecstasy","Range",CONST,"Element",ORDERABLE);
  public static XClz RANGEEE     = make_java("ecstasy","RangeEE",RANGE); // No Ecstasy matching class
  public static XClz RANGEIE     = make_java("ecstasy","RangeIE",RANGE); // No Ecstasy matching class
  public static XClz RANGEII     = make_java("ecstasy","RangeII",RANGE); // No Ecstasy matching class
  public static XClz STRINGBUFFER= make_java("ecstasy.text","StringBuffer",null);
  public static XClz TYPE        = make_java("ecstasy.reflect","Type",null,"DataType",XXTC,"OuterType",XXTC);
  public static XClz UNSUPPORTEDOPERATION = make_java("ecstasy","UnsupportedOperation",null);

  public static XClz NUMBER  = make_java("ecstasy.numbers","Number",CONST);
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
  public static XClz JDOUBLE     = make_java("ecstasy.numbers","Float64" ,BINARYFP);
  public static XClz FLOAT128    = make_java("ecstasy.numbers","Float128",BINARYFP);
  
  public static XClz DECIMALFP   = make_java("ecstasy.numbers","DecimalFPNumber",FPNUM);
  public static XClz DEC64       = make_java("ecstasy.numbers","Dec64" ,DECIMALFP);
  public static XClz DEC128      = make_java("ecstasy.numbers","Dec128",DECIMALFP);
  
  public static XClz ROUNDING    = make_java("ecstasy.numbers.FPNumber","Rounding",ENUM);

  // This is a mixin type
  public static XClz FUTUREVAR   = make_java("ecstasy.annotations","FutureVar",true,"ecstasy.annotations","FutureVar",null,"Referent",XXTC);
  
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
  public static XClz ARRAY    = make_java("ecstasy.collections","Array",true ,"ecstasy.collections","Array",null,"Element",VOID);
  // Generic Java Object[], this maps to the ecstasy.collections.Array class
  public static XClz ARYXTC   = make_java_ary("AryXTC",false,XXTC  );
  // Java primitive arrays
  public static XClz ARYBOOL  = make_java_ary("Aryboolean",false,JBOOL  );
  public static XClz ARYCHAR  = make_java_ary("Arychar"   ,false,JCHAR  );
  public static XClz ARYSTRING= make_java_ary("AryString" ,false,JSTRING);
  public static XClz ARYLONG  = make_java_ary("Arylong"   ,false,JLONG  );
  public static XClz ARYUBYTE = make_java_ary("AryUInt8"  ,false,JUINT8 );

  // Type sig for Iterator<Int64>, which returns a non-XTC type "Iteratorlong"
  // which supports a "long next8()" as well as the expected "Int64 next()".
  // No corresponding XTC class.
  static XClz ITERATORLONG = make_java("ecstasy.collections.Arylong","Iterlong",false,"ecstasy","Iterator",null,null,JLONG);

  // These are always expanded to some Java constant
  public static XClz INTLITERAL = make_java("ecstasy.numbers","IntLiteral",false,"ecstasy.numbers","IntLiteral",CONST);
  public static XClz  FPLITERAL = make_java("ecstasy.numbers", "FPLiteral",false,"ecstasy.numbers", "FPLiteral",CONST);

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

  
  // Made from a Java class directly; the XTC class shows up later.
  // Fields are hand-added and need to match the ClazzPart later.
  public static XClz make_java( String pack, String name, XClz supr, Object... flds ) {
    return make_java(pack,name,true,pack,name,supr,flds);
  }
  public static XClz make_java( String jpack, String jname, boolean jparms, String pack, String name, XClz supr, Object... flds ) {
    XClz clz = XClz.make(pack,"",name,flds.length>>1);
    clz._jpack = jpack;
    clz._jname = jname;
    clz._jparms = jparms;
    clz._nTypeParms = flds.length>>1;
    for( int i=0; i<flds.length; i += 2 ) {
      clz._tnames[i>>1] = (String)flds[i];
      clz._xts[i>>1] = (XType)flds[i+1];
    }
    // XXTC root XClz has no XTC _clz
    if( pack.isEmpty() && S.eq(name,"XTC") )
      return clz._intern();

    // Walk the XTC REPO in parallel, and find the matching XTC class
    String[] packs = pack.split("\\.");
    assert packs[0].equals("ecstasy");
    ClassPart pclz = XEC.ECSTASY;
    for( int i=1; i<packs.length; i++ )
      pclz = (ClassPart)pclz.child(packs[i]);
    pclz = (ClassPart)pclz.child(name);
    // Some XClzs have no XTC equivalent
    if( pclz==null || pclz._tclz!=null ) {
      clz._super = supr;
    } else {
      // Fill in XTC class details
      assert pclz._tcons.length==clz._nTypeParms;
      pclz._tclz = clz;
      // Set the super
      XClz sup = XClz.get_super(pclz);
      assert supr==sup || supr==null;
      clz._super = sup;
      // Set mod and clz
      clz._mod = pclz.mod();
      clz._clz = pclz;
      clz._iface = pclz._f==Part.Format.INTERFACE;
    }
    XClz clz2 = clz._intern();
    assert clz==clz2;           // No prior versions of these java-based XClzs
    return clz;
  }

  // Java primitive array classes, no corresponding XTC class.
  // Treated as a specific instance of the generic Array class using concrete types.
  private static XClz make_java_ary( String jname, boolean jparms, XType xelem ) {
    XClz clz = XClz.make("ecstasy.collections","","Array",1);
    clz._jpack = "ecstasy.collections";
    clz._jname = jname;
    clz._jparms = jparms;
    clz._tnames[0] = null;      // Concrete generic type
    clz._xts[0] = xelem;
    clz._clz = XCons.ARRAY._clz;
    return clz._intern();
  }

  
  public static XClz make_tuple( XType... clzs ) {
    XClz clz = XClz.make("ecstasy.collections","","Tuple",clzs.length);
    clz._jpack = "ecstasy.collections";
    clz._jname = "Tuple";
    clz._jparms = true;
    for( int i=0; i<clzs.length; i++ )
      clz._tnames[i] = (""+i).intern();
    clz._xts = clzs;
    clz._clz = XXTC._clz;
    clz._super = XXTC;
    return clz._intern();
  }
}
