package org.xvm.xtc;

import java.util.HashMap;

import static org.xvm.xtc.XClz.XXTC;
import static org.xvm.xtc.XClz.CONST;
import static org.xvm.xtc.XClz.ENUM;

public abstract class XCons {
  
  // Java non-primitive classes
  public static XClz JBOOL  = XClz.make_java("ecstasy","Boolean",ENUM);
  public static XClz JCHAR  = XClz.make_java("ecstasy.text","Char",null);
  public static XClz JSTRING= XClz.make_java("ecstasy.text","String",CONST);
  public static XClz JOBJECT= XClz.make_java("ecstasy","Object",null);
  public static XClz EXCEPTION = XClz.make_java("java.lang","Exception",false,"ecstasy","Exception",CONST);

  // Java primitives or primitive classes
  public static XBase BOOL = XBase.make("boolean");
  public static XBase BYTE = XBase.make("byte");
  public static XBase CHAR = XBase.make("char");
  public static XBase SHORT= XBase.make("short");
  public static XBase INT  = XBase.make("int");
  public static XBase LONG = XBase.make("long");
  public static XBase DOUBLE = XBase.make("double");
  public static XBase STRING = XBase.make("String");
  
  public static XBase VOID = XBase.make("void");
  public static XBase TRUE = XBase.make("true");
  public static XClz JTRUE = XClz.make_java("ecstasy.Boolean","True" ,JBOOL);
  public static XBase FALSE= XBase.make("false");
  public static XClz JFALSE= XClz.make_java("ecstasy.Boolean","False",JBOOL);
  public static XBase NULL = XBase.make("null");
  public static XClz JNULL = XClz.make_java("ecstasy","Nullable",null);
  
  // A set of common XTC classes, and their Java replacements...
  // AND these take the default import path from "org.xvm.xec.ecstasy...".
  public static XClz APPENDERCHAR= XClz.make_java("ecstasy","Appenderchar",false,"ecstasy","Appender",null,"Element",JCHAR);
  public static XClz CONSOLE     = XClz.make_java("ecstasy.io","Console",null);
  public static XClz HASHABLE    = XClz.make_java("ecstasy.collections","Hashable",null);
  public static XClz ILLARGX     = XClz.make_java("XTC","IllegalArgument",false,"ecstasy","IllegalArgument",null);
  public static XClz ILLSTATEX   = XClz.make_java("XTC","IllegalState"   ,false,"ecstasy","IllegalState"   ,null);
  public static XClz ITERABLE    = XClz.make_java("ecstasy","Iterable",null,"Element",XXTC);
  public static XClz ITERATOR    = XClz.make_java("ecstasy","Iterator",null,"Element",XXTC);
  public static XClz MUTABILITY  = XClz.make_java("ecstasy.collections.Array","Mutability",ENUM);
  public static XClz ORDERABLE   = XClz.make_java("ecstasy","Orderable",null);
  public static XClz ORDERED     = XClz.make_java("ecstasy","Ordered",ENUM);
  public static XClz RANGE       = XClz.make_java("ecstasy","Range",false,"ecstasy","Range",CONST,"Element",ORDERABLE);
  public static XClz RANGEEE     = XClz.make_java("ecstasy","RangeEE",RANGE); // No Ecstasy matching class
  public static XClz RANGEIE     = XClz.make_java("ecstasy","RangeIE",RANGE); // No Ecstasy matching class
  public static XClz RANGEII     = XClz.make_java("ecstasy","RangeII",RANGE); // No Ecstasy matching class
  public static XClz SERVICE     = XClz.make_java("ecstasy","Service",null);
  public static XClz STRINGBUFFER= XClz.make_java("ecstasy.text","StringBuffer",null);
  public static XClz TYPE        = XClz.make_java("ecstasy.reflect","Type",null,"DataType",XXTC,"OuterType",XXTC);
  public static XClz UNSUPPORTEDOPERATION = XClz.make_java("ecstasy","UnsupportedOperation",null);

  public static XClz NUMBER  = XClz.make_java("ecstasy.numbers","Number",CONST);
  public static XClz BIT     = XClz.make_java("ecstasy.numbers","Bit",CONST);
  
  public static XClz INTNUM  = XClz.make_java("ecstasy.numbers","IntNumber" ,NUMBER);
  public static XClz JBYTE   = XClz.make_java("ecstasy.numbers","Int8"      ,INTNUM);
  public static XClz JINT16  = XClz.make_java("ecstasy.numbers","Int16"     ,INTNUM);
  public static XClz JINT32  = XClz.make_java("ecstasy.numbers","Int32"     ,INTNUM);
  public static XClz JLONG   = XClz.make_java("ecstasy.numbers","Int64"     ,INTNUM);
  public static XClz JINT128 = XClz.make_java("ecstasy.numbers","Int128"    ,INTNUM);
  public static XClz JINTN   = XClz.make_java("ecstasy.numbers","IntN"      ,INTNUM);
  public static XClz UINTNUM = XClz.make_java("ecstasy.numbers","UIntNumber",INTNUM);
  public static XClz JUINT8  = XClz.make_java("ecstasy.numbers","UInt8"    ,UINTNUM);
  public static XClz JUINT16 = XClz.make_java("ecstasy.numbers","UInt16"   ,UINTNUM);
  public static XClz JUINT32 = XClz.make_java("ecstasy.numbers","UInt32"   ,UINTNUM);
  public static XClz JUINT64 = XClz.make_java("ecstasy.numbers","UInt64"   ,UINTNUM);
  public static XClz JUINT128= XClz.make_java("ecstasy.numbers","UInt128"  ,UINTNUM);
  public static XClz JUINTN  = XClz.make_java("ecstasy.numbers","UIntN"    ,UINTNUM);
  
  public static XClz FPNUM       = XClz.make_java("ecstasy.numbers","FPNumber",NUMBER);  
  public static XClz BINARYFP    = XClz.make_java("ecstasy.numbers","BinaryFPNumber",FPNUM);
  public static XClz JDOUBLE     = XClz.make_java("ecstasy.numbers","Float64" ,BINARYFP);
  public static XClz FLOAT128    = XClz.make_java("ecstasy.numbers","Float128",BINARYFP);
  
  public static XClz DECIMALFP   = XClz.make_java("ecstasy.numbers","DecimalFPNumber",FPNUM);
  public static XClz DEC64       = XClz.make_java("ecstasy.numbers","Dec64" ,DECIMALFP);
  public static XClz DEC128      = XClz.make_java("ecstasy.numbers","Dec128",DECIMALFP);
  
  public static XClz ROUNDING    = XClz.make_java("ecstasy.numbers.FPNumber","Rounding",ENUM);

  // This is a mixin type
  public static XClz FUTUREVAR   = XClz.make_java("ecstasy.annotations","FutureVar",true,"ecstasy.annotations","FutureVar",null,"Referent",XXTC);
  
  // Convert a Java primitive to the Java object version.
  static final HashMap<XBase, XClz> XBOX = new HashMap<>() {{
      put(NULL ,JNULL );
      put(BOOL ,JBOOL );
      put(BYTE ,JBYTE );
      put(CHAR ,JCHAR );
      put(SHORT,JINT16);
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
      put(JDOUBLE,DOUBLE);
      put(JLONG ,LONG );
      put(JTRUE ,TRUE );
      put(JFALSE,FALSE);
      put(JSTRING,STRING);
    }};

  // Generic Java Object[], this maps to the ecstasy.collections.Array class
  public static XClz ARYXTC   = XClz.make_java("ecstasy.collections","AryXTC",true ,"ecstasy.collections","Array",null,"Element",XXTC  );
  // Generic array; element type is unknown, and could be primitives or Java Object.
  public static XClz ARRAY    = XClz.make_java_ary("Array",true, VOID );
  // Java primitive arrays                                                             
  public static XClz ARYBOOL  = XClz.make_java_ary("Aryboolean",false,JBOOL  );
  public static XClz ARYCHAR  = XClz.make_java_ary("Arychar"   ,false,JCHAR  );
  public static XClz ARYSTRING= XClz.make_java_ary("AryString" ,false,JSTRING);
  public static XClz ARYLONG  = XClz.make_java_ary("Arylong"   ,false,JLONG  );
  public static XClz ARYUBYTE = XClz.make_java_ary("AryUInt8"  ,false,JUINT8 );
  
  // Type sig for Iterator<Int64>, which returns a non-XTC type "Iteratorlong"
  // which supports a "long next8()" as well as the expected "Int64 next()".
  // No corresponding XTC class.
  static XClz ITERATORLONG = XClz.make_java("ecstasy.collections.Arylong","Iterlong",false,"ecstasy","Iterator",null,"Element",JLONG);

  // These are always expanded to some Java constant
  public static XClz INTLITERAL = XClz.make_java("ecstasy.numbers","IntLiteral",false,"ecstasy.numbers","IntLiteral",CONST);
  public static XClz  FPLITERAL = XClz.make_java("ecstasy.numbers", "FPLiteral",false,"ecstasy.numbers", "FPLiteral",CONST);

  // Some tuples
  public static XClz TUPLE0 = XClz.make_tuple();
  public static XClz COND_CHAR = XClz.make_tuple(BOOL,CHAR);

}
