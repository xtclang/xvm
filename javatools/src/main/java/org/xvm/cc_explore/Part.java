package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

import java.util.ArrayList;
import java.util.HashMap;

/**
     DAG structure containment of components/parts
 */
abstract public class Part {
  public final Part _par;       // Parent in the parent chain; null ends.  Last is FilePart.
  public final int _nFlags;     // Some bits
  public final CondCon _cond;   // Conditional component
  public final IdCon _id;       // Identifier

  public final ArrayList<Contrib> _contribs;

  // Map from kid name to kid.  
  // TODO: I lifted this 1 layer from the original, and I'm pretty sure this
  // isn't right but I don't have a test case to debug yet
  private HashMap<String,Part> _name2kid;

  // Linked list of siblings at the same DAG level with the same name
  private Part _sibling = null;

  // If a child is lazily created, here is the buffer offset and length to parse.
  // The actual buffer is in the FilePart at the Part root.
  int _lazy_off, _lazy_len;
  
  Part( Part par, int nFlags, IdCon id, CondCon cond, FilePart X ) {
    _par = par;
    assert (par==null) ==  this instanceof FilePart; // File doesn't have a parent
    assert (id ==null) ==  this instanceof FilePart; // File doesn't have a id
    assert cond==null || !(this instanceof FilePart); // File can't be conditional
    
    if( id != null ) {
      id = (IdCon)id.resolveTypedefs();
      id.resetCachedInfo();
    }
    _nFlags = nFlags;
    _cond = cond;
    _id = id;

    // Only null for self FileComponent.  Other components need to parse bits.
    if( X!=null ) {
      int c = X.u31();
      _contribs = new ArrayList<>();
      for( int i=0; i<c; i++ )
        _contribs.add(new Contrib(X));
    } else {
      _contribs = null;
    }
  }

  void parseKids( FilePart X ) {
    int cnt = X.u31();
    for( int i=0; i<cnt; i++ ) {
      int n = X.u8();
      Part kid;
      if( (n & CONDITIONAL_BIT)==0 ) {
        // there isn't a conditional multiple-component list, so this is just
        // the first byte of the two-byte FLAGS value (which is the start of
        // the body) for a single component
        n = (n << 8) | X.u8();
        kid = Format.fromFlags(n).parse(this,X._pool.get(X.u31()), n, null, X);
      } else {
        kid = null;
        throw XEC.TODO();
      }
      // if the child is a method, it can only be contained by a MultiMethodStructure
      assert !(kid instanceof MethodPart);
      if( _name2kid==null ) _name2kid = new HashMap<>();
      Part old = _name2kid.get(kid.name());
      if( old==null ) _name2kid.put(kid.name(),kid);
      else {
        while( old._sibling!=null ) old = old._sibling; // Follow linked list to end
        old._sibling = kid;                             // Append kid to tail of linked list
      }
      // Here we can be lazy on the child's children
      int lazy_len = X.u31();
      if( lazy_len > 0 ) {
        if( X._lazy ) {
          X.x += lazy_len; // Skip in parser
          for( Part p=kid; p!=null; p=p._sibling ) {
            kid._lazy_off = X.x;
            kid._lazy_len = lazy_len;
          }
        } else {
          throw XEC.TODO();     // Recursively call parseKids
        }
      }
    }
  }

  // Walk the parent chain to the top
  FilePart getFileComp() {
    Part c = this;
    while( !(c instanceof FilePart filec) ) c = c._par;
    return filec;
  }

  String name() { return _id.name(); }

  
  // ----- inner class: Component Contribution ---------------------------------------------------
  /**
   * Represents one contribution to the definition of a class. A class (with the term used in the
   * abstract sense, meaning any class, interface, mixin, const, enum, or service) can be composed
   * of any number of contributing components.
   */
  public class Contrib {
    final Composition _comp;
    final TCon _tContrib;
    protected Contrib( FilePart X) {
      _comp = Composition.valueOf(X.u8());
      _tContrib = (TCon)X._pool.get(X.u31());
      assert _tContrib!=null;
      switch( _comp ) {
      case Extends:
      case Implements:
      case Into:
      case RebasesOnto:
        break;
      default: throw XEC.TODO();
      }
    }
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
     * Determine the format from a component's bit-flags value.
     * @param nFlags  the 2-byte component bit-flags value
     * @return the Format specified by the bit flags
     */
    static final int FORMAT_MASK = 0x000F, FORMAT_SHIFT = 0;
    static Format fromFlags(int nFlags) {
      return valueOf((nFlags & FORMAT_MASK) >>> FORMAT_SHIFT);
    }

    /**
     * Instantiate a component as it is being read from a stream, reading its body
     * @param par    the parent component
     * @param con    the constant for the new component's identity
     * @param nFlags the flags that define the common attributes of the component
     * @param cond   the cond under which the component is present, or null
     * @param X      file parser support
     * @return the new component
     */
    Part parse( Part par, Const con, int nFlags, CondCon cond, FilePart X ) {
      assert par!=null;
      return switch( this ) {
      case MODULE   -> new ModPart(par, nFlags, (ModCon) con, cond, X);
      //case PACKAGE  -> throw XEC.TODO(); // new PackageComponent(par, nFlags, (PackageCon) con, cond);
      //case INTERFACE, CLASS, CONST, ENUM, ENUMVALUE, MIXIN, SERVICE -> throw XEC.TODO(); // new ClassComponent(par, nFlags, (ClassCon) con, cond);
      //case TYPEDEF  -> throw XEC.TODO(); // new TypedefComponent(par, nFlags, (TypedefCon) con, cond);
      //case PROPERTY -> throw XEC.TODO(); // new PropertyComponent(par, nFlags, (PropCon) con, cond);
      //case MULTIMETHOD -> throw XEC.TODO(); //  new MMethodComponent(par, nFlags, (MMthodCon) con, cond);
      case METHOD   -> new MethodPart(par, nFlags, (MethodCon) con, cond, X);
      default ->  throw new IllegalArgumentException("uninstantiable format: " + this);
      };
    }

    /**
     * Look up a Format enum by its ordinal.
     * @param i  the ordinal
     * @return the Format enum for the specified ordinal
     */
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
