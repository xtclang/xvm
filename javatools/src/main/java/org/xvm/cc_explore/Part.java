package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.SB;

import java.util.ArrayList;
import java.util.HashMap;

/**
   DAG structure containment of the existing components/structures.
   Handles e.g. the Class hierarchy, Modules, Methods, and Packages.
   These things are represented as structures in memory.
   
   Does not handle Types nor Values?  Types being conceptual sets of Values,
   and a reflective mirror is used in memory.  Values represent concrete
   executable state.
 */
abstract public class Part {
  public final Part _par;       // Parent in the parent chain; null ends.  Last is FilePart.
  public final String _name;    // Identifier
  public final int _nFlags;     // Some bits
  public final CondCon _cond;   // Conditional component

  public final ArrayList<Contrib> _contribs;

  // Map from kid name to kid.  
  // TODO: I lifted this 1 layer from the original, and I'm pretty sure this
  // isn't right but I don't have a test case to debug yet
  public HashMap<String,Part> _name2kid;

  // Linked list of siblings at the same DAG level with the same name
  public Part _sibling;

  Part( Part par, int nFlags, IdCon id, String name, CondCon cond, CPool X ) {
    _par = par;
    _sibling = null;
    assert (par==null) ==  this instanceof FilePart; // File doesn't have a parent
    //assert (id ==null) ==  this instanceof FilePart; // File doesn't have a id
    assert cond==null || !(this instanceof FilePart); // File can't be conditional
    
    _name = id==null ? name : ((IdCon)(id.resolveTypedefs())).name();
    _nFlags = nFlags;
    _cond = cond;

    // Only null for self FilePart.  Other parts need to parse bits.
    if( X!=null ) {
      int c = X.u31();
      _contribs = new ArrayList<>();
      for( int i=0; i<c; i++ )
        _contribs.add( new Contrib( X ) );
    } else {
      _contribs = null;
    }
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
      if( _name2kid==null ) _name2kid = new HashMap<>();
      Part old = _name2kid.get(kid._name);
      if( old==null ) _name2kid.put(kid._name,kid);
      else {
        while( old._sibling!=null ) old = old._sibling; // Follow linked list to end
        old._sibling = kid;                             // Append kid to tail of linked list
      }
      // Here we could be lazy on the child's children, but instead are always eager.
      int len = X.u31();        // Length of serialized nested children
      if( len > 0 )             // If there are recursively more children
        kid.parseKids(X);
    }
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
    // Link all part innards
    if( _contribs != null )
      for( Contrib c : _contribs )
        c.link(repo);
    // Link specific part innards
    link_innards(repo);                 // Link internal Const
  
    // For all child parts
    if( _name2kid==null ) return this;
    for( String name : _name2kid.keySet() ) {
      Part kid = _name2kid.get(name).link(repo); 
      _name2kid.put(name,kid);  // Replace with upgrade and link
    }
    return this;
  }

  // Tok, replace self with a better Part
  Part link_as( XEC.ModRepo repo ) { return this; }

  // Tok, kid-specific internal linking.
  void link_innards( XEC.ModRepo repo ) { }
  
  public Part child(String s, XEC.ModRepo repo) {
    // most common result: no child by that name
    Part kid = _name2kid==null ? null : _name2kid.get(s);
    if( kid == null ) return null;

    // common result: exactly one non-conditional match
    if( kid._sibling == null && kid._cond == null ) return kid;

    // Filter based on Assembly, not implemented.
    // Gather the kids into an Array, build a CompositeComponent... but hey
    // MMethodPart is already a composite.
    assert this instanceof MMethodPart;
    return this;
  }

  // ----- inner class: Component Contribution ---------------------------------------------------
  /**
   * Represents one contribution to the definition of a class. A class (with the term used in the
   * abstract sense, meaning any class, interface, mixin, const, enum, or service) can be composed
   * of any number of contributing components.
   */
  public static class Contrib {
    final Composition _comp;
    private final TCon _tContrib;
    private final PropCon _prop;
    private final SingleCon _inject;
    private final Annot _annot;
    private final HashMap<String, TCon> _parms;
    // Post link values
    private Part _cPart;
    private final HashMap<String, ClassPart> _clzs;
    
    protected Contrib( CPool X ) {
      _comp = Composition.valueOf(X.u8());
      _tContrib = (TCon)X.xget();
      PropCon prop = null;
      Annot annot = null;
      SingleCon inject = null;
      HashMap<String, TCon> parms = null;
    
      assert _tContrib!=null;
      switch( _comp ) {
      case Extends:
      case Implements:
      case Into:
      case RebasesOnto:
        break;
      case Annotation:
        annot = (Annot)X.xget();
        break;
      case Delegates:
        prop = (PropCon)X.xget();
        break;
        
      case Incorporates:
        int len = X.u31();
        if( len == 0 ) break;
        parms = new HashMap<>();
        for( int i = 0; i < len; i++ ) {
          String name = ((StringCon)X.xget())._str;
          int ix = X.u31();
          parms.put(name, ix == 0 ? null : (TCon)X.get(ix));
        }
        break;

      case Import:
        inject = (SingleCon)X.xget();
        if( inject==null ) break;
        throw XEC.TODO();
        
      default: throw XEC.TODO();
      }
      _annot = annot;
      _prop = prop;
      _inject = inject;
      _parms = parms;
      _clzs = parms==null ? null : new HashMap<>();
    }
    // Link all the internal parts
    Part link( XEC.ModRepo repo ) {
      if( _cPart!=null ) return _cPart;
      _cPart = _tContrib.link(repo);
      
      if( _prop  !=null ) _prop  .link(repo);
      if( _inject!=null ) _inject.link(repo);
      if( _annot !=null ) _annot .link(repo);
      if( _parms !=null )
        for( String name : _parms.keySet() ) {
          TCon tcon = _parms.get(name);
          _clzs.put(name, tcon==null ? null : (ClassPart)tcon.link(repo));
        }
      return _cPart;
    }
    Part part() { assert _cPart!=null; return _cPart; }
  }
  
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
        ->               new  ClassPart(par, nFlags, (  ClassCon) con, cond, X);
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
