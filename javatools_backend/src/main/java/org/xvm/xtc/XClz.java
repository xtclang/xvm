package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.*;

import java.util.HashMap;

// Basically a Java class as a XType 
public class XClz extends XType {
  private static final HashMap<ClassPart,XClz> ZINTERN = new HashMap<>();
  public final String _name, _pack; // Self short name, package name
  public       ModPart _mod;   // Self module
  public       ClassPart _clz; // Self class, can be a module
  public       XClz _super;    // Super xtype or null
  public final String[] _flds; // Field names, matching _xts
  public       int _nTypeParms;// Number of type parameters, often 0,1 or 2 (e.g. Map<Key,Value> has 2)
  private XClz( ClassPart clz ) {
    _super = get_super(clz);
    _mod = clz.mod();  // Self module
    _clz = clz;
    // Class & package names.
    _name = S.java_class_name(clz.name());
    _pack = pack(clz);
    
    int len=0;
    for( Part part : clz._name2kid.values() )
      if( part instanceof PropPart prop && find(_super,prop._name)==null )
        len++;
    _flds = new String[len];
    _xts  = new XType [len];
    // Split init from new to avoid infinite recursion on init
  }
  
  // Class & package names.

  // Example: tck_module (the module itself)
  // _pack: null;
  // _name: tck_module
  
  // Example:  tck_module.comparison.Compare.AnyValue
  // _pack: tck_module.comparison
  // _name: Compare.AnyValue

  // Example: ecstasy.Enum
  // _pack: ecstasy
  // _name: Enum
  private String pack(ClassPart clz) {
    assert _mod!=null;
    if( clz == _mod ) return null; // XTC Modules never have a Java package
    clz = (ClassPart)clz._par;     // Skip the class, already in the _name field
    String pack = null;
    while( true ) {
      String pxname = S.java_class_name(clz.name());
      pack = pack==null ? pxname : pxname+"."+pack;
      if( clz == _mod ) break;
      clz = (ClassPart)clz._par;
    }
    return pack.intern();
  }
  
  // Split init from new to avoid infinite recursion on init
  private XClz init() {
    int len=0;
    for( Part part : _clz._name2kid.values() )
      if( part instanceof PropPart prop && find(_super,prop._name)==null ) {
        _flds[len  ] = prop._name;
        _xts [len++] = xtype(prop._con,false);
      }
    // Type parameters are first, and in-order
    if( _clz._tcons != null ) {
      _nTypeParms = _clz._tcons.size();
      for( String name : _clz._tcons.keySet() )
        if( (0xFFFF & S.find(_flds,name)) >= _nTypeParms ) // Unsigned check, -1 fails also
          throw XEC.TODO();                                // Need to reorder parms so types in the low set
    }
    return this;
  }
  
  // Made from XTC class
  public static XClz make( ClassPart clz ) {
    // Check for a pre-cooked class
    XClz xclz;
    xclz = IMPORT_XJMAP.get(XType.xjkey(clz));
    if( xclz != null ) return xclz.set(clz);
    XType xt = BASE_XJMAP.get(XType.xjkey(clz));
    if( xt != null ) return (XClz)xt;
    xclz = ZINTERN.get(clz);
    if( xclz!=null ) return xclz;

    ZINTERN.put(clz,xclz = new XClz(clz));
    return xclz.init();
  }
  // Made from a Java class directly; the XTC class shows up later.  No
  // fields are mentioned, and are not needed since the class is pre hand-
  // built.
  XClz( String pack, String name, XClz supr ) {
    _name = name;
    _pack = pack;
    // clz,mod set later
    _super=supr;
    _flds=null;
    _xts=null;
  }

  // Set in the loaded ClassPart from a previously internall defined XType
  XClz set(ClassPart clz) {
    if( _clz==clz ) return this;
    assert _clz==null;
    // Set the super
    XClz sup = get_super(clz);
    assert _super==sup || _super==null;
    _super = sup;
    // Set mod and clz
    _mod = clz.mod();
    _clz=clz;
    
    // XTC classes mapped directly to e.g. java.lang.Long or java.lang.String
    // take their Java class from rt.jar and NOT from the XEC directory.
    if( !XType.SPECIAL_RENAMES.contains(this) ) {
      assert S.eq(_name,S.java_class_name(clz.name()));
      assert S.eq(_pack,pack(clz));
    }
    return this;
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
    // Special intercept for the Const "interface", which maps to the Java
    // class (NOT interface) Const.java
    if( S.eq(clz._path._str,"ecstasy/Const.x") ) return XXTC;
    return null;
  }

  // Generally no, but Java lacks an Unsigned byte - so the XTC unsigned byte
  // is represented as a XClz, but will be mapped to some java primitive.
  @Override public boolean is_prim_base() { return this==JUBYTE; }

  // Does 'this' subclass 'sup' ?
  public boolean subClasses( XType sup ) {
    if( this==sup ) return true;
    if( _super==null ) return false;
    return _super.subClasses(sup);
  }

  
  @Override public boolean needs_import() {
    // Built-ins before being 'set' have no clz, and do not needs_import
    // Self module is also compile module.
    if( this==XXTC ) return false;
    return !S.eq("java.lang",_pack) && _clz != ClzBuilder.CCLZ;
  }
  public boolean needs_build() {
    // Check for a pre-cooked class
    if( _clz==null ) return false;
    String key = XType.xjkey(_clz);
    if(   BASE_XJMAP.containsKey(key) ) return false;
    if( IMPORT_XJMAP.containsKey(key) ) return false;
    return true;
  }
  
  // Find a field in the superclass chain
  static XType find(XClz clz, String fld) {
    for( ; clz!=null; clz = clz._super ) {
      int idx = S.find(clz._flds,fld);
      if( idx!= -1 )
        return clz._xts[idx];
    }
    return null;
  }

  // Number of type parameters
  @Override public int nTypeParms() { return _nTypeParms; }
  
  @Override public SB str( SB sb, VBitSet visit, VBitSet dups, boolean clz ) {
    if( clz ) return sb.p(_name);
    sb.p("class ").p(_name);
    if( _super!=null ) sb.p(":").p(_super._name);
    sb.p(" {").nl();
    if( _flds != null ) 
      for( int i=0; i<_flds.length; i++ )
        _xts[i].str(sb.p("  ").p(_flds[i]).p(":"),visit,dups,clz).p(";").nl();
    return sb.p("}").nl();
  }
  
  // Module: Lib  as  org.xv.xec.X$Lib
  // Class : tck_module.comparison.Compare.AnyValue  as  org.xv.xec.tck_module.comparison.Compare.AnyValue
  public String qualified_name() {
    if( S.eq(_pack,"java.lang") )
      return half_qual_name(); // java.lang is not part of the XEC.XCLZ directory
    return XEC.XCLZ + "." + (_mod!=null && _mod==_clz
      ? _name+".X$"+_name
      : half_qual_name());
  }
  
  // Class : tck_module.comparison.Compare.AnyValue
  public String half_qual_name() {
    assert _clz!=_mod || _mod==null;
    return _pack==null ? _name : _pack+"."+_name;
  }

  // "package org.xvm.xec.tck" or
  // "package org.xvm.xec.tck.arrays"
  public String package_name() {
    assert !"java.lang".equals(_pack);
    return XEC.XCLZ + "." + (_mod==_clz ? _name : _pack);
  }
  
  // Using shallow equals,hashCode, not deep, because the parts are already interned
  @Override boolean eq(XType xt) {
    XClz clz = (XClz)xt;
    return _name.equals(clz._name) && _super==clz._super;
  }
  @Override int hash() {
    return _name.hashCode() ^ (_super==null ? -1 : _super.hashCode() );
  }
}
  
