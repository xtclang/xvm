package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.tvar.TVar;
import org.xvm.cc_explore.util.SB;

import java.util.IdentityHashMap;

/**
   DAG structure containment of the existing components/structures.
   Handles e.g. the Class hierarchy, Modules, Methods, and Packages.
   These things are represented as structures in memory.
   
   Everything has a TVar type.  FileParts have the empty type.
   Classes are a TVStruct with fields (which include methods).
   Classes are nominal, and isa tests can start/stop on the name.
   Interfaces classes which require structural testing; the name is just for fun.
   Methods are TVLambda, and the Parameters are normal TVars.
   
 */
abstract public class Part {
  public final Part _par;       // Parent in the parent chain; null ends.  Last is FilePart.
  public final String _name;    // Identifier
  public final int _nFlags;     // Some bits
  public final CondCon _cond;   // Conditional component

  // Contributions.  A list of "extra" features about this Part.
  // Zero except for Class and Prop parts.
  final int _cslen;

  // Map from kid name to kid.  
  IdentityHashMap<String,Part> _name2kid;
  
  Part( Part par, int nFlags, Const id, String name, CondCon cond, CPool X ) {
    _par = par;
    assert (par==null) ==  this instanceof FilePart; // File doesn't have a parent
    //assert (id ==null) ==  this instanceof FilePart; // File doesn't have a id
    assert cond==null || !(this instanceof FilePart); // File can't be conditional
    
    _name = id==null ? name : ((IdCon)id.resolveTypedefs()).name();
    _nFlags = nFlags;
    _cond = cond;

    // X is self FilePart.  Other parts need to get the length.
    // Only Class and Prop have non-zero length.
    _cslen = X==null ? 0 : X.u31();
  }

  // Constructed class parts
  Part( Part par, String name ) {
    _par = par;
    _name = name;
    _nFlags = 0;
    _cond = null;
    _cslen = 0;
    
  }

  @Override public String toString() { return str(new SB()).toString(); }
  public SB str(SB sb) {
    sb.p(_name);
    return _par==null ? sb : _par.str(sb.p(" -> "));
  }

  // Tik-tok style recursive-descent parsing.  This is the Tik, shared amongst
  // all kids.  The Tok is where we do kid-specific parsing.  Since we don't
  // have the kids yet, we can't really do a Visitor pattern here.
  final void parseKids( CPool X ) {
    int cnt = X.u31();          // Number of kids
    for( int i=0; i<cnt; i++ ) {
      int n = X.u8();
      Part kid;
      if( (n & CONDITIONAL_BIT)==0 ) {
        // there isn't a conditional multiple-component list, so this is just
        // the first byte of the two-byte FLAGS value (which is the start of
        // the body) for a single component
        n = (n << 8) | X.u8();
        // Tok.
        kid = Format.fromFlags(n).parse(this,X.xget(), n, null, X);
      } else {
        kid = null;
        throw XEC.TODO();
      }
      // if the child is a method, it can only be contained by a MultiMethodPart
      assert !(kid instanceof MethodPart) || (this instanceof MMethodPart);
      // Insert name->kid mapping
      if( _name2kid==null ) _name2kid = new IdentityHashMap<>();
      putkid(kid._name,kid);
      // Here we could be lazy on the child's children, but instead are always eager.
      int len = X.u31();        // Length of serialized nested children
      if( len > 0 )             // If there are recursively more children
        kid.parseKids(X);
    }
  }

  void putkid(String name, Part kid) {
    assert !_name2kid.containsKey(kid._name);
    _name2kid.put(kid._name,kid);
  }
  
  // Tik-tok style recursive-descent linking.  This is the Tik, shared amongst
  // all kids.  The Tok is where we do kid-specific linking.  If I see too many
  // of these tik-tok patterns I'll probably add a Visitor instead.
  public final Part link( XEC.ModRepo repo ) {
    Part p = XEC.ModRepo.VISIT.get(this);
    if( p!=null ) return p;     // Already linked
    p = link_as(repo);          // In-place replacement (Required ModPart becomes Primary ModPart)
    XEC.ModRepo.VISIT.put(this,p);     // Stop cycles
    if( p!=this ) return p.link(repo); // Now link the replacement
    
    // Link specific part innards
    link_innards(repo);                 // Link internal Const

    // For all child parts
    if( _name2kid!=null ) 
      for( String name : _name2kid.keySet() ) {
        Part kid0 = _name2kid.get(name);
        //if( kid0 instanceof PropPart pp ) {
          // Do nothing?
        //} else {
          Part kid = kid0.link(repo); 
          _name2kid.put(name,kid);  // Replace with upgrade and link
        //}
      }
    return this;
  }

  // Tok, replace self with a better Part
  Part link_as( XEC.ModRepo repo ) { return this; }

  // Tok, kid-specific internal linking.
  abstract void link_innards( XEC.ModRepo repo );

  // Look up a child, post linking.  Does not need a repo
  public Part child(String s) { return child(s,null); }
  // Look up a child, during linking.  The overrides might need the repo.
  public Part child(String s, XEC.ModRepo ignore ) {
    return _name2kid==null ? null : _name2kid.get(s);
  }


  // ----- Types -----------------------------------------------------------
  // This problem in many ways appear to mirror AA types, including resolvable
  // fields.  It's not the full type inference, but there's definitely a "isa"
  // question being asked during linking - which corresponds to AA's "which is
  // the (exactly one) match for this type, from this set of choices".
  //
  // Things in the type grammer
  // - Classes.  Names matter.  Can be subtyped.  Uses TVStruct.
  // - Interfaces.  Name is ignored, and structural matching only; again TVStruct.
  // - Methods, using TVLambda.
  // - Generified parts: aka HM type variables.
  // - I'll probably get a lot of unspecified type vars, aka Leafs.
  // - - So using the UF algo like normal HM to roll up.
  //
  //   Short description from Appender.x XTC file:

  //   interface Iterator<Element> {
  //     next: { -> Element }; // Abstract
  //     take: { -> Element }; // Concrete
  //     ... more concrete fields...
  //   }
  //   
  //   interface Iterable<Element> {
  //     size:Int;
  //     iterator: { -> Iterator<Element> }; // Abstract
  //     toArray: { Mut -> Element[] } // Concrete, returns Element array
  //   }
  //
  //   
  //   interface Appender<Element> {
  //     add:{ Element -> Appender };
  //     addAll:{ Iterable<Element> -> Appender };
  //     addAll:{ Iterator<Element> -> Appender };
  //     ensureCapacity:{ Int -> Appender };
  //   }
  // Self type
  private TVar _tvar;
  public final boolean has_tvar() { return _tvar!=null; }

  // Access the self-type
  public final TVar tvar() {
    return _tvar.unified() ? (_tvar = _tvar.find()) : _tvar;
  }
  // Set the self-type exactly once
  public final TVar setype( ) {
    if( _tvar!=null ) return _tvar;
    _tvar = _setype();
    
    // Recursively kids
    if( _name2kid != null )
      for( String s : _name2kid.keySet() )
        _name2kid.get(s).setype();
    
    return _tvar;
  }
  final void setype_stop_cycles( TVar tv ) {_tvar = tv;}
  // Sub Parts use this return the initial tvar; and can be assured that they
  // are called only once, and they do not need to assign to tvar.
  abstract TVar _setype();
  
  // ----- Enums -----------------------------------------------------------
  /**
   * The Format enumeration defines the multiple different binary formats used
   * to store component information.  Those beginning with "RSVD_" are
   * reserved, and must not be used.
   */
  public enum Format {
    INTERFACE,
    CLASS,
    CONST,
    ENUM,
    ENUMVALUE,
    MIXIN,
    SERVICE,
    PACKAGE,
    MODULE,
    TYPEDEF,
    PROPERTY,
    METHOD,
    RSVD_C,
    RSVD_D,
    MULTIMETHOD,
    FILE;
    
    /**
     * Instantiate a component as it is being read from a stream, reading its body
     * @param par    the parent component
     * @param con    the constant for the new component's identity
     * @param nFlags the flags that define the common attributes of the component
     * @param cond   the cond under which the component is present, or null
     * @param X      file parser support
     * @return the new component
     */
    Part parse( Part par, Const con, int nFlags, CondCon cond, CPool X ) {
      assert par!=null;
      return switch( this ) {
      case MODULE     -> new    ModPart(par, nFlags, (    ModCon) con, cond, X);
      case PACKAGE    ->new PackagePart(par, nFlags, (PackageCon) con, cond, X);
      case INTERFACE, CLASS, CONST, ENUM, ENUMVALUE, MIXIN, SERVICE
        ->               new  ClassPart(par, nFlags, (  ClassCon) con, cond, X, this);
      case TYPEDEF    -> new   TDefPart(par, nFlags, (   TDefCon) con, cond, X);
      case PROPERTY   -> new   PropPart(par, nFlags, (   PropCon) con, cond, X);
      case MULTIMETHOD->new MMethodPart(par, nFlags, (MMethodCon) con, cond, X);
      case METHOD     -> new MethodPart(par, nFlags, ( MethodCon) con, cond, X);
      default ->  throw new IllegalArgumentException("uninstantiable format: " + this);
      };
    }

    /**
     * Determine the format from a component's bit-flags value.
     * @param nFlags  the 2-byte component bit-flags value
     * @return the Format specified by the bit flags
     */
    static final int FORMAT_MASK = 0x000F, FORMAT_SHIFT = 0;
    static Format fromFlags(int nFlags) {
      return valueOf((nFlags & FORMAT_MASK) >>> FORMAT_SHIFT);
    }

    public static Format valueOf(int i) { return FORMATS[i]; }
    private static final Format[] FORMATS = Format.values();
  }

  // ----- enumeration: Component Composition ----------------------------------------------------

  /** Types of composition. */
  public enum Composition {
    /**
     * Represents an annotation.
     * <p/>
     * The constant is a TypeConstant. (It could be a ClassConstant, but TypeConstant
     * was selected to keep it compatible with the other compositions.) An annotation has
     * optional annotation parameters, each of which is also a constant from the ConstantPool.
     */
    Annotation,
    /**
     * Represents class inheritance.
     * <p/>
     * The constant is a TypeConstant for the class.
     */
    Extends,
    /**
     * Represents interface inheritance.
     * <p/>
     * The constant is a TypeConstant.
     */
    Implements,
    /**
     * Represents interface inheritance plus default delegation of interface functionality.
     * <p/>
     * The constant is a TypeConstant. A "delegates" composition must specify a property that
     * provides the reference to which it delegates; this is represented by a PropertyConstant.
     */
    Delegates,
    /**
     * Represents that the class being composed is a mixin that applies to the specified type.
     * <p/>
     * The constant is a TypeConstant.
     */
    Into,
    /**
     * Represents the combining-in of a mix-in.
     * <p/>
     * The constant is a TypeConstant.
     */
    Incorporates,
    /**
     * Synthetic (transient) rebasing of a class onto a new category.
     */
    RebasesOnto,
    /**
     * Represents that the package being composed represents an imported module.
     * <p/>
     * The constant is a ModuleConstant.
     */
    Import,
    /**
     * Synthetic (transient) composition indicating an equivalency.
     * <p/>
     * The constant is a ClassConstant.
     */
    Equal,
    ;
    
    /**
     * Look up a Composition enum by its ordinal.
     * @param i  the ordinal
     * @return the Composition enum for the specified ordinal
     */
    public static Composition valueOf(int i) { return COMPOSITIONS[i]; }
    private static final Composition[] COMPOSITIONS = Composition.values();
  }


  /**
   * If the leading byte of the flags contains a conditional bit, then it isn't actually the
   * leading byte of the flags, and instead is an indicator that the conditional format is being
   * used, possibly with more than one component of the same name. Specifically, if that leading
   * byte has the CONDITIONAL_BIT set, then that byte is followed by a packed integer specifying
   * the number of components of the same name, and for each component there is a packed integer
   * for the conditional constant ID followed by the body of the component. (The children that go
   * with the various conditional components occur in the stream after the <b>last</b> body.)
   */
  public static final int CONDITIONAL_BIT  =   0x80;

  public static final int ACCESS_MASK      = 0x0300, ACCESS_SHIFT     = 8;
  public static final int ACCESS_PUBLIC    = 0x0100;
  public static final int ACCESS_PROTECTED = 0x0200;
  public static final int ACCESS_PRIVATE   = 0x0300;
  public static final int ABSTRACT_BIT     = 0x0400, ABSTRACT_SHIFT   = 10;
  public static final int STATIC_BIT       = 0x0800, STATIC_SHIFT     = 11;
  public static final int SYNTHETIC_BIT    = 0x1000, SYNTHETIC_SHIFT  = 12;
  public static final int COND_RET_BIT     = 0x2000, COND_RET_SHIFT   = 13;
  public static final int AUXILIARY_BIT    = 0x4000, AUXILIARY_SHIFT  = 14;  
}
