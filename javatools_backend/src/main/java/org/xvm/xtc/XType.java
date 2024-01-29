package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.xtc.cons.*;
import org.xvm.util.*;

import static org.xvm.xtc.XCons.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;

// Concrete java types from XTC types.  All instances are interned so deep
// compares are free/shallow.  
@SuppressWarnings("StaticInitializerReferencesSubClass")
public abstract class XType {
  // The intern table
  static final HashMap<XType,XType> INTERN = new HashMap<>();
  static final XType[] EMPTY = new XType[0];

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
  @Override public final String toString() {
    // Collect dups and a visit bit
    VBitSet visit = new VBitSet(), dups = new VBitSet();
    _dups(visit,dups);
    return _str(new SB(),visit.clr(),dups ).toString();
  }
  // Non-fancy print.  Dies on cycles and dups.  Suitable for flat or simple types.
  public SB str( SB sb ) { return str(sb,null,null); }

  private void _dups(VBitSet visit, VBitSet dups) {
    if( visit.tset(_uid) )
      dups.set(_uid);
    else if( _xts!=null )
      for( XType xt : _xts )
        if( xt != null )
          xt._dups(visit,dups);
  }

  // Default fancy print, stops at cycles and dups
  public SB _str( SB sb, VBitSet visit, VBitSet dups ) {
    if( dups!=null && dups.test(_uid) ) {
      sb.p("V").p(_uid);
      if( visit.tset(_uid) )  return sb;
      sb.p(":");
    }
    return str(sb,visit,dups);
  }

  // Internal specialized print, given dups and string/class flag
  abstract SB str( SB sb, VBitSet visit, VBitSet dups );

    // Alternative Class flavored fancy print
  public final String clz() { return _clz(new SB(),null).toString(); }
  public final SB clz( SB sb ) { return _clz(sb,null); }
  public final SB clz( SB sb, ParamTCon ptc ) { return _clz(sb,ptc); }
  abstract SB _clz( SB sb, ParamTCon ptc );

  // --------------------------------------------------------------------------

  public boolean needs_import() { return false; }

  abstract boolean eq( XType xt ); // Subclass specialized eq check
  @Override public final boolean equals(Object o) {
    if( this==o ) return true;
    if( this.getClass() != o.getClass() ) return false;
    if( !Arrays.equals(_xts,((XType)o)._xts) ) return false;
    return eq((XType)o);
  }
    
  private int _hash;            // Hash cache, never 0
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
  public XClz box() {
    if( this instanceof XClz clz ) return clz;
    return XBOX.get(this);
  } 
  public XType unbox() {
    XBase jt = UNBOX.get(this);
    return jt==null ? this : jt;
  }
  public XType box( boolean boxed ) {
    return boxed ? this : unbox();
  }
  
  static String xjkey(ClassPart clz) { return clz._name + "+" + clz._path._str; }
  public boolean primeq() { return XBOX.containsKey(this); }
  public boolean zero() { return primeq() && this!=STRING; }
  public String ztype() { return zero() ? "0" : "null"; }
  public boolean is_jdk() { return primeq() || this==JNULL; }
  
  // Either "==name" or ".equals(name)"
  public SB do_eq(SB sb, String name) {
    return zero() ? sb.p("==").p(name) : sb.p(".equals(").p(name).p(")");
  }

  // Only valid for Ary, Clz (Tuple???).
  // Always arrays have 1 type parameter, the element type.
  // Clzs mostly have 0, sometimes have 1 (e.g. Hashable<Value>), rarely have 2 or more (e.g. Map<Key,Value>)
  public int nTypeParms() { throw XEC.TODO(); }
  public XType typeParm(int i) { return _xts[i]; }

  
  public final boolean isa( XType xt ) { return this==xt || (getClass() == xt.getClass() && _isa(xt)); }
  abstract boolean _isa( XType xt );
  public boolean isVar() { return false; }

  public boolean generic_ary() { return false;  }
  public XType e() { throw XEC.TODO(); }
  
  // --------------------------------------------------------------------------

  
  // Convert an array of Const to an array of XType
  public static XType[] xtypes( Const[] cons   ) {
    if( cons==null ) return null;
    XType[] xts = new XType[cons.length];
    for( int i=0; i<cons.length; i++ )
      xts[i] = xtype(cons[i],false);
    return xts;
  }
  
  // Convert an array of Parameter._con to an array of XType
  public static XType[] xtypes( Parameter[] parms  ) {
    if( parms==null ) return null;
    XType[] xts = new XType[parms.length];
    for( int i=0; i<parms.length; i++ )
      xts[i] = parms[i].type();
    return xts;
  }
  
  // Produce a java type from a TermTCon
  public static XType xtype( Const tc, boolean boxed ) {
    return switch( tc ) {
    case TermTCon ttc -> {
      if( ttc.part() instanceof ClassPart clz ) {
        if( clz._path !=null && S.eq(clz._path._str,"ecstasy/Object.x") )
          yield XClz.XXTC;
        if( S.eq("Null",clz._name) )
          yield XCons.NULL;     // Primitive null
        XClz xclz = XClz.make(clz);
        XType xt = boxed ? xclz.box() : xclz.unbox();
        if( xt instanceof XClz xclz2 ) ClzBuilder.add_import(xclz2);
        yield xt;
      }
      
      if( ttc.part() instanceof ParmPart parm ) {
        if( ttc.id() instanceof TParmCon tpc ) {
          // Generic parameter name
          yield ((XClz)xtype(parm.parm().tcon(),boxed));
        } else if( ttc.id() instanceof DynFormalCon dfc ) {
          // Generic parameter name
          String dynt = ((TermTCon)((ParamTCon)dfc.type())._parms[0]).name();
          yield XBase.make("$"+dynt);
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
      ClassPart clz = ptc.clz();

      // These XTC classes are all intercepted and directly implemented in Java
      if( clz._name.equals("Array") && clz._path._str.equals("ecstasy/collections/Array.x") )
        yield ClzBuilder.add_import(XClz.make(ptc));

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

      if( clz._name.equals("Iterator") && clz._path._str.equals("ecstasy/Iterator.x") )
        yield XClz.make(ptc);
      if( clz._name.equals("Iterable") && clz._path._str.equals("ecstasy/Iterable.x") )
        yield XClz.make(ptc);

      if( clz._name.equals("Tuple") && clz._path._str.equals("ecstasy/collections/Tuple.x") ) {
        int N = ptc._parms==null ? 0 : ptc._parms.length;
        XType[] clzs = new XType[N];
        for( int i=0; i<N; i++ )
          clzs[i] = XType.xtype(ptc._parms[i],false);
        yield org.xvm.xec.ecstasy.collections.Tuple.make_class(ClzBuilder.XCLASSES, XClz.make_tuple(clzs));
      }

      // Attempt to use the Java class name
      if( clz._name.equals("Type") && clz._path._str.equals("ecstasy/reflect/Type.x") )
        yield telem;

      if( clz._name.equals("Appender") && clz._path._str.equals("ecstasy/Appender.x") ) {
        if( telem == CHAR || telem== JCHAR )
          yield ClzBuilder.add_import(APPENDERCHAR);
        yield XClz.make(ptc);
      }

      // No intercept, so just the parameterized class
      yield XClz.make(ptc);
    }

    case ImmutTCon itc ->
      xtype(itc.icon(),boxed); // Ignore immutable for now

    // Generalized union types gonna wait awhile.
    // Right now, allow null unions only
    case UnionTCon utc -> {
      XType u2 = xtype(utc._con2,false);
      if( ((ClzCon)utc._con1).clz()._name.equals("Nullable") )
        yield u2.zero() ? u2.box() : u2;
      XType u1 = xtype(utc._con1,false);
      yield XUnion.make(u1,u2);
    }

    case IntCon itc -> XClz.format_clz(itc._f).box(boxed);
    case CharCon cc -> JCHAR.box(boxed);
    case ByteCon cc -> JBYTE.box(boxed);
    
    case StringCon sc ->
      STRING;

    case EnumCon econ -> {
      // The enum instance as a ClassPart
      ClassPart eclz = (ClassPart)econ.part();
      // The Enum class itself, not the enum
      XClz xclz = ClzBuilder.add_import(XClz.make(eclz._super));
      // The enum
      yield xclz.unbox();
    }

    case LitCon lit -> {
      if( lit._f==Const.Format.IntLiteral )
        yield boxed ? JLONG : LONG;
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
      ClzBuilder.add_import(DEC64);

    case Dec128Con dcon ->
      ClzBuilder.add_import(DEC128);

    case ClassCon ccon ->
      XClz.make(ccon.clz());

    case ServiceTCon service ->
      SERVICE;

    case VirtDepTCon virt ->
      xtype(virt._par,false);

    case AnnotTCon acon ->
      switch( acon.clz()._name ) {
      case "InjectedRef" -> xtype(acon.con().is_generic(),true);
      case "FutureVar" ->
        XClz.wrapFuture(xtype(((ParamTCon)acon.con())._parms[0],true));
      default ->  throw XEC.TODO();
      };
    
    default -> throw XEC.TODO();
    };
  }

}
