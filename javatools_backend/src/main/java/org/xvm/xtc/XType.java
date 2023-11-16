package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.xtc.cons.*;
import org.xvm.util.*;

import java.util.HashMap;
import java.util.Arrays;

// Concrete java types from XTC types.  All instances are interned so deep
// compares are free/shallow.  
@SuppressWarnings("StaticInitializerReferencesSubClass")
public abstract class XType {
  // The intern table
  private static final HashMap<XType,XType> INTERN = new HashMap<>();

  @Override public final String toString() { return str(new SB()).toString(); }
  public SB p(SB sb) { return clz(sb); }
  public abstract SB str( SB sb );
  public final String clz() { return clz(new SB()).toString(); }
  abstract public SB clz( SB sb );     // Class string
  abstract public boolean is_prim_base();

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
    public static Base make( String j) {
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
    @Override public SB str( SB sb ) { return sb.p(_jtype); }
    @Override public SB clz( SB sb ) { return sb.p(_jtype); }
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

    @Override public SB str( SB sb ) {
      sb.p("( ");
      for( XType xt : _xts )
        xt.str(sb).p(",");
      return sb.unchar().p(" )");
    }
    @Override public SB clz( SB sb ) {
      sb.p("Tuple").p(_xts.length).p("$");
      for( XType xt : _xts )
        xt.str(sb).p("$");
      return sb.unchar();
    }
    // Using shallow equals,hashCode, not deep, because the parts are already interned
    @Override boolean eq(XType xt) { return Arrays.equals(_xts,((Tuple)xt)._xts); }
    @Override int hash() { return Arrays.hashCode(_xts); }
  }
  
  // Basically a Java class as a 
  public static class Clz extends XType {
    private static final HashMap<ClassPart,Clz> ZINTERN = new HashMap<>();
    public final ClassPart _mod;      // Compilation unit class
    public final ClassPart _clz;      // Self class, can be compilation unit
    public final Clz _super;          // Super xtype or null
    public final String[] _flds;
    public final XType[] _xts;
    private Clz( ClassPart clz ) {
      assert ClzBuilder.CCLZ!=null; // Init error
      _super = clz._super==null ? null : make(clz._super);
      _mod = ClzBuilder.CCLZ;  // Compile unit class
      _clz = clz;
      int len=0;
      for( Part part : clz._name2kid.values() )
        if( part instanceof PropPart prop && find(_super,prop._name)==null )
          len++;
      _flds = new String[len];
      _xts  = new XType [len];
      len=0;
      for( Part part : clz._name2kid.values() )
        if( part instanceof PropPart prop && find(_super,prop._name)==null ) {
          _flds[len  ] = prop._name;
          _xts [len++] = xtype(prop._con,false);
        }
    }
    public static Clz make( ClassPart clz ) {
      // Check for a pre-cooked class
      String xjkey = xjkey(clz);
      Base val = BASE_XJMAP.get(xjkey);
      if( val != null ) throw XEC.TODO();
      return ZINTERN.computeIfAbsent(clz, k->new Clz(clz));
    }
    String name() { return _mod==_clz || _mod== ClzBuilder.CCLZ ? _clz._name : _mod._name+"."+_clz._name; }
    @Override public boolean is_prim_base() { return false; }
    static XType find(Clz clz, String fld) {
      for( ; clz!=null; clz = clz._super ) {
        int idx = S.find(clz._flds,fld);
        if( idx!= -1 )
          return clz._xts[idx];
      }
      return null;
    }

    @Override public SB str( SB sb ) {
      sb.p("class ").p(name());
      if( _super!=null ) sb.p(":").p(_super.name());
      sb.p(" {").nl();
      for( int i=0; i<_flds.length; i++ )
        _xts[i].str(sb.p("  ").p(_flds[i]).p(":")).p(";").nl();
      return sb.p("}").nl();
    }
    @Override public SB clz( SB sb ) { return sb.p(name()); }
    // Using shallow equals,hashCode, not deep, because the parts are already interned
    @Override boolean eq(XType xt) {
      return Arrays.equals(_flds,((Clz)xt)._flds) && Arrays.equals(_xts,((Clz)xt)._xts);
    }
    @Override int hash() { return Arrays.hashCode(_flds) ^ Arrays.hashCode(_xts); }
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
    @Override public boolean is_prim_base() { return _e.is_prim_base(); }
    @Override public SB str( SB sb ) {
      if( _e.is_prim_base() && _e instanceof Base )
        return _e.p(sb.p("Ary")); // Primitives print as "Arylong" or "Arychar" classes
      return _e.str(sb.p("Ary<")).p(">");
    }
    @Override public SB clz( SB sb ) { return str(sb); }
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
    public static Fun make( MethodPart meth ) {
      return make(xtypes(meth._args),xtypes(meth._rets));
    }
    public int nargs() { return _args.length; }
    @Override public boolean is_prim_base() { return false; }
    @Override public SB str( SB sb ) {
      sb.p("{ ");
      if( _args != null )
        for( XType xt : _args )
          xt.str(sb).p(",");
      sb.unchar().p(" -> ");
      if( _rets != null )
        for( XType xt : _rets )
          xt.str(sb).p(",");
      return sb.unchar().p(" }");
    }
    @Override public SB clz( SB sb ) {
      sb.p("Fun");
      if( _args == null ) return sb;
      sb.p(_args.length).p("$");
      for( XType xt : _args )
        xt.str(sb).p("$");
      return sb.unchar();
    }
    // Using shallow equals,hashCode, not deep, because the parts are already interned
    @Override boolean eq(XType xt) { return Arrays.equals(_args,((Fun)xt)._args) && Arrays.equals(_rets,((Fun)xt)._rets); }
    @Override int hash() { return Arrays.hashCode(_args) ^ Arrays.hashCode(_rets); }

    // Make a callable interface with a particular signature
    public Fun make_class( ) {
      String tclz = clz();
      if( ClzBuilder.XCLASSES.containsKey(tclz) ) return this;
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
      if( _args!=null )
        for( XType arg : _args )
          arg.p(sb).p(" x").p(i++).p(",");
      sb.unchar().p(");").nl();
      // Class end
      sb.di().ip("}").nl();
      ClzBuilder.XCLASSES.put(tclz,sb.toString());
      return this;
    }
  }

  // Java non-primitive classes
  public static Base CONSOLE= Base.make("Console");
  public static Base EXCEPTION = Base.make("Exception");
  public static Base ILLARGX= Base.make("IllegalArgumentException");
  public static Base ILLSTATEX = Base.make("IllegalStateX");
  public static Base ITER64 = Base.make("Iterablelong");
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
      put("Exception+ecstasy/Exception.x",EXCEPTION);
      put("IllegalArgument+ecstasy.x",ILLARGX);
      put("IllegalState+ecstasy.x",ILLSTATEX);
      put("Int64+ecstasy/numbers/Int64.x",LONG);
      put("UInt8+ecstasy/numbers/UInt8.x",JUBYTE);
      put("IntLiteral+ecstasy/numbers/IntLiteral.x",LONG);
      put("Ordered+ecstasy.x",ORDERED);
      put("Object+ecstasy/Object.x",OBJECT);
      put("String+ecstasy/text/String.x",STRING);
      put("StringBuffer+ecstasy/text/StringBuffer.x",STRINGBUFFER);
    }};
  // A set of common XTC classes, and their Java replacements...
  // AND these take the default import.
  static final HashMap<String, Base> IMPORT_XJMAP = new HashMap<>() {{
      put("Console+ecstasy/io/Console.x",CONSOLE);
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
  public XType unbox() {
    Base jt = UNBOX.get(this);
    return jt==null ? this : jt;
  } 
  public boolean primeq() { return XBOX.containsKey(this); }
  public String ztype() { return primeq() ? "0" : "null"; }
  
  // Either "==name" or ".equals(name)"
  public SB do_eq(SB sb, String name) {
    return primeq() ? sb.p("==").p(name) : sb.p(".equals(").p(name).p(")");
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
    return switch( tc ) {
    case TermTCon ttc -> {
      ClassPart clz = (ClassPart)ttc.part();
      if( clz._path==null ) {
        if( clz._name.equals("Null" ) ) yield Base.NULL;
        if( clz._name.equals("True" ) ) yield Base.TRUE;
        if( clz._name.equals("False") ) yield Base.FALSE;
        throw XEC.TODO();
      }
      // Check the common base classes
      String xjkey = xjkey(clz);
      Base val = BASE_XJMAP.get(xjkey);
      if( val!=null ) yield boxed ? val.box() : val;
      // Again, common classes with imports
      val = IMPORT_XJMAP.get(xjkey);
      if( val!=null ) {
        String imp = clz._path._str;
        // Convert "ecstasy/io/Console.x" to "ecstasy.io.Console"
        String imp2 = imp.substring(0,imp.lastIndexOf(".")).replace('/','.');
        ClzBuilder.IMPORTS.add(imp2);
        yield val;
      }
      // Make one
      yield Clz.make(clz);
    }

    case ParamTCon ptc -> {
      ClassPart clz = ((ClzCon)ptc._con).clz();

      // These XTC classes are all intercepted and directly implemented in Java
      if( clz._name.equals("Array") && clz._path._str.equals("ecstasy/collections/Array.x") ) {
        XType telem = ptc._parms==null ? null : xtype(ptc._parms[0],false);
        String imp="Ary"; XType xt=null;
        if( telem == Base.LONG ) { imp="Arylong"; xt = ARYLONG; } // Java ArrayList specialized to int64
        if( telem == Base.CHAR ) { imp="Arychar"; xt = ARYCHAR; } // Java ArrayList specialized to char
        if( xt==null ) xt = Ary.make(telem);
        ClzBuilder.IMPORTS.add("ecstasy.collections."+imp);
        yield xt;
      }

      if( clz._name.equals("Function") && clz._path._str.equals("ecstasy/reflect/Function.x") ) {
        XType[] args = xtypes(((ParamTCon)ptc._parms[0])._parms);
        XType[] rets = xtypes(((ParamTCon)ptc._parms[1])._parms);
        yield Fun.make(args, rets).make_class();
      }

      XType telem = ptc._parms==null ? null : xtype(ptc._parms[0],true);
      
      // All the long-based ranges, intervals and iterators are just Ranges now.
      if( clz._name.equals("Range"   ) && clz._path._str.equals("ecstasy/Range.x"   ) ||
          clz._name.equals("Interval") && clz._path._str.equals("ecstasy/Interval.x") ) {
        ClzBuilder.IMPORTS.add("ecstasy.Range");        
        if( telem== Base.LONG || telem== Base.JLONG ) yield Base.RANGE; // Shortcut class
        else throw XEC.TODO();
      }

      if( clz._name.equals("Iterator") && clz._path._str.equals("ecstasy/Iterator.x") ) {
        if( telem== Base.LONG || telem== Base.JLONG ) {
          ClzBuilder.IMPORTS.add("ecstasy.Iterablelong");
          yield Base.ITER64; // Shortcut class
        } else throw XEC.TODO();
      }

    //  if( clz._name.equals("List") && clz._path._str.equals("ecstasy/collections/List.x") )
    //    yield "Ary<"+telem+">"; // Shortcut class

      if( clz._name.equals("Tuple") && clz._path._str.equals("ecstasy/collections/Tuple.x") )
        //yield org.xvm.xec.ecstasy.collections.Tuple.make_class(ClzBuilder.XCLASSES, xtype(ptc._parms));
        throw XEC.TODO();

    //  if( clz._name.equals("Map") && clz._path._str.equals("ecstasy/collections/Map.x") )
    //    yield XMap.make_class(ClzBuilder.XCLASSES, ptc._parms);
    //  
    //  // Attempt to use the Java class name
    //  if( clz._name.equals("Type") && clz._path._str.equals("ecstasy/reflect/Type.x") )
    //    yield telem + ".class";

      if( clz._name.equals("Appender") && clz._path._str.equals("ecstasy/Appender.x") )
        throw XEC.TODO();

      yield telem;
    }

    case ImmutTCon itc ->
      xtype(itc.icon(),boxed); // Ignore immutable for now

    // Generalized union types gonna wait awhile.
    // Right now, allow null unions only
    case UnionTCon utc -> {
      if( ((ClzCon)utc._con1).clz()._name.equals("Nullable") )
        yield xtype(utc._con2,true);
      throw XEC.TODO();
    }

    case IntCon itc -> {
      if( itc._f == Const.Format.Int64 )
        yield boxed ? Base.JLONG : Base.LONG;
      throw XEC.TODO();
    }

    case StringCon sc ->
      Base.STRING;

    case EnumCon econ ->
      Base.make(((ClassPart)econ.part())._super._name).unbox();

    case LitCon lit -> {
      if( lit._f==Const.Format.IntLiteral )
        yield boxed ? Base.JLONG : Base.LONG;
      throw XEC.TODO();
    }

    case AryCon ac ->
      xtype(ac.type(),false);

    case MethodCon mcon ->
      Fun.make((MethodPart)mcon.part());

    // Property constant
    case PropCon prop ->
      xtype(((PropPart)prop.part())._con,false);

    case SingleCon con0 -> {
      if( con0.part() instanceof ModPart mod )
        yield Clz.make(mod);
      if( con0.part() instanceof PropPart prop )
        yield Base.make(PropBuilder.jname(prop));
      throw XEC.TODO();
    }

    default -> throw XEC.TODO();
    };
  }

  private static Tuple xtype( TCon[] cons ) {
    int N = cons==null ? 0 : cons.length;
    XType[] clzs = new XType[N];
    for( int i=0; i<N; i++ )
      clzs[i]=XType.xtype(cons[i],false);
    return Tuple.make(clzs);
  }
  
}
