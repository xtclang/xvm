package org.xvm.cons;

import org.xvm.*;
import org.xvm.util.SB;

/**
   Abstract XEC constants.  These are all conceptually immutable, and shared
   across e.g. containers and services.  They take a one-time initialization
   during creation; after the Java constructor and during the resolve phase.
   They are actually immutable after resolving.
 */
public abstract class Const {

  // Resolve any internal references from the serialized form.
  public void resolve( CPool X ){};

  // After linking, the part call does not need the repo.
  Part part() { throw XEC.TODO();  }

  // Convert e.g. ClassCon/ModCon/PackCon to their Part equivalents.
  public Part link(XEC.ModRepo repo) { return null; }

  public final String toString() { return str(new SB()).toString(); }
  public SB str( SB sb ) { return sb.p(getClass().toString()); }
  
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

  // -------- Format ----------------------------------------------------------

  public enum Format {
    IntLiteral("numbers"),
    Bit       ("numbers"),
    Nibble    ("numbers"),
    Int8      ("numbers"),
    Int16     ("numbers"),
    Int32     ("numbers"),
    Int64     ("numbers"),
    Int128    ("numbers"),
    IntN      ("numbers"),
    UInt8     ("numbers"),
    UInt16    ("numbers"),
    UInt32    ("numbers"),
    UInt64    ("numbers"),
    UInt128   ("numbers"),
    UIntN     ("numbers"),
    FPLiteral ("numbers"),
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

    Format() { this(null); }
    Format(String sPackage) { _package = sPackage; }

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

    public static Access valueOf(int i) { return VALUES[i]; }
    private static final Access[] VALUES = Access.values();

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

  /**
   * The qualified name of the Ecstasy core module. This is the only module that has no external
   * dependencies (other than a conceptual dependency in the compiler on the prototype module,
   * due to the "turtles" problem of Ref.x having properties which are themselves refs).
   */
  public static final String ECSTASY_MODULE = "ecstasy.xtclang.org";

  // ----- NodeType -----------------------------------------------------------
  // Clone of javatools/org.xvm.asm.ast.BinaryAST.NodeType;
  public enum NodeType {
    None,               // 00:
    PropertyExpr,       // 01: property access
    InvokeExpr,         // 02: foo() (method)   (foo is a const)
    CondOpExpr,         // 03: "<", ">=", etc.
    Assign,             // 04: x=y;
    NamedRegAlloc,      // 05: int x; (classified as an expression to simplify "l-value" design)
    RelOpExpr,          // 06: "&&", "||", "^", etc.
    NarrowedExpr,       // 07:
    UnaryOpExpr,        // 08: "+", "-", etc.
    NewExpr,            // 09:
    ThrowExpr,          // 0A:
    CallExpr,           // 0B: foo() (function) (foo is a register/property)
    ArrayAccessExpr,    // 0C: x[i]
    BinOpAssign,        // 0D: x*=y; etc.
    TernaryExpr,        // 0E: x ? y : z
    OuterExpr,          // 0F:
    NotExpr,            // 10: !x
    MultiExpr,          // 11: (expr1, expr2, ...)
    BindFunctionExpr,   // 12: bind function's arguments
    DivRemExpr,         // 13: x /% y
    BindMethodExpr,     // 14: bind method's target
    NotNullExpr,        // 15: x?
    ConvertExpr,        // 16:
    TemplateExpr,       // 17:
    NewChildExpr,       // 18:
    TupleExpr,          // 19:
    CmpChainExpr,       // 1A: x < y <= z, etc.
    UnpackExpr,         // 1B:
    SwitchExpr,         // 1C: s = switch () {...}
    NewVirtualExpr,     // 1D:
    ListExpr,           // 1E:
    Escape,             // 1F: reserved #31: followed by NodeType ordinal as unsigned byte
    AnnoRegAlloc,       // same as RegAlloc, but annotated
    AnnoNamedRegAlloc,  // same as NamedRegAlloc, but annotated
    RegAlloc,           // int _; (classified as an expression to simplify "l-value" design)
    RegisterExpr,       // _ (unnamed register expr)
    InvokeAsyncExpr,    // foo^() (method)   (foo is a const)
    CallAsyncExpr,      // foo^() (function) (foo is a register/property)
    IsExpr,             // x.is(y)
    NegExpr,            // -x
    BitNotExpr,         // ~x
    PreIncExpr,         // --x
    PreDecExpr,         // ++x
    PostIncExpr,        // x--
    PostDecExpr,        // x++
    RefOfExpr,          // &x
    VarOfExpr,          // &x
    ConstantExpr,       //
    MapExpr,            //
    StmtExpr,           //
    NotCond,            // if (!(String s ?= foo())){...} TODO
    NotNullCond,        // if (String s ?= foo()){...}    TODO
    NotFalseCond,       // if (String s := bar()){...}    TODO
    MatrixAccessExpr,   // x[i, j]                        TODO
    AssertStmt,         //
    StmtBlock,          // {...}, do{...}while(False); etc.
    MultiStmt,          //
    IfThenStmt,         // if(cond){...}
    IfElseStmt,         // if(cond){...}else{...}
    SwitchStmt,         // switch(cond){...}
    LoopStmt,           // while(True){...} etc.
    WhileDoStmt,        // while(cond){...}
    DoWhileStmt,        // do{...}while(cond);
    ForStmt,            // for(init,cond,next){...}
    ForIteratorStmt,    // for(var v : iterator){...}
    ForRangeStmt,       // for(var v : range){...}
    ForListStmt,        // for(var v : list){...}
    ForMapStmt,         // for((var k, var v)) : map){...} etc.
    ForIterableStmt,    // for(var v : iterable){...}
    ContinueStmt,       // continue; or continue Label;
    BreakStmt,          // break; or break Label;
    Return0Stmt,        // return;
    Return1Stmt,        // return expr;
    ReturnNStmt,        // return expr, expr, ...;
    ReturnTStmt,        // return (expr, expr, ...);
    TryCatchStmt,       // using(res){...}, try(res){...} [catch(T e){...}]
    TryFinallyStmt,     // try{...} [catch(T e){...}] finally{...}
    InitAst,            // default initializer TODO move higher?
    ;
    public static final NodeType[] NODES = NodeType.values();
  }

  // Clone of javatools/org.xvm.asm.ast.BiExprAST.Operator
  public enum BinOp {
    Else        (":"   ), // an "else" for nullability checks
    CondElse    ("?:"  ), // the "elvis" operator
    CondOr      ("||"  ),
    CondXor     ("^^"  ),
    CondAnd     ("&&"  ),
    BitOr       ("|"   ),
    BitXor      ("^"   ),
    BitAnd      ("&"   ),
    CompEq      ("=="  ),
    CompNeq     ("!="  ),
    CompLt      ("<"   ),
    CompGt      (">"   ),
    CompLtEq    ("<="  ),
    CompGtEq    (">="  ),
    CompOrd     ("<=>" ),
    As          ("as"  ),
    Is          ("is"  ),
    RangeII     (".."  ),
    RangeIE     ("..<" ),
    RangeEI     (">.." ),
    RangeEE     (">..<"),
    Shl         ("<<"  ),
    Shr         (">>"  ),
    Ushr        (">>>" ),
    Add         ("+"   ),
    Sub         ("-"   ),
    Mul         ("*"   ),
    Div         ("/"   ),
    Mod         ("%"   ),
    DivRem      ("/%"  ),
    ;
    public final String text;
    BinOp(String text) { this.text = text; }
    public static final BinOp[] OPS = BinOp.values();
  }

  // Clone of javatools/org.xvm.asm.ast.UnaryOpExprAST.Operator
  public enum UniOp {
    Not       ("!"       , true ),
    Minus     ("-"       , true ),
    Compl     ("~"       , true ),
    PreInc    ("++"      , true ),
    PreDec    ("--"      , true ),
    PostInc   ("++"      , false),
    PostDec   ("--"      , false),
    Ref       ("&"       , true ),
    Var       ("&"       , true ),
    Type      ("typeOf:" , true ),
    Private   ("private:", true ),
    Protected ("private:", true ),
    Public    ("public:" , true ),
    Pack      (""        , true ),
    ToInt     (".TOINT()", false),
    Trace     (".TRACE()", false),
    ;
    public final String   text;
    public final boolean  pre;
    UniOp(String text, boolean pre) { this.text = text; this.pre = pre; }
    public static final UniOp[] OPS = UniOp.values();
  }

  // Clone of javatools/org.xvm.asm.ast.AssignAST.Operator
  public enum AsnOp {
    Asn           ("="   ),     // includes "<-" expression
    AddAsn        ("+="  ),
    SubAsn        ("-="  ),
    MulAsn        ("*="  ),
    DivAsn        ("/="  ),
    ModAsn        ("%="  ),
    ShiftLAsn     ("<<=" ),
    ShiftRAsn     (">>=" ),
    UShiftRAsn    (">>>="),
    AndAsn        ("&="  ),
    OrAsn         ("|="  ),
    XorAsn        ("^="  ),
    AsnIfNotFalse (":="  ),     // x := y; (includes when used as a condition, e.g. if (x := y))
    AsnIfNotNull  ("?="  ),     // x ?= y; (includes when used as a condition, e.g. if (x := y))
    AsnIfWasTrue  ("&&=" ),
    AsnIfWasFalse ("||=" ),
    AsnIfWasNull  ("?:=" ),
    Deref         ("->"  ),
    ;
    public final String text;
    AsnOp(String text) { this.text = text; }
    public static final AsnOp[] OPS = AsnOp.values();
  }

  
}
