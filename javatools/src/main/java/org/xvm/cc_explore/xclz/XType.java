package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.BiExprAST.Operator;
import org.xvm.cc_explore.*;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.*;
import org.xvm.cc_explore.xrun.XProp;

import java.util.HashMap;
import java.util.Arrays;

// Concrete java types from XTC types.  All instances are interned so deep
// compares are free/shallow.  
@SuppressWarnings("StaticInitializerReferencesSubClass")
public abstract class XType {
  // The intern table
  private static final HashMap<XType,XType> INTERN = new HashMap<>();

  // Compiled
  private static final HashMap<String,Base> XJMAP = new HashMap<>();
  static void install(ClassPart clz, String name) {
    XType old = XJMAP.put(xjkey(clz),Base.make(name));
    assert old==null;
  }
  
  @Override public final String toString() { return str(new SB()).toString(); }
  public SB p(SB sb) { return clz(sb); }
  abstract SB str( SB sb );
  public final String clz() { return clz(new SB()).toString(); }
  abstract SB clz( SB sb );     // Class string
  abstract boolean is_prim_base();

  abstract boolean eq( XType xt );
  @Override public boolean equals(Object o) {
    if( this==o ) return true;
    if( this.getClass() != o.getClass() ) return false;
    return eq((XType)o);
  }
    
  int _hash;
  abstract int hash();
  @Override final public int hashCode() {
    return _hash==0 ? (_hash=hash()) : _hash;
  }
  
  // Shallow Java class name, or type. 
  public static class Base extends XType {
    private static Base FREE = null;
    public String _jtype;
    private Base( String j) { _jtype = j; }
    static Base make( String j) {
      assert j.indexOf('<') == -1; // No generics here
      if( FREE==null ) FREE = new Base(null);
      FREE._hash = 0;
      FREE._jtype = j;
      Base jt = (Base)INTERN.get(FREE);
      if( jt!=null ) return jt;
      INTERN.put(jt=FREE,FREE);
      FREE=null;
      return jt;
    }
    @Override public boolean is_prim_base() { return XBOX.containsKey(this); }
    @Override SB str( SB sb ) { return sb.p(_jtype); }
    @Override SB clz( SB sb ) { return sb.p(_jtype); }
    @Override boolean eq(XType xt) { return _jtype.equals(((Base)xt)._jtype);  }
    @Override int hash() { return _jtype.hashCode(); }
  }

  // Basically a Java class as a tuple
  public static class Tuple extends XType {
    private static Tuple FREE = new Tuple(null);
    public XType[] _xts;
    private Tuple( XType[] xts) { _xts = xts; }
    public static Tuple make( XType... xts) {
      FREE._hash = 0;
      FREE._xts = xts;
      Tuple jtt = (Tuple)INTERN.get(FREE);
      if( jtt!=null ) return jtt;
      INTERN.put(jtt=FREE,FREE);
      FREE = new Tuple(null);
      return jtt;
    }
    @Override public boolean is_prim_base() { return false; }

    @Override SB str( SB sb ) {
      sb.p("( ");
      for( XType xt : _xts )
        xt.str(sb).p(",");
      return sb.unchar().p(" )");
    }
    @Override SB clz( SB sb ) {
      sb.p("Tuple").p(_xts.length).p("$");
      for( XType xt : _xts )
        xt.str(sb).p("$");
      return sb.unchar();
    }
    // Using shallow equals,hashCode, not deep, because the parts are already interned
    @Override boolean eq(XType xt) { return Arrays.equals(_xts,((Tuple)xt)._xts); }
    @Override int hash() { return Arrays.hashCode(_xts); }
  }
  

  // Basically a Java class as an array
  public static class Ary extends XType {
    private static Ary FREE = new Ary(null);
    public XType _e;
    private Ary( XType e) { _e = e; }
    public static Ary make( XType e) {
      FREE._hash = 0;
      FREE._e = e;
      Ary jtt = (Ary)INTERN.get(FREE);
      if( jtt!=null ) return jtt;
      INTERN.put(jtt=FREE,FREE);
      FREE = new Ary(null);
      return jtt;
    }
    @Override boolean is_prim_base() { return _e.is_prim_base(); }
    @Override SB str( SB sb ) {
      if( _e.is_prim_base() && _e instanceof Base )
        return _e.p(sb.p("Ary")); // Primitives print as "Arylong" or "Arychar" classes
      return _e.str(sb.p("Ary<")).p(">");
    }
    @Override SB clz( SB sb ) { return str(sb); }
    @Override boolean eq(XType xt) { return _e == ((Ary)xt)._e; }
    @Override int hash() { return _e.hashCode() ^ 123456789; }
  }

  
  // Basically a Java class as a function
  public static class Fun extends XType {
    private static Fun FREE = new Fun(null,null);
    public XType[] _args, _rets;
    private Fun( XType[] args, XType[] rets) { _args = args;  _rets = rets; }
    public static Fun make( XType[] args, XType[] rets) {
      FREE._hash = 0;
      FREE._args = args;
      FREE._rets = rets;
      Fun jtt = (Fun)INTERN.get(FREE);
      if( jtt!=null ) return jtt;
      INTERN.put(jtt=FREE,FREE);
      FREE = new Fun(null,null);
      return jtt;
    }
    public int nargs() { return _args.length; }
    @Override boolean is_prim_base() { return false; }
    @Override SB str( SB sb ) {
      sb.p("{ ");
      for( XType xt : _args )
        xt.str(sb).p(",");
      sb.unchar().p(" -> ");
      for( XType xt : _rets )
        xt.str(sb).p(",");
      return sb.unchar().p(" }");
    }
    @Override SB clz( SB sb ) {
      sb.p("Fun").p(_args.length).p("$");
      for( XType xt : _args )
        xt.str(sb).p("$");
      return sb.unchar();
    }
    // Using shallow equals,hashCode, not deep, because the parts are already interned
    @Override boolean eq(XType xt) { return Arrays.equals(_args,((Fun)xt)._args) && Arrays.equals(_rets,((Fun)xt)._rets); }
    @Override int hash() { return Arrays.hashCode(_args) ^ Arrays.hashCode(_rets); }

    public Fun make_class( HashMap<String,String> cache ) {
      String tclz = clz();
      if( cache.containsKey(tclz) ) return this;
      /* Gotta build one.  Looks like:
         interface Fun2$long$String {
         long call(long l, String s);
         }
      */
      SB sb = new SB();
      sb.p("interface ").p(tclz).p(" {").nl().ii();
      sb.ip("abstract ");
      // Return
      if( _rets==null || _rets.length==0 ) sb.p("void");
      else if( _rets.length==1 ) _rets[0].p(sb);
      else throw XEC.TODO();
      sb.p(" call( ");
      int i=0;
      for( XType arg : _args )
        arg.p(sb).p(" x").p(i++).p(",");
      sb.unchar().p(");").nl();
      // Class end
      sb.di().ip("}").nl();
      cache.put(tclz,sb.toString());               
      return this;
    }
  }

  // Java non-primitive classes
  public static Base CONSOLE= Base.make("Console");
  public static Base EXCEPTION = Base.make("Exception");
  public static Base ILLARGX= Base.make("IllegalArgumentException");
  public static Base ILLSTATEX = Base.make("IllegalStateX");
  public static Base ITER64 = Base.make("XIter64");
  public static Base JBOOL  = Base.make("Boolean");
  public static Base JCHAR  = Base.make("Character");
  public static Base JINT   = Base.make("Integer");
  public static Base JLONG  = Base.make("Long");
  public static Base JNULL  = Base.make("Nullable");
  public static Base JUBYTE = Base.make("XUByte");
  public static Base OBJECT = Base.make("Object");
  public static Base ORDERED= Base.make("Ordered");
  public static Base RANGE  = Base.make("Range");
  public static Base RANGEIE= Base.make("RangeIE");
  public static Base RANGEII= Base.make("RangeII");
  public static Base STRING = Base.make("String");
  public static Base STRINGBUFFER = Base.make("StringBuffer");

  // Java primitives or primitive classes
  public static Base BOOL = Base.make("boolean");
  public static Base CHAR = Base.make("char");
  public static Base LONG = Base.make("long");
  public static Base INT  = Base.make("int");
  
  public static Base FALSE= Base.make("false");
  public static Base NULL = Base.make("null");
  public static Base TRUE = Base.make("true");
  public static Base VOID = Base.make("void");

  public static Ary ARYCHAR= Ary.make(CHAR);
  public static Ary ARYLONG= Ary.make(LONG);
  public static Tuple COND_CHAR = Tuple.make(BOOL,CHAR);
  

  
  // A set of common XTC classes, and their Java replacements.
  // These are NOT parameterized.
  static final HashMap<String, Base> BASE_XJMAP = new HashMap<>() {{
      put("Boolean+ecstasy/Boolean.x",BOOL);
      put("Char+ecstasy/text/Char.x",CHAR);
      put("Console+ecstasy/io/Console.x",CONSOLE);
      put("Exception+ecstasy/Exception.x",EXCEPTION);
      put("IllegalArgument+ecstasy.x",ILLARGX);
      put("IllegalState+ecstasy.x",ILLSTATEX);
      put("Int64+ecstasy/numbers/Int64.x",LONG);
      put("UInt8+ecstasy/numbers/UInt8.x",JUBYTE);
      put("IntLiteral+ecstasy/numbers/IntLiteral.x",LONG);
      put("Object+ecstasy/Object.x",OBJECT);
      put("String+ecstasy/text/String.x",STRING);
      put("StringBuffer+ecstasy/text/StringBuffer.x",STRINGBUFFER);
    }};
  private static String xjkey(ClassPart clz) { return clz._name + "+" + clz._path._str; }

  // Convert a Java primitive to the Java object version.
  private static final HashMap<Base, Base> XBOX = new HashMap<>() {{
      put(BOOL,JBOOL);
      put(CHAR,JCHAR);
      put(INT ,JINT);
      put(LONG,JLONG);
    }};
  public XType box() {
    Base jt = XBOX.get(this);
    return jt==null ? this : jt;
  }
  // Convert a Java wrapped primitive to the unwrapped primitive
  static final HashMap<Base, Base> UNBOX = new HashMap<>() {{
      put(JBOOL,BOOL);
      put(JCHAR,CHAR);
      put(JINT ,INT );
      put(JLONG,LONG);
      put(JNULL,NULL);
    }};
  XType unbox() {
    Base jt = UNBOX.get(this);
    return jt==null ? this : jt;
  } 
  public boolean primeq() { return XBOX.containsKey(this); }
  public String ztype() { return primeq() ? "0" : "null"; }
  
  // Either "==name" or ".equals(name)"
  public SB do_eq(SB sb, String name) {
    return primeq() ? sb.p("==").p(name) : sb.p(".equals(").p(name).p(")");
  }
 

  // Legacy interface returning a string
  public static String jtype( Const tc, boolean boxed ) {
    return xtype(tc,boxed).toString();
  }

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
    if( tc instanceof TermTCon ttc ) {
      ClassPart clz = (ClassPart)ttc.part();
      if( clz._path==null ) {
        if( clz._name.equals("Null" ) ) return Base.NULL;
        if( clz._name.equals("True" ) ) return Base.TRUE;
        if( clz._name.equals("False") ) return Base.FALSE;
        throw XEC.TODO();
      }
      // Check the common base classes
      String xjkey = xjkey(clz);
      Base val = BASE_XJMAP.get(xjkey);
      if( val!=null )  return boxed ? val.box() : val;
      // Check installed classes
      val = XJMAP.get(xjkey);
      if( val!=null )  return val;
      // Same module compiles shortly, with the short name
      XType clzpar = Base.make(XClzBuilder.java_class_name(clz._par._name));
      if( clzpar==XClzBuilder.MOD_TYPE )
        return Base.make(XClzBuilder.java_class_name(clz._name));
      // TODO: Figure out cross-XTC-module naming
      throw XEC.TODO();
    }

    if( tc instanceof ParamTCon ptc ) {
      ClassPart clz = ((ClzCon)ptc._con).clz();

      // These XTC classes are all intercepted and directly implemented in Java
      if( clz._name.equals("Array") && clz._path._str.equals("ecstasy/collections/Array.x") ) {
        XType telem = ptc._parms==null ? null : xtype(ptc._parms[0],false);
        if( telem== Base.LONG ) return ARYLONG; // Java ArrayList specialized to int64
        if( telem== Base.CHAR ) return ARYCHAR; // Java ArrayList specialized to char
        return Ary.make(telem);   // Shortcut class
      }

      if( clz._name.equals("Function") && clz._path._str.equals("ecstasy/reflect/Function.x") ) {
        Tuple args = xtype(((ParamTCon)ptc._parms[0])._parms);
        Tuple rets = xtype(((ParamTCon)ptc._parms[1])._parms);
        return Fun.make(args._xts, rets._xts).make_class(XClzBuilder.XCLASSES);
      }

      XType telem = ptc._parms==null ? null : xtype(ptc._parms[0],true);
      
      // All the long-based ranges, intervals and iterators are just Ranges now.
      if( clz._name.equals("Range"   ) && clz._path._str.equals("ecstasy/Range.x"   ) ||
          clz._name.equals("Interval") && clz._path._str.equals("ecstasy/Interval.x") ) {
        if( telem== Base.LONG || telem== Base.JLONG ) return Base.RANGE; // Shortcut class
        else throw XEC.TODO();
      }

      if( clz._name.equals("Iterator") && clz._path._str.equals("ecstasy/Iterator.x") ) {
        if( telem== Base.LONG || telem== Base.JLONG ) return Base.ITER64; // Shortcut class
        else throw XEC.TODO();
      }

    //  if( clz._name.equals("List") && clz._path._str.equals("ecstasy/collections/List.x") )
    //    return "Ary<"+telem+">"; // Shortcut class

      if( clz._name.equals("Tuple") && clz._path._str.equals("ecstasy/collections/Tuple.x") )
        return org.xvm.cc_explore.xrun.Tuple.make_class(XClzBuilder.XCLASSES, xtype(ptc._parms));

    //  if( clz._name.equals("Map") && clz._path._str.equals("ecstasy/collections/Map.x") )
    //    return XMap.make_class(XClzBuilder.XCLASSES, ptc._parms);
    //  
    //  // Attempt to use the Java class name
    //  if( clz._name.equals("Type") && clz._path._str.equals("ecstasy/reflect/Type.x") )
    //    return telem + ".class";

      if( clz._name.equals("Appender") && clz._path._str.equals("ecstasy/Appender.x") )
        throw XEC.TODO();
      
      throw XEC.TODO();
    }

    if( tc instanceof ImmutTCon itc )
      return xtype(itc.icon(),boxed); // Ignore immutable for now

    // Generalized union types gonna wait awhile.
    // Right now, allow null unions only
    if( tc instanceof UnionTCon utc ) {
      if( ((ClzCon)utc._con1).clz()._name.equals("Nullable") )
        return xtype(utc._con2,true);
      throw XEC.TODO();
    }

    if( tc instanceof IntCon itc ) {
      if( itc._f == Const.Format.Int64 )
        return boxed ? Base.JLONG : Base.LONG;
      throw XEC.TODO();
    }

    if( tc instanceof StringCon )
      return Base.STRING;

    if( tc instanceof EnumCon econ ) {
      ClassPart clz = (ClassPart)econ.part();
      return Base.make(clz._super._name).unbox();
    }

    if( tc instanceof LitCon lit ) {
      if( lit._f==Const.Format.IntLiteral )
        return boxed ? Base.JLONG : Base.LONG;
      throw XEC.TODO();
    }

    if( tc instanceof AryCon ac )
      return xtype(ac.type(),false);

    if( tc instanceof MethodCon mcon )  {
      MethodPart meth = (MethodPart)mcon.part();
      String name = meth._name;
      if( !name.equals("->") && meth._par._par instanceof MethodPart pmeth )
        name = pmeth._name+"$"+meth._name;
      return Base.make(name);
    }

    // Property constant
    if( tc instanceof PropCon prop )
      return xtype(((PropPart)prop.part())._con,false);

    if( tc instanceof SingleCon con0 ) {
      if( con0.part() instanceof ModPart mod )
        return Base.make(XClzBuilder.java_class_name(mod._name));
      if( con0.part() instanceof PropPart prop )
        return Base.make(XProp.jname(prop));
      throw XEC.TODO();
    }
    
    throw XEC.TODO();
  }

  private static Tuple xtype( TCon[] cons ) {
    int N = cons==null ? 0 : cons.length;
    XType[] clzs = new XType[N];
    for( int i=0; i<N; i++ )
      clzs[i]=XType.xtype(cons[i],false);
    return Tuple.make(clzs);
  }

}
