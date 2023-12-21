package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.xtc.cons.*;
import org.xvm.util.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;

// Concrete java types from XTC types.  All instances are interned so deep
// compares are free/shallow.  
@SuppressWarnings("StaticInitializerReferencesSubClass")
public abstract class XType {
  // The intern table
  private static final HashMap<XType,XType> INTERN = new HashMap<>();
  private static final XType[] EMPTY = new XType[0];

  private static int CNT=1;
  final int _uid = CNT++;       // Unique id, for cycle printing
  
  // Children, if any
  XType[] _xts;

  // Fancy print, handlng cycles and dups.  Suitable for the debugger.
  @Override public final String toString() { return str_init(false); }
  // Alternative Class flavored fancy print
  public final String clz() { return str_init(true); }

  // Collect dups and a visit bit
  private String str_init(boolean clz) {
    VBitSet visit = new VBitSet(), dups = new VBitSet();
    _dups(visit,dups);
    return _str(new SB(),visit.clr(),dups,true ).toString();
  }
  private void _dups(VBitSet visit, VBitSet dups) {
    if( visit.tset(_uid) )
      dups.set(_uid);
    else if( _xts!=null )
      for( XType xt : _xts )
        xt._dups(visit,dups);
  }

  // Default fancy print, stops at cycles and dups
  public SB _str( SB sb, VBitSet visit, VBitSet dups, boolean clz ) {
    if( dups!=null && dups.test(_uid) ) {
      sb.p("V").p(_uid);
      if( visit.tset(_uid) )  return sb;
      sb.p(":");
    }
    return str(sb,visit,dups,clz);
  }

  // Internal specialized print, given dups and string/class flag
  abstract SB str( SB sb, VBitSet visit, VBitSet dups, boolean clz );

  // Non-fancy print.  Dies on cycles and dups.  Suitable for flat or simple types.
  public SB str( SB sb ) { return _str(sb,null,null,false); }
  public SB clz( SB sb ) { return _str(sb,null,null,true ); }
  
  abstract public boolean is_prim_base();
  public boolean needs_import() { return false; }

  abstract boolean eq( XType xt );
  @Override public boolean equals(Object o) {
    if( this==o ) return true;
    if( this.getClass() != o.getClass() ) return false;
    if( !Arrays.equals(_xts,((XType)o)._xts) ) return false;
    return eq((XType)o);
  }
    
  int _hash;
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
    @Override public boolean is_prim_base() { return primeq(); }
    @Override public SB str( SB sb, VBitSet visit, VBitSet dups, boolean clz ) { return sb.p(_jtype); }
    @Override boolean eq(XType xt) { return _jtype.equals(((Base)xt)._jtype);  }
    @Override int hash() { return _jtype.hashCode(); }
  }

  // Basically a Java class as a tuple
  public static class Tuple extends XType {
    private static Tuple FREE = new Tuple(null);
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
    public int nargs() { return _xts.length; }
    public XType arg(int i) { return _xts[i]; }
    @Override public boolean is_prim_base() { return false; }

    @Override public SB str( SB sb, VBitSet visit, VBitSet dups, boolean clz ) {
      if( clz )  sb.p("Tuple").p(_xts.length).p("$");
      else sb.p("( ");
      for( XType xt : _xts )
        xt.str(sb,visit,dups,clz).p( clz ? "$" : "," );
      sb.unchar();
      if( !clz ) sb.p(" )");
      return sb;
    }
    
    // Using shallow equals,hashCode, not deep, because the parts are already interned
    @Override boolean eq(XType xt) { return true; }
    @Override int hash() { return 0; }
  }
  
  // Basically a Java class as a XType 
  public static class Clz extends XType {
    private static final HashMap<ClassPart,Clz> ZINTERN = new HashMap<>();
    public final String _name, _pack;
    public       ModPart _mod;   // Self module
    public       ClassPart _clz; // Self class, can be a module
    public       Clz _super;     // Super xtype or null
    public final String[] _flds;
    private Clz( ClassPart clz ) {
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
    private Clz init() {
      int len=0;
      for( Part part : _clz._name2kid.values() )
        if( part instanceof PropPart prop && find(_super,prop._name)==null ) {
          _flds[len  ] = prop._name;
          _xts [len++] = xtype(prop._con,false);
        }
      return this;
    }
    
    // Made from XTC class
    public static Clz make( ClassPart clz ) {
      // Check for a pre-cooked class
      Clz xclz;
      xclz = IMPORT_XJMAP.get(xjkey(clz));
      if( xclz != null ) return xclz.set(clz);
      xclz = (Clz)BASE_XJMAP.get(xjkey(clz));
      if( xclz != null ) return xclz;
      xclz = ZINTERN.get(clz);
      if( xclz!=null ) return xclz;

      ZINTERN.put(clz,xclz = new Clz(clz));
      return xclz.init();
    }
    // Made from a Java class directly; the XTC class shows up later.  No
    // fields are mentioned, and are not needed since the class is pre hand-
    // built.
    Clz( String pack, String name, Clz supr ) {
      _name = name;
      _pack = pack;
      // clz,mod set later
      _super=supr;
      _flds=null;
      _xts=null;
    }

    // Set in the loaded ClassPart from a previously internall defined XType
    Clz set(ClassPart clz) {
      if( _clz==clz ) return this;
      assert _clz==null;
      // Set the super
      Clz sup = get_super(clz);
      assert _super==sup || _super==null;
      _super = sup;
      // Set mod and clz
      _mod = clz.mod();
      _clz=clz;
      
      // XTC classes mapped directly to e.g. java.lang.Long or java.lang.String
      // take their Java class from rt.jar and NOT from the XEC directory.
      if( !SPECIAL_RENAMES.contains(this) ) {
        assert S.eq(_name,S.java_class_name(clz.name()));
        assert S.eq(_pack,pack(clz));
      }
      return this;
    }

    // You'd think the clz._super would be it, but not for enums
    static Clz get_super( ClassPart clz ) {
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
    // is represented as a Clz, but will be mapped to some java primitive.
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
      String key = xjkey(_clz);
      if(   BASE_XJMAP.containsKey(key) ) return false;
      if( IMPORT_XJMAP.containsKey(key) ) return false;
      return true;
    }
    
    // Find a field in the superclass chain
    static XType find(Clz clz, String fld) {
      for( ; clz!=null; clz = clz._super ) {
        int idx = S.find(clz._flds,fld);
        if( idx!= -1 )
          return clz._xts[idx];
      }
      return null;
    }

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
      Clz clz = (Clz)xt;
      return _name.equals(clz._name) && _super==clz._super;
    }
    @Override int hash() {
      return _name.hashCode() ^ (_super==null ? -1 : _super.hashCode() );
    }
  }
  

  // Basically a Java class as an array
  public static class Ary extends XType {
    private static Ary FREE = new Ary(null);
    private Ary( XType e) { _xts = new XType[]{e}; }
    public static Ary make( XType e ) {
      FREE._hash = 0;
      FREE._xts = new XType[]{e};
      Ary jtt = (Ary)INTERN.get(FREE);
      if( jtt!=null ) return jtt;
      INTERN.put(jtt=FREE,FREE);
      FREE = new Ary(null);
      return jtt;
    }
    public XType e() { return _xts[0]; }
    @Override public boolean is_prim_base() { return _xts[0].is_prim_base(); }
    private boolean generic() { return !(e().is_prim_base() && (e() instanceof Base || e()==JUBYTE)); }
    
    @Override public SB str( SB sb, VBitSet visit, VBitSet dups, boolean clz ) {
      XType e = e();
      // Primitives print as "Arylong" or "Arychar" classes
      // Generics as "Ary<String>"
      boolean generic = generic();
      if( generic ) sb.p("Array<");
      else sb.p("Ary");
      e.str(sb,visit,dups,true);
      if( generic ) sb.p(">");
      return sb;
    }
    
    public String import_name() {
      SB sb = new SB();
      sb.p(XEC.XCLZ).p(".ecstasy.collections.");
      if( generic() ) sb.p("Array");
      else str(sb,null,null,false);
      return sb.toString();
    }

    @Override boolean eq(XType xt) { return true; }
    @Override int hash() { return 0; }
  }

  
  // Basically a Java class as a function
  public static class Fun extends XType {
    private static Fun FREE = new Fun();
    int _nargs;
    public static Fun make( XType[] args, XType[] rets) {
      FREE._hash = 0;
      if( args==null ) args = EMPTY;
      if( rets==null ) rets = EMPTY;
      FREE._nargs = args.length;
      FREE._xts = Arrays.copyOf(args,args.length+rets.length);
      System.arraycopy(rets,0,FREE._xts,args.length,rets.length);
      Fun jtt = (Fun)INTERN.get(FREE);
      if( jtt!=null ) return jtt;
      INTERN.put(jtt=FREE,FREE);
      FREE = new Fun();
      return jtt;
    }
    public static Fun make( MethodPart meth ) {
      return make(xtypes(meth._args),xtypes(meth._rets));
    }
    public int nargs() { return _nargs; }
    public int nrets() { return _xts.length-_nargs; }
    public XType arg(int i) { return _xts[i]; }
    
    public XType[] rets() {
      return nrets()==0 ? null : Arrays.copyOfRange(_xts,_nargs,_xts.length);
    }
    
    @Override public boolean is_prim_base() { return false; }
    @Override public SB str( SB sb, VBitSet visit, VBitSet dups, boolean clz ) {
      if( clz ) sb.p("Fun").p(_nargs);
      else      sb.p("{ ");
      for( int i=0; i<_nargs; i++ )
        _xts[i].str(sb,visit,dups,clz).p(clz ? "$" : ",");
      sb.unchar();
      
      if( !clz ) {
        sb.p(" -> ");
        for( int i=_nargs; i<_xts.length; i++ )
          _xts[i].str(sb,visit,dups,clz).p(",");
        sb.unchar().p(" }");        
      }
      return sb;
    }
    
    // Using shallow equals,hashCode, not deep, because the parts are already interned
    @Override boolean eq(XType xt) { return _nargs == ((Fun)xt)._nargs; }
    @Override int hash() { return _nargs; }

    // Make a callable interface with a particular signature
    public Fun make_class( ) {
      if( ClzBuilder.XCLASSES==null ) return this;
      String tclz = clz();
      if( ClzBuilder.XCLASSES.containsKey(tclz) )
        return this;
      /* Gotta build one.  Looks like:
         interface Fun2$long$String {
           long call(long l, String s);
         }
      */
      SB sb = new SB();
      sb.p("interface ").p(tclz).p(" {").nl().ii();
      sb.ip("abstract ");
      // Return
      int nrets = nrets();
      if( nrets==0 ) sb.p("void");
      else if( nrets==1 ) _xts[nargs()].str(sb);
      else throw XEC.TODO();
      sb.p(" call( ");
      int nargs = nargs();
      if( nargs>0 )
        for( int i=0; i<nargs; i++ )
          _xts[i].clz(sb).p(" x").p(i).p(",");
      sb.unchar().p(");").nl();
      // Class end
      sb.di().ip("}").nl();
      ClzBuilder.XCLASSES.put(tclz,sb.toString());
      return this;
    }
  }

  // A XTC union class
  public static class Union extends XType {
    private static Union FREE = new Union();
    public static Union make( XType u0, XType u1 ) {
      FREE._hash = 0;
      FREE._xts = new XType[]{u0,u1};
      Union jtt = (Union)INTERN.get(FREE);
      if( jtt!=null ) return jtt;
      INTERN.put(jtt=FREE,FREE);
      FREE = new Union();
      return jtt;
    }
    @Override public boolean is_prim_base() { return false; }
    @Override public SB str( SB sb, VBitSet visit, VBitSet dups, boolean clz ) {
      sb.p("Union[");
      _xts[0].str(sb,visit,dups,true).p(",");
      _xts[1].str(sb,visit,dups,true).p("]");
      return sb;
    }
    @Override boolean eq(XType xt) { return true; }
    @Override int hash() { return 0; }
  }

  // --------------------------------------------------------------------------

  public static Clz XXTC    = new Clz("","XTC",null);
  public static Clz CONST   = new Clz("ecstasy","Const",XXTC);
  public static Clz ENUM    = new Clz("ecstasy","Enum",CONST);
  
  // Java non-primitive classes
  public static Clz  JNULL  = new Clz("ecstasy","Nullable",null);
  public static Clz  JUBYTE = new Clz("ecstasy.numbers","UByte",null);
  public static Clz  JBOOL  = new Clz("ecstasy","Boolean",ENUM);
  public static Clz  JCHAR  = new Clz("ecstasy.text","Char",null);
  public static Clz  STRING = new Clz("java.lang","String",null);
  public static Clz  EXCEPTION = new Clz("java.lang","Exception",null);

  // Java primitives or primitive classes
  public static Base BOOL = Base.make("boolean");
  public static Base CHAR = Base.make("char");
  public static Base BYTE = Base.make("byte");
  public static Base LONG = Base.make("long");
  public static Base INT  = Base.make("int");
  
  public static Base FALSE= Base.make("false");
  public static Base NULL = Base.make("null");
  public static Base TRUE = Base.make("true");
  public static Base VOID = Base.make("void");

  public static Ary ARY    = Ary.make(XXTC);
  public static Ary ARYBOOL= Ary.make(BOOL);    // No Ecstasy matching class
  public static Ary ARYCHAR= Ary.make(CHAR);    // No Ecstasy matching class
  public static Ary ARYUBYTE= Ary.make(JUBYTE); // No Ecstasy matching class
  public static Ary ARYLONG= Ary.make(LONG);    // No Ecstasy matching class
  public static Tuple COND_CHAR = Tuple.make(BOOL,CHAR);
  

  
  // A set of common XTC classes, and their Java replacements.
  // These are NOT parameterized.
  // These will NOT have XTC-prefixed imports.
  static final HashMap<String, XType> BASE_XJMAP = new HashMap<>() {{
      put("Boolean+ecstasy/Boolean.x",BOOL);
      put("Char+ecstasy/text/Char.x",CHAR);
      put("Exception+ecstasy/Exception.x",EXCEPTION);
      put("Int64+ecstasy/numbers/Int64.x",LONG);
      put("IntLiteral+ecstasy/numbers/IntLiteral.x",LONG);
      put("Object+ecstasy/Object.x",XXTC);
      put("String+ecstasy/text/String.x",STRING);      
      put("UInt8+ecstasy/numbers/UInt8.x",JUBYTE);
    }};
  
  // A set of common XTC classes, and their Java replacements...
  // AND these take the default import path from "org.xvm.xec.ecstasy...".
  public static Clz APPENDERCHAR= new Clz("ecstasy","Appenderchar",null);
  public static Clz CONSOLE     = new Clz("ecstasy.io","Console",null);
  public static Clz ILLARGX     = new Clz("XTC","IllegalArgument",null);
  public static Clz ILLSTATEX   = new Clz("XTC","IllegalState",null);
  public static Clz HASHABLE    = new Clz("ecstasy.collections","Hashable",null);
  public static Clz ITER64      = new Clz("ecstasy","Iterablelong",null);
  public static Clz MUTABILITY  = new Clz("ecstasy.collections.Array","Mutability",ENUM);
  public static Clz ORDERABLE   = new Clz("ecstasy","Orderable",null);
  public static Clz ORDERED     = new Clz("ecstasy","Ordered",ENUM);
  public static Clz RANGE       = new Clz("ecstasy","Range",null);
  public static Clz RANGEIE     = new Clz("ecstasy","RangeIE",RANGE); // No Ecstasy matching class
  public static Clz RANGEII     = new Clz("ecstasy","RangeII",RANGE); // No Ecstasy matching class
  public static Clz SERVICE     = new Clz("ecstasy","Service",null);
  public static Clz STRINGBUFFER= new Clz("ecstasy.text","StringBuffer",null);
  public static Clz ARGUMENT    = new Clz("ecstasy.reflect","Argument",null);
  public static Clz TYPE        = new Clz("ecstasy.reflect","Type",null);
  public static Clz UNSUPPORTEDOPERATION = new Clz("ecstasy","UnsupportedOperation",null);

  public static Clz NUMBER      = new Clz("ecstasy.numbers","Number",CONST);
  
  public static Clz INTNUM      = new Clz("ecstasy.numbers","IntNumber",NUMBER);
  public static Clz UINTNUM     = new Clz("ecstasy.numbers","UIntNumber",INTNUM);
  public static Clz JLONG       = new Clz("ecstasy.numbers","Int64",INTNUM);
  
  public static Clz FPNUM       = new Clz("ecstasy.numbers","FPNumber",NUMBER);
  public static Clz DECIMALFP   = new Clz("ecstasy.numbers","DecimalFPNumber",FPNUM);
  public static Clz DEC64       = new Clz("ecstasy.numbers","Dec64",DECIMALFP);

  static final HashMap<String, Clz> IMPORT_XJMAP = new HashMap<>() {{
      put("Boolean+ecstasy/Boolean.x",JBOOL);      
      put("Char+ecstasy/text/Char.x",JCHAR);
      put("Console+ecstasy/io/Console.x",CONSOLE);
      put("Const+ecstasy/Const.x",CONST);
      put("Dec64+ecstasy/numbers/Dec64.x",DEC64);
      put("DecimalFPNumber+ecstasy/numbers/DecimalFPNumber.x",DECIMALFP);
      put("Enum+ecstasy/Enum.x",ENUM);
      put("FPNumber+ecstasy/numbers/FPNumber.x",FPNUM);
      put("Hashable+ecstasy/collections/Hashable.x", HASHABLE);
      put("IllegalArgument+ecstasy.x",ILLARGX);
      put("IllegalState+ecstasy.x",ILLSTATEX);
      put("Int64+ecstasy/numbers/Int64.x",JLONG);
      put("IntNumber+ecstasy/numbers/IntNumber.x",INTNUM);
      put("Iterable+ecstasy/Iterable.x",ITER64);
      put("Mutability+ecstasy/collections/Array.x",MUTABILITY);
      put("Number+ecstasy/numbers/Number.x",NUMBER);
      put("Orderable+ecstasy/Orderable.x",ORDERABLE);
      put("Ordered+ecstasy.x",ORDERED);
      put("Range+ecstasy/Range.x",RANGE);
      put("StringBuffer+ecstasy/text/StringBuffer.x",STRINGBUFFER);
      put("Type+ecstasy/reflect/Argument.x",ARGUMENT);
      put("Type+ecstasy/reflect/Type.x",TYPE);
      put("UIntNumber+ecstasy/numbers/UIntNumber.x",UINTNUM); // TODO: Java prim 'long' is not unsigned
      put("UnsupportedOperation+ecstasy.x",UNSUPPORTEDOPERATION);
    }};
  private static String xjkey(ClassPart clz) { return clz._name + "+" + clz._path._str; }

  private static final HashSet<Clz> SPECIAL_RENAMES = new HashSet<>() {{
      add(ITER64);              // Remapped ecstasy.Iterable to ecstasy.Iterablelong
      add(STRING);              // Remapped to java.lang.String
      add(EXCEPTION);           // Remapped to java.lang.Exception
      add(ILLSTATEX); // Remapped to avoid collision with java.lang.IllegalStateException
      add(ILLARGX);   // Remapped to avoid collision with java.lang.IllegalArgumentException
    }};
  

  // Convert a Java primitive to the Java object version.
  private static final HashMap<Base, Clz> XBOX = new HashMap<>() {{
      put(BOOL,JBOOL);
      put(CHAR,JCHAR);
      put(LONG,JLONG);
      put(NULL,JNULL);
    }};
  public Clz box() {
    if( this instanceof Clz clz ) return clz;
    return XBOX.get(this);
  }
  // Convert a Java wrapped primitive to the unwrapped primitive
  static final HashMap<XType, Base> UNBOX = new HashMap<>() {{
      put(JBOOL,BOOL);
      put(JCHAR,CHAR);
      put(JLONG,LONG);
      put(JNULL,NULL);
    }};
  public XType unbox() {
    Base jt = UNBOX.get(this);
    return jt==null ? this : jt;
  } 
  public boolean primeq() { return XBOX.containsKey(this); }
  public String ztype() { return primeq() ? "0" : "null"; }
  public boolean is_jdk() { return primeq() || this==STRING; }
  
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
      if( ttc.part() instanceof ClassPart clz ) {
        if( clz._path==null ) {
          if( clz._name.equals("Null" ) ) yield NULL;
          if( clz._name.equals("True" ) ) yield TRUE;
          if( clz._name.equals("False") ) yield FALSE;
          throw XEC.TODO();
        }
        // Check the common base classes
        String xjkey = xjkey(clz);
        XType val = BASE_XJMAP.get(xjkey);
        if( val!=null ) yield boxed ? val.box() : val;
        // Again, common classes with imports
        Clz xclz = IMPORT_XJMAP.get(xjkey);
        if( xclz!=null ) {
          xclz.set(clz);
          yield ClzBuilder.add_import(xclz);
        }
        // Make one
        yield Clz.make(clz);
      }
      
      if( ttc.part() instanceof ParmPart ) {
        if( ttc.id() instanceof TParmCon tpc ) {
          yield ((Clz)xtype(tpc.parm()._con,boxed));
        } else if( ttc.id() instanceof DynFormalCon dfc ) {
          yield xtype(dfc.type(),false);
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
      ClassPart clz = ((ClzCon)ptc._con).clz();

      // These XTC classes are all intercepted and directly implemented in Java
      if( clz._name.equals("Array") && clz._path._str.equals("ecstasy/collections/Array.x") ) {
        XType telem = ptc._parms==null ? null : xtype(ptc._parms[0],false);
        XType.Ary xt = null;
        if( telem == BOOL  ) xt = ARYBOOL ; // Java ArrayList specialized to boolean
        if( telem == CHAR  ) xt = ARYCHAR ; // Java ArrayList specialized to char
        if( telem == JUBYTE) xt = ARYUBYTE; // Java ArrayList specialized to unsigned bytes
        if( telem == LONG  ) xt = ARYLONG ; // Java ArrayList specialized to int64
        if( xt==null ) xt = Ary.make(telem);
        yield ClzBuilder.add_import(xt);
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
        if( telem== LONG || telem== JLONG )
          yield ClzBuilder.add_import(RANGE); // Shortcut class
        throw XEC.TODO();
      }

      if( clz._name.equals("Iterator") && clz._path._str.equals("ecstasy/Iterator.x") ) {
        if( telem== LONG || telem== JLONG )
          yield ClzBuilder.add_import(ITER64); // Shortcut class
        throw XEC.TODO();
      }

    //  if( clz._name.equals("List") && clz._path._str.equals("ecstasy/collections/List.x") )
    //    yield "Ary<"+telem+">"; // Shortcut class

      if( clz._name.equals("Tuple") && clz._path._str.equals("ecstasy/collections/Tuple.x") )
        //yield org.xvm.xec.ecstasy.collections.Tuple.make_class(ClzBuilder.XCLASSES, xtype(ptc._parms));
        throw XEC.TODO();

    //  if( clz._name.equals("Map") && clz._path._str.equals("ecstasy/collections/Map.x") )
    //    yield XMap.make_class(ClzBuilder.XCLASSES, ptc._parms);
    //  
      // Attempt to use the Java class name
      if( clz._name.equals("Type") && clz._path._str.equals("ecstasy/reflect/Type.x") ) {
        //if( telem instanceof Clz tclz )
        //  yield tclz;
        //if( telem instanceof Ary ary )
        //  yield ARY;
        //if( telem instanceof Base base )
        //  yield Base.make("base clz (of java generic array)");
        //throw XEC.TODO();
        yield telem;
      }

      if( clz._name.equals("Appender") && clz._path._str.equals("ecstasy/Appender.x") ) {
        if( telem == CHAR || telem== JCHAR )
          yield ClzBuilder.add_import(APPENDERCHAR);
        throw XEC.TODO();
      }

      // No intercept, so just the class
      yield Clz.make(clz);
    }

    case ImmutTCon itc ->
      xtype(itc.icon(),boxed); // Ignore immutable for now

    // Generalized union types gonna wait awhile.
    // Right now, allow null unions only
    case UnionTCon utc -> {
      if( ((ClzCon)utc._con1).clz()._name.equals("Nullable") )
        yield xtype(utc._con2,true);
      XType u1 = xtype(utc._con1,false);
      XType u2 = xtype(utc._con2,false);
      yield Union.make(u1,u2);
    }

    case IntCon itc -> {
      if( itc._f == Const.Format.Int64 )
        yield boxed ? JLONG : LONG;
      throw XEC.TODO();
    }

    case CharCon cc ->
      boxed ? JCHAR : CHAR;
    
    case StringCon sc ->
      STRING;

    case EnumCon econ -> {
      // The enum instance as a ClassPart
      ClassPart eclz = (ClassPart)econ.part();
      // Th Enum class itself
      Clz xclz = Clz.make(eclz._super);
      yield new Clz(xclz._name,eclz._name,xclz);
      //yield Base.make(eclz._super._name).unbox();
      //throw XEC.TODO();
    }

    case LitCon lit -> {
      if( lit._f==Const.Format.IntLiteral )
        yield boxed ? JLONG : LONG;
      if( lit._f==Const.Format.FPLiteral )
        yield DEC64;
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

    case Dec64Con dcon ->
      DEC64;

    case ClassCon ccon ->
      Clz.make(ccon.clz());

    case ServiceTCon service ->
      SERVICE;

      case VirtDepTCon virt ->
        xtype(virt._par,false);
    
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
