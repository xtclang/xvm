package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.Part;

/**
  Exploring XEC Constants
 */
public abstract class Const {

  abstract public void resolve( CPool pool );

  /**
   * Recurse through the constants that make up this constant, replacing
   * typedefs with the types that they refer to.
   * <p/>
   * Note: In addition to resolving typedefs, this method is also used to resolve any
   *       {@link UnresolvedNameConstant}s used by any structure during the registration phase.
   *
   * @return this same type, but without any typedefs or resolvable {@link UnresolvedNameConstant}s in it
   */
  public Const resolveTypedefs() { return this; }

  
  public enum Format {
    IntLiteral("numbers"),
    Bit       ("numbers"),
    Nibble    ("numbers"),
    Int       ("numbers"),
    CInt8     ("numbers"),  // C=Checked (aka a constrained integer)
    Int8      ("numbers"),  // no "C" means @Unchecked
    CInt16    ("numbers"),
    Int16     ("numbers"),
    CInt32    ("numbers"),
    Int32     ("numbers"),
    CInt64    ("numbers"),
    Int64     ("numbers"),
    CInt128   ("numbers"),
    Int128    ("numbers"),
    CIntN     ("numbers"),
    IntN      ("numbers"),
    UInt      ("numbers"),
    CUInt8    ("numbers"),
    UInt8     ("numbers"),
    CUInt16   ("numbers"),
    UInt16    ("numbers"),
    CUInt32   ("numbers"),
    UInt32    ("numbers"),
    CUInt64   ("numbers"),
    UInt64    ("numbers"),
    CUInt128  ("numbers"),
    UInt128   ("numbers"),
    CUIntN    ("numbers"),
    UIntN     ("numbers"),
    FPLiteral ("numbers"),
    Dec       ("numbers"),
    Dec32     ("numbers"),
    Dec64     ("numbers"),
    Dec128    ("numbers"),
    DecN      ("numbers"),
    Float8e4  ("numbers"),
    Float8e5  ("numbers"),
    BFloat16  ("numbers"),
    Float16   ("numbers"),
    Float32   ("numbers"),
    Float64   ("numbers"),
    Float128  ("numbers"),
    FloatN    ("numbers"),
    Char      ("text"),
    String    ("text"),
    RegEx     ("text"),
    Date,               // ISO8601 YYYY-MM-DD date format
    TimeOfDay,          // ISO8601 HH:MM[:SS[.sssssssss]]['Z' | ('+'|'-')hh[:mm]] format
    TimeZone,           // ISO8601 ['Z' | ('+'|'-')hh[:mm]] format
    Time,               // ISO8601 date ['T' time] format
    Duration,           // ISO8601 P[n]Y[n]M[n]DT[n]H[n]M[n]S | P[n]W format
    Version   ("reflect"),
    SingletonConst,     // identity constant for a Module, Package, or a static const
    EnumValueConst,     // identity constant for an enum value
    SingletonService,   // identity constant of a Service class
    Tuple     ("collections"),
    Array     ("collections"),
    UInt8Array,         // byte[]
    Set       ("collections"),
    MapEntry  ("collections"),
    Map       ("collections"),
    Range,
    RangeInclusive,
    RangeExclusive,
    Any,
    Path      ("fs"),
    FileStore ("fs"),
    FSDir     ("fs"),
    FSFile    ("fs"),
    FSLink    ("fs"),
    ResponseSender ("http"),

    /*
     * Structural identifiers.
     */
    Module,
    Package,
    Class,
    Typedef,
    Property,
    MultiMethod,
    Method,
    Annotation,

    /*
     * FrameDependent (run-time aware) identifiers.
     */
    Register,
    BindTarget,

    /*
     * Pseudo identifiers.
     */
    UnresolvedName,
    DeferredValue,
    ThisClass,
    ParentClass,
    ChildClass,
    TypeParameter,
    FormalTypeChild,
    DynamicFormal,
    Signature,
    DecoratedClass,
    NativeClass,
    IsConst,
    IsEnum,
    IsModule,
    IsPackage,
    IsClass,

    /*
     * Types.
     */
    UnresolvedType,
    TerminalType,
    ImmutableType,
    ServiceType,
    AccessType,
    AnnotatedType,
    ParameterizedType,
    TurtleType,
    VirtualChildType,
    InnerChildType,
    AnonymousClassType,
    PropertyClassType,
    IntersectionType,
    CastType,
    UnionType,
    DifferenceType,
    RecursiveType,

    /*
     * Conditions.
     */
    ConditionNot,
    ConditionAll,
    ConditionAny,
    ConditionNamed,
    ConditionPresent,
    ConditionVersionMatches,
    ConditionVersioned;

    // -------------------------------------------------------------------------------------
    Format() { this(null); }
    Format(String sPackage) { _package = sPackage; }
    //public Format next() { return Format.valueOf(this.ordinal() + 1); }
    
    ///**
    // * @return true if a corresponding Constant could be used as a terminal type
    // */
    //public boolean isTypeable() {
    //  return switch (this) {
    //  case Module, Package, Class, Typedef, Property, TypeParameter, FormalTypeChild,
    //    DynamicFormal, ThisClass, ParentClass, ChildClass, DecoratedClass, NativeClass,
    //    UnresolvedName, IsConst, IsEnum, IsModule, IsPackage, IsClass
    //    -> true;
    //  default -> false;
    //  };
    //}
    //
    ///**
    // * @return fully qualified Ecstasy class name corresponding to this enum value
    // */
    //public String getEcstasyName() {
    //  return _package == null ? name() : _package + '.' + name();
    //}
    //
    ///**
    // * @param pool the ConstantPool to use
    // *
    // * @return the Ecstasy type for this format enum value
    // */
    //public TypeConstant getType(ConstantPool pool) {
    //    return switch (this) {
    //    case Int      -> pool.typeInt();
    //    case CInt8    -> pool.typeCInt8();
    //    case Int8     -> pool.typeInt8();
    //    case CInt16   -> pool.typeCInt16();
    //    case Int16    -> pool.typeInt16();
    //    case CInt32   -> pool.typeCInt32();
    //    case Int32    -> pool.typeInt32();
    //    case CInt64   -> pool.typeCInt64();
    //    case Int64    -> pool.typeInt64();
    //    case CInt128  -> pool.typeCInt128();
    //    case Int128   -> pool.typeInt128();
    //    case CIntN    -> pool.typeCIntN();
    //    case IntN     -> pool.typeIntN();
    //    case UInt     -> pool.typeUInt();
    //    case CUInt8   -> pool.typeCUInt8();
    //    case UInt8    -> pool.typeUInt8();
    //    case CUInt16  -> pool.typeCUInt16();
    //    case UInt16   -> pool.typeUInt16();
    //    case CUInt32  -> pool.typeCUInt32();
    //    case UInt32   -> pool.typeUInt32();
    //    case CUInt64  -> pool.typeCUInt64();
    //    case UInt64   -> pool.typeUInt64();
    //    case CUInt128 -> pool.typeCUInt128();
    //    case UInt128  -> pool.typeUInt128();
    //    case CUIntN   -> pool.typeCUIntN();
    //    case UIntN    -> pool.typeUIntN();
    //    case Dec      -> pool.typeDec();
    //    default       -> pool.ensureEcstasyTypeConstant(getEcstasyName());
    //    };
    //}

    /**
     * Look up a Format enum by its ordinal, without exposing the values array.
     * @param i  the ordinal
     * @return the Format enum for the specified ordinal
     */
    public static Format valueOf(int i) { return FORMATS[i]; }
    /** All of the Format enums in an ordinal array. */
    private static final Format[] FORMATS = Format.values();

    /** The package name. */
    private final String _package;
  }


  // ----- accessibility levels ------------------------------------------------------------------
  /**
   * The Access enumeration refers to the level of accessibility to a class that a reference will have:
   * <ul>
   * <li>{@link #STRUCT STRUCT} - direct access to the underlying data structure (but only to the data structure);</li>
   * <li>{@link #PUBLIC PUBLIC} - access to the public members of the object's class;</li>
   * <li>{@link #PROTECTED PROTECTED} - access to the protected members of the object's class;</li>
   * <li>{@link #PRIVATE PRIVATE} - access to the private members of the object's class;</li>
   * </ul>
   */
  public enum Access {
    STRUCT   (0),
    PUBLIC   ( Part.ACCESS_PUBLIC   ),
    PROTECTED( Part.ACCESS_PROTECTED),
    PRIVATE  ( Part.ACCESS_PRIVATE  );

    Access(int flags) { this.FLAGS = flags; }
    /**
     * The integer flags used to encode the access enum.
     * @see Part#ACCESS_MASK
     * @see Part#ACCESS_SHIFT
     * @see Part#ACCESS_PUBLIC
     * @see Part#ACCESS_PROTECTED
     * @see Part#ACCESS_PRIVATE
     */
    public final int FLAGS;
  }
  
}
