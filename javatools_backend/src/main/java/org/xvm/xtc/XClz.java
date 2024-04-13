package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.S;
import org.xvm.util.SB;
import org.xvm.util.VBitSet;
import org.xvm.xtc.cons.*;

import static org.xvm.XEC.TODO;

import java.util.Arrays;

// Basically a Java class as a XType.
//
// XTC classes have true generics/parameterization, no type erasure.
// We can map from an XTC ClassPart to a generified/erased Java class.
// We can map from a ParamTCon to a true generic XType, with XType generic classes.
// An XType carries the ClassPart it came from, but not the PTC (if any).

// XClz tracks super-class but NOT the set of implemented interfaces

// Example class def:
//   Here E is the class name for a type
//   class List<E> { ... }
//   XClz: List{E=XTC}

// Example class usage with a concrete type, not a type-variable:
//   No field name, since Int is a Type not a TypeVar
//   class Nums extends List<Int> { ... }
//   XClz: Nums{_=Int}

//Example class usage:
//   Name is valid in local scope and NOT in List's scope
//   class MyList<Q> extends List<Q> { ... }
//   XClz: MyList{Q=XTC}

//Example class usage:
//   Name and constraint are valid in local scope.
//   class MyList<H extends Hashable> extends List<H> { ... }
//   XClz: MyList{H=Hashable}

public class XClz extends XType {
  private static final String[] STR0 = new String[0];

  // Force XCons to fill the XTpe INTERNs
  public static XClz _FORCE = XCons.JNULL;

  // The uniqueness is on these things: package, name, and all type parms.
  public String _pack, _nest, _name; // Package name, nested class name, short name
  public String[] _tns;         // Type names, matching _xts
  public boolean _ro;           // Read/Only type; set if super is set

  // Not tested as part of uniqueness, but implied
  public  XClz _super;           // Super xtype or null
  public  ModPart _mod;          // Self module
  public  ClassPart _clz;        // Self class, can be a module

  // Java-name, if any there exists a Java implementation.
  // If the name is NOT equal to _pack,_name, then the name
  // also directly encodes the parameter.
  // Example: XTC Array<Char> vs Java Arychar
  //          XTC Array<Int>  vs Java Arylong
  public  String _jpack, _jname;

  // This is a tempory field; set and cleared per-compilation unit
  transient public boolean _ambiguous;

  // Private no-arg constructor, always use "make" for interning
  private XClz() { _pack = "0xDEADBEEF"; }

  // Free list of XClzs to recycle
  private static XClz FREE;

  private static XClz malloc( boolean notNull, String pack, String nest, String name ) {
    XClz clz = FREE==null ? new XClz() : FREE;
    if( clz==FREE ) FREE=null;
    assert clz._pack == "0xDEADBEEF"; // Was from FREE list

    // These next fields are all part of uniqueness
    clz._notNull = notNull;     // Field in XType
    // Class & package names.
    clz._pack = pack;
    clz._nest = nest;
    clz._name = name;
    // Default false, check if making R/O
    clz._ro = false;
    clz._jname = clz._jpack = ""; // So toString doesnt barf on half built XClz
    return clz;
  }

  // Makes a new uninterned XCLZ.  Has blank xts/tns arrays ready to be filled in.
  static XClz malloc( boolean notNull, String pack, String nest, String name, int len ) {
    XClz clz = malloc(notNull,pack,nest,name);
    // See if we can reuse the xts,flds
    if( clz._xts==null || clz._xts.length != len ) {
      clz._xts = len==0 ? EMPTY : new XType [len];
      clz._tns = len==0 ? STR0  : new String[len];
    }
    return clz;
  }

  // Makes a new unintered XClz cloned from the prototype.
  // SHARES xts/tns; so these need to be defensively copied before changing.
  private XClz malloc() {
    XClz clz = malloc(_notNull,_pack, _nest, _name);
    clz._xts = _xts;
    clz._tns = _tns;
    return clz;
  }

  // Intern, but do not fill in the extra info fields.
  XClz _intern() {
    assert _pack != "0xDEADBEEF"; // Got set
    XClz clz = (XClz)intern(this);
    if( clz!=this ) { FREE = this; _pack = "0xDEADBEEF"; }
    return clz;
  }

  // Intern and fill out a parameterized class from the prototype
  XClz _intern( XClz proto ) {
    // See if already exists
    XClz clz = _intern();
    if( clz != this ) {
      assert proto._super==clz._super && (proto._clz==clz._clz || clz._clz==null);
      return clz;
    }
    assert _ro || (_super==null || !_super._ro);  // Set if super is set

    _mod = proto._mod;
    _clz = proto._clz;
    _super = proto._super;
    _jpack = proto._jpack;
    _jname = proto._jname;
    return clz;
  }



  // Fill in common fields.  Not interned yet.
  private static XClz _malloc( ClassPart clz ) {
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

    XClz xclz = malloc( true, pack(clz,mod), cnest, cname, clz._tnames.length );
    xclz._mod = mod;  // Self module
    xclz._clz = clz;
    xclz._jname = xclz._jpack = "";
    xclz._super = get_super(clz._super);
    return xclz;
  }

  // Made from XTC class.
  //   Here E is the class name for a type
  //   class List<E> { ... }
  //   XClz: List{E=XTC}
  public static XClz make( ClassPart clz ) {
    if( clz._tclz != null )     // Check class cache
      return clz._tclz;
    XClz xclz = _malloc(clz);
    // Load the type parameters, part of the uniqueness
    for( int i=0; i<clz._tnames.length; i++ ) {
      xclz._tns[i] = clz._tnames[i];
      xclz._xts[i] = xtype(clz._tcons[i],true,xclz);
    }
    xclz._super = get_super(clz);
    XClz xclz2 = xclz._intern();
    assert xclz2 == xclz;       // Only intern XTC ClassPart once
    clz._tclz = xclz;           // Assign the class cache
    return xclz;
  }


  // Make from a parameterized class: like a
  // normal class but the type parms from the PTC.

  // Example class usage with a concrete type, not a type-variable:
  //   class Nums extends List<Int> { ... }
  //   XClz: Nums{_=Int}
  //   No field name, since Int is a Type not a TypeVar

  //Example class usage:
  //   class MyList<Q> extends List<Q> { ... }
  //   XClz: MyList{Q=XTC}
  //   Name is valid in local scope and NOT in List's scope

  //Example class usage:
  //   class MyList<H extends Hashable> extends List<H> { ... }
  //   XClz: MyList{H=Hashable}
  //   Name and constraint are valid in local scope.

  public static XClz make( ParamTCon ptc ) {
    // Get the UNparameterized class
    ClassPart clz = ptc.clz();
    XClz proto = make(clz);

    // Very specifically, generic parameterized AryXTC<XTC> means the
    // un-element-typed Array<VOID>, used to make both primitive arrays and
    // Object arrays.
    if( proto == XCons.ARRAY || proto == XCons.ARRAY_RO ) {
      // A generified array needs to remain un-element-typed
      if( ptc._parms[0] instanceof TermTCon ttc && ttc.id() instanceof TParmCon )
        return proto;
      proto = proto._ro ? XCons.ARYXTC_RO : XCons.ARYXTC;
    }

    XClz xclz = proto.malloc();
    xclz._xts = ptc._parms==null ? EMPTY : new XType [ptc._parms.length];
    xclz._tns = ptc._parms==null ? STR0  : new String[ptc._parms.length];
    // Override parameterized type fields
    for( int i=0; i<xclz._tns.length; i++ ) {
      // If the ptc parameter is a TermTCon - a "terminal type constant" it's a
      // type, not a type variable.  No type name, but a type directly.
      boolean isType = ptc._parms[i] instanceof TermTCon ttc && ttc.id() instanceof ClassCon;
      xclz._tns[i] = isType ? null : clz._tnames[i];
      xclz._xts[i] = xtype(ptc._parms[i],true);
    }

    return xclz._intern(proto);
  }

  // An inner class, which gets the outer class Type variables
  public static XClz make( VirtDepTCon virt ) {
    ClassPart iclz = virt.part(); // Inner clz
    XClz xclz = _malloc(iclz);    // Inner xclz
    ParamTCon ptc = (ParamTCon)virt._par;
    TCon[] parms = ptc._parms;
    if( parms != null ) {
      ClassPart oclz = ptc.clz(); // Outer class
      XType [] xts = xclz._xts;
      String[] tns = xclz._tns;
      int len = xts==null ? 0 : xts.length;
      xclz._xts    = xts = xts==null ? new XType [parms.length] : Arrays.copyOf(xts,len+parms.length);
      xclz._tns = tns = tns==null ? new String[parms.length] : Arrays.copyOf(tns,len+parms.length);
      for( int i=0; i<parms.length; i++ ) {
        boolean isType = parms[i] instanceof TermTCon ttc && ttc.id() instanceof ClassCon;
        tns[len+i] = isType ? null : oclz._tnames[i];
        xts[len+i] = xtype(parms[i],true);
      }
    }
    xclz._super = get_super(iclz);
    xclz._jpack = "";
    xclz._jname = "";
    XClz xclz2 = xclz._intern();
    if( xclz2 != xclz ) return xclz2;
    iclz._tclz = xclz;
    return xclz;
  }

  // Make a specialized FutureVar type
  public static XClz wrapFuture( XType xt ) {
    ClzBuilder.add_import(XCons.FUTUREVAR);
    if( xt instanceof XClz clz ) ClzBuilder.add_import(clz);
    XClz xclz = _malloc(XCons.FUTUREVAR._clz);
    xclz._tns = XCons.FUTUREVAR._tns;
    xclz._xts[0] = xt;
    return xclz._intern(XCons.FUTUREVAR);
  }

  // You'd think the clz._super would be it, but not for enums
  static XClz get_super( ClassPart clz ) {
    if( clz==null ) return null;
    if( clz._super!=null )
      return make(clz._super);
    // The XTC Enum is a special interface, extending the XTC Const interface.
    // They are implemented as normal Java classes, with Enum extending Const.
    if( S.eq(clz._path._str,"ecstasy/Enum.x") ) return XCons.CONST;
    // Other enums are flagged via the Part.Format and do not have the
    // _super field set.
    if( clz._f==Part.Format.CONST ) return XCons.CONST;
    if( clz._f==Part.Format.ENUM  ) return XCons.ENUM ;
    if( clz._f==Part.Format.SERVICE ) return XCons.SERVICE;
    // Special intercept for the Const "interface", which maps to the Java
    // class (NOT interface) Const.java
    if( S.eq(clz._path._str,"ecstasy/Const.x") ) return XCons.XXTC;
    // Special intercept for the Service "interface", which maps to the Java
    // class (NOT interface) Service.java
    if( S.eq(clz._path._str,"ecstasy/Service.x") ) return XCons.XXTC;
    return null;
  }

  @Override public XClz nullable() {
    if( !_notNull ) return this;
    XClz clz = malloc();
    clz._notNull = false;
    return clz._intern(this);
  }
  public XClz readOnly() {
    if( _ro ) return this;
    XClz clz = malloc();
    clz._ro = true;
    return clz._intern(this);
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
  public final boolean iface() { return _clz!=null && _clz._f==Part.Format.INTERFACE; }


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
    if( this==XCons.XXTC ) return false;
    if( this==XCons.JTRUE ) return false;
    if( this==XCons.JFALSE) return false;
    if( this==XCons.JNULL ) return false;
    if( this==XCons.JSTRING ) return false;
    return !S.eq("java.lang",_jpack) && (!self || _clz != ClzBuilder.CCLZ);
  }
  // No java name means needs a build
  public boolean needs_build() { return _jname.isEmpty(); }

  // Find a type name in the superclass chain
  public XType find(String tname) {
    XClz clz = this;
    for( ; clz!=null; clz = clz._super ) {
      int idx = S.find(clz._tns,tname);
      if( idx!= -1 )
        return clz._xts[idx];
    }
    return null;
  }

  // Bare name, no generics
  public String clz_bare( ) {
    // Tuples have a mangled class name without generics
    if( isTuple() ) return strTuple(new SB()).toString();
    // These guys need a fully qualified name to avoid name conflicts
    if( this==XCons.JSTRING || this==XCons.JOBJECT || _ambiguous ) return qualified_name();
    return name();
  }

  @Override public SB str( SB sb, VBitSet visit, VBitSet dups  ) {
    sb.p("class ").p(clz_bare());
    if( _super!=null ) sb.p(":").p(_super._name);
    sb.p(" {").nl();
    if( _tns != null )
      for( int i = 0; i< _tns.length; i++ ) {
        sb.p("  ");
        if( _tns[i]!=null ) sb.p( _tns[i]);
        sb.p(":");
        if( _xts[i] != null )  _xts[i]._str(sb,visit,dups);
        sb.p(";").nl();
      }
    sb.p("}");
    if( !_notNull ) sb.p("?");
    return sb.nl();
  }

  // Walk and print Java class name, including generics.  If XTC is providing
  // e.g. method-specific generic names, use them instead.  Using "T" here
  // instead of "XTC": "<T extends XTC> void bar( T gold, AryXTC<T> ary )"
  @Override SB _clz( SB sb, ParamTCon ptc, boolean print ) {
    // Specifically JSTRING uses the fully qualified name, to not be confused
    // with java.lang.String.
    if( this==XCons.JSTRING || this==XCons.JOBJECT )
      return sb.p(qualified_name());

    // Alternative class printing for tuples.
    if( S.eq(_jname,"Tuple") )
      return strTuple(sb);

    sb.p(clz_bare());

    // Print generic parameters?
    if( !print || noTypeParms( ptc,true ) ) return sb;

    // Printing generic type parameters
    sb.p("<");
    for( int i=0; i<_xts.length; i++ ) {
      // No ParamTCon provided, using the generic class as-is
      if( ptc==null )  _xts[i]._clz(sb,ptc,print);
      // ParamTCon has a different name, it's not a type, it's a generic name.
      else if( ptc._parms[i] instanceof TermTCon ttc && ttc.part() instanceof ParmPart parm )
        sb.p("$").p(parm._name);
      // Structurally recurse
      else _xts[i].clz(sb);
      sb.p(",");
    }
    return sb.unchar().p(">");
  }

  public String name( ) { return _jname.isEmpty() ? _name : _jname; }
  public String pack( ) { return _jpack.isEmpty() ? _pack : _jpack; }

  public boolean noTypeParms() { return noTypeParms( null, true ); }
  public boolean noTypeParms( ParamTCon ptc, boolean print ) {
    // No type parameters
    if( _xts.length==0 ) return true;
    // Print Array<void> as Array
    if( ptc==null && (this==XCons.ARRAY || this==XCons.ARRAY_RO) ) return print;
    // Print Other AryXTC with types
    if( _jname.equals( "AryXTC" ) ) return false;
    // Some Java implementations already specify the XTC generic directly: Arylong
    return !_jname.isEmpty() && !S.eq( _name, _jname );
  }

  // Module: Lib  as  org.xv.xec.X$Lib
  // Class : tck_module.comparison.Compare.AnyValue  as  org.xv.xec.tck_module.comparison.Compare.AnyValue
  public String qualified_name() {
    if( S.eq(_jpack,"java.lang") )
      return "java.lang."+_jname;
    String name = this==XCons.TUPLE0 ? "Tuple0" : name();
    String pack = pack().isEmpty() ? "" : "."+pack();
    String nest = _nest .isEmpty() ? "" : "."+_nest ;
    return (XEC.XCLZ + pack + nest + "." + name).intern();
  }

  // "Foo<Value extends Hashable>"
  public SB clz_generic( SB sb, boolean do_name, boolean generic_def ) {
    if( _ambiguous ) throw XEC.TODO();
    if( do_name )  sb.p(name());

    // Do not print generic parameters
    if( noTypeParms() ) return sb;

    // Printing generic type parameters
    sb.p("<");
    for( int i=0; i<_xts.length; i++ ) {
      if( _tns[i]==null ) _xts[i].clz(sb);
      else sb.p("$").p( _tns[i]);
      if( generic_def ) {
        XClz xclz = (XClz)_xts[i];
        sb.p(" extends XTC" );
        if( xclz.iface() )
          xclz.clz_generic(sb.p(" & "),true, true);
      }
      sb.p(",");
    }
    return sb.unchar().p("> ");
  }

  private SB strTuple( SB sb ) {
    sb.p("Tuple").p(_xts.length).p("$");
    for( XType xt : _xts )
      xt._clz(sb,null,false).p("$");
    return sb.unchar();
  }

  // Using shallow equals, hashCode, not deep, because the parts are already interned
  @Override boolean eq(XType xt) {
    XClz clz = (XClz)xt;
    return Arrays.equals( _tns,clz._tns ) &&
      S.eq( _name,clz. _name) && S.eq( _nest,clz. _nest) && S.eq( _pack,clz. _pack) &&
      _ro == clz._ro;
  }
  @Override int hash() {
    int hash = _name.hashCode() ^ _nest.hashCode() ^ _pack.hashCode() ^ (_ro ? 2048 : 0);
    if( _tns != null )
      for( String tname : _tns )
        if( tname != null )
          hash ^= tname.hashCode();
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
    if( _xts.length != clz._xts.length ) return false;
    for( int i=0; i<_xts.length; i++ )
      if( !_xts[i].isa(clz._xts[i]) )
        return false;
    return true;
  }
}
