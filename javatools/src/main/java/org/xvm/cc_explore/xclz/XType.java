package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.xrun.*;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.*;

import java.util.HashMap;
import java.util.Arrays;

// Concrete java types from XTC types.  All instances are interned so deep
// compares are free/shallow.  
@SuppressWarnings("StaticInitializerReferencesSubClass")
public abstract class XType {
  // The intern table
  private static final HashMap<XType,XType> INTERN = new HashMap<>();

  @Override public final String toString() { return str(new SB()).toString(); }
  public SB p(SB sb) { return str(sb); }
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
  public static class JType extends XType {
    private static JType FREE = null;
    public String _jtype;
    private JType(String j) { _jtype = j; }
    static JType make(String j) {
      assert j.indexOf('<') == -1; // No generics here
      if( FREE==null ) FREE = new JType(null);
      FREE._hash = 0;
      FREE._jtype = j;
      JType jt = (JType)INTERN.get(FREE);
      if( jt!=null ) return jt;
      INTERN.put(jt=FREE,FREE);
      FREE=null;
      return jt;
    }
    @Override public boolean is_prim_base() { return XBOX.containsKey(this); }
    @Override SB str( SB sb ) { return sb.p(_jtype); }
    @Override SB clz( SB sb ) { return sb.p(_jtype); }
    @Override boolean eq(XType xt) { return _jtype.equals(((JType)xt)._jtype);  }
    @Override int hash() { return _jtype.hashCode(); }
  }

  // Basically a Java class as a tuple
  public static class JTupleType extends XType {
    private static JTupleType FREE = new JTupleType(null);
    public XType[] _xts;
    private JTupleType(XType[] xts) { _xts = xts; }
    public static JTupleType make(XType... xts) {
      FREE._hash = 0;
      FREE._xts = xts;
      JTupleType jtt = (JTupleType)INTERN.get(FREE);
      if( jtt!=null ) return jtt;
      INTERN.put(jtt=FREE,FREE);
      FREE = new JTupleType(null);
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
    @Override boolean eq(XType xt) { return Arrays.equals(_xts,((JTupleType)xt)._xts); }
    @Override int hash() { return Arrays.hashCode(_xts); }
  }
  

  // Basically a Java class as an array
  public static class JAryType extends XType {
    private static JAryType FREE = new JAryType(null);
    public XType _e;
    private JAryType(XType e) { _e = e; }
    public static JAryType make(XType e) {
      FREE._hash = 0;
      FREE._e = e;
      JAryType jtt = (JAryType)INTERN.get(FREE);
      if( jtt!=null ) return jtt;
      INTERN.put(jtt=FREE,FREE);
      FREE = new JAryType(null);
      return jtt;
    }
    @Override boolean is_prim_base() { return _e.is_prim_base(); }
    @Override SB str( SB sb ) {
      if( _e.is_prim_base() && _e instanceof JType )
        return _e.p(sb.p("Ary")); // Primitives print as "AryLong" or "AryChar" classes
      return clz(sb);
    }
    @Override SB clz( SB sb ) {
      return _e.str(sb.p("Ary<")).p(">");
    }
    @Override boolean eq(XType xt) { return _e == ((JAryType)xt)._e; }
    @Override int hash() { return _e.hashCode() ^ 123456789; }
  }

  
  // Basically a Java class as a function
  public static class JFunType extends XType {
    private static JFunType FREE = new JFunType(null,null);
    public XType[] _args, _rets;
    private JFunType(XType[] args, XType[] rets) { _args = args;  _rets = rets; }
    public static JFunType make(XType[] args, XType[] rets) {
      FREE._hash = 0;
      FREE._args = args;
      FREE._rets = rets;
      JFunType jtt = (JFunType)INTERN.get(FREE);
      if( jtt!=null ) return jtt;
      INTERN.put(jtt=FREE,FREE);
      FREE = new JFunType(null,null);
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
      sb.p("XFunc").p(_args.length).p("$");
      for( XType xt : _args )
        xt.str(sb).p("$");
      return sb.unchar();
    }
    // Using shallow equals,hashCode, not deep, because the parts are already interned
    @Override boolean eq(XType xt) { return Arrays.equals(_args,((JFunType)xt)._args) && Arrays.equals(_rets,((JFunType)xt)._rets); }
    @Override int hash() { return Arrays.hashCode(_args) ^ Arrays.hashCode(_rets); }
  }

  // Java non-primitive classes
  public static JType CONSOLE= JType.make("Console");
  public static JType EXCEPTION = JType.make("Exception");  
  public static JType ILLARGX= JType.make("IllegalArgumentException");  
  public static JType ILLSTATEX = JType.make("IllegalStateX");  
  public static JType ITER64 = JType.make("XIter64");
  public static JType JBOOL  = JType.make("Boolean");
  public static JType JCHAR  = JType.make("Character");
  public static JType JINT   = JType.make("Integer");
  public static JType JLONG  = JType.make("Long");
  public static JType JNULL  = JType.make("Nullable");
  public static JType JUBYTE = JType.make("XUByte");  
  public static JType OBJECT = JType.make("Object");
  public static JType ORDERED= JType.make("Ordered");
  public static JType RANGE  = JType.make("Range");
  public static JType RANGEIE= JType.make("RangeIE");
  public static JType RANGEII= JType.make("RangeII");
  public static JType STRING = JType.make("String");  
  public static JType STRINGBUFFER = JType.make("StringBuffer");  
  public static JType XCONSOLE=JType.make("XConsole");

  // Java primitives or primitive classes
  public static JType BOOL = JType.make("boolean");
  public static JType CHAR = JType.make("char");
  public static JType LONG = JType.make("long");
  public static JType INT  = JType.make("int");
  
  public static JType FALSE= JType.make("false");
  public static JType NULL = JType.make("null");
  public static JType TRUE = JType.make("true");
  public static JType VOID = JType.make("void");

  public static JAryType ARYCHAR= JAryType.make(CHAR);
  public static JAryType ARYLONG= JAryType.make(LONG);
  public static JTupleType COND_CHAR = JTupleType.make(BOOL,CHAR);
  

  
  // A set of common XTC classes, and their Java replacements.
  // These are NOT parameterized.
  static final HashMap<String,JType> XJMAP = new HashMap<>() {{
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

  // Convert a Java primitive to the Java object version.
  private static final HashMap<JType,JType> XBOX = new HashMap<>() {{
      put(BOOL,JBOOL);
      put(CHAR,JCHAR);
      put(INT ,JINT);
      put(LONG,JLONG);
    }};
  public XType box() {
    JType jt = XBOX.get(this);
    return jt==null ? this : jt;
  }
  // Convert a Java wrapped primitive to the unwrapped primitive
  static final HashMap<JType,JType> UNBOX = new HashMap<>() {{
      put(JBOOL,BOOL);
      put(JCHAR,CHAR);
      put(JINT ,INT );
      put(JLONG,LONG);
      put(JNULL,NULL);
    }};
  XType unbox() {
    JType jt = UNBOX.get(this);
    return jt==null ? this : jt;
  } 
  public String ztype() { return XBOX.containsKey(this) ? "0" : "null"; }
 

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
        if( clz._name.equals("Null" ) ) return JType.NULL;
        if( clz._name.equals("True" ) ) return JType.TRUE;
        if( clz._name.equals("False") ) return JType.FALSE;
        throw XEC.TODO();
      }
      String key = clz._name + "+" + clz._path._str;
      JType val = XJMAP.get(key);
      if( val!=null )
        return boxed ? val.box() : val;
      throw XEC.TODO();
    }

    if( tc instanceof ParamTCon ptc ) {
      ClassPart clz = ((ClzCon)ptc._con).clz();

      // These XTC classes are all intercepted and directly implemented in Java
      if( clz._name.equals("Array") && clz._path._str.equals("ecstasy/collections/Array.x") ) {
        XType telem = ptc._parms==null ? null : xtype(ptc._parms[0],false);
        if( telem==JType.LONG ) return ARYLONG; // Java ArrayList specialized to int64
        if( telem==JType.CHAR ) return ARYCHAR; // Java ArrayList specialized to char
        return JAryType.make(telem);   // Shortcut class
      }

      XType telem = ptc._parms==null ? null : xtype(ptc._parms[0],true);
      
      // All the long-based ranges, intervals and interators are just Ranges now.
      if( clz._name.equals("Range"   ) && clz._path._str.equals("ecstasy/Range.x"   ) ||
          clz._name.equals("Interval") && clz._path._str.equals("ecstasy/Interval.x") ) {
        if( telem==JType.LONG || telem==JType.JLONG ) return JType.RANGE; // Shortcut class
        else throw XEC.TODO();
      }

      if( clz._name.equals("Iterator") && clz._path._str.equals("ecstasy/Iterator.x") ) {
        if( telem==JType.LONG || telem==JType.JLONG ) return JType.ITER64; // Shortcut class
        else throw XEC.TODO();
      }

    //  if( clz._name.equals("List") && clz._path._str.equals("ecstasy/collections/List.x") )
    //    return "Ary<"+telem+">"; // Shortcut class

      if( clz._name.equals("Tuple") && clz._path._str.equals("ecstasy/collections/Tuple.x") )
        return Tuple.make_class(XClzBuilder.XCLASSES, ptc._parms);

    //  if( clz._name.equals("Map") && clz._path._str.equals("ecstasy/collections/Map.x") )
    //    return XMap.make_class(XClzBuilder.XCLASSES, ptc._parms);
    //  
    //  // Attempt to use the Java class name
    //  if( clz._name.equals("Type") && clz._path._str.equals("ecstasy/reflect/Type.x") )
    //    return telem + ".class";

      if( clz._name.equals("Function") && clz._path._str.equals("ecstasy/reflect/Function.x") ) {
        XType rets = xtype(ptc._parms[1],false);
        return JFunType.make(((JTupleType)telem)._xts, ((JTupleType)rets)._xts);
      }
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
        return boxed ? JType.JLONG : JType.LONG;
      throw XEC.TODO();
    }

    if( tc instanceof StringCon )
      return JType.STRING;

    if( tc instanceof EnumCon econ ) {
      ClassPart clz = (ClassPart)econ.part();
      return JType.make(clz._super._name).unbox();
    }

    if( tc instanceof LitCon lit ) {
      if( lit._f==Const.Format.IntLiteral )
        return boxed ? JType.JLONG : JType.LONG;
      throw XEC.TODO();
    }

    if( tc instanceof AryCon ac )
      return xtype(ac.type(),false);

    if( tc instanceof MethodCon mcon )  {
      MethodPart meth = (MethodPart)mcon.part();
      String name = meth._name;
      if( !name.equals("->") && meth._par._par instanceof MethodPart pmeth )
        name = pmeth._name+"$"+meth._name;
      return JType.make(name);
    }

    if( tc instanceof SingleCon con0 )
      return JType.make(XClzBuilder.java_class_name(con0.part()._name));

    throw XEC.TODO();
  }  

}
