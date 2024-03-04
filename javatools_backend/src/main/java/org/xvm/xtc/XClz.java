package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.*;
import org.xvm.xtc.cons.*;

import java.util.Arrays;

// Basically a Java class as a XType.
//
// XTC classes have true generics/parameterization, no type erasure.
// We can map from an XTC ClassPart to a generified/erased Java class.
// We can map from a ParamTCon to a true generic XType, with XType generic classes.
// An XType carries the ClassPart it came from, but not the PTC (if any).

public class XClz extends XType {
  private static XClz FREE;
  public static XClz XXTC    = XClz.make_java("","XTC",null);
  public static XClz CONST   = XClz.make_java("ecstasy","Const",XXTC);
  public static XClz ENUM    = XClz.make_java("ecstasy","Enum" ,CONST);
  public static XClz SERVICE = XClz.make_java("ecstasy","Service",XXTC);
  // Force XCons to fill the XTpe INTERNs
  public static XClz _FORCE = XCons.JNULL;
  
  // The uniqueness is on these 3 things: package, name, and all type parms.
  public String _pack, _nest, _name; // Package name, nested class name, short name
  public int _nTypeParms;     // Number of XTC type parameters

  public String _jpack, _jname; // Java-name, if any there exists a Java implementation
  public boolean _jparms;       // Java code has no parms, they are included in the Java name
  public ModPart _mod;          // Self module
  public ClassPart _clz;        // Self class, can be a module
  public boolean _iface;        // True if interface
  public boolean _ambiguous;    // True if ambiguous in the current compilation unit, and needs the fully qualified name everywhere
  public XClz _super;           // Super xtype or null
  public String[] _flds;        // Field names, matching _xts
  // Private no-arg constructor, always use "make" for interning
  private XClz() { _nTypeParms = -99; }

  private static XClz make( String pack, String nest, String name, int len ) {
    XClz clz = FREE==null ? new XClz() : FREE;
    if( clz==FREE ) FREE=null;
    
    // Class & package names.
    clz._pack = pack;
    clz._nest = nest;
    clz._name = name;
    // See if we can reuse the xts,flds
    if( clz._nTypeParms != len ) {
      clz._xts = len==0 ? null : new XType [len];
      clz._flds= len==0 ? null : new String[len];
    }
    assert clz._nTypeParms == -99; // Was from FREE list
    clz._nTypeParms = len;
    clz._jpack = clz._jname = null; // Need these to be filled in
    return clz;
  }
  private XClz _intern() {
    XClz clz = (XClz)intern(this);
    if( clz!=this ) { FREE = this; _nTypeParms = -99; }
    return clz;
  }

  
  // Made from a Java class directly; the XTC class shows up later.
  // Fields are hand-added and need to match the ClazzPart later.
  public static XClz make( String pack, String name, XClz supr, Object... flds ) {
    return make_java(null,null,true,pack,name,supr,flds);
  }
  public static XClz make_java( String pack, String name, XClz supr, Object... flds ) {
    return make_java(pack,name,true,pack,name,supr,flds);
  }
  public static XClz make_java( String jpack, String jname, boolean jparms, String pack, String name, XClz supr, Object... flds ) {
    XClz clz = make(pack,"",name,flds.length>>1);
    clz._jpack = jpack;
    clz._jname = jname;
    clz._jparms = jparms;
    clz._nTypeParms = flds.length>>1;
    for( int i=0; i<flds.length; i += 2 ) {
      clz._flds[i>>1] = (String)flds[i  ];
      clz._xts [i>>1] = (XType )flds[i+1];
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
      XClz sup = get_super(pclz);
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

  // Java primitive array classes, no corresponding XTC class
  public static XClz make_java_ary( String jname, boolean jparms, XType xelem ) {
    XClz clz = make("ecstasy.collections","","Array",1);
    clz._jpack = "ecstasy.collections";
    clz._jname = jname;
    clz._jparms = jparms;
    clz._flds[0] = "Element";
    clz._xts [0] = xelem;
    clz._clz = XCons.ARRAY._clz;
    clz._super = null;
    return clz._intern();
  }

  public static XClz make_tuple( XType... clzs ) {
    XClz clz = make("ecstasy.collections","","Tuple",clzs.length);
    clz._jpack = "ecstasy.collections";
    clz._jname = "Tuple";
    clz._jparms = true;
    for( int i=0; i<clzs.length; i++ )
      clz._flds[i] = (""+i).intern();
    clz._xts = clzs;
    clz._clz = XXTC._clz;
    clz._super = XXTC;
    return clz._intern();
  }

  // Fill in common fields.  Not interned yet.
  private static XClz _make_clz( ClassPart clz ) {
    // Extra useful info
    ModPart mod = clz.mod();
    // Class & package names.
    String cname, cnest;
    if( clz==mod ) {
      // Module: java name is always Module.M$Module
      // Class : java name is always Module.Class
      cnest = "";
      cname = ("M$"+S.java_class_name(clz.name())).intern();
    } else if( clz._par instanceof MethodPart meth ) {
      cnest = "";
      cname = S.java_class_name(meth._name+"$"+clz.name());
    } else if( !clz._path.equals(mod._path) ) {
      cnest = "";
      cname = S.java_class_name( clz.name() );
    } else {
      // Class embedded in a Module:
      // java name is always Module.M$Module.Class
      cnest = S.java_class_name(mod.name());
      cnest = ("M$"+cnest).intern();
      cname = S.java_class_name(clz.name());
    }
    
    XClz xclz = make( pack(clz,mod), cnest, cname, clz._tnames.length );
    xclz._mod = mod;  // Self module
    xclz._clz = clz;
    xclz._iface = clz._f==Part.Format.INTERFACE;
    assert xclz._jname==null && xclz._jpack==null;
    xclz._jparms = true;        // Honor any parameters
    return xclz;
  }
  
  
  // Made from XTC class
  public static XClz make( ClassPart clz ) {
    if( clz._tclz != null )
      return clz._tclz;
    XClz xclz = _make_clz(clz);
    xclz._jname = xclz._jpack = ""; // No corresponding java name
    // Load the type parameters, part of the uniqueness
    for( int i=0; i<clz._tnames.length; i++ ) {
      xclz._flds[i] = clz._tnames[i];
      xclz._xts [i] = xtype(clz._tcons[i],true);
    }
    xclz._super = get_super(clz);
    XClz xclz2 = xclz._intern();
    assert xclz2 == xclz;       // Only intern XTC ClassPart once
    clz._tclz = xclz;
    return xclz;
  }

  // Make a specialized FutureVar type
  public static XClz wrapFuture( XType xt ) {
    ClzBuilder.add_import(XCons.FUTUREVAR);
    if( xt instanceof XClz clz ) ClzBuilder.add_import(clz);
    XClz xclz = _make_clz(XCons.FUTUREVAR._clz);
    xclz._flds = XCons.FUTUREVAR._flds;
    xclz._xts[0] = xt;
    return xclz._make(XCons.FUTUREVAR);
  }
  
  // Make from a parameterized class; normal class but the type parms from the
  // PTC.
  public static XClz make( ParamTCon ptc ) {
    ClassPart clz = ptc.clz();
    XClz xclz = _make_clz(clz);
    // Override parameterized type fields
    for( int i=0; i<ptc._parms.length; i++ ) {
      xclz._flds[i] = clz._tnames[i];
      xclz._xts [i] = xtype(ptc._parms[i],true);
    }
    
    // Get the UNparameterized class, for the jpack/jname
    XClz plain_clz = make(clz);
    // Very specifically, generic parameterized AryXTC<XTC> means the
    // un-element-typed Array<VOID>, used to make both primitive arrays and
    // Object arrays.
    if( plain_clz == XCons.ARRAY ) {
      // A generified array needs to remain un-element-typed
      if( ptc._parms[0] instanceof TermTCon ttc && ttc.id() instanceof TParmCon )
        return XCons.ARRAY;
      plain_clz = XCons.ARYXTC;
    }

    return xclz._make(plain_clz);
  }
  
  // Intern and fill out a parameterized class from a plain class
  private XClz _make( XClz plain_clz ) {
    // See if already exists
    XClz xclz2 = _intern();

    if( xclz2 != this ) return xclz2; // Already did

    // Copy Java fields; this could be a parameterized version of a Java
    // implementation
    assert           _jname==null &&           _jpack==null;
    assert plain_clz._jname!=null && plain_clz._jpack!=null;
    _jpack = plain_clz._jpack;
    _jname = plain_clz._jname;
    //_jparms= plain_clz._jparms; // Turned off for ARRAY vs ARYXTC
    
    // Fill in Super
    xclz2._super = get_super(_clz);
    return xclz2;
  }
  
  
  // You'd think the clz._super would be it, but not for enums
  static XClz get_super( ClassPart clz ) {
    if( clz._super!=null )
      return make(clz._super);
    // The XTC Enum is a special interface, extending the XTC Const interface.
    // They are implemented as normal Java classes, with Enum extending Const.
    if( S.eq(clz._path._str,"ecstasy/Enum.x") ) return CONST;
    // Other enums are flagged via the Part.Format and do not have the
    // _super field set.
    if( clz._f==Part.Format.CONST ) return CONST;
    if( clz._f==Part.Format.ENUM  ) return ENUM ;
    if( clz._f==Part.Format.SERVICE ) return SERVICE;
    // Special intercept for the Const "interface", which maps to the Java
    // class (NOT interface) Const.java
    if( S.eq(clz._path._str,"ecstasy/Const.x") ) return XXTC;
    // Special intercept for the Service "interface", which maps to the Java
    // class (NOT interface) Service.java
    if( S.eq(clz._path._str,"ecstasy/Service.x") ) return XXTC;
    return null;
  }

  // Generic XTC array; when making XTC array constants have to pass
  // the explicit array element type
  @Override public boolean generic_ary() { return S.eq(_jname,"AryXTC");  }
  @Override public boolean isTuple() { return S.eq(_jname,"Tuple");  }
  // TODO: Really needs to be an ISA on XTC Var
  @Override public boolean isVar() { return S.eq(_name,"Var") || S.eq(_jname,"FutureVar"); }
  // Some kind of array
  @Override public boolean isAry() { return S.eq("ecstasy.collections",_pack) && S.eq("Array",_name); }
  // XTC array element type
  public XType e() {
    assert isAry() || isVar();
    return _xts[0];
  }
  
  // Class & package names.

  // Example: tck_module (the module itself)
  // _pack: ""
  // _nest: ""
  // _name: tck_module
  
  // Example:  tck_module.comparison.Compare.AnyValue
  // _pack: tck_module.comparison
  // _nest: ""
  // _name: Compare.AnyValue

  // Example: ecstasy.Enum
  // _pack: ecstasy
  // _nest: ""
  // _name: Enum
  
  // Example:  nQueens.Board
  // _pack: nQueens
  // _nest: M$nQueens
  // _name: Board
  private static String pack(Part pclz, ModPart mod) {
    assert mod!=null;
    if( pclz == mod ) return mod.name(); // XTC Modules never have a Java package
    while( !(pclz._par instanceof ClassPart clz) )
      pclz = pclz._par;
    String pack = null;
    while( true ) {
      String pxname = S.java_class_name(clz.name());
      pack = pack==null ? pxname : pxname+"."+pack;
      if( clz == mod ) break;
      clz = (ClassPart)clz._par;
    }
    return pack.intern();
  }

  
  @Override public boolean needs_import(boolean self) {
    // Built-ins before being 'set' have no clz, and do not needs_import
    // Self module is also compile module.
    if( this==XXTC ) return false;
    if( this==XCons.JTRUE ) return false;
    if( this==XCons.JFALSE) return false;
    if( this==XCons.JNULL ) return false;
    if( this==XCons.JSTRING ) return false;
    return !S.eq("java.lang",_jpack) && (!self || _clz != ClzBuilder.CCLZ);
  }
  // No java name means needs a build
  public boolean needs_build() { return _jname.isEmpty(); }
  
  // Find a field in the superclass chain
  XType find(String fld) {
    XClz clz = this;
    for( ; clz!=null; clz = clz._super ) {
      int idx = S.find(clz._flds,fld);
      if( idx!= -1 )
        return clz._xts[idx];
    }
    return null;
  }

  // Number of type parameters
  @Override public int nTypeParms() { return _nTypeParms; }
  
  @Override public SB str( SB sb, VBitSet visit, VBitSet dups  ) {
    sb.p("class ").p(_ambiguous ? qualified_name() : _name);
    if( _super!=null ) sb.p(":").p(_super._name);
    sb.p(" {").nl();
    if( _flds != null ) 
      for( int i=0; i<_flds.length; i++ )
        _xts[i].str(sb.p("  ").p(_flds[i]).p(":"),visit,dups).p(";").nl();
    return sb.p("}").nl();
  }

  // Walk and print Java class name, including generics.  If XTC is providing
  // e.g. method-specific generic names, use them instead.  Using "T" here
  // instead of "XTC": "<T extends XTC> void bar( T gold, AryXTC<T> ary )"
  @Override SB _clz( SB sb, ParamTCon ptc ) {
    // Specifically JSTRING uses the fully qualified name, to not be confused
    // with java.lang.String.
    if( this==XCons.JSTRING || this==XCons.JOBJECT )
      return sb.p(qualified_name());

    // Alternative class printing for tuples.
    if( S.eq(_jname,"Tuple") )
      return strTuple(sb);

    sb.p(_ambiguous ? qualified_name() : name());
    // Some Java implementations already specify the XTC generic directly: Arylong
    if( !_jparms || _nTypeParms==0 ) return sb;
    
    // Print Array<void> as Array
    if( ptc==null && this==XCons.ARRAY ) return sb;
    
    // Printing generic type parameters
    sb.p("<");
    for( int i=0; i<_nTypeParms; i++ ) {
      // No ParamTCon provided, using the generic class as-is
      if( ptc==null )  _xts[i]._clz(sb,ptc);
      // ParamTCon has a different name, it's not a type, it's a generic name.
      else if( ptc._parms[i] instanceof TermTCon ttc && ttc.part() instanceof ParmPart parm )
        sb.p("$").p(parm._name);
      // Sructurally recurse
      else _xts[i].clz(sb);
      sb.p(",");
    }
    return sb.unchar().p(">");
  }

  public String name( ) { return _jname.isEmpty() ? _name : _jname; }
  public String pack( ) { return _jpack.isEmpty() ? _pack : _jpack; }

  // Module: Lib  as  org.xv.xec.X$Lib
  // Class : tck_module.comparison.Compare.AnyValue  as  org.xv.xec.tck_module.comparison.Compare.AnyValue
  public String qualified_name() {
    if( S.eq(_jpack,"java.lang") )
      return "java.lang."+_jname;
    String name = this==XCons.TUPLE0 ? "Tuple0" : name();
    return (XEC.XCLZ + "." + pack() + (_nest.isEmpty() ? "" : "."+_nest) + "." + name).intern();
  }

  // Bare name
  public String clz_bare( ) {
    return this==XCons.JSTRING || this==XCons.JOBJECT || _ambiguous ? qualified_name() : name();
  }

  // "Foo<Value extends Hashable>"
  public SB clz_generic( SB sb, boolean do_name, boolean generic_def ) {
    if( _ambiguous ) throw XEC.TODO();
    if( do_name )  sb.p(name());
    // Some Java implementations already specify the XTC generic directly: Arylong
    if( !_jparms || _nTypeParms==0 ) return sb;
    // Print Array<void> as Array
    if( this==XCons.ARRAY ) return sb;
    // Printing generic type parameters
    sb.p("<");
    for( int i=0; i<_nTypeParms; i++ ) {
      sb.p("$").p(_flds[i]);
      if( generic_def ) {
        XClz xclz = (XClz)_xts[i];
        sb.p(" extends XTC" );
        if( xclz._iface )
          xclz.clz_generic(sb.p(" & "),true, true);
      }
      sb.p(",");
    }
    return sb.unchar().p("> ");
  }
  
  private SB strTuple( SB sb ) {
    sb.p("Tuple").p(_xts.length).p("$");
    for( XType xt : _xts )
      xt._clz(sb,null).p("$");
    return sb.unchar();
  }
  
  // Using shallow equals, hashCode, not deep, because the parts are already interned
  @Override boolean eq(XType xt) {
    XClz clz = (XClz)xt;
    return Arrays.equals(_flds,clz._flds) &&
      S.eq( _name,clz. _name) && S.eq( _nest,clz. _nest) && S.eq( _pack,clz. _pack);
  }
  @Override int hash() {
    int hash = _name.hashCode() ^ _nest.hashCode() ^ _pack.hashCode();
    if( _flds != null )
      for( String fld : _flds )
        hash ^= fld.hashCode();
    return hash;
  }

  // Does 'this' subclass 'sup' ?
  private boolean subClasses( XClz sup ) {
    if( _clz==sup._clz ) return true;
    if( _super==null ) return false;
    return _super.subClasses(sup);
  }

  @Override boolean _isa( XType xt ) {
    XClz clz = (XClz)xt;        // Contract
    if( !subClasses(clz) ) return false;
    if( _nTypeParms != clz._nTypeParms ) return false;
    for( int i=0; i<_nTypeParms; i++ )
      if( !_xts[i].isa(clz._xts[i]) )
        return false;
    return true;
  }

  private static final XClz[] XCLZS = new XClz[] {
    null,                     // IntLiteral
    null,                     // Bit
    null,                     // Nybble
    XCons.JBYTE   ,
    XCons.JINT16  ,
    XCons.JINT32  ,
    XCons.JLONG   ,
    XCons.JINT128 ,
    XCons.JINTN   ,
    null,
    XCons.JUINT16 ,
    XCons.JUINT32 ,
    XCons.JUINT64 ,
    XCons.JUINT128,
    XCons.JUINTN  ,
  };
  public static XClz format_clz(Const.Format f) { return XCLZS[f.ordinal()]; }
}
