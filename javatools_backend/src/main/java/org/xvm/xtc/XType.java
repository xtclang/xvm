package org.xvm.xtc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import org.xvm.XEC;
import org.xvm.util.*;
import org.xvm.xtc.cons.*;
import static org.xvm.xtc.XCons.*;

// Concrete java types from XTC types.  All instances are interned so deep
// compares are free/shallow.
@SuppressWarnings("StaticInitializerReferencesSubClass")
public abstract class XType<TV extends TVar> {
  // The intern table
  static final HashMap<XType,XType> INTERN = new HashMap<>();

  private static int CNT=1;
  final int _uid = CNT++;       // Unique id, for cycle printing

  // Children, if any
  public Ary<TV> _tvars;

  // Must a field be initialized not-null?
  // Primitives are always "nullable" even when 0.
  // XBase.STRING is not-nullable, despite being a XBase.
  // XClzs go both ways, depends on if they got unioned with Null or not.
  public boolean _notNull;

  // --------------------------------------------------------------------------

  boolean eq( XType xt ) { return true; } // Subclass specialized eq check
  @Override public final boolean equals(Object o) {
    if( this==o ) return true;
    if( this.getClass() != o.getClass() ) return false;
    XType xt = (XType)o;
    if( _notNull != xt._notNull || !eq(xt) || len() != xt.len() )
      return false;
    for( int i=0; i<len(); i++ )
      if( at(i) != xt.at(i) )
        return false;
    return true;
  }

  private int _hash;            // Hash cache, never 0
  abstract long hash();         // Subclass hash
  @Override final public int hashCode() {
    if( _hash!=0 ) return _hash;
    long hash = hash() ^ (_notNull ? 1024 : 0);
    for( int i=0; i<len(); i++ )
      hash = S.rot(hash,7) ^ at(i).hashCode();
    int ihash = (int)(hash ^ (hash>>>32));
    if( ihash==0 ) ihash = 0xdeadbeef;
    return (_hash=ihash);
  }

  // Interning support
  static XType intern( XType free ) {
    free._hash = 0;
    assert free._tvars!=null;
    XType jt = INTERN.get(free);
    if( jt!=null ) { free._tvars.clear(); return jt; }
    INTERN.put(jt=free,free);
    return jt;
  }

  // Fancy print, handling cycles and dups.  Suitable for the debugger.
  @Override public final String toString() { return str(new SB()).toString();  }
  // Fancy recursive print
  public SB str( SB sb ) {
    // Collect dups and a visit bit
    VBitSet visit = new VBitSet(), dups = new VBitSet();
    _dups(visit,dups);
    return _str0(sb,visit.clr(),dups );
  }

  void _dups(VBitSet visit, VBitSet dups) {
    if( visit.tset(_uid) )
      dups.set(_uid);
    else if( _tvars!=null )
      for( int i=0; i<len(); i++ )
        if( at(i) != null )
          xt(i)._dups(visit,dups);
  }

  // Default fancy print, stops at cycles and dups
  final SB _str0( SB sb, VBitSet visit, VBitSet dups ) {
    if( dups!=null && dups.test(_uid) ) {
      sb.p("V").p(_uid);
      if( visit.tset(_uid) )  return sb;
      sb.p(":");
    }
    return _str1(sb,visit,dups);
  }

  // Internal specialized print, given dups and string/class flag
  abstract SB _str1( SB sb, VBitSet visit, VBitSet dups );

  public final int len() { return _tvars==null ? 0 : _tvars._len; }
  public final TV at(int i) { return _tvars.at(i); }
  public final XType xt(int i) { return at(i)._xt; }


  // Alternative Class flavored fancy print
  public final String clz() { return _clz(new SB(),null).toString(); }
  public final SB clz( SB sb ) { return _clz(sb,null); }
  public final SB clz( SB sb, ParamTCon ptc ) { return _clz(sb,ptc); }
  public SB clz_bare( SB sb ) { return _clz(sb,null); }
  abstract SB _clz( SB sb, ParamTCon ptc );

  // --------------------------------------------------------------------------

  public boolean needs_import(boolean self) { return false; }

  public void makeImports( SB sb ) {
    HashSet<String> imports = new HashSet<>();
    _makeImports(sb,imports);
    sb.nl();
  }
  final void _makeImports( SB sb, HashSet<String> imports ) {
    if( _tvars==null ) return;
    for( int i=0; i<len(); i++ ) {
      XType xt = xt(i);
      XClz box = xt.box();
      if( box!=null && box.needs_import(false) ) {
        String tqual = box.qualified_name();
        if( !imports.contains(tqual) ) {
          imports.add(tqual);
          sb.fmt("import %0;\n",tqual);
          box._makeImports(sb,imports);
        }
      }
    }
  }

  // --------------------------------------------------------------------------
  // Get the boxed version of self
  public XClz box() {
    if( this instanceof XClz clz ) return clz;
    return XBOX.get(this);
  }
  // Get the unboxed version of self.  If this==unbox() then either this is
  // already an unboxed primitive, or no unboxed version exits (this is a ref).
  // If this!=unbox() then the unbox() version is a primitive of this.
  public XType unbox() {
    XBase jt = UNBOX==null ? null : UNBOX.get(this);
    return jt==null ? this : jt;
  }
  public boolean isUnboxed() { return XBOX.containsKey(this); }
  public XType box( boolean boxed ) {
    return boxed ? this : unbox();
  }

  public XType nullable() {
    if( !_notNull ) return this;
    throw XEC.TODO();
  }

  static String xjkey(ClassPart clz) { return clz._name + "+" + clz._path._str; }
  public boolean primeq() { return XBOX.containsKey(this); }
  public boolean zero() { return primeq() && this!=STRING && this!=STRINGN && this!=TRUE && this!=FALSE; }
  public String ztype() { return zero() ? "0" : "null"; }
  public boolean is_jdk() { return primeq() || this==JNULL; }

  // Either "==name" or ".equals(name)"
  public SB do_eq(SB sb, String name) {
    return sb.fmt(zero() ? "==%0" : ".equals(%0)", name);
  }

  public final boolean isa( XType xt ) {
    return this==xt
      || (getClass() == xt.getClass() && _isa(xt))
      || (this==XCons.NULL && !xt._notNull);
  }
  abstract boolean _isa( XType xt );
  public boolean isVar() { return false; }

  public boolean isAry() { return false; }
  public boolean isTuple() { return false;  }
  public XType e() { throw XEC.TODO(); }

  public XType readOnly() { throw XEC.TODO(); }
  public boolean isBool() {
    return
      this==JBOOL  || this==BOOL ||
      this==JTRUE  || this==TRUE ||
      this==JFALSE || this==FALSE;
  }

  // --------------------------------------------------------------------------


  // Convert an array of Const to an array of XType
  public static XType[] xtypes( Const[] cons ) { return xtypes(cons,false); }
  public static XType[] xtypes( Const[] cons, boolean box ) {
    if( cons==null ) return null;
    XType[] xts = new XType[cons.length];
    for( int i=0; i<cons.length; i++ )
      xts[i] = xtype(cons[i],box);
    return xts;
  }

  // Convert an array of Parameter._con to an array of XType
  public static XType[] xtypes( Parameter[] parms, boolean box ) {
    if( parms==null ) return null;
    XType[] xts = new XType[parms.length];
    for( int i=0; i<parms.length; i++ )
      xts[i] = parms[i].type(box);
    return xts;
  }

  public static XType xtype( Const tc, boolean boxed ) { return xtype(tc,boxed,null); }

  // Produce a java type from a TermTCon
  static XType xtype( Const tc, boolean boxed, XClz self ) {
    return switch( tc ) {
    case TermTCon ttc ->
      switch( ttc.part() ) {
      case ClassPart clz -> {
        if( clz._path !=null && S.eq(clz._path._str,"ecstasy/Object.x") )
          yield XCons.XXTC;
        if( S.eq("Null",clz._name) )
          yield XCons.NULL;     // Primitive null
        XClz xclz = clz.xclz();
        if( ttc.id() instanceof FormalTChildCon ftcc )
          yield xclz.find(ftcc._name);
        yield boxed ? xclz.box() : xclz.unbox();
      }

      case ParmPart parm -> {
        if( ttc.id() instanceof TParmCon tpc ) {
          // Generic parameter name
          yield xtype(parm.parm().tcon(),boxed,self);
        } else if( ttc.id() instanceof DynFormalCon dfc ) {
          // Generic parameter name
          String dynt = ((TermTCon)((ParamTCon)dfc.type())._parms[0]).name();
          yield XBase.make("$"+dynt,true);
        } else
          throw XEC.TODO();
      }

      // Hidden extra XTC type argument (GOLD instance of the class whose hash
      // implementation to use)
      case PropPart prop -> xtype(prop._con,false,self);

      case null ->
        throw XEC.TODO();

      default -> throw XEC.TODO();
      };

    case ParamTCon ptc -> {
      ClassPart clz = ptc.clz();

      // These XTC classes are all intercepted and directly implemented in Java
      if( clz._name.equals("Array") && clz._path._str.equals("ecstasy/collections/Array.x") )
        yield XClz.make(ptc);

      if( clz._name.equals("Map") && clz._path._str.equals("ecstasy/maps/Map.x") )
        yield XClz.make(ptc);

      if( clz._name.equals("Function") && clz._path._str.equals("ecstasy/reflect/Function.x") ) {
        Const[] args = ((ParamTCon)ptc._parms[0])._parms;
        Const[] rets = ((ParamTCon)ptc._parms[1])._parms;
        yield XFun.make(null,false,xtypes(args,boxed),xtypes(rets,boxed)).make_class();
      }

      XType telem = ptc._parms==null ? null : xtype(ptc._parms[0],true,self);

      // All the long-based ranges, intervals and iterators are just Ranges now.
      if( clz._name.equals("Range"   ) && clz._path._str.equals("ecstasy/Range.x"   ) ||
          clz._name.equals("Interval") && clz._path._str.equals("ecstasy/Interval.x") ) {
        if( telem== JLONG )
          yield RANGE;          // Shortcut class
        yield XClz.make(ptc);
      }

      if( clz._name.equals("Iterator") && clz._path._str.equals("ecstasy/Iterator.x") )
        yield XClz.make(ptc);
      if( clz._name.equals("Iterable") && clz._path._str.equals("ecstasy/Iterable.x") )
        yield XClz.make(ptc);

      if( clz._name.equals("Tuple") && clz._path._str.equals("ecstasy/collections/Tuple.x") ) {
        int N = ptc._parms==null ? 0 : ptc._parms.length;
        XType[] clzs = new XType[N];
        for( int i=0; i<N; i++ )
          clzs[i] = xtype(ptc._parms[i],false,self);
        yield org.xvm.xec.ecstasy.collections.Tuple.make_class(XCons.make_tuple(clzs));
      }

      // Attempt to use the Java class name
      if( clz._name.equals("Type") && clz._path._str.equals("ecstasy/reflect/Type.x") )
        yield telem==null ? XCons.XXTC : telem;

      if( clz._name.equals("Appender") && clz._path._str.equals("ecstasy/Appender.x") ) {
        if( telem== JCHAR )
          yield APPENDERCHAR;
        yield XClz.make(ptc);
      }

      // No intercept, so just the parameterized class
      yield XClz.make(ptc);
    }

    case ImmutTCon itc ->
      xtype(itc.icon(),boxed,self).readOnly();

    // Generalized union types gonna wait awhile.
    // Right now, allow null unions only
    case UnionTCon utc -> {
      XType u2 = xtype(utc._con2,false,self);
      if( !(utc._con1 instanceof TermTCon tcon) )
        yield XCons.XXTC;      // Not a union+null, bail out
      ClassPart clz1 = tcon.clz();
      if( clz1 !=null && clz1._name.equals("Nullable") )
        yield (u2.zero() ? u2.box() : u2).nullable();
      XType u1 = xtype(utc._con1,true,self);
      if( u2 instanceof XFun ) u2 = XCons.XXTC; // Union of functions just goes to XTC for now
      yield XClz.lca((XClz)u1, u2.box());
    }

    // Generalized union types gonna wait awhile.
    // Right now, allow null unions only
    case InterTCon utc -> {
      XType u2 = xtype(utc._con2,false,self);
      XType u1 = xtype(utc._con1,false,self);
      yield XInter.make(u1,u2);
    }

    case IntCon itc -> XCons.format_clz(itc._f).box(boxed);
    case CharCon cc -> JCHAR.box(boxed);
    case ByteCon cc -> JBYTE.box(boxed);
    case Flt64Con fc -> JDOUBLE.box(boxed);
    case Flt32Con fc -> JFLOAT.box(boxed);

    case StringCon sc -> STRING;

    case EnumCon econ -> {
      // The enum instance as a ClassPart
      ClassPart eclz = (ClassPart)econ.part();
      // The Enum class itself, not the enum
      XClz xclz = eclz._super.xclz();
      // The enum
      yield xclz.unbox();
    }

    case LitCon lit -> {
      if( lit._f==Const.Format.IntLiteral )
        yield XCons.INTLITERAL;
      throw XEC.TODO();
    }

    case AryCon ac ->
      xtype(ac.type(),false,self);
    case MapCon mc ->
      xtype(mc.type(),false,self);

    case MethodCon mcon ->
      ((MethodPart)mcon.part()).xfun();

    case FormalTChildCon ftcc ->
      ftcc.clz().xclz().find(ftcc._name);

    // Property constant
    case PropCon prop ->
      xtype(((PropPart)prop.part())._con,false,self);

    case SingleCon con0 -> {
      if( con0.part() instanceof ModPart mod )
        yield mod.xclz();
      if( con0.part() instanceof PropPart prop )
        yield XBase.make(PropBuilder.jname(prop),false);
      if( con0.part() instanceof ClassPart clz )
        yield clz.xclz();
      throw XEC.TODO();
    }

    case Dec64Con dcon -> DEC64;

    case Dec128Con dcon -> DEC128;

    case ClassCon    ccon -> ccon.clz().xclz();
    case AnonClzTCon anon -> anon.clz().xclz();

    case ServiceTCon service -> XCons.SERVICE;

    case VirtDepTCon virt -> XClz.make(virt);

    case AnnotTCon acon ->
      switch( acon.clz()._name ) {
      case "InjectedRef" -> xtype(acon.con().is_generic(),true,self);
      case "FutureVar" ->
        XClz.wrapFuture(xtype(((ParamTCon)acon.con())._parms[0],true,self));
      case "VolatileVar" ->
        xtype(acon.con(),false,self);
      default ->  throw XEC.TODO();
      };

    case AccessTCon acon ->
      xtype(acon._con,boxed,self);

    // Self recursive type
    case TSeqTCon tseq -> {
      assert self!=null;
      yield self;
    }

    case InnerDepTCon inn -> xtype(inn._child,boxed,self);

    // A conditional mixin class with perhaps several generic types,
    // but one with no constraints lands here.  e.g. ListMapIndex
    case null -> XCons.XXTC;
    default -> throw XEC.TODO();
    };
  }

}
