package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.*;
import org.xvm.xtc.cons.*;
import static org.xvm.XEC.TODO;

// Basically a Java class as a XType.
//
// XTC classes have true generics/parameterization, no type erasure.
// We can map from an XTC ClassPart to a generified/erased Java class.
// We can map from a ParamTCon to a true generic XType, with XType generic classes.
// An XType carries the ClassPart it came from, but not the PTC (if any).
// XClz tracks super-class.

// Example: class Base<Key>; class Child extends Base;
// Base  : {"Key" ,null , -} // Key is local to Base
// Child : {"Key" ,Base , -} // Key is local to Base

// Example: interface IFace<Elem>;  class Base<Key> implements IFace<Key>;
// IFace : {"Elem",null , -   } // Elem is local to IFace
// Base  : {"Key" ,null , -   } // Key  is local to Base
// Base  : {"Elem",IFace,"Key"} // Elem is local to IFace, tied to local Key

// Example: class HashSet<E extends Hashable> implements HashMap<E,E> { .... }
// HasherMap: {"Key"  ,  null   , - }
// HasherMap: {"Value",  null   , - }
// HashMap:   {"Key"  ,HasherMap, - }:Hashable
// HashMap:   {"Value",HasherMap, - }
// HashSet:   {"E"    ,  null   , - }:Hashable
// HashSet:   {"Key"  ,HasherMap,"E"}:Hashable
// HashSet:   {"Value",HasherMap,"E"}


// Example, Part 1: Outer class HasherMap<Key,Value>
// HasherMap : {"Key"  ,null,-} // Key   is local to HasherMap
// HasherMap : {"Value",null,-} // Value is local to HasherMap

// Example, Part 2: Inner class CollectionImpl<Element>
// CollectionImpl : {"Element",null     ,-} // Element is local to CollectionImpl
// CollectionImpl : {"Key"    ,HasherMap,-} // Key   is HasherMap@0
// CollectionImpl : {"Value"  ,HasherMap,-} // Value is HasherMap@1

// Example, Part 3: KeySet extends CollectionImpl<Key>
// KeySet : {"Element",CollectionImpl,"Key"} // Element is CollectionImpl@0; also Key
// KeySet : {"Key"    ,HasherMap     ,-} // Key     is HasherMap@0
// KeySet : {"Value"  ,HasherMap     ,-} // Value   is HasherMap@1

// Example, Part 4: KeySetFreezer is a MIXIN
// KeySetFreezer : {"Element", null, -} // Element is local to KeySetFreezer

// Example, Part 5: KeySet<Key> incorps KeySetFreezer<Key extends X>
// So KeySetFreezer$MIX<Key extends X> extends KeySet<Key>
// KeySetFreezer$MIX : {"Element",CollectionImpl,"Key"} // Element is CollectionImpl@0; also Key
// KeySetFreezer$MIX : {"Key"    ,HasherMap     ,-} // Key     is HasherMap@0
// KeySetFreezer$MIX : {"Value"  ,HasherMap     ,-} // Value   is HasherMap@1


// Example, from tck/clazz/Medium.x, typevars:
// IFaceRep: { "KeyIFace",null    ,  -   }
// IFaceCol: {"ElemIFace",null    ,  -   }
// Outer   : { "Key"     ,null    ,  -   }
// Outer   : { "Val"     ,null    ,  -   }
// Outer   : { "KeyIFace",IFaceRep,"Key" }
// Inner   : {"Elem"     ,null    ,  -   }
// Inner   : { "Key"     ,Outer   ,  -   }
// Inner   : { "Val"     ,Outer   ,  -   }
// Inner   : { "KeyIFace",IFaceRep,"Key" }
// Inner   : {"ElemIFace",IFaceCol,"Elem"}
// Derived : {"Elem"     ,Inner   ,  -   }
// Derived : { "Key"     ,Outer   ,  -   }
// Derived : { "Val"     ,Outer   ,  -   }
// Derived : { "KeyIFace",IFaceRep,"Key" }
// Derived : {"ElemIFace",IFaceCol,"Elem"}

public class XClz extends XType<TVarZ> {
  // the uniqueness is on these things: package, name, ro, and all type parms.
  public String _pack, _nest, _name; // Package name, nested class name, short name
  public boolean _ro;           // Read/Only type; set if super is set

  // Not part of uniqueness, kept as a convenient cache.
  public XClz _super;           // Super xtype or null
  public XClz _outer;           // Outer xtype or null; might be indirect
  public ModPart _mod;          // Self module
  public ClassPart _clz;        // Self class, can be a module
  public int _depth = -99;      // Depth for LCA
  public boolean _abstrct;       // Abstract class

  // Java-name, if any there exists a Java mirror implementation.
  // If the name is NOT equal to _pack,_name, then the name
  // also directly encodes the parameter.
  // Example: XTC Array<Char> vs Java Arychar
  //          XTC Array<Int>  vs Java Arylong
  public String _jpack, _jname;

  // This is a temporary field; set and cleared per-compilation unit.
  // If true, in this compilation-unit use the fully qualified name
  // to avoid a name conflict.
  transient public boolean _ambiguous;

  // Bit for managing file-local code-gen
  public boolean _did_gen;

  // Only for conditional mixins, the conditional types that are tested (and upcast).
  TVarZ[] _mixs;

  // Private no-arg constructor, always use "make" for interning
  private XClz() { _pack = "0xDEADBEEF"; _tvars = new Ary<>(TVarZ.class); }

  // Get a tvar by name
  public TVarZ tvar(String name) {
    int idx = idx(name);
    return idx == -1 ? null : at(idx);
  }
  // Get tvar index by name
  public int idx(String name) {
    for( int i=0; i<len(); i++ )
      if( S.eq(at(i)._name,name) )
        return i;
    return -1;
  }

  public int mix(XType xt) {
    for( int i=0; i<_mixs.length; i++ )
      if( _mixs[i]._xt==xt )
        return i;
    return -1;
  }

  // Put a subtype; might refine a super-type.
  public void putSub( String name, XType xt ) {
    int idx = idx(name);
    if( idx != -1 ) {
      TVar old = at(idx);
      if( !(old instanceof TVarZ tv2) ) throw XEC.TODO();
      _tvars.set(idx,tv2.makeRefine(xt));
    } else
      _tvars.add(TVarZ.make(xt,name));
  }
  // Put a subtype; must refine or rename a super-type.
  public void putSub( int idx, String name, XType xt ) {
    // Always refining a type
    TVarZ tv = at(idx);
    assert xt.isa(tv._xt) || isMixin();
    assert S.eq(tv._name,name);
    TVarZ tvr = tv.makeRefine(xt);
    if( tvr==tv ) return;
    _tvars.set(idx,tvr); // Replace with refined type

    // Pick up constraint on local renames also
    for( int i=0; i<len(); i++ ) {
      TVarZ tv2 = at(i);
      if( S.eq(tv2._local,name) )
        _tvars.set(i,tv2.makeRefine(xt));
    }
  }

  // Either adding or refining; can also add a local map
  public void put( XType xt, String name, XClz def, String local ) {
    int idx = idx(name);
    TVarZ tv = TVarZ.make(xt,name,def,local);
    if( idx != -1 ) {
      TVarZ old = at(idx);
      assert xt.isa(old._xt);
      if( old._local != null && local==null ) local = old._local; // Do not lose an old local name
      assert old._local==null || S.eq(local,old._local);
      assert old._def  ==null || def == old._def;
      _tvars.set(idx,tv);
    } else {
      _tvars.add(tv);
    }
  }

  public void putAllTypes(XClz clz) {
    _tvars.addAll(clz._tvars);
  }

  // Is this named TVar derived from my outer class?
  // If so, access is "$outer.name" vs just "name"
  public boolean outer(String name) {
    TVarZ tv = tvar(name);
    if( tv==null ) return false;
    if( tv._def == _outer ) return true;
    return tv._def!=null && tv._def.outer(name);
  }

  // Number of local type vars
  public int localLen() {
    int len=0;
    for( TVarZ tv : _tvars )
      if( tv.local() )
        len++;
    return len;
  }

  // --------------------------------------------------------------------------
  // Interning: hash and compare the XClz-specific parts, check in the INTERN
  // table, manage FREE, fill in based fields.

  // Free XClz to recycle
  private static XClz FREE;

  // Using shallow equals & shallow hashCode, not deep, because the parts are already interned
  @Override boolean eq(XType xt) {
    XClz clz = (XClz)xt;
    if( !(S.eq(_name, clz._name) && S.eq(_nest, clz._nest) && S.eq(_pack, clz._pack) &&
          _ro == clz._ro ) )
      return false;
    return _tvars.equals(clz._tvars);
  }

  @Override long hash() {
    long hash = S.rot(_name.hashCode(),13) ^ S.rot(_nest.hashCode(),17) ^ S.rot(_pack.hashCode(),23) ^ (_ro ? 2048 : 0);
    hash ^= S.rot(_tvars.hashCode(),29);
    return hash;
  }

  @Override void _dups(VBitSet visit, VBitSet dups) {
    if( this!=XCons.XXTC )
      super._dups(visit,dups);
  }

  @Override SB _str1( SB sb, VBitSet visit, VBitSet dups  ) {
    _clz_bare(sb,false);
    if( _super!=null ) sb.p(":").p(_super._name);
    if( !_tvars.isEmpty() ) {
      sb.p(" { ");
      for( TVarZ tv : _tvars )
        tv._xt._str0(sb.p(tv._name).p(":"),visit,dups).p(",");
      sb.unchar().p("}");
    }
    if( !_notNull ) sb.p("?");
    return sb;
  }

  // Intern, but do not fill in the extra info fields.
  XClz _intern() {
    assert _pack != "0xDEADBEEF" ; // Got set
    for( TVarZ tv : _tvars ) {
      TVarZ tvx = tvar(tv._local);
      if( tvx != null && tvx != tv && tvx._local!=null )
        throw XEC.TODO();       // Double indirection, needs to U-F
    }

    XClz clz = (XClz)intern(this);
    if( clz!=this ) { FREE = this; _tvars.clear(); _pack = "0xDEADBEEF"; }
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
    _outer = proto._outer;
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
    assert clz._tvars.isEmpty();
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

  // Makes a new uninterned XClz cloned from the prototype.
  // SHARES _tvars; so these need to be defensively copied before changing.
  XClz _mallocClone() {
    XClz clz = mallocBase(_notNull,_pack, _nest, _name);
    clz._mod   = _mod;
    clz._clz   = _clz;
    clz._super = _super;
    clz._outer = _outer;
    // Copy tvars from original
    clz.putAllTypes(this);
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
      // If the method is in a property, use that for a more unique mangled name
      return S.java_class_name(meth._name+"$"+
                               (meth._par._par instanceof PropPart prop ? prop._name : clz._name));
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
    for( int i=0; i<clz._tnames.length; i++ )
      xclz._tvars.add(TVarZ.make(xtype(clz._tcons[i],true,xclz), clz._tnames[i]));

    // Gather side types from Implements and Extends.
    if( clz._contribs != null )
      for( Contrib c : clz._contribs )
        if( c._comp == Part.Composition.Implements || c._comp == Part.Composition.Extends /*|| c._comp == Part.Composition.Incorporates*/ ) {
          XClz pclz = (XClz)xtype(c._tContrib,true);
          // Take all the type vars from super,mixin,interface
          for( TVarZ tv : pclz._tvars )
            xclz.put(tv._xt,tv._name,pclz,null);
          if( c._tContrib instanceof ParamTCon ptc ) {

            // Walk the parameterized types; refer back to the class's types and refine
            for( int i=0; i<ptc._parms.length; i++ ) {
              if( ptc._parms[i] instanceof TermTCon ttc ) {
                // PCLZ is usually extends, so super class.  E.g. InnerOut is the super of Derived

                // Super-class type var.  E.g. Elem:XTC
                TVarZ ptv = pclz.at(i);

                // Refined super-class type
                XType px = xtype(ptc._parms[i],true);
                assert px.isa(ptv._xt);
                // Name in the self/local.  E.g. Key, which is in Outer.Key:XTC
                String tname = ttc.part() instanceof PropPart pp ? pp._name : null;
                // Unify self, with refinement, with super-class type var
                xclz.put(px,ptv._name,pclz,tname);
              } else
                throw XEC.TODO();
            }
          }
        }


    // An inner class has an instance of the outer class, and gets to access
    // all the outer type variables; derived inner classes already got them
    // from their super.
    ClassPart outer = clz.isNestedInnerClass();
    if( outer!=null && supr==null ) {
      XClz out = outer.xclz();
      for( int i=0; i<out.len(); i++ ) {
        TVarZ tv = out.at(i);
        xclz.put(tv._xt,tv._name,out,null);
      }
    }

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
            //mixin._sides = xclz._sides;
            //mixin = mixin._intern();
            //ClassPart mixclass = new ClassPart(mixin0._clz,mixname);
            //mixclass._tclz = mixin;
            //mixin._clz = mixclass;
            //
            //// Sides for self use mixin as the supr
            //xclz._sides = new HashMap<>();
            //for( XClz s : mixin._sides.keySet() )
            //  xclz._sides.put( s==supr ? mixin : s, mixin._sides.get(s) );
            //supr = mixin;
            throw XEC.TODO();

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
            // If the mixin is *inside* the class its mixing, lift it to be parallel:
            // "class Plain incorp cond Mix<E extends X> { private static mixin <E extends XX> into Plain"
            if( mixin0._clz._par==xclz._clz ) {
              assert mixin._pack.endsWith(xclz._name);
              mixin._pack = mixin._pack.substring(0,mixin._pack.length()-1-xclz._name.length());
            }

            // Set aside the conditional types for code gen later.
            // The current mixin subclass types are all made generic,
            // so the mixin subclass can extend any other class
            assert mixin.len()>0;
            Ary<TVarZ> tvs = mixin._tvars;
            mixin._mixs = tvs.asAry();
            tvs.clear();
            for( TVarZ tv : xclz._tvars ) {
              //int idx; for( idx=0; idx<mixin._mixs.length; idx++ ) if( S.eq(mixin._mixs[idx]._name,tv._name) ) break;
              //String local = idx < mixin._mixs.length ? tv._name : null;
              tvs.add(TVarZ.make(tv._xt,tv._name,xclz, tv._name/*local*/));
            }
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
    xclz._outer = outer==null ? null : outer.xclz();
    xclz._depth = supr==null ? 0 : supr._depth+1;
    xclz._abstrct = abstrct;

    return xclz;
  }

  //// Gather side types from Implements and Extends and Incorporates.
  //private void sides( ClassPart clz ) {
  //  _sides = SIDES0;
  //  if( clz==null || clz._contribs == null ) return;
  //
  //  for( Contrib c : clz._contribs )
  //    if( c._comp == Part.Composition.Implements || c._comp == Part.Composition.Extends || c._comp == Part.Composition.Incorporates )
  //      switch( c._tContrib ) {
  //      case TermTCon ttc: break;   // no extra type params
  //      case ImmutTCon imm when imm.icon() instanceof TermTCon ttc: break;
  //      case ParamTCon ptc: _sides(ptc,_tns);  break;
  //        // No tvars here?
  //      case InnerDepTCon dep:  assert dep .clz()._tnames.length==0;  break;
  //      case VirtDepTCon virt:  assert virt.clz()._tnames.length==0;  break;
  //      default: throw XEC.TODO();
  //      }
  //}
  //
  //private void sides( ParamTCon ptc, String[] tnames ) {
  //  _sides = SIDES0;
  //  _sides(ptc,tnames);
  //}
  //private void _sides( ParamTCon ptc, String[] tnames ) {
  //  // Now I got some sides!
  //  if( _sides == SIDES0 ) _sides = new HashMap<>();
  //  // Setup a side array from the base type to its parameterized version
  //  XClz pclz = (XClz)xtype(ptc,true);
  //  int[] idxs = new int[ptc._parms.length];
  //  // Walk the parameterized types; refer back to the class's types
  //  for( int i=0; i<ptc._parms.length; i++ ) {
  //    String pname = ptc._parms[i] instanceof TermTCon ttc ? ttc.name() : pclz._tns[i];
  //    int idx = S.find(tnames,pname); // Matches a clz name, uses that type
  //    if( idx == -1 ) {                    // No match, add an entry
  //      _xts = Arrays.copyOf(_xts,(idx = _xts.length)+1);
  //      _xts[idx] = pclz._xts[i];
  //    }
  //    idxs[i] = idx;
  //  }
  //  _sides.put(pclz,idxs);
  //  //assert ptc._parms.length==pclz._tns.length;
  //}

  // Made from XTC class.
  //   Here E is the class name for a type
  //   class List<E> { ... }
  //   XClz: List{E=XTC}
  static XClz makeClz( ClassPart clz ) {
    XClz xclz = _malloc(clz);
    XClz xclz2 = xclz._intern();
    assert xclz2 == xclz;       // Only intern XTC ClassPart once
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
    XClz proto = clz.xclz();

    // Very specifically, generic parameterized AryXTC<XTC> means the
    // un-element-typed Array<VOID>, used to make both primitive arrays and
    // Object arrays.
    if( proto == XCons.ARRAY || proto == XCons.ARRAY_RO ) {
      // A generified array needs to remain un-element-typed
      if( ptc._parms[0] instanceof TermTCon ttc && ttc.id() instanceof TParmCon )
        return proto;
      proto = proto._ro ? XCons.ARYXTC_RO : XCons.ARYXTC;
    }

    assert proto.len()>0;
    XClz xclz = proto._mallocClone();
    // Override parameterized type fields
    if( ptc._parms != null )
      for( int i=0; i<ptc._parms.length; i++ ) {
        XType px = xtype(ptc._parms[i],true,xclz);
        xclz.putSub(i,clz._tnames[i],px);
      }
    return xclz._intern(proto);
  }

  public static XClz make( Contrib c ) {
    // Get the UNparameterized class
    ClassPart clz = ((ClzCon)c._tContrib).clz();
    XClz proto = clz.xclz();
    if( c._parms==null ) return proto;

    assert proto.len()>0;
    XClz xclz = proto._mallocClone();
    // Override parameterized type fields
    for( String tn : c._parms.keySet() )
      xclz.putSub(tn, xtype(c._parms.get(tn),true,xclz));
    return xclz._intern(proto);
  }


  // Make a R/O version
  public static XClz make( ImmutTCon imm ) {
    if( imm.icon() instanceof TermTCon ttc ) {
      XClz clz = ttc.clz().xclz();
      return clz.readOnly();
    }
    throw XEC.TODO();
  }


  // An inner class, which gets the outer class Type variables
  public static XClz make( VirtDepTCon virt ) {
    ClassPart iclz = virt.part(); // Inner  clz
    ClassPart out = iclz.isNestedInnerClass();
    assert out==iclz._par;
    XClz xclz = iclz.xclz();       // Prototype inner xclz
    XClz vclz = xclz._mallocClone(); // Inner xclz, cloned
    ParamTCon ptc = (ParamTCon)virt._par;
    for( int i=0; i<ptc._parms.length; i++ ) {
      XType px = xtype(ptc._parms[i],true,xclz);
      vclz.putSub(out._tnames[i],px);
    }

    XClz vclz2 = vclz._intern();
    if( vclz2 != vclz ) return vclz2;
    //iclz._tclz = vclz;
    return vclz;
  }

  // Make a specialized FutureVar type
  public static XClz wrapFuture( XType xt ) {
    ClzBuilder.add_import(XCons.FUTUREVAR);
    if( xt instanceof XClz clz ) ClzBuilder.add_import(clz);
    XClz xclz = _malloc(XCons.FUTUREVAR._clz);
    //xclz._tns = XCons.FUTUREVAR._tns;
    //xclz._xts[0] = xt;
    //return xclz._intern(XCons.FUTUREVAR);
    throw XEC.TODO();
  }

  // You'd think the clz._super would be it, but not for enums
  static XClz get_super( ClassPart clz ) {
    if( clz==null ) return null;
    if( clz._super!=null )
      return clz._super.xclz();
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
    return xt(0);
  }
  public final boolean iface() { return _clz!=null && _clz._f==Part.Format.INTERFACE; }
  // Is a mixin
  public final boolean isMixin() { return _clz._f==Part.Format.MIXIN; }

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
    case   ClassPart clz  -> _pack(clz ,mod)+(clz._par instanceof MethodPart ? "$" : ".")+clz._name; // e.g. ecstasy.collections.Array
    case  MethodPart meth -> _pack(meth._par,mod)+"."+meth._name;  // e.g. tck.constructors.Basic.meth$SomeClass
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
      TVar tv = clz.tvar(tname);
      if( tv!=null ) return tv._xt;
    }
    return null;
  }

  // Local only find
  public XType _find(String tn) {
    //int idx = S.find(_tns,tn);
    //return idx == -1 ? null : _xts[idx];
    throw XEC.TODO();
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

  // Bare name, no generics
  public String clz_bare( ) { return _clz_bare(new SB(),false).toString(); }
  @Override public SB clz_bare( SB sb ) { return _clz_bare(sb,true); }
  private  SB _clz_bare( SB sb, boolean imprt ) {
    // These guys need a fully qualified name to avoid name conflicts
    if( this==XCons.JSTRING || this==XCons.JSTRINGN || this==XCons.JOBJECT || _ambiguous )
      return sb.p(qualified_name());
    // Nested class in other module?
    if( _clz!=null && _clz._par.getClass() == ClassPart.class &&
        _clz._par != ClzBuilder.CCLZ )
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
    if( localLen()==0 ) return sb; // None to print
    if( !printsTypeParm() ) return sb; // Java mirror encodes the generic type name already

    // Printing generic type parameters
    sb.p("<");
    for( int i=0; i<len(); i++ )
      if( at(i).local() )  {
        if( ptc!=null && ptc._parms[i] instanceof TermTCon ttc && ttc.part() instanceof ParmPart parm )
          sb.p("$").p(parm._name);
        else
          // No ParamTCon provided, using the generic class as-is
          xt(i)._clz(sb,null);
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
    sb.p("/*generic_def*/");
    // Include all local and super type parameters.
    // Exclude outer and interfaces.
    boolean found=false;
    for( TVarZ tv : _tvars )
      if( tv.genericDef(_super) )
        { found=true; break; }
    if( !found ) return sb;

    sb.p("< ");
    for( TVarZ tv : _tvars ) {
      if( !tv.genericDef(_super) ) continue;
      sb.p("$").p(tv._name);
      sb.p(" extends " );
      XClz xclz = (XClz)tv._xt;
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
  public void clz_generic_use( SB sb, XClz base ) {
    sb.p("/*generic_use*/");
    assert !_ambiguous;

    boolean found=false;
    for( TVarZ btv : base._tvars )
      if( btv.genericUse(base) )
        { found=true; break; }
    if( !found ) return;

    sb.p("<");
    for( TVarZ btv : base._tvars )
      if( btv.genericUse(base) ) {
        sb.fmt("$%0, ",btv._local); // Local rename
        /*
        else if( tvar(btv._name).local() ) // Local in super class
          // Concrete class name
          btv._xt.clz_bare(sb).p(", ");

        else ;   // Else nothing: not-local in super, not-local here
        */
      }
    sb.unchar(2).p("> ");
  }

  private SB strTuple( SB sb ) {
    sb.p("Tuple").p(len()).p("$");
    for( int i=0; i<len(); i++ )
      xt(i).clz_bare(sb).p("$");
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
          if( iclz == iface || iclz.isa(iface) )
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
    if( len() < clz.len() ) return false;
    // Now check pieces-parts
    for( int i=0; i<clz.len(); i++ )
      if( !xt(i).isa(clz.xt(i)) )
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
