package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.*;
import org.xvm.xtc.cons.*;

import static org.xvm.XEC.TODO;

import java.util.Arrays;
import java.util.HashMap;

// Basically a Java class as a XType.
//
// XTC classes have true generics/parameterization, no type erasure.
// We can map from an XTC ClassPart to a generified/erased Java class.
// We can map from a ParamTCon to a true generic XType, with XType generic classes.
// An XType carries the ClassPart it came from, but not the PTC (if any).
// XClz tracks super-class

/*
  current plan is broken when type names collide when coming from different sources.
  Canonical fail:
  - Outer class HasherMap<Key,Value>
  - Inner class CollectionImpl<Element> // has visible 3 type names: Element,Key,Value
  - KeySet extends CollectionImpl<Key>
  - - Here the "Key" is "HasherMap.Key" and is punning "CI.Element"
  - KeySetFreezer<Element extends X> mixins to KeySet<Key extends X>
  - - Here "Element" is a local name,
  - - punning "KeySet.Key" thus "HasherMap.Key" AND "CI.Element"

  Tentative New Plan
  - Class has self TNames, TVars only, no parents
  - - Rename strat for self-names to parent is "aligned,null name for concrete"
  - - Example: Base<Key,Value>            [Key   /XTC   , Value/XTC]
  - - - then   Derived<String,Value>      [null  /String, Value/XTC]
  - - - then   new Derived<String,Int>    [null  /String, null /Int]
  - Sides: map Interface names to self type index
  - - Example: IFace<Elem,QQQ>
  - - - Base<Key,Value> implements IFace<Value,Key>
  - - - Base.sides{ IFace -> [1,0] } // IFace.Elem maps to Base.type[1]/Value
  - Mixin standard gets cloned into a full Class;
  - Mixin conditional is Just Another Class with some code-gen rules on when to override

  Counter to New Plan:
  - - Example: Base<B1,B2,B3>
  - -          Derived<D1,D2,D3,D4> extends Base<D2,D3,Int>

  Tentative New Plan#B
  - Class has a set of local tvars, which are either renames of parent or standalone
  - - Inner class has all Outer directly (no rename, no subtype)
  - - Need a rename strat
  - All local tvars also have a type; this is a subtype of parent if renamed parent
  - IFaces - perhaps can ignore tvars...???? xcc has confirmed ok
  - Mixins are *just Classes*.  Normal for conditional, custom-per-base for standard.

  3 things per-type, so now kinda want Ary-of-Struct instead of Struct-of-Ary
  - local name
  - local type (subtype of parent, if any)
  - super class map (or null if new here)

 */


// XClz has a set of self-tvar-names in TNS, and a set of XTypes; a #of local
// TVars (length of _tns), a side mapping from interface XClz type
// indices into the XTS.

// Example class def:
//   Here E is a type variable.
//   interface List<E> { ... } // Has 1 TVar
//   XClz: List:1{E/XTC} side[]
//         Since List is interface, no concrete field for E

// Example class usage with a concrete type, not a type-variable:
//   class Nums implements List<Int> { ... } // Has ZERO TVars, and a mapping for List
//   XClz: Nums:0{/Int} side[List=[0]];
//         Since Nums is concrete, no super, has concrete field for E

//Example class usage:
//   Name is valid in local scope and NOT in List's scope
//   class MyList<Q> implements List<Q> { ... }
//   XClz: MyList:1{Q/XTC}, side[List=[0]]
//         Since MyList is concrete, no super, has concrete field for Q/E

//Example class usage:
//   Name and constraint are valid in local scope.
//   class MyList<E extends Hashable> implements List<E> { ... }
//   XClz: MyList:1{E/Hashable}, side[List=[0]]
//         Since MyList is concrete, no super, has concrete field for Q/E

//Example class usage:
//   class CaseInsensitive implements Hasher<String> { .... }
//   XClz: Hasher:1{Value/XTC}, side[]
//   XClz: CaseInsensitive:1{Value/String}, side[Hasher=[0]]
//         Since CaseInsensitive is concrete, no super, has concrete field for String/Value

//Example class usage:
//   class HashSet<E extends Hashable> implements HashMap<E,E> { .... }
//   XClz: HashMap:2{Key/Hashable,Value/XTC}, side[]
//   XClz: HashSet:1{E/Hashable}, side[HashMap=[0,0]]
//         Since HashSet is concrete, no super, has concrete field for Key/Value

// Example class usage:
//   class MyMap<A,B,C extends Hashable> implements HashMap<C,String>
//   XClz: HashMap:2{Key/Hashable,Value/XTC}, side[]
//   XClz:   MyMap:3[A/XTC,B/XTC,C/Hashable,/String], side[HashMap=[2,3]]
//         Since MyMap is concrete, no super, hash concrete field for Key/Value

// Example class usage:
//   class Super<E>
//   XClz: Super:1{E/XTC}, side[]
//         Since Super is concrete, no super, has concrete field for E
//   class Base<E> extends Super<E>
//   XClz: Base:1{E/XTC}, side[Super=0]
//         Since Base is concrete with Super and Super has field for E, no fields

public class XClz extends XType {
  static final String[] STR0 = new String[0];
  static final HashMap<XClz,int[]> SIDES0 = new HashMap<>();

  // The uniqueness is on these things: package, name, and all type parms.
  public String _pack, _nest, _name; // Package name, nested class name, short name
  public String[] _tns;         // Type names, matching up to _xts, but might be short
  public boolean _ro;           // Read/Only type; set if super is set
  // Other mappings for super and interface types.
  // These map from a ClassPart to an array of local xts indices, one per ClassPart tname
  HashMap<XClz,int[]> _sides;

  // Not part of uniqueness, kept as a convenient cache.
  public  XClz _super;           // Super xtype or null
  public  ModPart _mod;          // Self module
  public  ClassPart _clz;        // Self class, can be a module
  public  int _depth = -99;      // Depth for LCA
  public boolean _abstrct;       // Abstract class

  // Java-name, if any there exists a Java mirror implementation.
  // If the name is NOT equal to _pack,_name, then the name
  // also directly encodes the parameter.
  // Example: XTC Array<Char> vs Java Arychar
  //          XTC Array<Int>  vs Java Arylong
  public  String _jpack, _jname;

  // This is a temporary field; set and cleared per-compilation unit.
  // If true, in this compilation-unit use the fully qualified name
  // to avoid a name conflict.
  transient public boolean _ambiguous;

  // Bit for managing file-local code-gen
  public boolean _did_gen;

  // Only for mixins, the conditional types
  XType[] _mixts;

  // Private no-arg constructor, always use "make" for interning
  private XClz() { _pack = "0xDEADBEEF"; }


  // --------------------------------------------------------------------------
  // Interning: hash and compare the XClz-specific parts, check in the INTERN
  // table, manage FREE, fill in based fields.

  // Free XClz to recycle
  private static XClz FREE;

  // Using shallow equals & shallow hashCode, not deep, because the parts are already interned
  @Override boolean eq(XType xt) {
    XClz clz = (XClz)xt;
    if( !(S.eq(_name, clz._name) && S.eq(_nest, clz._nest) && S.eq(_pack, clz._pack) &&
          _ro == clz._ro &&
          Arrays.equals(_tns, clz._tns ) &&
          _sides.size() == clz._sides.size() ) )
      return false;
    // Deep array equals of a hash table.
    for( XClz iclz : _sides.keySet() ) {
      int[] idxs0 = _sides.get(iclz);
      int[] idxs1 = clz._sides.get(iclz);
      if( idxs1 == null ) return false; // Missing in other
      if( !Arrays.equals(idxs0,idxs1) ) return false;
    }
    return true;
  }
  @Override int hash() {
    int hash = _name.hashCode() ^ _nest.hashCode() ^ _pack.hashCode() ^ (_ro ? 2048 : 0);
    for( String tname : _tns )
      hash ^= tname.hashCode();
    return hash;
  }

  // Intern, but do not fill in the extra info fields.
  XClz _intern() {
    assert _pack != "0xDEADBEEF" && _sides!=null; // Got set
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
    _depth = proto._depth;
    return clz;
  }

  // --------------------------------------------------------------------------

  // Makes a new uninterned XCLZ.
  // Fills in package,nesting,name,null-ness.
  // Default R/W.
  // Defaults not-a-java-mirror.
  static XClz mallocBase( boolean notNull, String pack, String nest, String name ) {
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
    clz._jname = clz._jpack = ""; // So toString doesn't barf on half built XClz
    return clz;
  }

  // Makes a new uninterned XCLZ from above malloc.
  //   Fills in package,nesting,name,null-ness.
  //   Default R/W.
  //   Defaults not-a-java-mirror.
  // Has blank xts/tns arrays ready to be filled in.
  static XClz mallocLen( boolean notNull, String pack, String nest, String name, int len ) {
    XClz clz = mallocBase(notNull,pack,nest,name);
    // See if we can reuse the xts,flds
    if( clz._xts==null || clz._xts.length != len )  clz._xts = len==0 ? EMPTY : new XType [len];
    if( clz._tns==null || clz._tns.length != len )  clz._tns = len==0 ? STR0  : new String[len];
    clz._sides = SIDES0;
    return clz;
  }

  // Makes a new uninterned XClz cloned from the prototype.
  // SHARES xts/tns; so these need to be defensively copied before changing.
  private XClz _mallocClone() {
    XClz clz = mallocBase(_notNull,_pack, _nest, _name);
    clz._xts = _xts;
    clz._tns = _tns;
    clz._sides = _sides;
    clz._mod = _mod;
    clz._clz = _clz;
    return clz;
  }

  // Nested names; usually blank
  private static String _cnest(ClassPart clz) {
    ModPart mod = clz.mod();
    return clz==mod || clz._par instanceof MethodPart meth || !mod._path.equals(clz._path)
      ? ""
      // Class embedded in a Module file; mangle name to avoid Module name.
      // java name is always Module.M$Module.Class
      : ("M$"+S.java_class_name(mod.name())).intern();
  }

  private static String _cname( ClassPart clz ) {
    ModPart mod = clz.mod();
    String cname = S.java_class_name(clz._name);
    // Module: java name is always Module.M$Module
    if( clz==mod ) return ("M$"+cname).intern();
    if( clz._par instanceof MethodPart meth ) // Class nested inside a Method
      return S.java_class_name(meth._name+"$"+clz._name);
    // Normal case, just the class name
    return cname;
  }

  // "Normal" classes and modules come in here.
  private static XClz _malloc( ClassPart clz ) {
    return _malloc(clz, _cnest(clz), _cname(clz), get_super(clz));
  }

  // Common XClz constructor for most cases: passed in a bunch of fields
  // which vary by the path we get here.
  // Fills in common fields.  Not interned yet.  Handles all kinds of Contribs.
  private static XClz _malloc( ClassPart clz, String cnest, String cname, XClz supr ) {
    boolean abstrct = false;
    assert supr!=null || get_super(clz)==null;

    // Make a basic XClz with space for type vars
    ModPart mod = clz.mod();
    XClz xclz = mallocBase( true, pack(clz,mod), cnest, cname );
    xclz._clz = clz;
    xclz._mod = mod;  // Self module
    xclz._jname = xclz._jpack = "";

    // Fill in self type variables
    XType [] xts = xclz._xts = new XType[clz._tnames==null ? 0 : clz._tnames.length];
    String[] tns = xclz._tns = clz._tnames==null ? STR0 : clz._tnames;
    for( int i=0; i<tns.length; i++ )
      xts[i] = xtype(clz._tcons[i],true,xclz);
    // Inner classes pick up all outer class type variables
    if( clz._f != Part.Format.MIXIN && clz._f != Part.Format.INTERFACE ) {
      Part par = clz._par;
      while( par != null && par.getClass() == ClassPart.class ) {
        ClassPart pclz = (ClassPart) par;
        XClz pxclz = make(pclz);
        for( int i=0; i<pxclz._tns.length; i++ ) {
          if( S.find(tns,pxclz._tns[i])== -1 ) {
            int len = xts.length;
            xclz._xts = xts = Arrays.copyOf(xts,len+1);
            xclz._tns = tns = Arrays.copyOf(tns,len+1);
            xts[len] = pxclz._xts[i];
            tns[len] = pxclz._tns[i];
          }
        }
        par = par._par;
      }
    }

    // Gather side types from Implements and Extends.
    // May extend the _xts array.
    xclz.sides(clz);

    // Look for mixins
    if( clz._contribs != null )
      for( Contrib c : clz._contribs ) {
        switch( c._comp ) {

          // Many of these have explicit runtime or meta-class behaviors.
          // Object <- Base_this <- annot_Mixin <- Derived
        case Part.Composition.Annotation:
          ClassPart annot = ((ClassCon)c._annot._par).clz();
          if( annot._name.equals("Abstract") ) { abstrct = true; break; }
          else if( annot._name.equals("Override") ) { break; } // Ignore overrides
          else throw XEC.TODO();

        case Part.Composition.Into:
          // Don't ask for the base, because the base is asking for the mixin!
          // Will cause infinite loop.  Instead, just move along.
          break;             // Mixin_this is mixing into named base, any style
        case Part.Composition.Incorporates: {
          XClz mixin0 = make(c); // The bare mixin
          if( c._parms==null ) {
            // No parameters, so this is a standard "incorporates".  Make a
            // single class with fields and functions with the Base overriding
            // any Mixin fields.  This becomes the new supr class.
            // Object <- Super <- MixIn <- Base <- Derived
            String mixname = (mixin0._name+"$"+cname).intern();
            XClz mixin = _malloc(mixin0._clz,mixin0._nest,mixname,supr);
            mixin._abstrct = true;
            mixin._sides = xclz._sides;
            mixin = mixin._intern();
            ClassPart mixclass = new ClassPart(mixin0._clz,mixname);
            mixclass._tclz = mixin;
            mixin._clz = mixclass;

            // Sides for self use mixin as the supr
            xclz._sides = new HashMap<>();
            for( XClz s : mixin._sides.keySet() )
              xclz._sides.put( s==supr ? mixin : s, mixin._sides.get(s) );
            supr = mixin;

          } else {
            // Has parameters, so conditional incorporates; the Mixin overrides
            // the Base.  The Mixin class always exists and requires a runtime
            // test to actually execute.
            // Object <- Super <- Base <- Mixin <- Derived
            // Finish xclz
            xclz._super = supr;
            xclz._depth = supr==null ? 0 : supr._depth+1;
            xclz._abstrct = abstrct;
            xclz = xclz._intern();
            // Recursively make mixin with custom name and super
            String mixname = (cname+"$"+mixin0._name).intern();
            XClz mixin = _malloc(mixin0._clz,mixin0._nest,mixname,xclz);
            mixin._mixts = mixin._xts;
            mixin.  _xts = xclz ._xts;
            // Sides for mixin use xclz as the supr
            mixin._sides = new HashMap<>();
            for( XClz s : xclz._sides.keySet() )
              mixin._sides.put( s==supr ? xclz : s, xclz._sides.get(s) );
            supr = xclz;
            xclz = mixin;
          }
          break;
        }
        case Part.Composition.Extends:
          assert clz._super != null; break; // Already picked up
        case Part.Composition.Implements: break; // Normal interface.  Maybe someday collect them here
        case Part.Composition.Delegates: break; // TODO: Dunno what this is supposed to do
        case Part.Composition.RebasesOnto:
        case Part.Composition.Import:
        case Part.Composition.Equal:
          throw XEC.TODO();     // TODO
        }
      }

    xclz._super = supr;
    xclz._depth = supr==null ? 0 : supr._depth+1;
    xclz._abstrct = abstrct;

    return xclz;
  }

  // Gather side types from Implements and Extends and Incorporates.
  private void sides( ClassPart clz ) {
    _sides = SIDES0;
    if( clz==null || clz._contribs == null ) return;

    for( Contrib c : clz._contribs )
      if( c._comp == Part.Composition.Implements || c._comp == Part.Composition.Extends || c._comp == Part.Composition.Incorporates )
        switch( c._tContrib ) {
        case TermTCon ttc: break;   // no extra type params
        case ImmutTCon imm when imm.icon() instanceof TermTCon ttc: break;
        case ParamTCon ptc: _sides(ptc,_tns);  break;
          // No tvars here?
        case InnerDepTCon dep:  assert dep .clz()._tnames.length==0;  break;
        case VirtDepTCon virt:  assert virt.clz()._tnames.length==0;  break;
        default: throw XEC.TODO(); // Expecting a PTC here
        }
  }

  private void sides( ParamTCon ptc, String[] tnames ) {
    _sides = SIDES0;
    _sides(ptc,tnames);
  }
  private void _sides( ParamTCon ptc, String[] tnames ) {
    // Now I got some sides!
    if( _sides == SIDES0 ) _sides = new HashMap<>();
    // Setup a side array from the base type to its parameterized version
    XClz pclz = (XClz)xtype(ptc,true);
    int[] idxs = new int[ptc._parms.length];
    // Walk the parameterized types; refer back to the class's types
    for( int i=0; i<ptc._parms.length; i++ ) {
      String pname = ptc._parms[i] instanceof TermTCon ttc ? ttc.name() : pclz._tns[i];
      int idx = S.find(tnames,pname); // Matches a clz name, uses that type
      if( idx == -1 ) {                    // No match, add an entry
        _xts = Arrays.copyOf(_xts,(idx = _xts.length)+1);
        _xts[idx] = pclz._xts[i];
      }
      idxs[i] = idx;
    }
    _sides.put(pclz,idxs);
    //assert ptc._parms.length==pclz._tns.length;
  }

  // Made from XTC class.
  //   Here E is the class name for a type
  //   class List<E> { ... }
  //   XClz: List{E=XTC}
  public static XClz make( ClassPart clz ) {
    if( clz._tclz != null )     // Check class cache
      return clz._tclz;
    XClz xclz = _malloc(clz);
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

    assert proto._xts.length>0;
    XClz xclz = proto._mallocClone();
    xclz._xts = proto._xts.clone();
    xclz._tns = proto._tns.clone();
    // Override parameterized type fields
    if( ptc._parms != null )
      for( int i=0; i<ptc._parms.length; i++ )
        xclz._xts[i] = xtype(ptc._parms[i],true,xclz);

    return xclz._intern(proto);
  }

  public static XClz make( Contrib c ) {
    // Get the UNparameterized class
    ClassPart clz = ((ClzCon)c._tContrib).clz();
    XClz proto = make(clz);
    if( c._parms==null ) return proto;

    assert proto._xts.length>0;
    XClz xclz = proto._mallocClone();
    xclz._xts = proto._xts.clone();
    xclz._tns = proto._tns.clone();
    // Override parameterized type fields
    for( String tn : c._parms.keySet() ) {
      int i = S.find(xclz._tns,tn);
      xclz._xts[i] = xtype(c._parms.get(tn),true,xclz);
    }
    return xclz._intern(proto);
  }


  // Make a R/O version
  public static XClz make( ImmutTCon imm ) {
    if( imm.icon() instanceof TermTCon ttc ) {
      XClz clz = make(ttc.clz());
      return clz.readOnly();
    }
    throw XEC.TODO();
  }


  // An inner class, which gets the outer class Type variables
  public static XClz make( VirtDepTCon virt ) {
    ClassPart iclz = virt.part(); // Inner  clz
    XClz xclz = make(iclz);       // Inner xclz
    XClz vclz = xclz._mallocClone(); // Inner xclz, cloned
    vclz.sides((ParamTCon)virt._par,iclz._tnames);

    XClz vclz2 = vclz._intern();
    if( vclz2 != vclz ) return vclz2;
    iclz._tclz = vclz;
    return vclz;
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
    if( clz._path==null ) { assert clz._f==Part.Format.CLASS; return null; }
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
    XClz clz = _mallocClone();
    clz._notNull = false;
    return clz._intern(this);
  }
  @Override public XClz readOnly() {
    if( _ro ) return this;
    XClz clz = _mallocClone();
    clz._ro = true;
    return clz._intern(this);
  }

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
  // MIXIN "incorporates" style
  public final boolean inCorp() {
    return _clz._f==Part.Format.MIXIN;
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

  // Example: Collection.x, equals() method, enum NonExistent [NotAValue]
  // _pack: ecstasy.collections
  // _nest: ""
  // _name: Collection.equals$NonExistent
  // _name: Collection.equals$NonExistent.NotAValue
  private static String pack(ClassPart pclz, ModPart mod) {
    return _pack(pclz,mod.name()).intern();
  }
  private static String _pack(Part pclz, String mod) {
    // Observed "_par" grammar:
    // File.Mod               - Just the module directly, e.g. module tck
    // File.Mod.Clz           - stand-alone module, no package
    // File.Mod.Pack.Clz      - Part of a package deal, e.g. tck.array.Basic
    // File.Mod.Pack.Clz.Clz  - Nested class (or enum), e.g. ecstasy.collections.Array.ArrayDelegate
    // File.Mod.Pack.Pack.Clz - Nested package        , e.g. ecstasy.collections.deferred.DeferredCollection
    // File.Mod.Pack.Clz.MMeth.Meth.Clz - Method-local class (or enum), e.g. tck.constructors.Basic.Test
    // File.Mod.Pack.Clz.Prop.MMeth.Meth.Clz - Method-local class (or enum) as a property init
    return switch( pclz._par ) {
    case    FilePart file -> mod;  // e.g. module tck
    case     ModPart mod2 -> mod;  // eg. module ecstasy
    // case PackagePart pack; EXTENDS CLASSPART             // e.g. ecstasy.collections.deferred
    case   ClassPart clz  -> _pack(clz ,mod)+"."+clz._name; // e.g. ecstasy.collections.Array
    case  MethodPart meth -> _pack(meth._par,mod);          // e.g. tck.constructors.Basic
    case    PropPart prop -> prop._name+"$"+_pack(prop,mod);
    default -> {
      for( Part p=pclz; p!=null; p = p._par )
        System.out.print(p.getClass().getSimpleName()+" ");
      System.out.println();
      throw XEC.TODO();
    }
    };
  }

  @Override public boolean needs_import(boolean self) {
    // Built-ins before being 'set' have no clz, and do not needs_import
    if( this==XCons.XXTC  ) return false;
    if( this==XCons.JTRUE ) return false;
    if( this==XCons.JFALSE) return false;
    if( this==XCons.JNULL ) return false;
    if( this==XCons.JSTRING ) return false;
    if( this==XCons.JSTRINGN ) return false;
    // Everything in java.lang.* imports by default already
    if( S.eq("java.lang",_jpack) ) return false;
    // Self module is also compile module.
    if( self && _clz == ClzBuilder.CCLZ ) return false; // Self does not need to import self
    // No clz is reserved for Java builtins with no corresponding XTC class
    // (e.g. RangeII).
    return true;
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

  // Local only find
  public XType _find(String tn) {
    int idx = S.find(_tns,tn);
    return idx == -1 ? null : _xts[idx];
  }

  // Prints/passes the type parameters.
  // Other than the java mirrors with types, always:
  //   Map<Key,Value>  ...  new Map(Key.GOLD,Value.GOLD,....)
  // AryXTC yes also:
  //   AryXTC<Person>  ...  new Ary(Person.GOLD,....)
  // Other arrays or mirrors include the class name in the base java name:
  //   Arylong         ...  new Arylong(...);    // As opposed to: Arylong<Int64.GOLD>   or AryXTC<Int64.GOLD>
  //   Appenderchar    ...  new Appenderchar();  // As opposed to: Appendchar<Char.GOLD> or Appender<Char.GOLD>
  // Tuples include type names in the printed version but not the XClz version
  //   Tuple2$long$long                          // As opposed to Tuple2$long$long<long,long>
  public boolean printsTypeParm() {
    return _jname.isEmpty() ||  // Not a java mirror
      S.eq(_jname,"AryXTC") ||  // The one typed mirror needing an explicit type
      (S.eq(_jname,_name) &&    // Java mirror and name does not include type
       !S.eq(_jname,"Tuple") ); // Tuples with types and not generics
  }

  @Override public SB str( SB sb, VBitSet visit, VBitSet dups  ) {
    _clz_bare(sb,false);
    if( _super!=null ) sb.p(":").p(_super._name);
    if( _tns != null && _tns.length > 0 ) {
      sb.p(" {");
      for( int i = 0; i< _tns.length; i++ ) {
        sb.p("  ");
        if( _tns[i]!=null ) sb.p( _tns[i]);
        sb.p(":");
        if( _xts[i] != null )  _xts[i]._str(sb,visit,dups);
        sb.p(";");
      }
      sb.p("}");
    }
    if( !_notNull ) sb.p("?");
    return sb;
  }


  // Bare name, no generics
  public String clz_bare( ) {
    // Tuples have a mangled class name without generics
    if( isTuple() ) return strTuple(new SB()).toString();
    // These guys need a fully qualified name to avoid name conflicts
    if( this==XCons.JSTRING || this==XCons.JOBJECT || _ambiguous ) return qualified_name();
    return name();
  }

  @Override public SB clz_bare( SB sb ) { return _clz_bare(sb,true); }
  private  SB _clz_bare( SB sb, boolean imprt ) {
    // These guys need a fully qualified name to avoid name conflicts
    if( this==XCons.JSTRING || this==XCons.JSTRINGN ||this==XCons.JOBJECT || _ambiguous )
      return sb.p(qualified_name());
    if( imprt ) ClzBuilder.add_import(this);
    // Tuples have a mangled class name without generics
    if( isTuple() ) return strTuple(sb);
    return sb.p(name());
  }

  // Walk and print Java class name, including generics.  If XTC is providing
  // e.g. method-specific generic names, use them instead.  Using "T" here
  // instead of "XTC": "<T extends XTC> void bar( T gold, AryXTC<T> ary )"
  @Override SB _clz( SB sb, ParamTCon ptc ) {
    _clz_bare(sb,false);

    // Print generic parameters?
    if( _tns.length==0 ) return sb; // None to print
    if( !printsTypeParm() ) return sb; // Java mirror encodes the generic type name already

    // Printing generic type parameters
    sb.p("<");
    for( int i=0; i<_tns.length; i++ ) {
      // No ParamTCon provided, using the generic class as-is
      if( ptc==null )  _xts[i]._clz(sb,null);
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
    String name = name();
    if( isTuple() ) name = strTuple(new SB()).toString();
    String pack = pack().isEmpty() ? "" : "."+pack();
    String nest = _nest .isEmpty() ? "" : "."+_nest ;
    return (XEC.XCLZ + pack + nest + "." + name).intern();
  }

  // "Foo<Value extends Hashable>"
  public SB clz_generic_def( SB sb ) {
    // No named type arguments
    if( _tns.length==0 ) return sb;

    sb.p("< ");
    for( int i=0; i<_tns.length; i++ ) {
      sb.p("$").p( _tns[i]);
      sb.p(" extends " );
      XClz xclz = (XClz)_xts[i];
      // Conditional incorps must weaken, no generics for the Java side
      //if( !inCorp() ) xclz = XCons.XXTC;
      if( xclz.iface() ) {
        sb.p("XTC & ");
        sb.p(xclz.name());
      } else {
        sb.p(xclz.clz());
        xclz.clz_generic_def(sb);
      }
      sb.p(", ");
    }
    return sb.unchar(2).p("> ");
  }
  // "Map<Key,Value>"
  public SB clz_generic_use( SB sb, XClz base ) {
    assert !_ambiguous;
    // No named type arguments
    int[] idxs = base._sides.get(this);
    if( idxs==null ) return sb;
    sb.p("<");
    for( int idx : idxs )
      if( idx < base._tns.length ) sb.p("$").p(base._tns[idx]).p(", ");
      else base._xts[idx].clz_bare(sb).p(", ");
    return sb.unchar(2).p("> ");
  }

  private SB strTuple( SB sb ) {
    sb.p("Tuple").p(_xts.length).p("$");
    for( XType xt : _xts )
      xt.clz_bare(sb).p("$");
    return sb.unchar();
  }


  // -------------------------------------------------------------------------
  // Does 'this' subclass 'sup' ?
  private boolean subClasses( XClz sup ) {
    if( _clz==sup._clz ) return true;
    if( _super==null ) return false;
    return _super.subClasses(sup);
  }

  private boolean subIFaces( XClz iface ) {
    if( isIFace(iface) ) return true;
    return _super != null && _super.subIFaces( iface );
  }
  // No chasing class super, yes chasing IFace super
  private boolean isIFace( XClz iface ) {
    if( _clz!=null && _clz._contribs!=null )
      for( Contrib c : _clz._contribs )
        if( c._comp == Part.Composition.Implements ) {
          XClz iclz = (XClz)xtype(c._tContrib, false);
          if( iclz == iface || iclz.isIFace(iface) )
            return true;
        }
    return false;
  }

  @Override boolean _isa( XType xt ) {
    XClz clz = (XClz)xt;        // Contract
    if( xt == XCons.XXTC ) return true; // Everything isa XTC
    // Interface or subclass check.  Const, Service are declared interface in
    // XTC but declared a Class in Java.
    if( !iface() && clz.iface() && clz != XCons.CONST && clz != XCons.SERVICE )
      return subIFaces(clz);
    // Check basic subclassing.  Two classes or two interfaces come here.
    if( !subClasses(clz) ) return false;
    if( _xts.length < clz._xts.length ) return false;
    // Now check pieces-parts
    for( int i=0; i<clz._xts.length; i++ )
      if( !_xts[i].isa(clz._xts[i]) )
        return false;
    return true;
  }

  // Common ancestor
  static XClz lca( XClz u0, XClz u1 ) {
    while( u0._depth < u1._depth ) u1 = u1._super;
    while( u1._depth < u0._depth ) u0 = u0._super;
    while( u0!=u1 ) { u0 = u0._super; u1 = u1._super; }
    return u0;
  }
}
