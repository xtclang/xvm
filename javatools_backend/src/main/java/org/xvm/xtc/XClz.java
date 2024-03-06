package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.S;
import org.xvm.util.SB;
import org.xvm.util.VBitSet;
import org.xvm.xtc.cons.*;

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
  public String[] _tnames;      // Type names, matching _xts
  // Private no-arg constructor, always use "make" for interning
  private XClz() { _nTypeParms = -99; }

  // Free list of XClzs to recycle
  private static XClz FREE;
  static XClz make( String pack, String nest, String name, int len ) {
    XClz clz = FREE==null ? new XClz() : FREE;
    if( clz==FREE ) FREE=null;
    assert clz._nTypeParms == -99; // Was from FREE list
    
    // Class & package names.
    clz._pack = pack;
    clz._nest = nest;
    clz._name = name;
    // See if we can reuse the xts,flds
    if( clz._nTypeParms != len ) {
      clz._xts = len==0 ? null : new XType[len];
      clz._tnames = len==0 ? null : new String[len];
      clz._nTypeParms = len;
    }
    // If this class has a direct Java implementation, these are the Java names
    clz._jpack = clz._jname = null; // Need these to be filled in
    clz._notNull = true;            // Default, use nullable to change
    clz._super = null;
    return clz;
  }
  
  XClz _intern() {
    XClz clz = (XClz)intern(this);
    if( clz!=this ) { FREE = this; _nTypeParms = -99; }
    return clz;
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
  
  
  // Made from XTC class.
  //   Here E is the class name for a type
  //   class List<E> { ... }
  //   XClz: List{E=XTC}
  public static XClz make( ClassPart clz ) {
    if( clz._tclz != null )
      return clz._tclz;
    XClz xclz = _make_clz(clz);
    xclz._jname = xclz._jpack = ""; // No corresponding java name
    // Load the type parameters, part of the uniqueness
    for( int i=0; i<clz._tnames.length; i++ ) {
      xclz._tnames[i] = clz._tnames[i];
      xclz._xts [i] = xtype(clz._tcons[i],true);
    }
    xclz._super = get_super(clz);
    XClz xclz2 = xclz._intern();
    assert xclz2 == xclz;       // Only intern XTC ClassPart once
    clz._tclz = xclz;
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
    ClassPart clz = ptc.clz();
    XClz xclz = _make_clz(clz);
    // Override parameterized type fields
    for( int i=0; i<ptc._parms.length; i++ ) {
      // If the ptc parameter is a TermTCon - a "terminal type constant" it's a
      // type, not a type variable.  No type name, but a type directly.
      boolean isType = ptc._parms[i] instanceof TermTCon ttc && ttc.id() instanceof ClassCon;
      xclz._tnames[i] = isType ? null : clz._tnames[i];
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
  

  // Make a specialized FutureVar type
  public static XClz wrapFuture( XType xt ) {
    ClzBuilder.add_import(XCons.FUTUREVAR);
    if( xt instanceof XClz clz ) ClzBuilder.add_import(clz);
    XClz xclz = _make_clz(XCons.FUTUREVAR._clz);
    xclz._tnames = XCons.FUTUREVAR._tnames;
    xclz._xts[0] = xt;
    return xclz._make(XCons.FUTUREVAR);
  }
  
  // You'd think the clz._super would be it, but not for enums
  static XClz get_super( ClassPart clz ) {
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

  @Override public XClz nullable() {
    if( !_notNull ) return this;
    XClz clz = make(_pack,_nest,_name,_nTypeParms);
    clz._xts      = _xts   ;       // Share subtypes and fields
    clz._tnames = _tnames;
    clz._jpack    = _jpack ;
    clz._jname    = _jname ;
    clz._jparms   = _jparms;
    clz._clz      = _clz   ;
    clz._iface    = _iface ;
    clz._ambiguous= _ambiguous;
    clz._super    = _super ;
    clz._notNull  = false  ;
    return clz._intern();
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
  XType find(String tname) {
    XClz clz = this;
    for( ; clz!=null; clz = clz._super ) {
      int idx = S.find(clz._tnames,tname);
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
    if( _tnames != null )
      for( int i = 0; i< _tnames.length; i++ ) {
        sb.p("  ");
        if( _tnames[i]!=null ) sb.p(_tnames[i]);
        sb.p(":");
        if( _xts[i] != null )  _xts[i].str(sb,visit,dups);
        sb.p(";").nl();
      }
    sb.p("}");
    if( !_notNull ) sb.p("?");
    return sb.nl();
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
      // Structurally recurse
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
      if( _tnames[i]==null ) _xts[i].clz(sb);
      else sb.p("$").p(_tnames[i]);
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
    return Arrays.equals( _tnames,clz._tnames ) &&
      S.eq( _name,clz. _name) && S.eq( _nest,clz. _nest) && S.eq( _pack,clz. _pack);
  }
  @Override int hash() {
    int hash = _name.hashCode() ^ _nest.hashCode() ^ _pack.hashCode();
    if( _tnames != null )
      for( String tname : _tnames )
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
    if( _nTypeParms != clz._nTypeParms ) return false;
    for( int i=0; i<_nTypeParms; i++ )
      if( !_xts[i].isa(clz._xts[i]) )
        return false;
    return true;
  }
}
