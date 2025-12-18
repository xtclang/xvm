# XTC Compiler Architecture Analysis: Road to LSP

## Executive Summary

This document began as an analysis of Java's broken `clone()` mechanism in the XVM codebase, but quickly revealed deeper architectural issues that would prevent building a modern Language Server Protocol (LSP) implementation for XTC.

**The Core Problem**: The current lexer, parser, and compiler were designed for batch compilation - parse once, compile once, discard. An LSP/IDE integration requires the opposite: incremental updates, concurrent queries, error tolerance, and long-lived data structures.

**What This Document Covers**:
1. **Parts 1-7**: Clone/copy anti-patterns and why `transient` doesn't work as intended
2. **Parts 8-11**: AST mutability, thread-safety issues, and Lexer/Parser design
3. **Parts 12-16**: Null handling, TypeInfo bottlenecks, arrays vs collections, and migration patterns
4. **Parts 17-20**: Generic type erasure, thread safety chaos, LSP requirements, and error handling
5. **Part 21**: The practical solution - building an LSP adapter layer

**The Key Insight**: Rather than rewriting the compiler from scratch, we can build an **adapter layer** that:
1. Extracts data from the existing (flawed) structures after parsing/compilation
2. Transforms it into clean, immutable, LSP-friendly data structures
3. Provides proper APIs for IDE features (hover, completion, go-to-definition, etc.)

This approach lets us leverage the existing compiler's correctness while providing a modern API for tooling.

---

## Part 1: Why Java's Clone is Broken by Design

### 1.1 The Fundamental Problem

Java's `clone()` mechanism was introduced in Java 1.0 and is widely considered one of the worst-designed features in the language. Joshua Bloch, former Java platform lead at Sun Microsystems, wrote in *Effective Java*:

> "The Cloneable interface was intended as a mixin interface for objects to advertise that they permit cloning. Unfortunately, it fails to serve this purpose... Cloneable has many problems. This is a case where the cure is worse than the disease."

### 1.2 The Problems with Cloneable

#### Problem 1: Cloneable Contains No Methods

```java
public interface Cloneable {
    // Empty! No clone() method defined here.
}
```

The `Cloneable` interface is a **marker interface** that contains no methods. The `clone()` method is defined on `Object`, not on `Cloneable`. This means:

- You cannot call `clone()` through a `Cloneable` reference
- There's no compile-time contract for what clone should do
- The interface provides zero type safety

#### Problem 2: Object.clone() is Protected

```java
// This doesn't compile!
Cloneable c = getSomeCloneable();
Object copy = c.clone();  // Error: clone() has protected access in Object
```

To make clone work, every class must:
1. Implement `Cloneable` (or get `CloneNotSupportedException`)
2. Override `clone()` to make it public
3. Call `super.clone()` and handle the checked exception
4. Cast the result (since `Object.clone()` returns `Object`)

#### Problem 3: The Exception is a Lie

```java
public Object clone() {
    try {
        return super.clone();
    } catch (CloneNotSupportedException e) {
        throw new IllegalStateException(e);  // This pattern is everywhere in XVM
    }
}
```

If a class implements `Cloneable`, `CloneNotSupportedException` will **never** be thrown. But because it's a checked exception, every clone implementation must catch it. This is pure boilerplate noise.

#### Problem 4: Shallow Copy Semantics

`Object.clone()` performs a **shallow copy**: it copies primitive fields and copies references (not the objects they point to). This means:

```java
class Container implements Cloneable {
    private List<String> items = new ArrayList<>();

    public Object clone() {
        return super.clone();  // items list is SHARED with clone!
    }
}

Container original = new Container();
Container copy = (Container) original.clone();
copy.getItems().add("oops");  // Modifies original too!
```

Every mutable field must be manually deep-copied, and **nothing in the type system enforces this**.

#### Problem 5: Constructors Are Bypassed

`Object.clone()` creates objects **without calling any constructor**. This violates fundamental object-oriented principles:

- Final fields cannot be set (they're copied from the original)
- Constructor invariants are not established
- Initialization logic is skipped

```java
class Validated implements Cloneable {
    private final int value;

    public Validated(int value) {
        if (value < 0) throw new IllegalArgumentException();
        this.value = value;
    }

    // clone() bypasses the validation!
}
```

#### Problem 6: Inheritance Nightmares

If a class's `clone()` doesn't call `super.clone()`, subclasses break:

```java
class Base implements Cloneable {
    public Base clone() {
        return new Base();  // Wrong! Doesn't call super.clone()
    }
}

class Derived extends Base {
    private int extra;

    public Derived clone() {
        Derived copy = (Derived) super.clone();  // ClassCastException!
        copy.extra = this.extra;
        return copy;
    }
}
```

### 1.2 How Clone Is Actually Used in XVM

Understanding **why** clone exists in the codebase helps explain why it's problematic and what should replace it.

#### Use Case 1: Parser Backtracking (Token Cloning)

The parser clones tokens to save/restore its position:

```java
// Parser.java - saving parser state
mark.token     = m_token        == null ? null : m_token.clone();
mark.putBack   = m_tokenPutBack == null ? null : m_tokenPutBack.clone();
mark.lastMatch = m_tokenPrev    == null ? null : m_tokenPrev.clone();
```

**Purpose**: Allow the parser to try a parse, fail, and restore to a previous point.

**What should be done instead**: Tokens should be immutable records. Parser state should be a separate immutable snapshot:
```java
record ParserState(int tokenIndex, List<Token> tokens) {
    ParserState advance() { return new ParserState(tokenIndex + 1, tokens); }
    ParserState reset(ParserState saved) { return saved; }
}
```

#### Use Case 2: Statement Block Cloning for Multiple Validation Passes

Loop constructs clone their body blocks before validation:

```java
// ForStatement.java, WhileStatement.java, LambdaExpression.java
StatementBlock blockTemp = (StatementBlock) body.clone();
// ... validate blockTemp (which mutates it) ...
// ... if validation fails, try again with fresh clone ...
```

**Purpose**: The validator **mutates** AST nodes during validation (adds type info, resolves symbols). If validation fails and needs retry (e.g., with different type assumptions), a fresh copy is needed.

**What should be done instead**: Validation should NOT mutate AST nodes. Instead:
- AST nodes should be immutable
- Validation results stored in separate TypeContext/ValidationResult
- No cloning needed because original is never modified

#### Use Case 3: Type Array Defensive Copying

Arrays are cloned before modification:

```java
// InvocationExpression.java
atypeParams = atypeParams.clone();  // Don't mess up the actual types
atypeParams[i] = resolvedType;      // Now safe to modify

// ConvertExpression.java
TypeConstant[] aType = expr.getTypes().clone();
aType[0] = type;
```

**Purpose**: Arrays passed around might be shared references. Clone before modifying to avoid corrupting other references.

**What should be done instead**: Use immutable lists:
```java
List<TypeConstant> types = new ArrayList<>(expr.getTypes());
types.set(0, type);
// Or: List.copyOf() for immutable defensive copy
```

#### Use Case 4: Expression Copying for Speculative Evaluation

Expressions are cloned for "what-if" analysis:

```java
// NewExpression.java
TypeExpression exprTest = (TypeExpression) type.clone();
// ... try to resolve exprTest as a specific type ...
// ... if fails, original type expression is untouched ...
```

**Purpose**: Try to validate an expression a certain way. If it fails, the original is unchanged for alternative validation.

**What should be done instead**: Expressions should be immutable. Validation produces results separately:
```java
sealed interface TypeResolution permits Resolved, Unresolved {}
TypeResolution result = tryResolve(type, context);
// Original 'type' unchanged regardless of result
```

#### The Core Problem: Validation Mutates AST

**Every use of clone traces back to one root cause: the validation phase modifies AST nodes in place.**

If validation were side-effect-free (immutable AST + separate validation context), clone would be unnecessary:

| Current Pattern | Root Cause | Better Design |
|-----------------|------------|---------------|
| Clone before validate | Validation mutates nodes | Immutable AST |
| Clone tokens for backtrack | Parser holds token state | Immutable token list + position index |
| Clone arrays before modify | Arrays are mutable | Immutable lists |
| Clone for speculative eval | Original would be corrupted | Separate ValidationResult |

#### Why This Matters for LSP

In an LSP server:
1. Multiple threads query the AST simultaneously
2. Background validation runs while UI queries hover info
3. User edits file while diagnostics are being computed

With mutable AST + clone:
- Must clone entire AST for each thread → expensive
- Clone races with ongoing mutations → data corruption
- Can't share cached validation results → memory bloat

With immutable AST:
- Share same AST across all threads → no copying
- New edits create new AST version → old queries complete on old version
- Cache validation results safely → memory efficient

---

## Part 2: The `transient` Keyword Misconception

### 2.1 What `transient` Actually Does

The `transient` keyword in Java has **one purpose**: to exclude fields from Java's built-in serialization mechanism (`ObjectInputStream`/`ObjectOutputStream`).

```java
class Example implements Serializable {
    private String saved;      // Serialized
    private transient String notSaved;  // Not serialized
}
```

### 2.2 What `transient` Does NOT Do

**`transient` has absolutely no effect on `Object.clone()`.**

When you call `super.clone()`, Java performs a bitwise copy of the object's memory. Every field is copied, regardless of whether it's marked `transient`.

```java
class Broken implements Cloneable {
    private transient int scratchData = 42;

    public Object clone() {
        return super.clone();  // scratchData IS copied! Value will be 42.
    }
}
```

### 2.3 The XVM Codebase Misconception

The XVM codebase uses `transient` extensively with comments like:

```java
private transient int m_iPos = -1;      // "cached position"
private transient int m_cRefs;          // "reference count"
private transient Object m_oValue;      // "runtime value"
private transient TypeInfo m_typeinfo;  // "cached type info"
```

The intent is clearly "don't copy this field." **But the fields ARE being copied.**

This is a **semantic mismatch**: the developers think `transient` means "transient data, don't copy" but Java interprets it as "don't serialize" (which XVM doesn't even use).

### 2.4 Separating Compilation State from Syntax Tree

The root problem is that the AST conflates **two different concerns**:

1. **Syntactic structure** - the parsed representation of source code (immutable after parsing)
2. **Semantic analysis results** - types, resolved symbols, validation state (computed during compilation)

Currently, these are mixed in the same objects:

```java
// Expression.java - mixing syntax and semantics
public class Expression extends AstNode {
    // Syntax (from parsing)
    protected Token operator;
    protected Expression left, right;

    // Semantics (from validation) - stored as "transient"
    private transient Object m_oType;        // Resolved type
    private transient Object m_oConst;       // Constant value if known
    private transient Stage m_stage;         // Validation progress
}
```

#### The Correct Architecture: Separate Layers

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: Immutable Syntax Tree (SyntaxNode)                │
│  - Pure structure from parsing                              │
│  - Immutable records                                        │
│  - No compilation state                                     │
│  - Can be shared across threads, cached indefinitely        │
└─────────────────────────────────────────────────────────────┘
         │
         │  Semantic analysis produces
         ▼
┌─────────────────────────────────────────────────────────────┐
│  Layer 2: Semantic Model (keyed by SyntaxNode)              │
│  - TypeMap: SyntaxNode → TypeConstant                       │
│  - SymbolMap: SyntaxNode → ResolvedSymbol                   │
│  - DiagnosticMap: SyntaxNode → List<Diagnostic>             │
│  - Can be rebuilt without touching syntax tree              │
└─────────────────────────────────────────────────────────────┘
         │
         │  Code generation reads both
         ▼
┌─────────────────────────────────────────────────────────────┐
│  Layer 3: Code Generation                                   │
│  - Reads immutable syntax tree                              │
│  - Reads semantic model                                     │
│  - Produces bytecode                                        │
└─────────────────────────────────────────────────────────────┘
```

#### Implementation: Syntax Nodes

```java
// Immutable syntax - just structure
public record BinaryExpression(
    Expression left,
    Token operator,
    Expression right,
    SourceRange range
) implements Expression {
    // No type info, no validation state, no transient fields
}
```

#### Implementation: Semantic Context

```java
// Compilation results stored separately
public class SemanticModel {
    private final Map<Expression, TypeConstant> types = new IdentityHashMap<>();
    private final Map<NameExpression, Symbol> symbols = new IdentityHashMap<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public void recordType(Expression expr, TypeConstant type) {
        types.put(expr, type);
    }

    public Optional<TypeConstant> typeOf(Expression expr) {
        return Optional.ofNullable(types.get(expr));
    }

    // Semantic model can be rebuilt from same syntax tree
    public static SemanticModel analyze(SyntaxTree tree, CompilationContext ctx) {
        SemanticModel model = new SemanticModel();
        new TypeChecker(model, ctx).visit(tree.root());
        return model;
    }
}
```

#### Current "Transient" Fields and Their Proper Home

| Current Field | Current Location | Proper Home |
|---------------|------------------|-------------|
| `m_oType` | Expression | SemanticModel.types map |
| `m_oConst` | Expression | SemanticModel.constants map |
| `m_stage` | AstNode | ValidationState (separate) |
| `m_typeinfo` | TypeConstant | TypeInfoCache (separate) |
| `m_iPos` | Constant | ConstantPoolIndex (separate) |
| `m_cRefs` | Constant | ReferenceCounter (separate) |

#### Benefits of Separation

| Aspect | Mixed (Current) | Separated (Proposed) |
|--------|-----------------|---------------------|
| Clone needed | Yes (to preserve syntax) | No (syntax immutable) |
| Thread safety | Requires synchronization | Syntax inherently safe |
| Incremental recompile | Must re-parse | Reuse syntax tree |
| LSP queries | Race with validation | Query stable syntax |
| Memory for cache | Per-node transient fields | Single shared cache |
| Testing | Hard to isolate | Test each layer separately |

#### Migration Path

**Phase 1**: Define the semantic model interface
```java
public interface SemanticModel {
    Optional<TypeConstant> typeOf(Expression expr);
    Optional<Symbol> symbolOf(NameExpression name);
    List<Diagnostic> diagnosticsFor(AstNode node);
}
```

**Phase 2**: Populate semantic model during validation (dual-write)
```java
// During validation, write to both old location AND new model
void recordType(Expression expr, TypeConstant type) {
    expr.m_oType = type;                    // Old (temporary)
    semanticModel.recordType(expr, type);   // New
}
```

**Phase 3**: Migrate readers to use semantic model
```java
// Before
TypeConstant type = expr.getType();  // Reads m_oType

// After
TypeConstant type = model.typeOf(expr).orElseThrow();
```

**Phase 4**: Remove transient fields from AST nodes
**Phase 5**: Make AST nodes records (immutable)

---

## Part 3: Bugs Found in the XVM Codebase

### 3.1 Constant.adoptedBy() - Incomplete Reset

```java
// From Constant.java
protected Constant adoptedBy(ConstantPool pool) {
    Constant that;
    try {
        that = (Constant) super.clone();
    } catch (CloneNotSupportedException e) {
        throw new IllegalStateException(e);
    }
    that.setContaining(pool);
    that.resetRefs();  // Only resets m_cRefs!
    return that;
}

void resetRefs() {
    m_cRefs = 0;  // What about m_iPos and m_oValue?
}
```

**Bug**: `m_iPos` (the constant's position in the pool) and `m_oValue` (cached runtime value) are copied to the clone but never reset. A constant adopted by a new pool will have:
- An incorrect position index from the old pool
- A stale cached value that may be invalid in the new context

### 3.2 Component.cloneBody() - Manual Field Nulling

```java
// From Component.java
protected Component cloneBody() {
    Component that;
    try {
        that = (Component) super.clone();
    } catch (CloneNotSupportedException e) {
        throw new IllegalStateException(e);
    }

    // Deep clone the contributions
    // ...

    that.m_sibling     = null;
    that.m_childByName = null;
    that.m_abChildren  = null;

    return that;
}
```

**Problem**: Every time a new field is added to `Component`, someone must remember to add it to the manual nulling list. This is error-prone and has likely already caused bugs.

### 3.3 AstNode.clone() - Reflection Fragility

```java
// From AstNode.java
public AstNode clone() {
    AstNode that;
    try {
        that = (AstNode) super.clone();
    } catch (CloneNotSupportedException e) {
        throw new IllegalStateException(e);
    }

    for (Field field : getChildFields()) {
        Object oVal = field.get(this);
        // ... reflection-based deep copying
    }
    return that;
}
```

**Problems**:
1. Uses reflection, which is slow and bypasses compile-time checking
2. Relies on subclasses correctly implementing `getChildFields()`
3. Does NOT handle transient fields - they're all copied
4. If a subclass forgets to include a field in `getChildFields()`, it silently shares that field with its clone

### 3.4 Widespread Transient Field Copying

Every class with `transient` fields that uses `clone()` has this bug. Here's a sample:

| Class | Transient Fields Incorrectly Copied |
|-------|-------------------------------------|
| `Constant` | `m_iPos`, `m_cRefs`, `m_oValue` |
| `TypeConstant` | `m_typeinfo`, `m_handle`, `m_mapRelations`, ~10 more |
| `MethodStructure` | `m_registry`, `m_code`, `m_ast`, `m_cVars`, ~8 more |
| `ClassStructure` | `m_typeFormal`, `m_typeCanonical`, `m_safety`, ~4 more |
| `ConstantPool` | `m_fRecurseReg`, ~150 cached type/class constants |

**Total**: Over 200 transient fields that are being copied when they shouldn't be.

---

## Part 4: Array Cloning Issues

### 4.1 The Pattern

Throughout the codebase:

```java
if (needToModify) {
    array = array.clone();
    array[i] = newValue;
}
```

### 4.2 Problems with array.clone()

1. **No length parameter**: You can't resize while copying
2. **Returns Object**: Requires casting: `(Type[]) array.clone()`
3. **Obscures intent**: Is this a defensive copy? A resize? Unclear.

### 4.3 Modern Alternative

```java
// Clear intent, explicit length, no cast needed
array = Arrays.copyOf(array, array.length);

// Or for defensive parameter copying:
this.items = Arrays.copyOf(items, items.length);
```

---

## Part 5: What Should Be Done Instead

### 5.1 Explicit Copy Methods

Replace `Cloneable` with an explicit interface:

```java
public interface Copyable<T> {
    /**
     * Creates a copy of this object. Transient/cached fields are not copied.
     */
    T copy();
}
```

### 5.2 Copy Constructors

For simple classes, use copy constructors:

```java
public class Token {
    private final long position;
    private final String value;

    public Token(Token original) {
        this.position = original.position;
        this.value = original.value;
    }
}
```

Benefits:
- Constructor is called (invariants established)
- Explicit field handling (compiler catches missing fields if you use records)
- No reflection, no exceptions, no casts

### 5.3 The @CopyIgnore Annotation

Replace the `transient` keyword with a custom annotation that clearly communicates its purpose:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface CopyIgnore {
    /**
     * Marks fields that should not be copied by {@link Copyable#copy()}.
     * These fields retain their default values in copied instances.
     *
     * Use for:
     * - Cached/computed values that can be recalculated
     * - Scratch data used during processing
     * - Reference counts and bookkeeping
     * - Runtime handles and context references
     *
     * This replaces the incorrect use of Java's {@code transient} keyword,
     * which only affects serialization, not cloning or copying.
     */
}
```

Then in copy methods:

```java
public Constant copy() {
    Constant copy = new Constant();
    copy.m_iHash = this.m_iHash;
    // @CopyIgnore fields are intentionally not copied
    return copy;
}
```

**Why @CopyIgnore instead of @Transient?**
- Avoids confusion with Java's `transient` keyword
- Name explicitly describes the behavior (ignored during copy)
- Mirrors naming conventions like `@JsonIgnore`

### 5.4 Immutability

The best solution for many cases: make objects immutable. If objects can't change, they don't need to be copied.

```java
public final class ImmutableToken {
    private final long position;
    private final String value;

    // No setters, no clone needed, just share the reference
}
```

---

## Part 6: Scale of the Problem

### Current State

| Metric | Count |
|--------|-------|
| Classes implementing `Cloneable` | 9 |
| Uses of `.clone()` on objects | ~50 |
| Uses of `.clone()` on arrays | ~100 |
| Transient fields (incorrectly copied) | ~200 |
| AstNode subclasses (reflection risk) | ~80 |

### Risk Assessment

| Issue | Severity | Likelihood | Impact |
|-------|----------|------------|--------|
| Transient fields copied | **Critical** | Certain | Data corruption, stale caches |
| Missing field in cloneBody() | High | Likely | Shared mutable state |
| Missing field in getChildFields() | High | Likely | Shared mutable state |
| Array clone confusion | Low | Certain | Code clarity only |

---

## Part 7: Conclusion

The `Cloneable`/`clone()` mechanism and the `transient` keyword are being used in ways that:

1. **Don't work as intended** - transient fields ARE copied
2. **Are error-prone** - manual field handling is easy to get wrong
3. **Bypass type safety** - reflection and casts everywhere
4. **Violate encapsulation** - subclasses must know parent implementation details
5. **Are deprecated by the industry** - no modern Java code uses these patterns

The codebase should migrate to explicit `copy()` methods with a `Copyable<T>` interface, use `@CopyIgnore` annotations to document non-copied fields, and replace array `.clone()` with `Arrays.copyOf()`.

---

## Part 8: AST Node Design - LSP/Language Server Incompatibility

The `AstNode` hierarchy has fundamental design issues that make it unsuitable for modern IDE tooling like Language Server Protocol (LSP) implementations.

### 8.1 Pervasive Mutability

AST nodes are **deeply mutable** with state scattered across hundreds of fields:

```java
// From AstNode.java
private Stage m_stage = Stage.Initial;  // Mutated during compilation
private AstNode m_parent;               // Set via setParent()

// From Expression.java - validated state stored in fields
private TypeConstant[] m_aTypes;        // Set during validation
private boolean m_fValidated;           // Mutation flag

// From NameExpression.java - 15+ transient fields
private transient TargetInfo m_targetInfo;
private transient Argument m_arg;
private transient Plan m_plan = Plan.None;
private transient MethodConstant m_idBjarnLambda;
private transient PropertyAccess m_propAccessPlan;
// ... and more
```

**Problem for LSP**: Language servers need to:
1. Parse incrementally as the user types
2. Maintain multiple versions of the AST (for undo, diff, etc.)
3. Allow concurrent queries while the user edits

With mutable nodes, you can't safely query an AST while another thread modifies it.

### 8.2 Parent References Create Cycles

Every `AstNode` has a mutable `m_parent` reference:

```java
protected void setParent(AstNode parent) {
    assert parent == null || !this.isDiscarded() && !parent.isDiscarded();
    this.m_parent = parent;
}
```

**Problems**:
1. **Can't share subtrees**: If you want to copy part of a tree, you must deep-clone because parents would point to the wrong tree
2. **Memory leaks**: Parent references prevent garbage collection of orphaned subtrees
3. **Serialization complexity**: Parent references create cycles that complicate serialization
4. **No structural sharing**: Two ASTs that differ by one node can't share the common parts

### 8.3 Compilation State Mixed with Structure

AST nodes mix structural data with compilation state:

```java
// ForEachStatement.java - 18 transient fields for compilation state
private transient Label            m_labelContinue;
private transient Expression       m_exprLValue;
private transient Plan             m_plan;
private transient Context          m_ctxLabelVars;
private transient ErrorListener    m_errsLabelVars;
private transient Register         m_regFirst;
private transient Register         m_regLast;
private transient Register         m_regCount;
// ... etc
```

**Problem for LSP**:
- You can't re-validate a node without clearing all this state
- If validation fails partway through, the node is in an inconsistent state
- You can't query the AST's semantic information while re-compiling

### 8.4 Reflection-Based Child Traversal

The `getChildFields()` / `ChildIterator` mechanism uses reflection:

```java
@Override
protected Field[] getChildFields() {
    return CHILD_FIELDS;
}
private static final Field[] CHILD_FIELDS = fieldsForNames(
        SomeNode.class, "expr", "args", "body");
```

**Problems**:
1. **Runtime cost**: Reflection is slow, especially in hot paths
2. **Fragility**: Add a field, forget to update `fieldsForNames()` → bug
3. **No compile-time safety**: Field names are strings
4. **Modification requires runtime checks**: `ChildIteratorImpl.replaceWith()` uses reflection to modify parent

### 8.5 Thread Safety: Non-Existent

There is **zero** thread-safety in the AST implementation:

```java
// Multiple threads could call this simultaneously
public void replaceChild(AstNode nodeOld, AstNode nodeNew) {
    ChildIterator children = children();
    for (AstNode node : children) {
        if (node == nodeOld) {
            children.replaceWith(adopt(nodeNew));  // Modifies internal state!
            return;
        }
    }
}

// Stage is mutated without synchronization
protected void setStage(Stage stage) {
    if (stage != null && stage.compareTo(m_stage) > 0) {
        m_stage = stage;  // Race condition!
    }
}
```

**For LSP this is fatal**:
- Background threads parse/validate while UI thread queries
- Hover information queries the AST while validation runs
- Go-to-definition and find-references access nodes concurrently

### 8.6 The Clone Problem for LSP

To work around mutability, you might try cloning the AST for each operation. But:

1. **Clone is broken** (as documented above - transient fields copied incorrectly)
2. **Clone is expensive** - must deep-copy the entire tree
3. **Clone doesn't help with sharing** - two clones share no structure

---

## Part 9: The Solution - Immutable Copy-on-Write AST

### 9.1 Immutable Nodes with Final Fields

```java
public final class BinaryExpression implements AstNode {
    private final Token operator;
    private final Expression left;
    private final Expression right;
    private final long startPos;
    private final long endPos;

    // Constructor is the ONLY way to create a node
    public BinaryExpression(Token op, Expression left, Expression right) {
        this.operator = Objects.requireNonNull(op);
        this.left = Objects.requireNonNull(left);
        this.right = Objects.requireNonNull(right);
        this.startPos = left.getStartPosition();
        this.endPos = right.getEndPosition();
    }

    // "Modification" returns a new node
    public BinaryExpression withLeft(Expression newLeft) {
        return new BinaryExpression(operator, newLeft, right);
    }
}
```

**Benefits**:
- Inherently thread-safe
- Safe to share between versions
- No parent references needed (use zippers or paths for navigation)
- Structural sharing: `expr.withLeft(newLeft)` reuses `right` and `operator`

### 9.2 Lazy Cached Values with Memoizing Suppliers

For computed/cached values (like resolved types), use memoizing suppliers:

```java
import com.google.common.base.Suppliers;
import java.util.function.Supplier;

public final class TypeExpression implements AstNode {
    private final Token typeName;

    // Lazy computation, thread-safe, computed at most once
    private final Supplier<TypeConstant> resolvedType =
        Suppliers.memoize(() -> computeResolvedType());

    public TypeConstant getResolvedType() {
        return resolvedType.get();
    }

    private TypeConstant computeResolvedType() {
        // Expensive resolution logic here
    }
}
```

**Benefits**:
- Thread-safe lazy initialization (Guava's `Suppliers.memoize` uses double-checked locking)
- No mutable state
- Computed on first access, cached forever
- If resolution context changes, create a new node (copy-on-write)

### 9.3 Copy-on-Write Tree Updates

When you need to "modify" the AST (e.g., user types a character):

```java
public interface AstNode {
    // Returns a copy with the child at the given path replaced
    AstNode replaceAt(Path path, AstNode newChild);
}

// Usage:
AstNode oldTree = ...;
AstNode newTree = oldTree.replaceAt(
    Path.of(0, 2, 1),  // Path to the node to replace
    newExpressionNode
);
// oldTree is unchanged - can still be queried by other threads
// newTree shares structure with oldTree except along the path
```

**Benefits**:
- Old versions remain valid (for undo, concurrent access)
- Structural sharing minimizes memory overhead
- Thread-safe: readers never see partially-modified state

### 9.4 Separate Compilation State

Keep compilation/validation state separate from the AST structure:

```java
// Immutable AST node
public final class MethodDeclaration implements AstNode {
    private final Token name;
    private final List<Parameter> params;
    private final Statement body;
}

// Separate, mutable compilation context
public class CompilationContext {
    private final Map<AstNode, TypeInfo> resolvedTypes = new ConcurrentHashMap<>();
    private final Map<AstNode, List<Diagnostic>> errors = new ConcurrentHashMap<>();

    public void setResolvedType(AstNode node, TypeInfo type) {
        resolvedTypes.put(node, type);
    }

    public TypeInfo getResolvedType(AstNode node) {
        return resolvedTypes.get(node);
    }
}
```

**Benefits**:
- AST can be shared; only context is per-compilation
- Re-validation = new context, same AST
- Clear separation of concerns

### 9.5 @CopyIgnore Annotation for Semantic Information

If some data must be stored on nodes (for performance), use an explicit annotation:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface CopyIgnore {
    // Field is not part of the node's structural identity.
    // - Not included in equals()/hashCode()
    // - Not copied in copy-on-write operations
    // - May be lazily computed
}
```

---

## Part 10: Lexer and Parser - LSP Compatibility Analysis

The `Lexer` and `Parser` classes have their own set of issues that affect LSP/language server viability.

### 10.1 Lexer Design

**Current State:**
```java
public class Lexer implements Iterator<Token> {
    private final Source m_source;          // final - good
    private final ErrorListener m_errorListener;  // final - good
    private boolean m_fWhitespace;          // mutable state
}
```
Also ad
**Issues:**
1. **Consumes source destructively**: The Lexer iterates through `Source`, which maintains position state (`m_of`, `m_iLine`, `m_iLineOffset`). Once consumed, you must create a new `Source` to re-lex.

2. **No incremental lexing**: There's no way to lex just a changed region. Any edit requires re-lexing from the beginning.

3. **Source is mutable**: `Source` maintains cursor position and is `Cloneable` (with the broken clone pattern):
   ```java
   public class Source implements Constants, Cloneable {
       private int m_of;              // current offset - mutated
       private int m_iLine;           // current line - mutated
       private int m_iLineOffset;     // line offset - mutated
   }
   ```

**For LSP**: Every keystroke would require creating a new `Source` and re-lexing the entire file.

### 10.2 Parser Design

**Current State:**
```java
public class Parser {
    private final Source m_source;
    private ErrorListener m_errorListener;    // NOT final - can be swapped!
    private final Lexer m_lexer;
    private Token m_tokenPutBack;             // lookahead state
    private Token m_tokenPrev;                // previous token
    private Token m_token;                    // current token
    private Token m_doc;                      // doc comment
    private StatementBlock m_root;            // parse result
    private boolean m_fDone;                  // one-shot flag
    private boolean m_fAvoidRecovery;         // error recovery flag
    private SafeLookAhead m_lookAhead;        // speculative parsing
}
```

**Issues:**

1. **One-shot parsing**: The `m_fDone` flag ensures parsing can only happen once:
   ```java
   public StatementBlock parseSource() {
       if (!m_fDone) {
           m_fDone = true;  // Can never parse again!
           // ...
       }
       return m_root;
   }
   ```

2. **ErrorListener swapping**: The error listener can be changed mid-parse:
   ```java
   public String parseModuleNameIgnoreEverythingElse() {
       ErrorListener errsPrev = m_errorListener;
       try {
           m_errorListener = ErrorListener.BLACKHOLE;  // Thread-unsafe mutation
           // ...
       }
   }
   ```

3. **No incremental parsing**: No support for updating the AST when source changes. Must re-parse entire file.

4. **Direct AST mutation**: Parser creates and mutates AST nodes directly:
   ```java
   m_root = new StatementBlock(stmts, m_source, ...);
   ```

### 10.3 Token Design

```java
public class Token implements Cloneable {
    private long m_lStartPos;           // NOT final - can change!
    private final long m_lEndPos;       // final - good
    private Id m_id;                    // NOT final - can change!
    private final Object m_oValue;      // final - good
    private boolean m_fLeadingWhitespace;   // mutable
    private boolean m_fTrailingWhitespace;  // mutable
}
```

**Issues:**
- `m_lStartPos` and `m_id` are not final, allowing mutation
- Whitespace flags are set after construction via `noteWhitespace()`
- Token implements `Cloneable` (broken pattern)

### 10.4 What Would Be Needed for LSP

1. **Immutable tokens**: All fields final, set at construction
2. **Immutable Source**: Or at least a position-independent representation
3. **Incremental lexer**: Only lex changed regions
4. **Incremental parser**: Update AST for local changes
5. **Cancelable operations**: Abort parsing when new input arrives
6. **Thread-safe**: All operations must be safe for concurrent access

---

## Part 11: State That Can Be Made Final

### 11.1 Immediately Finalizable (No Code Changes)

These fields are never reassigned after construction and can be made `final` immediately:

**Token.java:**
```java
private long m_lStartPos;    // → private final long m_lStartPos;
private Id m_id;             // → private final Id m_id;
```

**Parser.java:**
```java
private ErrorListener m_errorListener;  // Harder - it's swapped in one method
```

**Many AST nodes** have fields assigned only in constructors but not declared final.

### 11.2 Finalizable with Minor Refactoring

**Token whitespace flags**: Instead of mutating after construction:
```java
// Current (mutable)
Token token = eatToken();
token.noteWhitespace(before, after);

// Better (immutable)
Token token = new Token(pos, end, id, value, whitespaceBefore, whitespaceAfter);
```

**Source position state**: Instead of mutating position:
```java
// Current (mutable Source)
char ch = source.next();  // mutates m_of

// Better (position passed separately)
record LexerState(Source source, int offset) {}
LexerResult lex(LexerState state) {
    return new LexerResult(token, state.withOffset(newOffset));
}
```

### 11.3 Finalizable with Better Design

**AST Node transient fields**: These are assigned lazily during validation. With memoizing suppliers:
```java
// Current
private transient TypeConstant m_type;  // assigned during validation

// Better
private final Supplier<TypeConstant> m_type = Suppliers.memoize(this::computeType);
```

**Parser lookahead state**: Could be passed as parameters instead of stored:
```java
// Current
private Token m_token;
private Token m_tokenPutBack;

// Better - pure functional style
record ParserState(Token current, Token putBack, List<Token> remaining) {}
```

### 11.4 Fields That CANNOT Be Final (Fundamental Design)

Some fields represent genuinely mutable state that can't be eliminated without redesign:

- **AstNode.m_parent**: Parent references require mutation when building trees (unless using zippers)
- **AstNode.m_stage**: Compilation stages are inherently sequential mutations
- **Source.m_of**: Current position (unless redesigned as above)

---

## Part 12: Null Handling Problems

The codebase has pervasive null-safety issues that compound the mutability problems.

### 12.1 Multiple Meanings of Null

Null is used inconsistently to mean different things:

| Context | Null Means | Example |
|---------|------------|---------|
| Uninitialized | "Not yet computed" | `m_type = null` before validation |
| Absent | "No value exists" | Optional parent reference |
| Error | "Computation failed" | Failed resolution returns null |
| Default | "Use default behavior" | Null error listener = use default |
| Empty | "Empty collection" | Null instead of empty list |

**Problem**: Code must guess which meaning applies:
```java
TypeConstant type = expr.getType();
if (type == null) {
    // Is this an error? Not validated yet? Void type? Optional absence?
}
```

### 12.2 Defensive Null Checks Everywhere

The codebase is littered with defensive null checks:
```java
// From various AST nodes
if (type != null && type.isResolved()) { ... }
if (args != null && !args.isEmpty()) { ... }
if (parent != null && parent.getStage() != null) { ... }
```

This creates:
- Code bloat
- Inconsistent handling (some places check, some don't)
- NullPointerExceptions when checks are forgotten

### 12.3 Null Sanitization/Mutation

Inputs are often "sanitized" by replacing null with defaults:
```java
public void setArgs(Expression[] args) {
    m_args = args == null ? Expression.NO_EXPRS : args;  // Silent mutation
}
```

**Problems:**
- Hides bugs (null passed where it shouldn't be)
- Inconsistent - some methods accept null, some don't
- No documentation of which behavior applies

### 12.4 No Nullness Annotations

The codebase lacks `@Nullable`/`@NonNull` annotations:
- No static analysis to catch null errors
- No documentation of contracts
- IDE can't help find bugs

### 12.5 Recommended Solutions

**1. Use Optional for Genuinely Optional Values:**
```java
// Instead of
public TypeConstant getType() { return m_type; }  // may return null!

// Use
public Optional<TypeConstant> getType() { return Optional.ofNullable(m_type); }
```

**2. Add Nullness Annotations (JSpecify or JetBrains):**
```java
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;

public @NonNull TypeConstant getType() { ... }  // Never returns null
public @Nullable TypeConstant findType() { ... }  // May return null
```

**3. Fail Fast on Invalid Null:**
```java
public void setArgs(@NonNull Expression[] args) {
    this.m_args = Objects.requireNonNull(args, "args cannot be null");
}
```

**4. Use Empty Collections Instead of Null:**
```java
// Instead of
private List<Expression> m_args;  // may be null

// Use
private List<Expression> m_args = List.of();  // never null, immutable empty
```

**5. Define Null Semantics Explicitly:**
Create a project convention document:
- Null = error (fail fast)
- Optional = absence (may not exist)
- Empty collection = no items (never null)

---

## Part 13: TypeInfo as Compilation Bottleneck

The `TypeInfo` class is central to the compilation process and presents specific challenges for both performance and immutability.

### 13.1 What TypeInfo Does

TypeInfo is the "flattened" representation of a type's structure:
- All properties (inherited, declared, virtual)
- All methods (with call chains)
- Type parameters and annotations
- Child types
- Contribution chain (extends, implements, incorporates, etc.)

It's used **everywhere**:
- 475 references across 35 files
- Called via `ensureTypeInfo()` from TypeConstant
- Required for method resolution, property access, type checking

### 13.2 Current Architecture Issues

**1. Synchronized Construction:**
```java
private synchronized TypeInfo ensureTypeInfo(TypeInfo info, ErrorListener errs) {
    // Entire method is synchronized!
    // ...
    info = buildTypeInfo(errs);  // Expensive operation under lock
}
```

This means only one thread can build a TypeInfo for a given type at a time, and other threads block waiting.

**2. Invalidation Cascades:**
```java
f_cInvalidations = cInvalidations;  // Tracks when TypeInfo was built

protected boolean isUpToDate(TypeInfo info) {Can you a
    return info.getInvalidationCount() >= getConstantPool().getInvalidationCount();
}
```

When the constant pool is modified, ALL TypeInfo objects can become stale, requiring rebuild.

**3. Circular Dependencies:**
```java
// From the code comments:
// "to complete the TypeInfo for type X, it has to get the TypeInfo for
// type Y, and to build that, it has to get the TypeInfo for type X"
```

The code has elaborate deferred completion logic to handle circular type references:
```java
for (int cDeferredPrev = 0, iTry = 0; hasDeferredTypeInfo(); iTry++) {
    List<TypeConstant> listDeferred = takeDeferredTypeInfo();
    // ... retry building deferred TypeInfo
}
```

**4. Heavy Objects:**
TypeInfo stores many large maps:
```java
private final Map<PropertyConstant, PropertyInfo> f_mapProps;
private final Map<MethodConstant, MethodInfo>     f_mapMethods;
private final Map<Object, PropertyInfo>           f_mapVirtProps;
private final Map<Object, MethodInfo>             f_mapVirtMethods;
private final ConcurrentHashMap<...>              f_cacheById;   // Additional caches
private final ConcurrentHashMap<...>              f_cacheByNid;
```

Creating new TypeInfo objects for each access level (`limitAccess()`) or transformation (`asInto()`) copies all these maps.

### 13.3 Why This Hurts

**Compilation bottleneck:**
1. Every type reference needs TypeInfo
2. TypeInfo building is synchronized per-type
3. Circular deps cause retry loops
4. Invalidation causes mass rebuilds

**Memory pressure:**
1. Large maps per TypeInfo
2. Multiple derived TypeInfo objects (different access levels)
3. Caches duplicated across related TypeInfo objects

### 13.4 Compatibility with Immutable AST

Moving to immutable AST nodes **does not conflict** with TypeInfo, but highlights opportunities:

**Current flow:**
```
AST Node → Validate → Store type in node (mutable)
                   ↓
              TypeInfo cached on TypeConstant (mutable)
```

**Immutable flow:**
```
AST Node → Validate → Return new node with type (immutable)
                   ↓
              TypeInfo in external cache (immutable or memoized)
```

### 13.5 Potential Improvements

**1. Lock-Free TypeInfo with CAS:**
```java
// Instead of synchronized
private volatile TypeInfo m_typeinfo;

public TypeInfo ensureTypeInfo(ErrorListener errs) {
    TypeInfo info = m_typeinfo;
    if (isComplete(info) && isUpToDate(info)) {
        return info;
    }

    // Build new TypeInfo (not synchronized)
    TypeInfo newInfo = buildTypeInfo(errs);

    // CAS to install (retry if another thread won)
    if (!TYPEINFO_UPDATER.compareAndSet(this, info, newInfo)) {
        return m_typeinfo;  // Another thread already set it
    }
    return newInfo;
}
```

**2. Lazy Method/Property Resolution:**
Instead of pre-populating all methods/properties:
```java
// Current: all methods computed upfront
f_mapMethods = computeAllMethods();

// Better: compute on demand
private final Supplier<Map<MethodConstant, MethodInfo>> methods =
    Suppliers.memoize(this::computeAllMethods);
```

**3. Structural Sharing:**
When creating access-limited TypeInfo, share immutable portions:
```java
public TypeInfo limitAccess(Access access) {
    // Don't copy maps - filter on access
    return new FilteredTypeInfo(this, access);
}
```

**4. Separate Caches:**
Move caches out of TypeInfo into a shared cache:
```java
// Current: each TypeInfo has its own cache
private final ConcurrentHashMap<...> f_cacheById;

// Better: shared cache keyed by (TypeConstant, MethodId)
class TypeInfoCache {
    private final ConcurrentHashMap<CacheKey, MethodInfo> methodCache;
}
```

**5. Incremental Invalidation:**
Instead of global invalidation count, track dependencies:
```java
// Current: global counter invalidates everything
if (info.getInvalidationCount() < pool.getInvalidationCount()) {
    rebuild();
}

// Better: track specific dependencies
if (info.getDependencies().anyModifiedSince(info.getTimestamp())) {
    rebuild();
}
```

### 13.6 Impact on Immutable Migration

TypeInfo improvements are **orthogonal** to immutable AST:

| Concern | AST Immutability | TypeInfo Optimization |
|---------|------------------|----------------------|
| Thread safety | Final fields, no parent refs | Lock-free construction |
| Memory | Structural sharing | Lazy computation, shared caches |
| Performance | Copy-on-write | Incremental invalidation |
| Correctness | Memoized suppliers | CAS-based updates |

Both can proceed independently. In fact, immutable AST makes TypeInfo easier because:
- No risk of AST mutation during TypeInfo construction
- TypeInfo can be computed once and cached forever for a given AST version

---

## Part 14: Arrays vs Collections and the Null/Empty/One/N Pattern

The codebase has pervasive use of arrays where collections would be more appropriate, and a recurring anti-pattern of branching on array/collection size.

### 14.1 The Null/Empty/One/N Anti-Pattern

There are **489 occurrences** of length/size checks across 134 files. The typical pattern:

```java
// Pattern found EVERYWHERE
int cParams = atypeParams == null ? 0 : atypeParams.length;  // 50+ occurrences

// Or the expanded version
if (args == null) {
    // handle no args
} else if (args.length == 0) {
    // handle empty (different from null?)
} else if (args.length == 1) {
    // special case for one arg
} else {
    // handle N args
}
```

**Real examples from the codebase:**
```java
// From ReturnStatement.java
int cRets = atypeRet == null ? 0 : atypeRet.length;
int cRets = aRetTypes == null ? -1 : aRetTypes.length;  // -1 means something different!

// From LambdaExpression.java
int cReqParams  = atypeReqParams  == null ? -1 : atypeReqParams.length;
int cReqReturns = atypeReqReturns == null ? -1 : atypeReqReturns.length;

// From InvocationExpression.java
int cTypeParams = aargTypeParams == null ? 0 : aargTypeParams.length;
int cAll        = atypeParams == null ? 0 : atypeParams.length;
```

**Problems:**
1. **Null vs empty ambiguity**: Does `null` mean "not specified" and empty mean "explicitly none"?
2. **Magic numbers**: `-1` sometimes means "not applicable", `0` sometimes means "use default"
3. **Defensive boilerplate**: Same ternary pattern repeated hundreds of times
4. **Logic duplication**: Often the null and empty cases do the same thing

### 14.2 Empty Array Constants Proliferation

The codebase has **40+ "NO_*" or "EMPTY_*" constants**:

```java
// A sampling:
public static final Parameter[]      NO_PARAMS       = new Parameter[0];
public static final TypeConstant[]   NO_TYPES        = new TypeConstant[0];
public static final Constant[]       NO_CONSTS       = new Constant[0];
public static final Op[]             NO_OPS          = new Op[0];
public static final int[]            NO_ARGS         = new int[0];
public static final Annotation[]     NO_ANNOTATIONS  = new Annotation[0];
public static final MethodBody[]     NO_BODIES       = new MethodBody[0];
public static final BinaryAST[]      NO_ASTS         = new BinaryAST[0];
public static final ExprAST[]        NO_EXPRS        = new ExprAST[0];
public static final RegisterAST[]    NO_REGS         = new RegisterAST[0];
public static final ObjectHandle[]   OBJECTS_NONE    = new ObjectHandle[0];
public static final Field[]          NO_FIELDS       = new Field[0];
public static final Assignable[]     NO_LVALUES      = new Assignable[0];
public static final Argument[]       NO_RVALUES      = new Argument[0];
// ... and many more
```

This is a symptom of the problem - every place that uses arrays needs its own empty constant because:
1. Can't use `null` (NPE risk)
2. Creating `new T[0]` everywhere is wasteful
3. Arrays can't be made truly immutable constants

### 14.3 Where Arrays Should Be Collections

**Arrays that should be `List<T>`:**
```java
// Current
TypeConstant[] atypeParams;    // Used for iteration, size check
Expression[] aexprArgs;        // Used for iteration, passed around
Parameter[] aParams;           // Rarely mutated after creation

// Better
List<TypeConstant> typeParams = List.of(...);  // Immutable
List<Expression> args = List.of(...);          // Immutable
List<Parameter> params = List.of(...);         // Immutable
```

**Benefits of `List.of()`:**
- Immutable by default
- Never null (use empty list)
- `size()` is obvious
- No need for separate `NO_*` constants
- Better for generics (no array covariance issues)

### 14.4 The Fix: Always Use Collections, Never Null

**1. Replace null with empty:**
```java
// Instead of
TypeConstant[] atypes = condition ? computeTypes() : null;
int count = atypes == null ? 0 : atypes.length;

// Use
List<TypeConstant> types = condition ? computeTypes() : List.of();
int count = types.size();  // Always safe
```

**2. Use Optional for "not applicable":**
```java
// Instead of using -1 or null to mean "not applicable"
int cReqParams = atypeReqParams == null ? -1 : atypeReqParams.length;

// Use
Optional<List<TypeConstant>> reqParams = ...;
int count = reqParams.map(List::size).orElse(-1);
// Or better: use a dedicated type that captures the semantics
```

**3. Single unified empty constant:**
```java
// Instead of 40+ NO_* constants
public final class Empty {
    public static <T> List<T> list() { return List.of(); }
    public static <T> Set<T> set() { return Set.of(); }
    public static <K,V> Map<K,V> map() { return Map.of(); }
}
```

### 14.5 Handling the One-vs-Many Optimization

Sometimes there's a legitimate performance reason for special-casing single elements:

```java
// Current: optimization for single value
if (args.length == 1) {
    processSingle(args[0]);  // Avoid array overhead
} else {
    processMany(args);
}
```

**Better approach - use specialized types:**
```java
sealed interface Args permits NoArgs, SingleArg, MultiArgs {
    int size();
    Argument get(int i);
}

record NoArgs() implements Args { ... }
record SingleArg(Argument arg) implements Args { ... }
record MultiArgs(List<Argument> args) implements Args { ... }
```

Or simply accept that `List` overhead is negligible in 2025 JVMs.

### 14.6 Migration Complexity

Changing arrays to collections is a large undertaking:

| Array Type | Occurrences | Migration Difficulty |
|------------|-------------|---------------------|
| `TypeConstant[]` | Very high | Hard - deep in type system |
| `Expression[]` | High | Medium - AST nodes |
| `Parameter[]` | Medium | Easy - mostly data |
| `int[]` (arg IDs) | High | Hard - bytecode ops |
| `Constant[]` | High | Medium |

**Recommended approach:**
1. Start with new code - use `List<T>` in new APIs
2. Add `List<T>` overloads alongside array methods
3. Deprecate array methods
4. Migrate callers incrementally

### 14.7 Mutable Structures Returned from Getters

The lexer, parser, and AST classes frequently return mutable internal arrays/lists:

```java
// From NameExpression.java - returns internal list!
public List<Token> getNameTokens() {
    return m_names;  // Caller can modify!
}

// From CaseManager.java - returns internal array!
public Label[] getCaseLabels() {
    return m_aLabels;  // Caller can modify!
}

public Constant[] getCaseConstants() {
    return m_aConstants;  // Caller can modify!
}
```

**Problems:**
1. **Encapsulation violation**: Callers can modify internal state
2. **Defensive copying overhead**: To be safe, must clone on every return
3. **Thread safety**: Shared mutable state across threads

**Fix**: Return immutable views or store as immutable from the start.

**Note**: Some XTC language-level constructs may require arrays for the native Java implementation. Those should be documented and isolated.

---

## Part 15: The MemoizingSupplier Pattern

The codebase has hundreds of "cache if null" patterns that are thread-unsafe and error-prone.

### 15.1 The Current Anti-Pattern

```java
// Found throughout the codebase
private transient TypeInfo m_typeinfo;

public TypeInfo ensureTypeInfo() {
    TypeInfo info = m_typeinfo;
    if (info == null) {
        info = computeTypeInfo();  // Expensive
        m_typeinfo = info;         // Race condition!
    }
    return info;
}
```

**Problems:**
1. **Race condition**: Two threads can both see `null` and compute twice
2. **Incomplete publication**: Other threads may see partially constructed object
3. **Not final**: Field can't be final, breaks immutability

### 15.2 The Solution: MemoizingSupplier

```java
package org.xvm.util;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A thread-safe, memoizing supplier.
 *
 * <p>Semantics:
 * <ul>
 *   <li>The supplier is invoked at most once.</li>
 *   <li>The computed value is safely published.</li>
 *   <li>If the supplier throws, the value is not cached.</li>
 *   <li>After initialization, no synchronization or volatile reads occur.</li>
 * </ul>
 */
public final class MemoizingSupplier<T>
        implements Supplier<T>, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private volatile Supplier<T> delegate;
    private T value;

    public MemoizingSupplier(Supplier<T> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public T get() {
        Supplier<T> s = delegate;
        if (s != null) {
            synchronized (this) {
                s = delegate;
                if (s != null) {
                    T t = Objects.requireNonNull(s.get(), "supplier returned null");
                    value = t;
                    delegate = null; // volatile write = safe publication
                    return t;
                }
            }
        }
        return value;
    }
}
```

### 15.3 How to Use It

**Before (mutable, thread-unsafe):**
```java
private transient TypeInfo m_typeinfo;  // Can't be final

public TypeInfo ensureTypeInfo() {
    if (m_typeinfo == null) {
        m_typeinfo = computeTypeInfo();  // Race!
    }
    return m_typeinfo;
}
```

**After (immutable, thread-safe):**
```java
// FINAL field - can never be reassigned
private final Supplier<TypeInfo> m_typeinfo =
    new MemoizingSupplier<>(this::computeTypeInfo);

public TypeInfo ensureTypeInfo() {
    return m_typeinfo.get();  // Thread-safe, computed once
}
```

### 15.4 Why This Is Better

| Aspect | Cache-if-null | MemoizingSupplier |
|--------|---------------|-------------------|
| Thread safety | Race condition | Double-checked locking |
| Field modifier | Must be non-final | Can be final |
| Null handling | May cache null | Rejects null |
| Post-init overhead | Volatile read | Plain field read |

### 15.5 When NOT to Use

- **Copy operations**: `@CopyIgnore` fields should be reset, not memoized
- **Invalidatable caches**: If value needs recomputation
- **Context-dependent values**: If computation depends on mutable context

---

## Part 16: Migration Path

### Phase 1: @CopyIgnore Infrastructure
1. Create `@CopyIgnore` annotation
2. Mark all existing `transient` fields with `@CopyIgnore`
3. Document the semantic difference from Java's `transient` keyword
4. Add NullAway or JSpecify for null analysis

### Phase 2: Fix Clone/Copy
1. Create `Copyable<T>` interface
2. Implement proper `copy()` methods that respect `@CopyIgnore`
3. Deprecate `Cloneable` implementations

### Phase 3: Make Fields Final
1. Audit all fields that can be immediately made final
2. Refactor Token to be fully immutable
3. Add `with*()` methods for "modification"

### Phase 4: Memoized Suppliers
1. Replace eager transient caches with `Suppliers.memoize()`
2. Ensure thread-safety of lazy initialization
3. Benchmark memory and performance

### Phase 5: Null Safety
1. Add JSpecify annotations throughout
2. Replace null returns with Optional
3. Use empty collections instead of null
4. Enable NullAway in build

### Phase 6: Immutable AST (Long-term)
1. Pick one leaf node type as experiment
2. Make it fully immutable with final fields
3. Separate compilation state from structure
4. Allow multiple concurrent compilations

---

## Part 17: Raw Object Types and Generic Type Erasure

The codebase makes extensive use of raw `Object` types instead of proper generics, leading to runtime type errors, unchecked casts, and loss of type safety.

### 17.1 The Expression Type Storage Anti-Pattern

The most egregious example is in `Expression.java`:

```java
/**
 * After validation, contains the type(s) of the expression, stored as either a
 * {@code TypeConstant} or a {@code TypeConstant[]}.
 */
private Object m_oType;  // Could be TypeConstant OR TypeConstant[]!

/**
 * After validation, contains the constant value(s) of the expression, iff the expression is a
 * constant, stored as either a {@code Constant} or a {@code Constant[]}.
 */
private Object m_oConst;  // Could be Constant OR Constant[]!
```

**Usage requires `instanceof` checks and unchecked casts:**
```java
public TypeConstant getType() {
    checkValidated();

    if (m_oType instanceof TypeConstant type) {
        return type;
    }

    TypeConstant[] atype = (TypeConstant[]) m_oType;  // Unchecked cast!
    return atype.length == 0 ? null : atype[0];
}
```

**Problems:**
1. **No compile-time type safety** - compiler can't verify correct usage
2. **Runtime ClassCastException risk** - any bug causes runtime failure
3. **Code duplication** - every accessor must check `instanceof`
4. **Semantic confusion** - is `null` allowed? What about empty array?

**Better design using sealed types:**
```java
sealed interface ExpressionTypes permits SingleType, MultipleTypes, VoidType {
    TypeConstant getFirst();
    TypeConstant[] getAll();
}
record SingleType(TypeConstant type) implements ExpressionTypes { ... }
record MultipleTypes(TypeConstant[] types) implements ExpressionTypes { ... }
record VoidType() implements ExpressionTypes { ... }
```

### 17.2 Token Value Object

```java
// Token.java
private final Object m_oValue;  // Can be String, Number, Character, PackedInteger, etc.
```

The Token class stores literal values as `Object`, requiring consumers to know what type to expect based on the token ID:

```java
Token tok = lexer.next();
if (tok.getId() == Id.LIT_STRING) {
    String s = (String) tok.getValue();  // Must cast, could fail
} else if (tok.getId() == Id.LIT_INT) {
    PackedInteger i = (PackedInteger) tok.getValue();  // Must cast
}
```

**Better design using sealed types or generics:**
```java
sealed interface TokenValue permits StringValue, IntValue, CharValue, ... { }
record StringValue(String value) implements TokenValue { }
record IntValue(PackedInteger value) implements TokenValue { }
```

### 17.3 Raw Types in Clone/Copy Logic

The `AstNode.clone()` method uses raw types with unchecked casts:

```java
// AstNode.java clone() method
for (Field field : getChildFields()) {
    Object oVal = field.get(this);  // Raw Object

    if (oVal != null) {
        if (oVal instanceof AstNode node) {
            // ...
        } else if (oVal instanceof List list) {  // Raw List!
            for (AstNode node : (List<AstNode>) list) {  // Unchecked cast!
                listNew.add(node.clone());
            }
        }
    }
}
```

**Problems:**
1. Raw `List` type loses generic parameter
2. Unchecked cast to `List<AstNode>` - could fail at runtime
3. Reflection access bypasses all type checking

### 17.4 Raw Types in Production Code

Examples found throughout the codebase:

```java
// NewExpression.java - raw List
List listCopy = new ArrayList<>(list.size());  // Raw type!
for (AstNode node : list) {
    listCopy.add(node.clone());  // Unchecked add
}
return listCopy;

// ClassStructure.java - raw Map
Map mapThisParams = this.m_mapParams;  // Raw type!
Map mapThatParams = that.m_mapParams;  // Raw type!

// Launcher.java - raw List and Map
List list = (List) oVal;  // Raw type!
Map map = v == null ? new ListMap() : (ListMap) v;  // Raw type!
```A

### 17.5 Pervasive `instanceof` Checks (364 occurrences)

The codebase has **364 `instanceof` pattern matches** across 44 AST files. This is a symptom of:

1. **Lack of proper polymorphism** - checking types instead of calling methods
2. **Object fields** - must determine actual type at runtime
3. **Missing sealed hierarchies** - exhaustive matching not enforced

**Examples:**
```java
// Throughout the codebase
if (typeRequired instanceof UnionTypeConstant typeUnion) { ... }
if (m_constId instanceof TypeConstant constType) { ... }
if (typeLeft instanceof AnnotatedTypeConstant typeAnno) { ... }
if (type instanceof PendingTypeConstant) { ... }
```

### 17.6 The Cost of Type Erasure

| Issue | Frequency | Impact |
|-------|-----------|--------|
| `Object` fields storing typed data | 10+ fields | Runtime type errors |
| Raw `List`/`Map` declarations | 7+ occurrences | Unchecked warnings |
| `instanceof` type checks | 364 occurrences | Code bloat, missed cases |
| Unchecked casts `(Type[])` | 50+ occurrences | ClassCastException risk |
| No `@SuppressWarnings("unchecked")` | 2 occurrences | Hidden compiler warnings |

### 17.7 The Fix: Proper Generic Design

**1. Use sealed interfaces for variant types:**
```java
// Instead of Object that could be T or T[]
sealed interface TypeResult permits Single, Multiple, Void {
    TypeConstant first();
    Stream<TypeConstant> all();
}
```

**2. Use generics consistently:**
```java
// Instead of raw types
List<AstNode> listCopy = new ArrayList<>(list.size());
Map<String, TypeConstant> mapParams = new HashMap<>();
```

**3. Eliminate instanceof with visitor pattern:**
```java
// Instead of instanceof chain
interface TypeConstantVisitor<R> {
    R visit(AnnotatedTypeConstant type);
    R visit(UnionTypeConstant type);
    R visit(ParameterizedTypeConstant type);
    // ...
}
```

**4. Use Optional instead of null for absent values:**
```java
// Instead of
private Object m_oConst;  // null means not constant

// Use
private Optional<ConstantValue> m_constant = Optional.empty();
```

### 17.8 Migration Plan: Raw Object Types

**Step-by-step order to eliminate raw `Object` fields:**

**Phase 1: Define Replacement Types (no code changes yet)**
```java
// Create sealed types for Expression's m_oType
sealed interface ExpressionType permits SingleType, MultiType, VoidType {}
record SingleType(TypeConstant type) implements ExpressionType {}
record MultiType(List<TypeConstant> types) implements ExpressionType {}
record VoidType() implements ExpressionType {}

// Create sealed types for Token's m_oValue
sealed interface TokenValue permits
    StringValue, IntValue, CharValue, DecimalValue, ... {}
```

**Phase 2: Add New Field, Keep Old (parallel operation)**
```java
// In Expression.java
private Object m_oType;                    // Keep temporarily
private ExpressionType m_exprType;         // New field

// Update setters to populate both
protected void setType(TypeConstant type) {
    m_oType = type;                        // Old path
    m_exprType = new SingleType(type);     // New path
}
```

**Phase 3: Migrate Callers One-by-One**
```java
// Before
TypeConstant type = (TypeConstant) expr.m_oType;

// After
TypeConstant type = switch (expr.getExpressionType()) {
    case SingleType(var t) -> t;
    case MultiType(var list) -> list.get(0);
    case VoidType() -> null;
};
```

**Phase 4: Remove Old Field**
- Once all callers migrated, delete `m_oType`
- Rename `m_exprType` to something simpler if desired

**Priority Order:**
1. `Expression.m_oType` - most impactful, 100+ usages
2. `Expression.m_oConst` - similar pattern
3. `Token.m_oValue` - contained to lexer

### 17.9 Migration Plan: Eliminating instanceof Chains

**The 364 `instanceof` checks indicate missing polymorphism. Fix strategy:**

**Phase 1: Identify Clusters**

Group instanceof checks by what they're checking:
```
TypeConstant subtypes:     180 checks
Expression subtypes:        95 checks
Statement subtypes:         45 checks
Constant subtypes:          44 checks
```

**Phase 2: Create Visitor for Most Common**

Start with TypeConstant (180 checks):

```java
public interface TypeConstantVisitor<R> {
    R visitAnnotated(AnnotatedTypeConstant type);
    R visitUnion(UnionTypeConstant type);
    R visitIntersection(IntersectionTypeConstant type);
    R visitParameterized(ParameterizedTypeConstant type);
    R visitTerminal(TerminalTypeConstant type);
    // ... all 25+ subtypes
}

// In TypeConstant base class
public abstract <R> R accept(TypeConstantVisitor<R> visitor);
```

**Phase 3: Migrate instanceof to Visitor Calls**

```java
// Before (scattered instanceof chains)
if (type instanceof UnionTypeConstant union) {
    // handle union
} else if (type instanceof AnnotatedTypeConstant anno) {
    // handle annotated
}

// After (visitor)
Result result = type.accept(new TypeConstantVisitor<Result>() {
    @Override
    public Result visitUnion(UnionTypeConstant union) {
        // handle union
    }
    @Override
    public Result visitAnnotated(AnnotatedTypeConstant anno) {
        // handle annotated
    }
    // compiler enforces all cases handled
});
```

**Phase 4: Consider Sealed Types (Java 17+)**

If sealed types are available:
```java
public sealed interface TypeConstant permits
    AnnotatedTypeConstant,
    UnionTypeConstant,
    IntersectionTypeConstant,
    ParameterizedTypeConstant,
    TerminalTypeConstant { }

// Pattern matching becomes exhaustive
Result result = switch (type) {
    case AnnotatedTypeConstant anno -> handleAnnotated(anno);
    case UnionTypeConstant union -> handleUnion(union);
    // ... compiler enforces all cases
};
```

**Priority Order:**
1. TypeConstant hierarchy (180 checks) - biggest impact
2. Expression hierarchy (95 checks) - validation code
3. Create visitor interfaces first, migrate incrementally

**Benefits After Migration:**
- Compile-time exhaustiveness checking
- No ClassCastException risk
- Easier to add new subtypes (compiler shows all places to update)
- Better IDE navigation

---

## Part 18: Thread Safety Anti-Patterns

The codebase uses multiple inconsistent thread-safety mechanisms that are difficult to reason about and prone to bugs.

### 18.1 The Thread Safety Zoo

The codebase employs at least **5 different synchronization mechanisms**:

| Mechanism | Files | Usage |
|-----------|-------|-------|
| `synchronized` blocks/methods | 11 files | TypeConstant, ConstantPool, Component |
| `ConcurrentHashMap` | 13 files | TypeInfo, Container, ClassComposition |
| `StampedLock` | 2 files | SignatureConstant, ParameterizedTypeConstant |
| `volatile` fields | 10 files | TypeConstant, Component, Runtime |
| No synchronization | Most files | Lexer, Parser, AST nodes |

### 18.2 The `ensureTypeInfo()` Synchronized Method

The most critical synchronized code is the TypeInfo construction:

```java
// TypeConstant.java
private synchronized TypeInfo ensureTypeInfo(TypeInfo info, ErrorListener errs) {
    // ... 150+ lines of code under a single lock ...
    // Calls buildTypeInfo() which can recursively call ensureTypeInfo()
    // Can block all threads trying to get type info for this type
}
```

**Problems:**
1. **Coarse-grained locking** - entire method synchronized
2. **Potential deadlock** - recursive calls to `ensureTypeInfo` on different types
3. **Serializes compilation** - only one thread can build TypeInfo per type
4. **No timeout** - threads block indefinitely

### 18.3 StampedLock for Comparison Caching

```java
// SignatureConstant.java
private final StampedLock m_lockPrev = new StampedLock();
private transient SignatureConstant m_sigPrev;
private transient int m_nCmpPrev;

public int compareTo(SignatureConstant that) {
    // Optimistic read
    long stamp = m_lockPrev.tryOptimisticRead();
    if (stamp != 0) {
        int nCmpPrev = m_nCmpPrev;
        if (that == m_sigPrev && m_lockPrev.validate(stamp)) {
            return nCmpPrev;  // Cache hit
        }
    }

    // Compute result...
    int n = computeComparison(that);

    // Try to cache
    long stamp = m_lockPrev.tryWriteLock();
    if (stamp != 0) {
        m_sigPrev = that;
        m_nCmpPrev = n;
        m_lockPrev.unlockWrite(stamp);
    }
    return n;
}
```

**Issues:**
1. **Complex API** - easy to misuse StampedLock
2. **Cache only one previous value** - limited benefit
3. **Non-final transient fields** - caching mutable state
4. **Lock per instance** - memory overhead

### 18.4 ConcurrentHashMap Caches

```java
// TypeInfo.java
private final ConcurrentHashMap<Object, MethodInfo> f_cacheById;
private final ConcurrentHashMap<Object, MethodInfo> f_cacheByNid;

// ClassComposition.java
private volatile ConcurrentHashMap<String, ObjectHandle> m_mapFields;
```

**Issues:**
1. **ConcurrentHashMap overhead** - significant memory per map
2. **Inconsistent state** - map visible before fully populated
3. **No coordinated updates** - changes to related caches not atomic

### 18.5 Volatile Without Memory Barriers

```java
// TypeConstant.java
private volatile TypeInfo m_typeinfo;

public TypeInfo getTypeInfo() {
    TypeInfo info = m_typeinfo;  // Volatile read
    if (isComplete(info) && isUpToDate(info)) {
        return info;
    }
    return ensureTypeInfo(info, errs);  // May re-read m_typeinfo
}
```

**Problem**: The volatile field provides visibility but not atomicity. Between the read and the `isUpToDate` check, another thread could invalidate the TypeInfo.

### 18.6 No Thread Safety in Lexer/Parser/AST

The Lexer, Parser, and AST nodes have **zero thread safety**:

```java
// Parser.java - mutable fields, no synchronization
private ErrorListener m_errorListener;  // Can be swapped mid-parse!
private Token m_tokenPutBack;
private Token m_token;

// AstNode.java - mutable stage, no synchronization
protected void setStage(Stage stage) {
    if (stage != null && stage.compareTo(m_stage) > 0) {
        m_stage = stage;  // Race condition!
    }
}
```

**For LSP/IDE integration, this is fatal** - background threads parse/validate while UI thread queries.

### 18.7 The Problem: Inconsistent Mental Model

The codebase has no consistent approach to thread safety:

| Component | Assumed Threading Model |
|-----------|------------------------|
| Lexer/Parser | Single-threaded |
| AST nodes | Single-threaded (but cloned for "safety") |
| TypeConstant | Multi-threaded (synchronized methods) |
| TypeInfo | Multi-threaded (ConcurrentHashMap) |
| ConstantPool | Multi-threaded (synchronized + volatile) |
| Runtime | Multi-threaded (complex fiber model) |

This makes it **impossible to reason about correctness**.

### 18.8 The Solution: Immutability

Instead of complex locking, use **immutability** to achieve thread safety:

**Principle**: Immutable objects are inherently thread-safe. No synchronization needed.

**1. Immutable AST Nodes:**
```java
public final class BinaryExpression implements Expression {
    private final Expression left;   // final = immutable reference
    private final Expression right;  // final = immutable reference
    private final Operator op;       // final = immutable reference

    // No setters, no mutation, thread-safe by construction
}
```

**2. Memoized Suppliers for Lazy Values:**
```java
// Thread-safe lazy initialization, field can be final
private final Supplier<TypeInfo> typeInfo = new MemoizingSupplier<>(this::compute);
```

**3. Copy-on-Write for "Modifications":**
```java
public Expression withLeft(Expression newLeft) {
    return new BinaryExpression(newLeft, this.right, this.op);
}
```

**4. Separate Mutable Caches:**
```java
// Immutable AST
public final class MethodDeclaration { ... }

// Mutable compilation context (explicit thread-safety)
public class CompilationCache {
    private final ConcurrentHashMap<MethodDeclaration, TypeInfo> typeCache;
}
```

### 18.9 Benefits of Immutability-First

| Current Approach | Immutable Approach |
|-----------------|-------------------|
| 5+ synchronization mechanisms | Zero synchronization for core objects |
| Complex lock ordering | No deadlocks possible |
| Race conditions in AST | Thread-safe by construction |
| Can't share between compilations | Share freely across threads |
| Clone for "safety" | No copying needed |

### 18.10 Migration Strategy

1. **Phase 1: Identify Immutable Data**
   - Token (can be fully immutable)
   - Parsed AST structure (before validation)
   - Type constants (mostly immutable already)

2. **Phase 2: Separate Mutable State**
   - Move validation state to external context
   - Move caches to dedicated cache objects
   - Use MemoizingSupplier for lazy computation

3. **Phase 3: Remove Locks**
   - As objects become immutable, remove synchronization
   - Replace ConcurrentHashMap with immutable maps where possible
   - Use atomic references for single-value caches

4. **Phase 4: Verify with Testing**
   - Add concurrent tests
   - Use thread sanitizers
   - Profile for lock contention

---

## Part 19: LSP API Requirements for XTC

A modern XTC Language Server Protocol (LSP) implementation would require specific APIs and would expose all the issues documented above.

### 19.1 Required LSP Operations

| LSP Feature | Description | XTC API Requirement |
|-------------|-------------|---------------------|
| `textDocument/didOpen` | File opened | Parse source into AST |
| `textDocument/didChange` | File edited | Incremental re-parse |
| `textDocument/didSave` | File saved | Re-validate |
| `textDocument/completion` | Auto-complete | Query symbols at position |
| `textDocument/hover` | Hover info | Get type at position |
| `textDocument/definition` | Go to definition | Resolve symbol to location |
| `textDocument/references` | Find references | Search all usages |
| `textDocument/documentSymbol` | Outline view | List all symbols in file |
| `textDocument/formatting` | Format document | Pretty-print AST |
| `textDocument/rename` | Rename symbol | Find all, modify all |
| `textDocument/diagnostic` | Show errors | Compile, collect errors |

### 19.2 What Each Operation Needs

**1. Parse and Validate (`didOpen`, `didChange`):**
```
Source text → Lexer → Tokens → Parser → AST → Validator → Typed AST
```
- **Current issue**: One-shot parser, can't incrementally update
- **Requirement**: Incremental parser that updates only changed regions

**2. Position-Based Queries (`hover`, `completion`, `definition`):**
```java
// Need: Map from source position to AST node
interface PositionIndex {
    AstNode nodeAt(int line, int column);
    List<AstNode> nodesContaining(int line, int column);
}
```
- **Current issue**: No position → node mapping; must traverse entire tree
- **Requirement**: Spatial index of AST nodes by source position

**3. Symbol Resolution (`definition`, `references`, `rename`):**
```java
// Need: Map from symbol to declaration and all usages
interface SymbolIndex {
    Declaration declarationOf(Symbol symbol);
    List<Reference> referencesTo(Declaration decl);
    List<Declaration> declarationsIn(SourceFile file);
}
```
- **Current issue**: Symbols resolved during validation, not indexed
- **Requirement**: Persistent symbol table with cross-references

**4. Type Information (`hover`, `completion`):**
```java
// Need: Get type of any expression
interface TypeOracle {
    TypeConstant typeOf(Expression expr);
    List<MethodInfo> methodsOf(TypeConstant type);
    List<PropertyInfo> propertiesOf(TypeConstant type);
}
```
- **Current issue**: Types stored in mutable AST fields after validation
- **Requirement**: Queryable type database

### 19.3 Threading Requirements

An LSP server must handle:

| Scenario | Requirement |
|----------|-------------|
| User types while hover query runs | Concurrent read/write |
| Multiple files open, all need diagnostics | Parallel validation |
| User cancels long operation | Cancelable computation |
| User navigates while compiling | Stale data acceptable |

**Current XTC State:**
- ❌ No concurrent access to AST
- ❌ No incremental parsing
- ❌ No cancelation support
- ❌ No staleness model

### 19.4 Data Structure Requirements

**1. Immutable AST with Versions:**
```java
// Each edit creates a new version
record AstVersion(int version, AstNode root, SourceText source) {}

// Old versions retained for in-flight operations
class AstVersionCache {
    AstVersion current();
    AstVersion version(int v);  // Get historical version
}
```

**2. Incremental Lexer:**
```java
interface IncrementalLexer {
    // Re-lex only the changed region
    TokenStream relexRegion(TokenStream old, TextEdit edit);
}
```

**3. Incremental Parser:**
```java
interface IncrementalParser {
    // Re-parse only affected nodes
    AstNode reparse(AstNode old, TokenStream newTokens, Range changedRange);
}
```

**4. Persistent Indexes:**
```java
interface SemanticIndex {
    // Survives across edits, updated incrementally
    void update(AstVersion oldAst, AstVersion newAst);

    // Fast queries
    Declaration declarationAt(Position pos);
    List<Reference> referencesTo(Declaration decl);
    TypeConstant typeAt(Position pos);
}
```

### 19.5 Specific API Gaps

| Required API | Current State | Gap |
|--------------|---------------|-----|
| `Source.withEdit(edit)` | Source is mutable | Need immutable Source |
| `Lexer.relexFrom(position)` | Full re-lex only | Need incremental |
| `Parser.reparseNode(node)` | Full re-parse only | Need incremental |
| `AstNode.nodeAt(position)` | Not implemented | Need position index |
| `TypeConstant.methodsAccessibleAt(access)` | TypeInfo only | Need filtered view |
| `SymbolTable.referencesTo(symbol)` | Not tracked | Need reference tracking |

### 19.6 Minimum Viable LSP

To implement even basic LSP features:

**Must Have:**
1. Thread-safe AST (immutable or properly synchronized)
2. Position → Node mapping
3. Symbol → Declaration mapping
4. Type information queryable without mutation

**Should Have:**
1. Incremental parsing (for responsiveness)
2. Cancelable operations
3. Staleness tolerance

**Nice to Have:**
1. Incremental type checking
2. Error recovery parsing
3. Partial type information for incomplete code

### 19.7 Recommended Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     LSP Server                               │
├─────────────────────────────────────────────────────────────┤
│  Request Handler (async, cancelable)                         │
├─────────────────────────────────────────────────────────────┤
│  Document Manager                                            │
│  ├── VersionedDocument (immutable snapshots)                │
│  ├── EditQueue (pending changes)                            │
│  └── ValidationScheduler (background)                       │
├─────────────────────────────────────────────────────────────┤
│  Semantic Model                                              │
│  ├── ImmutableAST (copy-on-write)                           │
│  ├── TypeDatabase (queryable, cached)                       │
│  ├── SymbolIndex (persistent, incremental)                  │
│  └── DiagnosticStore (per-version)                          │
├─────────────────────────────────────────────────────────────┤
│  Compiler Core                                               │
│  ├── IncrementalLexer                                       │
│  ├── IncrementalParser                                      │
│  ├── IncrementalValidator                                   │
│  └── TypeResolver (thread-safe)                             │
└─────────────────────────────────────────────────────────────┘
```

### 19.8 Impact on Codebase

Implementing proper LSP support requires:

| Change | Scope | Difficulty |
|--------|-------|------------|
| Immutable AST | ~80 node classes | High |
| Position indexing | New subsystem | Medium |
| Symbol tracking | New subsystem | Medium |
| Incremental lexer | Lexer rewrite | High |
| Incremental parser | Parser rewrite | Very High |
| Thread-safe TypeInfo | TypeConstant changes | Medium |

**Estimated effort**: Major refactoring, likely 6+ months of focused work.

**Alternative**: Build LSP layer on top of existing compiler with:
- Full re-parse on every change (slow but works)
- Read-only snapshots (clone before query)
- Single-threaded request handling (limits responsiveness)

This would provide basic functionality but poor user experience for large files.

---

## Part 20: Error Handling Chaos

The codebase has no consistent error handling strategy, making it extremely difficult to reliably collect, report, or recover from errors. This is fatal for LSP implementations which must gracefully handle incomplete or erroneous code.

### 20.1 The Error Handling Zoo

The codebase uses at least **5 different error communication mechanisms**:

| Mechanism | When Used | Problem |
|-----------|-----------|---------|
| `return null` | ~357 occurrences in compiler | Caller must check; null has multiple meanings |
| `ErrorListener.log()` | 687 occurrences | Side-effect based; listener can be swapped |
| `throw RuntimeException` | 1276 occurrences | Unchecked; can abort anywhere |
| `ErrorListener.BLACKHOLE` | 20+ usages | Silently swallows errors |
| Boolean return (success/fail) | Various | No error details; must check listener |

### 20.2 Null Returns: The Silent Failure

Throughout the codebase, `null` is returned to indicate errors:

```java
// From Expression.java - null means error
protected TypeConstant inferTypeFromRequired(TypeConstant typeActual, TypeConstant typeRequired) {
    // ... complex logic ...
    return null;  // What went wrong? No information!
}

// From SwitchStatement.java
if (...) {
    return null; // an error must've been reported
}
```

**Problems:**
1. **No error information** - just `null`, no reason
2. **Relies on side effects** - assumes ErrorListener was called
3. **Comment says "must've"** - not certain, could be a bug
4. **Caller must check** - easy to forget

### 20.3 ErrorListener Swapping Mid-Operation

The `ErrorListener` is mutable and can be swapped during operations:

```java
// From Parser.java - swaps error listener mid-operation!
public String parseModuleNameIgnoreEverythingElse() {
    ErrorListener errsPrev = m_errorListener;
    try {
        m_errorListener = ErrorListener.BLACKHOLE;  // Swallow all errors!

        Loop: while (!eof()) {
            if (match(Id.MODULE) != null) {
                m_errorListener = new ErrorList(1);  // Now collect again!
                List<Token> tokens = parseQualifiedName();
                if (!m_errorListener.hasSeriousErrors()) {
                    // ...
                }
            }
        }
    } catch (RuntimeException ignore) {  // Swallow exceptions too!
    } finally {
        m_errorListener = errsPrev;  // Restore
    }
    return null;  // And return null on failure
}
```

**Problems:**
1. **Thread-unsafe** - what if another thread is using the parser?
2. **Lost errors** - BLACKHOLE discards everything
3. **Exception swallowing** - `catch (RuntimeException ignore)` hides bugs
4. **Multiple error channels** - side-effect logging AND return value

### 20.4 The BLACKHOLE Pattern

`ErrorListener.BLACKHOLE` is used in 20+ places to intentionally discard errors:

```java
// "Speculative" operations that swallow errors
Argument arg = resolveRawArgument(ctx, false, ErrorListener.BLACKHOLE);
TypeConstant type = getImplicitType(ctx, null, ErrorListener.BLACKHOLE);
if (!ensurePrepared(ErrorListener.BLACKHOLE)) { ... }
```

**This pattern is used when:**
1. Trying something that might fail
2. Checking if something is valid without reporting
3. Extracting partial information from invalid code

**Problems for LSP:**
- LSP needs ALL errors, even from "speculative" paths
- Can't show "as-you-type" errors if they're swallowed
- No way to know what went wrong when null is returned

### 20.5 RuntimeException Everywhere (1276 occurrences)

Almost all exceptions in the codebase are unchecked `RuntimeException`:

```java
// IllegalStateException for programming errors
throw new IllegalStateException("class=" + this.getClass().getSimpleName(), e);

// IllegalArgumentException for bad inputs
throw new IllegalArgumentException("unsupported type: " + type);

// Generic RuntimeException
throw new RuntimeException("Internal error");

// CompilerException (extends RuntimeException)
throw new CompilerException("...");
```

**Problems:**
1. **No checked exceptions** - compiler doesn't enforce handling
2. **Can abort anywhere** - no predictable control flow
3. **Mixed concerns** - programming errors vs. user errors vs. system errors
4. **No recovery** - exception = operation aborted

### 20.6 Exception Swallowing

Exceptions are frequently caught and ignored:

```java
// From Parser.java
} catch (RuntimeException ignore) {
}

// From RelOpExpression.java
} catch (RuntimeException ignore) {}

// From UnaryMinusExpression.java
} catch (RuntimeException ignore) {}

// From ArrayAccessExpression.java
} catch (RuntimeException e) {
    // Swallowed, continue with fallback
}
```

**This makes debugging nearly impossible** - errors vanish silently.

### 20.7 No Unified Error Context

There's no way to pass a single error context through a compilation:

```java
// Different methods take ErrorListener differently
Expression validate(Context ctx, TypeConstant type, ErrorListener errs);
boolean validateCondition(ErrorListener errs);
void resolveNames(StageMgr mgr, ErrorListener errs);
TypeConstant ensureTypeInfo(ErrorListener errs);

// Some methods create their own
ErrorListener errs = new ErrorList(1);  // Local listener, may be lost!

// Some methods use BLACKHOLE internally
m_errorListener = ErrorListener.BLACKHOLE;
```

**Problems:**
1. **No transaction boundary** - errors from different phases mix
2. **Lost context** - where did this error originate?
3. **No rollback** - partial state changes on error
4. **Branching complexity** - `errs.branch()` / `errs.merge()` is easy to misuse

### 20.8 Error Severity Controls Execution

Methods may or may not throw depending on error severity:

```java
// From ErrorListener interface
/**
 * @return true to attempt to abort the process that reported the error
 */
boolean log(ErrorInfo err);

// From validation methods
if (errs.hasSeriousErrors()) {
    return null;  // Abort
}
// Continue even with warnings
```

**Problems:**
1. **Inconsistent behavior** - same code path, different outcomes
2. **Hard to test** - must test with different severity thresholds
3. **Unpredictable** - caller doesn't know if method will return or abort

### 20.9 Input Sanitization Hides Errors

Inputs are silently "fixed" instead of reporting errors:

```java
// Silently replace null with empty
public void setArgs(Expression[] args) {
    m_args = args == null ? Expression.NO_EXPRS : args;
}

// Silently clamp values
int index = Math.max(0, Math.min(index, length - 1));

// Silently provide defaults
TypeConstant type = typeRequired != null ? typeRequired : pool().typeObject();
```

**Problems:**
1. **Bugs hidden** - caller passed null but never knows it was wrong
2. **Silent corruption** - index was wrong but silently "fixed"
3. **Defaults mask issues** - required type was missing but defaulted

### 20.10 What LSP Needs: Structured Error Handling

An LSP implementation requires:

**1. Error Result Types:**
```java
// Instead of null + side effects
sealed interface ValidationResult permits Success, Failure {
    boolean isSuccess();
    List<Diagnostic> getDiagnostics();
}

record Success(Expression validated, List<Diagnostic> warnings)
    implements ValidationResult { }

record Failure(List<Diagnostic> errors)
    implements ValidationResult { }
```

**2. Error Accumulator (Not Abort):**
```java
// Always continue, collect all errors
interface DiagnosticCollector {
    void add(Diagnostic d);  // Never aborts
    List<Diagnostic> getAll();
    boolean hasErrors();
}
```

**3. Scoped Error Context:**
```java
// Errors automatically scoped to AST region
try (var scope = diagnostics.scope(node)) {
    // All errors in this block associated with 'node'
    validate(scope);
}
```

**4. No Exception for User Errors:**
```java
// User errors = diagnostics, not exceptions
// Exceptions only for programming errors (bugs)

// BAD: throws on user input error
if (type == null) {
    throw new CompilerException("Missing type");
}

// GOOD: reports diagnostic, continues
if (type == null) {
    diagnostics.error(node, "Missing type");
    return Failure.missingType(node);
}
```

**5. Speculative Parsing with Error Capture:**
```java
// Instead of BLACKHOLE
SpeculativeResult result = speculatively(() -> {
    return parseExpression();
});
// result.value() if successful
// result.diagnostics() even if failed - for error recovery hints
```

### 20.11 The Scale of the Problem

| Pattern | Count | Impact |
|---------|-------|--------|
| `return null` (in compiler) | 357 | Silent failure, no error info |
| `ErrorListener` parameter | 687 | Side-effect based, easy to miss |
| `RuntimeException` throws | 1,276 | Unpredictable aborts |
| `BLACKHOLE` usage | 20+ | Lost diagnostic information |
| `catch (RuntimeException ignore)` | 15+ | Hidden bugs |
| `hasSeriousErrors()` checks | 62 | Inconsistent abort behavior |

### 20.12 Recommended Error Handling Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   Error Handling Layer                       │
├─────────────────────────────────────────────────────────────┤
│  DiagnosticCollector                                         │
│  ├── Never aborts (always collects)                         │
│  ├── Scoped to AST nodes                                    │
│  ├── Severity levels (error, warning, info, hint)           │
│  └── Source locations with ranges                           │
├─────────────────────────────────────────────────────────────┤
│  Result Types                                                │
│  ├── ValidationResult<T>  (success + warnings OR failure)   │
│  ├── ResolutionResult     (resolved OR unresolved + why)    │
│  ├── ParseResult          (AST + all errors)                │
│  └── TypeCheckResult      (typed OR errors)                 │
├─────────────────────────────────────────────────────────────┤
│  Exception Policy                                            │
│  ├── Never throw for user errors                            │
│  ├── IllegalStateException for bugs only                    │
│  ├── No exception swallowing                                │
│  └── All exceptions logged with context                     │
├─────────────────────────────────────────────────────────────┤
│  Speculative Execution                                       │
│  ├── Try alternative parses                                 │
│  ├── Capture all diagnostics (don't discard)                │
│  └── Choose best result, merge diagnostics                  │
└─────────────────────────────────────────────────────────────┘
```

### 20.13 Migration Strategy for Error Handling

**Phase 1: Stop the Bleeding**
1. Replace `catch (RuntimeException ignore)` with logging
2. Replace BLACKHOLE with diagnostic-capturing listeners
3. Add `@NonNull` / `@Nullable` annotations to document contracts

**Phase 2: Result Types**
1. Introduce `ValidationResult` sealed types
2. Migrate `validate()` methods one-by-one
3. Remove null returns where result types exist

**Phase 3: Unified Diagnostic Collection**
1. Create `DiagnosticCollector` that never aborts
2. Replace `ErrorListener` parameter passing
3. Scope diagnostics to AST nodes automatically

**Phase 4: Exception Discipline**
1. Create `UserError` diagnostic vs `BugError` exception distinction
2. Remove RuntimeException throws for user input errors
3. Add context to all remaining exceptions

---

## Part 21: The Practical Solution - LSP Adapter Layer

Rather than rewriting the compiler, we can build an **adapter layer** that extracts data from the existing structures and transforms it into clean, LSP-friendly representations.

### 21.1 The Core Insight

The existing compiler is **correct** - it successfully compiles XTC code. The problems are about structure, not correctness. **Solution**: Let the compiler do its job, then **snapshot** the results into proper data structures.

```
┌─────────────────────────────────────────────────────────────┐
│  EXISTING COMPILER (unchanged)                               │
│  ├── Lexer → Token stream                                   │
│  ├── Parser → Mutable AST                                   │
│  └── Validator → Types resolved                             │
├─────────────────────────────────────────────────────────────┤
│  ADAPTER LAYER (new)                                         │
│  ├── Snapshot tokens → ImmutableTokenList                   │
│  ├── Snapshot AST → ImmutableAst                            │
│  ├── Snapshot types → TypeDatabase                          │
│  └── Build indexes → SymbolIndex, PositionIndex             │
├─────────────────────────────────────────────────────────────┤
│  LSP Server (uses only immutable data)                       │
└─────────────────────────────────────────────────────────────┘
```

### 21.2 Immutable Token Extraction

```java
// Clean immutable version for LSP
public record LspToken(
    int startLine, int startColumn,
    int endLine, int endColumn,
    TokenKind kind,
    String text,
    @Nullable Object value
) {
    public static LspToken from(Token token, Source source) {
        long start = token.getStartPosition();
        long end = token.getEndPosition();
        return new LspToken(
            Source.calculateLine(start),
            Source.calculateOffset(start),
            Source.calculateLine(end),
            Source.calculateOffset(end),
            mapTokenKind(token.getId()),
            token.getValueText(),
            token.getValue()
        );
    }
}

// Extract all tokens after lexing
public record LspTokenList(List<LspToken> tokens) {
    public static LspTokenList extract(Source source) {
        Lexer lexer = new Lexer(source, new ErrorList(100));
        List<LspToken> list = new ArrayList<>();
        while (lexer.hasNext()) {
            list.add(LspToken.from(lexer.next(), source));
        }
        return new LspTokenList(List.copyOf(list));  // Immutable!
    }
}
```

### 21.3 Immutable AST Extraction

```java
// Sealed hierarchy for exhaustive matching
public sealed interface LspNode permits LspExpr, LspStmt, LspDecl {
    SourceRange range();
    List<LspNode> children();
}

public record LspBinaryExpr(
    SourceRange range,
    LspExpr left,
    Operator op,
    LspExpr right,
    @Nullable TypeRef type
) implements LspExpr {

    public static LspBinaryExpr from(BiExpression expr, TypeExtractor types) {
        return new LspBinaryExpr(
            SourceRange.from(expr),
            LspExpr.from(expr.getExpression1(), types),
            mapOp(expr.getOperator()),
            LspExpr.from(expr.getExpression2(), types),
            types.typeOf(expr)
        );
    }
}
```

### 21.4 Symbol Index for Navigation

```java
public class LspSymbolIndex {
    private final Map<SymbolId, SourceLocation> declarations;
    private final Map<SymbolId, List<SourceLocation>> references;
    private final IntervalTree<SourceLocation, SymbolId> positionIndex;

    // LSP: textDocument/definition
    public Optional<SourceLocation> declarationOf(SymbolId symbol) {
        return Optional.ofNullable(declarations.get(symbol));
    }

    // LSP: textDocument/references
    public List<SourceLocation> referencesTo(SymbolId symbol) {
        return references.getOrDefault(symbol, List.of());
    }

    // LSP: Find symbol at cursor position
    public Optional<SymbolId> symbolAt(int line, int column) {
        return positionIndex.query(line, column);
    }
}
```

### 21.5 Complete Document Snapshot

```java
public record LspDocumentSnapshot(
    URI uri,
    int version,
    String content,
    LspTokenList tokens,
    LspNode ast,
    LspTypeDatabase types,
    LspSymbolIndex symbols,
    LspDiagnosticList diagnostics
) {
    public static LspDocumentSnapshot compile(URI uri, String content, int version) {
        // Run existing compiler
        Source source = new Source(uri.getPath(), content);
        ErrorList errors = new ErrorList(1000);
        Parser parser = new Parser(source, errors);
        StatementBlock astRoot = parser.parseSource();
        // ... validation ...

        // Extract into clean structures
        return new LspDocumentSnapshot(
            uri, version, content,
            LspTokenList.extract(source),
            LspNode.extract(astRoot),
            LspTypeDatabase.extract(pool),
            LspSymbolIndex.extract(astRoot),
            LspDiagnosticList.extract(errors)
        );
    }
}
```

### 21.6 Using Snapshots for LSP

```java
public class XtcLanguageServer {
    private final Map<URI, LspDocumentSnapshot> docs = new ConcurrentHashMap<>();

    public Hover hover(HoverParams params) {
        LspDocumentSnapshot doc = docs.get(params.getUri());
        return doc.symbols()
            .symbolAt(params.getLine(), params.getColumn())
            .flatMap(s -> doc.types().typeOf(s))
            .map(type -> new Hover(type.toString()))
            .orElse(null);
    }

    public void didChange(DidChangeParams params) {
        // Recompile in background, replace snapshot
        CompletableFuture.runAsync(() -> {
            LspDocumentSnapshot snapshot = LspDocumentSnapshot.compile(...);
            docs.put(params.getUri(), snapshot);
        });
    }
}
```

### 21.7 Benefits

| Aspect | Direct Fix | Adapter Layer |
|--------|------------|---------------|
| Development time | 6+ months | 2-4 weeks |
| Risk | High | Low (parallel code) |
| Compiler changes | Extensive | None |
| Thread safety | Must audit all | Built-in by design |

---

## Part 22: Code Review Red Flags

If this codebase were submitted for code review by a modern Java development team, these issues would be immediately flagged.

### 22.1 Critical: Using Cloneable

```java
// REJECTED: Cloneable is a known anti-pattern since Effective Java (2001)
public class Token implements Cloneable { ... }
public class AstNode implements Cloneable { ... }
```

**Every senior Java developer knows**: Don't use `Cloneable`. Use copy constructors or factory methods.

### 22.2 Critical: Reflection-Based Child Traversal

```java
// REJECTED: Reflection is slow, fragile, anAdd bypasses type safety
protected Field[] getChildFields() {
    return CHILD_FIELDS;
}
private static final Field[] CHILD_FIELDS = fieldsForNames(
    SomeNode.class, "expr", "args", "body");
```

**Modern approach**: Visitor pattern or sealed types with exhaustive matching.

### 22.3 Critical: Mutable Fields Without Final

```java
// REJECTED: Non-final fields in data classes
public class Token {
    private long m_lStartPos;    // Should be final
    private Id m_id;             // Should be final
}
```

**Modern approach**: Records, or final fields with builder pattern.

### 22.4 Critical: Object Instead of Generics

```java
// REJECTED: Raw Object loses all type safety
private Object m_oType;   // Could be TypeConstant or TypeConstant[]
private Object m_oConst;  // Could be Constant or Constant[]
private final Object m_oValue;  // Could be String, Number, etc.
```

**Modern approach**: Sealed interfaces or union types.

```java
// APPROVED
sealed interface ExprType permits Single, Multiple, Void {}
record Single(TypeConstant type) implements ExprType {}
```

### 22.5 Critical: Null Return for Errors

```java
// REJECTED: Null is not an error channel
protected Expression validate(...) {
    if (error) {
        return null;  // What error? No information!
    }
}
```

**Modern approach**: Result types or Optional.

```java
// APPROVED
sealed interface ValidationResult permits Success, Failure {}
record Success(Expression expr) implements ValidationResult {}
record Failure(List<Diagnostic> errors) implements ValidationResult {}
```

### 22.6 Critical: Mutable Error Listener

```java
// REJECTED: Swapping error handlers is thread-unsafe and confusing
ErrorListener errsPrev = m_errorListener;
m_errorListener = ErrorListener.BLACKHOLE;  // Silently discard!
// ... do stuff ...
m_errorListener = errsPrev;
```

**Modern approach**: Immutable error collector passed through call chain.

### 22.7 Critical: Exception Swallowing

```java
// REJECTED: Never catch and ignore exceptions
} catch (RuntimeException ignore) {
}
```

**Modern approach**: Handle, rethrow, or at minimum log.

### 22.8 Critical: Hungarian Notation

```java
// REJECTED: Hungarian notation was deprecated in the 1990s
private int m_cRefs;        // "c" for count
private long m_lStartPos;   // "l" for long
private Object m_oValue;    // "o" for object
private boolean m_fDone;    // "f" for flag
private String[] m_asNames; // "as" for array of strings
```

**Modern approach**: Descriptive names without type prefixes.

```java
// APPROVED
private int referenceCount;
private long startPosition;
private Object value;
private boolean done;
private String[] names;
```

### 22.9 Major: Raw Collections

```java
// REJECTED: Raw types lose generic information
List listCopy = new ArrayList<>(list.size());
Map mapParams = this.m_mapParams;
```

**Modern approach**: Always specify type parameters.

### 22.10 Major: Package-Cycle Dependencies

The compiler has circular dependencies between packages:
- `org.xvm.compiler` ↔ `org.xvm.asm`
- `org.xvm.compiler.ast` ↔ `org.xvm.asm.constants`

**Modern approach**: Clear dependency direction, interfaces at boundaries.

### 22.11 Major: No Nullability Annotations

```java
// REJECTED: No indication of null contract
public TypeConstant getType() { ... }  // Returns null? Maybe?
public void setArgs(Expression[] args) { ... }  // Accepts null? Who knows!
```

**Modern approach**: `@NonNull` / `@Nullable` annotations everywhere.

### 22.12 Major: God Classes

Some classes have thousands of lines:
- `TypeConstant.java` - 6000+ lines
- `Expression.java` - 3000+ lines
- `InvocationExpression.java` - 3500+ lines

**Modern approach**: Single Responsibility Principle, extract smaller classes.

### 22.13 Moderate: Method Too Long

Many methods exceed 200 lines:
- `ensureTypeInfo()` - 200+ lines
- `validateImpl()` methods - often 300+ lines

**Modern approach**: Methods should be 20-50 lines max, extract helpers.

### 22.14 Moderate: Magic Numbers

```java
// REJECTED: Magic numbers without explanation
ErrorList errs = new ErrorList(1);
ErrorList errs = new ErrorList(1000);
int cReqParams = atypeReqParams == null ? -1 : atypeReqParams.length;
```

**Modern approach**: Named constants with documentation.

### 22.15 Moderate: Boolean Parameters

```java
// REJECTED: Boolean parameters are unclear at call site
resolveRawArgument(ctx, false, errs);  // What is 'false'?
validate(ctx, true, null, errs);       // What is 'true'?
```

**Modern approach**: Enum or builder pattern.

```java
// APPROVED
resolveRawArgument(ctx, ResolutionMode.LAZY, errs);
validate(ctx, ValidationOptions.builder().strict().build(), errs);
```

### 22.16 Summary: Technical Debt Score

If scored on modern Java practices (10 = perfect):

| Category | Score | Issues |
|----------|-------|--------|
| Immutability | 2/10 | Mutable everywhere, no records |
| Type Safety | 3/10 | Raw Object, no sealed types |
| Null Safety | 2/10 | Null returns, no annotations |
| Error Handling | 2/10 | Null/exception mix, swallowing |
| Thread Safety | 3/10 | Mixed mechanisms, races |
| Code Style | 4/10 | Hungarian notation, long methods |
| API Design | 3/10 | Side effects, mutability |
| **Overall** | **2.7/10** | Significant modernization needed |

This isn't a criticism of the developers - much of this code predates modern Java features (records, sealed types, pattern matching). But it explains why building an LSP layer requires careful adaptation rather than direct use.

---

## Part 23: The XTC-Java Bridge Layer

The bridge subprojects define the interface between XTC runtime and Java. Understanding what **must** be arrays (XTC semantics) vs. what **can** be collections (internal Java) is crucial.

### 23.1 Arrays Required by XTC Semantics

**These MUST remain arrays** - XTC language defines them:
- `Array<Element>` - XTC's native array type
- `Tuple` - fixed-arity tuples
- Function parameters/returns with XTC semantics

### 23.2 What Can Be Collections in Java Layer

**Inside the compiler**, these can be collections:

| Current | Can Be Collection | Reason |
|---------|-------------------|--------|
| `TypeConstant[]` returns | `List<TypeConstant>` | Internal API |
| `Expression[]` arguments | `List<Expression>` | Variable length |
| `Token[]` operators | `List<Token>` | Variable length |

### 23.3 Migration Strategy

**Phase 1**: Change internal method signatures from `T[]` to `List<T>`
**Phase 2**: Change field types
**Phase 3**: Use `List.of()` for empty/singleton, `List.copyOf()` for immutable

---

## Part 24: Reflection-Based Traversal Anti-Pattern

### 24.1 The Current Pattern

Every AST node defines reflection-based child fields:

```java
// 58 classes have this pattern!
private static final Field[] CHILD_FIELDS = fieldsForNames(BiExpression.class, "expr1", "expr2");
```

The `clone()` method uses reflection to traverse:

```java
for (Field field : getChildFields()) {
    Object oVal = field.get(this);       // Reflective read
    if (oVal instanceof AstNode node) {
        field.set(that, node.clone());   // Reflective write
    }
}
```

### 24.2 Problems

| Issue | Impact |
|-------|--------|
| Performance | 10-100x slower than direct access |
| Type Safety | String field names, no compile-time check |
| Refactoring | Rename field = silent break |
| Security | `setAccessible(true)` bypasses encapsulation |

### 24.3 The Fix: Abstract `children()` Method

```java
public abstract class AstNode {
    public abstract List<AstNode> children();
}

public class BiExpression extends Expression {
    @Override
    public List<AstNode> children() {
        return List.of(expr1, expr2);  // Direct, type-safe
    }
}
```

### 24.4 Incremental Migration

**Phase 1**: Add default `getChildren()` that falls back to reflection
**Phase 2**: Override in each subclass (58 classes)
**Phase 3**: Remove `CHILD_FIELDS`, `fieldsForNames()`, reflection code

### 24.5 The Immutable Solution

With records, no traversal code needed:

```java
public record BiExpression(Expression left, Token op, Expression right) {
    public List<AstNode> children() { return List.of(left, right); }
}
```

---

## Part 25: Bad vs Good - Complete Reference

This section provides side-by-side comparisons of anti-patterns found in the codebase with their modern Java equivalents.

### 25.1 Clone vs Copy Factory

```java
// ❌ BAD: Java's broken clone mechanism
public class Token implements Cloneable {
    private long m_lStartPos;
    private Id m_id;
    private Object m_oValue;

    @Override
    public Token clone() {
        try {
            return (Token) super.clone();  // Shallow copy, bypasses constructor
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);  // Never happens but must catch
        }
    }
}

// ✅ GOOD: Copy factory method
public final class Token {
    private final long startPos;
    private final Id id;
    private final TokenValue value;

    public static Token copyOf(Token original) {
        return new Token(original.startPos, original.id, original.value);
    }
}
```

### 25.2 Mutable State vs Immutable Record

```java
// ❌ BAD: Mutable fields, no encapsulation
public class BiExpression extends Expression {
    protected Expression expr1;
    protected Token operator;
    protected Expression expr2;
    private transient TypeConstant m_type;  // Cached, mutable

    public void setExpr1(Expression e) { expr1 = e; }
    public void setExpr2(Expression e) { expr2 = e; }
}

// ✅ GOOD: Immutable record with copy-on-write
public record BiExpression(
    Expression left,
    Token operator,
    Expression right
) implements Expression {

    // "Modify" by creating new instance
    public BiExpression withLeft(Expression newLeft) {
        return new BiExpression(newLeft, operator, right);
    }
}
```

### 25.3 Object Field vs Sealed Type

```java
// ❌ BAD: Raw Object loses type information
public class Expression {
    private Object m_oType;  // Could be TypeConstant OR TypeConstant[]!

    public TypeConstant getType() {
        if (m_oType instanceof TypeConstant type) {
            return type;
        }
        return ((TypeConstant[]) m_oType)[0];  // Unchecked cast!
    }
}

// ✅ GOOD: Sealed type hierarchy
public sealed interface ExpressionType permits SingleType, MultiType, VoidType {}
public record SingleType(TypeConstant type) implements ExpressionType {}
public record MultiType(List<TypeConstant> types) implements ExpressionType {}
public record VoidType() implements ExpressionType {}

public class Expression {
    private ExpressionType type;

    public TypeConstant getType() {
        return switch (type) {
            case SingleType(var t) -> t;
            case MultiType(var list) -> list.getFirst();
            case VoidType() -> null;
        };
    }
}
```

### 25.4 Null Return vs Optional

```java
// ❌ BAD: Null for "not found" or "error"
public TypeConstant resolveType(String name) {
    // ... search logic ...
    return null;  // Not found? Error? Caller can't tell
}

// Usage requires null check that's easy to forget
TypeConstant type = resolveType(name);
type.getName();  // NPE if not found!

// ✅ GOOD: Optional communicates absence
public Optional<TypeConstant> resolveType(String name) {
    // ... search logic ...
    return Optional.empty();  // Clearly: not found
}

// Usage forces handling
resolveType(name)
    .map(TypeConstant::getName)
    .orElse("unknown");
```

### 25.5 null/empty/one/N Pattern vs Collections

```java
// ❌ BAD: Repeated null/empty/one/N checks
public void process(Expression[] args) {
    if (args == null || args.length == 0) {
        // handle no args
    } else if (args.length == 1) {
        process(args[0]);
    } else {
        for (Expression arg : args) {
            process(arg);
        }
    }
}

// ✅ GOOD: Collections handle all cases uniformly
public void process(List<Expression> args) {
    // Empty list = no args (no null check needed)
    // List.of(x) = one arg
    // List.of(x, y, z) = N args
    args.forEach(this::processSingle);
}
```

### 25.6 Reflection Traversal vs Abstract Method

```java
// ❌ BAD: Reflection-based child access
public class BiExpression extends Expression {
    private static final Field[] CHILD_FIELDS =
        fieldsForNames(BiExpression.class, "expr1", "expr2");

    @Override
    protected Field[] getChildFields() {
        return CHILD_FIELDS;  // Reflection, string names, slow
    }
}

// In clone():
for (Field field : getChildFields()) {
    Object val = field.get(this);  // Reflective read
    field.set(that, ((AstNode)val).clone());  // Reflective write
}

// ✅ GOOD: Direct method override
public class BiExpression extends Expression {
    @Override
    public List<AstNode> children() {
        return List.of(expr1, expr2);  // Direct, type-safe, fast
    }

    @Override
    public BiExpression copy() {
        return new BiExpression(expr1.copy(), operator, expr2.copy());
    }
}
```

### 25.7 instanceof Chain vs Visitor/Sealed

```java
// ❌ BAD: Long instanceof chain
public String describe(TypeConstant type) {
    if (type instanceof UnionTypeConstant u) {
        return "union of " + u.getTypes();
    } else if (type instanceof AnnotatedTypeConstant a) {
        return "annotated " + a.getAnnotation();
    } else if (type instanceof ParameterizedTypeConstant p) {
        return "parameterized " + p.getParams();
    }
    // ... 20 more cases, easy to miss one
    return "unknown";
}

// ✅ GOOD: Sealed type with exhaustive switch
public sealed interface TypeConstant permits
    UnionTypeConstant, AnnotatedTypeConstant, ParameterizedTypeConstant, ... {}

public String describe(TypeConstant type) {
    return switch (type) {
        case UnionTypeConstant u -> "union of " + u.types();
        case AnnotatedTypeConstant a -> "annotated " + a.annotation();
        case ParameterizedTypeConstant p -> "parameterized " + p.params();
        // Compiler error if case missing!
    };
}
```

### 25.8 Synchronized Method vs Immutable + MemoizingSupplier

```java
// ❌ BAD: Coarse-grained synchronization
public class TypeConstant {
    private TypeInfo m_typeinfo;

    public synchronized TypeInfo ensureTypeInfo() {
        if (m_typeinfo == null) {
            m_typeinfo = buildTypeInfo();  // 200 lines, holds lock
        }
        return m_typeinfo;
    }
}

// ✅ GOOD: Immutable with lazy initialization
public final class TypeConstant {
    private final Supplier<TypeInfo> typeInfo;

    public TypeConstant(...) {
        this.typeInfo = new MemoizingSupplier<>(() -> buildTypeInfo());
    }

    public TypeInfo getTypeInfo() {
        return typeInfo.get();  // Thread-safe, no explicit lock
    }
}
```

### 25.9 ErrorListener Side Effect vs Result Type

```java
// ❌ BAD: Side-effect error reporting
public Expression validate(Context ctx, ErrorListener errs) {
    if (error) {
        errs.log(new ErrorInfo(...));  // Side effect
        return null;  // Null = failure? Maybe?
    }
    return this;  // Success? Or just no error logged?
}

// ✅ GOOD: Result type communicates outcome
public sealed interface ValidationResult permits Valid, Invalid {}
public record Valid(Expression expr, List<Warning> warnings) implements ValidationResult {}
public record Invalid(List<Diagnostic> errors) implements ValidationResult {}

public ValidationResult validate(Context ctx) {
    if (error) {
        return new Invalid(List.of(new Diagnostic(...)));
    }
    return new Valid(this, List.of());
}
```

### 25.10 Exception Swallowing vs Proper Handling

```java
// ❌ BAD: Swallow exception silently
try {
    parseExpression();
} catch (RuntimeException ignore) {
    // Bug? IO error? Who knows!
}

// ✅ GOOD: Handle or propagate with context
try {
    parseExpression();
} catch (ParseException e) {
    return ParseResult.failure(e.getDiagnostic());
} catch (RuntimeException e) {
    logger.error("Unexpected error during parsing", e);
    throw new CompilerBugException("Parsing failed unexpectedly", e);
}
```

### 25.11 Mutable ErrorListener vs Immutable Context

```java
// ❌ BAD: Mutable error listener, swapped mid-operation
public class Parser {
    private ErrorListener m_errorListener;

    public void parse() {
        ErrorListener prev = m_errorListener;
        m_errorListener = BLACKHOLE;  // Discard errors!
        try { ... }
        finally { m_errorListener = prev; }
    }
}

// ✅ GOOD: Immutable context passed through
public record ParseContext(
    Source source,
    DiagnosticCollector diagnostics,  // Append-only
    CancellationToken cancellation
) {
    public ParseContext withDiagnostics(DiagnosticCollector d) {
        return new ParseContext(source, d, cancellation);
    }
}

public ParseResult parse(ParseContext ctx) {
    // ctx is immutable, thread-safe
}
```

### 25.12 Hungarian Notation vs Descriptive Names

```java
// ❌ BAD: Hungarian notation (deprecated since 1990s)
private int m_cRefs;           // "c" = count
private long m_lStartPos;      // "l" = long
private Object m_oValue;       // "o" = object
private boolean m_fDone;       // "f" = flag
private String[] m_asNames;    // "as" = array of strings
private transient int m_iPos;  // "i" = int, "m_" = member

// ✅ GOOD: Clear, descriptive names
private int referenceCount;
private long startPosition;
private TokenValue value;
private boolean done;
private List<String> names;
private int position;  // @CopyIgnore for transient semantics
```

### 25.13 Raw Array vs Typed Collection

```java
// ❌ BAD: Raw arrays with null checks
public class MethodDeclaration {
    private Expression[] args;

    public void setArgs(Expression[] args) {
        this.args = args == null ? NO_EXPRS : args;
    }

    public void process() {
        if (args != null && args.length > 0) {
            for (Expression arg : args) { ... }
        }
    }
}

// ✅ GOOD: Typed collection, no nulls
public class MethodDeclaration {
    private final List<Expression> args;

    public MethodDeclaration(List<Expression> args) {
        this.args = List.copyOf(args);  // Defensive copy, immutable
    }

    public void process() {
        args.forEach(this::processArg);  // Empty list = no-op
    }
}
```

### 25.14 ConcurrentHashMap Cache vs Computed Value

```java
// ❌ BAD: Concurrent cache with complex synchronization
public class TypeInfo {
    private final ConcurrentHashMap<Object, MethodInfo> cache = new ConcurrentHashMap<>();

    public MethodInfo getMethod(Object key) {
        return cache.computeIfAbsent(key, k -> {
            // Complex computation, may call other synchronized methods
            return compute(k);
        });
    }
}

// ✅ GOOD: Precomputed immutable structure
public record TypeInfo(
    Map<MethodId, MethodInfo> methods,  // Immutable map
    Map<PropertyId, PropertyInfo> properties
) {
    public Optional<MethodInfo> getMethod(MethodId id) {
        return Optional.ofNullable(methods.get(id));
    }

    public static TypeInfo build(TypeConstant type) {
        // Build once, return immutable
        return new TypeInfo(
            buildMethods(type),
            buildProperties(type)
        );
    }
}
```

### 25.15 Summary Table

| Anti-Pattern | Occurrences | Modern Solution |
|--------------|-------------|-----------------|
| `Cloneable` | 9 classes | Copy factories, records |
| `Object` fields | 10+ | Sealed types |
| `return null` | 357 | `Optional`, Result types |
| `transient` misuse | 200+ | `@CopyIgnore` annotation |
| Reflection traversal | 58 classes | Abstract `children()` method |
| `instanceof` chains | 364 | Visitor, sealed types |
| `synchronized` methods | 11 files | Immutable + MemoizingSupplier |
| Exception swallowing | 15+ | Proper handling, Result types |
| Hungarian notation | Everywhere | Descriptive names |
| Raw arrays | 40+ constants | Typed collections |
| Mutable listeners | Throughout | Immutable context |

---

## Summary: The Path Forward

This document has traced a path from specific anti-patterns to a comprehensive understanding of what's needed for modern tooling:

| Part | Topic | LSP Impact |
|------|-------|------------|
| 1-7 | Clone/Copy Anti-Patterns | Can't safely share AST |
| 8-11 | AST Mutability & Parser | Can't query while compiling |
| 12-16 | Null, TypeInfo, Collections | Unreliable APIs |
| 17-20 | Generics, Threading, Errors | Type-unsafe, race conditions |
| 21 | Adapter Layer Solution | Practical path forward |
| 22 | Code Review Red Flags | Why modernization is needed |
| 23-24 | Bridge Layer, Reflection | What must stay, what must go |
| 25 | Bad vs Good Reference | Side-by-side comparisons |

**The Bottom Line**: The existing compiler works. Build an adapter layer that extracts results into clean, immutable structures with proper APIs. This gives us a working LSP server in weeks, not years.

---

## Part 26: The Dual Path Forward

This chapter outlines two parallel tracks that can be pursued simultaneously:

1. **Track A: LSP Adapter Layer** - Build clean new classes that snapshot compiler state, enabling LSP immediately while forming the basis for a future clean compiler
2. **Track B: Codebase Remediation** - Incrementally unwind architectural mistakes in the existing codebase, improving code quality over time

These tracks are complementary: Track A provides immediate value, Track B improves maintainability. Code written for Track A can eventually become the "real" implementation as Track B converges toward it.

---

### 26.1 Track A: The LSP Adapter Layer

#### A.1 Philosophy

Don't fight the existing compiler - **embrace and extract**. The compiler works; it successfully compiles XTC code. Our job is to:
1. Let it do its work
2. Snapshot the results into clean structures
3. Provide proper APIs for tooling

#### A.2 New Package Structure

```
org.xvm.lsp/
├── model/                    # Clean immutable data structures
│   ├── LspToken.java
│   ├── LspExpression.java   (sealed interface)
│   ├── LspStatement.java    (sealed interface)
│   ├── LspDeclaration.java  (sealed interface)
│   ├── LspType.java
│   └── ...
├── semantic/                 # Semantic analysis results
│   ├── SemanticModel.java
│   ├── TypeDatabase.java
│   ├── SymbolIndex.java
│   └── DiagnosticStore.java
├── extract/                  # Extractors from compiler structures
│   ├── TokenExtractor.java
│   ├── AstExtractor.java
│   ├── TypeExtractor.java
│   └── SymbolExtractor.java
├── index/                    # Position-based indexes
│   ├── PositionIndex.java
│   ├── IntervalTree.java
│   └── SourceRange.java
├── document/                 # Document management
│   ├── DocumentSnapshot.java
│   ├── DocumentManager.java
│   └── VersionedDocument.java
└── server/                   # LSP protocol implementation
    ├── XtcLanguageServer.java
    ├── HoverProvider.java
    ├── CompletionProvider.java
    └── DefinitionProvider.java
```

#### A.3 Implementation Order

**Week 1-2: Core Model Classes**

```java
// Step 1: Define immutable token
public record LspToken(
    SourceRange range,
    TokenKind kind,
    String text,
    @Nullable Object value
) {
    public static LspToken from(Token compilerToken, Source source) {
        return new LspToken(
            SourceRange.from(compilerToken, source),
            mapKind(compilerToken.getId()),
            compilerToken.getValueText(),
            compilerToken.getValue()
        );
    }
}

// Step 2: Define source range
public record SourceRange(
    int startLine, int startColumn,
    int endLine, int endColumn
) implements Comparable<SourceRange> {
    public boolean contains(int line, int column) { ... }
    public boolean overlaps(SourceRange other) { ... }
}

// Step 3: Define sealed expression hierarchy
public sealed interface LspExpression extends LspNode permits
    LspBinaryExpr, LspUnaryExpr, LspLiteralExpr,
    LspNameExpr, LspInvokeExpr, LspNewExpr, ... {

    SourceRange range();
    Optional<LspTypeRef> type();  // Resolved type if available
}

public record LspBinaryExpr(
    SourceRange range,
    LspExpression left,
    BinaryOp operator,
    LspExpression right,
    Optional<LspTypeRef> type
) implements LspExpression {}
```

**Week 2-3: Extractors**

```java
// Step 4: Token extractor
public class TokenExtractor {
    public static List<LspToken> extract(Source source) {
        List<LspToken> tokens = new ArrayList<>();
        Lexer lexer = new Lexer(source, ErrorListener.BLACKHOLE);
        while (lexer.hasNext()) {
            tokens.add(LspToken.from(lexer.next(), source));
        }
        return List.copyOf(tokens);
    }
}

// Step 5: AST extractor (visitor pattern)
public class AstExtractor extends AstVisitor<LspNode> {
    private final TypeExtractor typeExtractor;

    @Override
    public LspNode visitBiExpression(BiExpression expr) {
        return new LspBinaryExpr(
            SourceRange.from(expr),
            (LspExpression) visit(expr.getExpression1()),
            mapOperator(expr.getOperator()),
            (LspExpression) visit(expr.getExpression2()),
            typeExtractor.typeOf(expr)
        );
    }

    // ... visit methods for all ~80 AST node types
}

// Step 6: Type extractor
public class TypeExtractor {
    private final Map<AstNode, LspTypeRef> types = new IdentityHashMap<>();

    public void recordType(Expression expr) {
        if (expr.isValidated()) {
            TypeConstant tc = expr.getType();
            if (tc != null) {
                types.put(expr, LspTypeRef.from(tc));
            }
        }
    }

    public Optional<LspTypeRef> typeOf(AstNode node) {
        return Optional.ofNullable(types.get(node));
    }
}
```

**Week 3-4: Document Snapshot**

```java
// Step 7: Complete document snapshot
public record DocumentSnapshot(
    URI uri,
    int version,
    String content,
    List<LspToken> tokens,
    LspNode ast,
    SemanticModel semantics,
    SymbolIndex symbols,
    PositionIndex positions,
    List<LspDiagnostic> diagnostics,
    Instant timestamp
) {
    public static DocumentSnapshot compile(URI uri, String content, int version) {
        // Run existing compiler
        Source source = new Source(uri.getPath(), content);
        ErrorList errors = new ErrorList(1000);
        Parser parser = new Parser(source, errors);
        StatementBlock root = parser.parseSource();

        // Validate if parsing succeeded
        TypeExtractor typeExtractor = new TypeExtractor();
        if (!errors.hasSeriousErrors()) {
            // Run validation (mutates AST, we don't care)
            validate(root, errors, typeExtractor);
        }

        // Extract into clean structures
        List<LspToken> tokens = TokenExtractor.extract(source);
        AstExtractor astExtractor = new AstExtractor(typeExtractor);
        LspNode ast = root != null ? astExtractor.visit(root) : null;

        SymbolIndex symbols = SymbolExtractor.extract(root, typeExtractor);
        PositionIndex positions = PositionIndex.build(ast);

        return new DocumentSnapshot(
            uri, version, content,
            tokens, ast,
            SemanticModel.from(typeExtractor),
            symbols, positions,
            DiagnosticExtractor.extract(errors),
            Instant.now()
        );
    }
}
```

**Week 4-5: LSP Server**

```java
// Step 8: LSP server implementation
public class XtcLanguageServer implements LanguageServer {
    private final Map<URI, DocumentSnapshot> documents = new ConcurrentHashMap<>();
    private final ExecutorService compiler = Executors.newSingleThreadExecutor();

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        var capabilities = new ServerCapabilities();
        capabilities.setHoverProvider(true);
        capabilities.setDefinitionProvider(true);
        capabilities.setReferencesProvider(true);
        capabilities.setDocumentSymbolProvider(true);
        capabilities.setCompletionProvider(new CompletionOptions());
        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        URI uri = params.getTextDocument().getUri();
        String content = params.getTextDocument().getText();
        recompile(uri, content, 1);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        URI uri = params.getTextDocument().getUri();
        String content = params.getContentChanges().get(0).getText();
        int version = params.getTextDocument().getVersion();
        recompile(uri, content, version);
    }

    private void recompile(URI uri, String content, int version) {
        compiler.submit(() -> {
            DocumentSnapshot snapshot = DocumentSnapshot.compile(uri, content, version);
            documents.put(uri, snapshot);
            publishDiagnostics(uri, snapshot.diagnostics());
        });
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        return CompletableFuture.supplyAsync(() -> {
            DocumentSnapshot doc = documents.get(params.getTextDocument().getUri());
            if (doc == null) return null;

            int line = params.getPosition().getLine();
            int col = params.getPosition().getCharacter();

            return doc.positions().nodeAt(line, col)
                .flatMap(node -> node.type())
                .map(type -> new Hover(formatType(type)))
                .orElse(null);
        });
    }
}
```

#### A.4 This Becomes the New Compiler

The key insight: the `org.xvm.lsp.model` classes can eventually become the **real** AST. As Track B progresses:

1. Parser produces `LspNode` directly (not `AstNode`)
2. Validation writes to `SemanticModel` (not `AstNode` fields)
3. Code generation reads `LspNode` + `SemanticModel`
4. Old `AstNode` classes deleted

---

### 26.2 Track B: Codebase Remediation

#### B.1 Philosophy

**Fix things in the right order**. Some changes enable other changes. Don't try to fix everything at once - create a dependency graph and work bottom-up.

#### B.2 The Remediation Dependency Graph

```
┌─────────────────────────────────────────────────────────────┐
│                    PHASE 5: FULL IMMUTABILITY               │
│  - AST nodes become records                                 │
│  - No mutable state anywhere                                │
│  - Delete all clone() methods                               │
└─────────────────────────────────────────────────────────────┘
                              ▲
                              │ requires
┌─────────────────────────────────────────────────────────────┐
│                    PHASE 4: SEMANTIC SEPARATION             │
│  - SemanticModel holds all type/symbol info                 │
│  - AST nodes have no transient fields                       │
│  - Validation is side-effect free                           │
└─────────────────────────────────────────────────────────────┘
                              ▲
                              │ requires
┌─────────────────────────────────────────────────────────────┐
│                    PHASE 3: TYPE SAFETY                     │
│  - Sealed type hierarchies                                  │
│  - Eliminate Object fields                                  │
│  - Visitor pattern for type dispatch                        │
│  - Proper generics everywhere                               │
└─────────────────────────────────────────────────────────────┘
                              ▲
                              │ requires
┌─────────────────────────────────────────────────────────────┐
│                    PHASE 2: ERROR HANDLING                  │
│  - Result types instead of null                             │
│  - DiagnosticCollector instead of ErrorListener             │
│  - No exception swallowing                                  │
│  - Remove BLACKHOLE pattern                                 │
└─────────────────────────────────────────────────────────────┘
                              ▲
                              │ requires
┌─────────────────────────────────────────────────────────────┐
│                    PHASE 1: FOUNDATION                      │
│  - @CopyIgnore annotation                                   │
│  - Nullability annotations                                  │
│  - Arrays → Collections                                     │
│  - Remove reflection-based traversal                        │
└─────────────────────────────────────────────────────────────┘
```

#### B.3 Phase 1: Foundation (Weeks 1-4)

**B.3.1 Add @CopyIgnore Annotation**

```java
// Create the annotation
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface CopyIgnore {
    String reason() default "";
}

// Apply to all transient fields
public class Expression extends AstNode {
    @CopyIgnore(reason = "Computed during validation")
    private Object m_oType;

    @CopyIgnore(reason = "Cached constant value")
    private Object m_oConst;
}
```

**Effort**: 2 days (200+ fields to annotate)

**B.3.2 Add Nullability Annotations**

```java
// Add JSpecify or Checker Framework annotations
public class Expression {
    public @Nullable TypeConstant getType() { ... }

    public void validate(
        @NonNull Context ctx,
        @Nullable TypeConstant typeRequired,
        @NonNull ErrorListener errs
    ) { ... }
}
```

**Effort**: 1 week (requires reviewing every method signature)

**B.3.3 Arrays → Collections**

Priority order:
1. Method return types (most impactful)
2. Method parameters
3. Field types
4. Local variables

```java
// Before
public TypeConstant[] getParamTypes() {
    return m_atypeParams == null ? TypeConstant.NO_TYPES : m_atypeParams;
}

// After
public List<TypeConstant> getParamTypes() {
    return m_paramTypes;  // Never null, use List.of() for empty
}
```

**Effort**: 2 weeks (40+ constants, hundreds of usages)

**B.3.4 Remove Reflection-Based Traversal**

```java
// Step 1: Add abstract method to AstNode
public abstract class AstNode {
    // New: Override in each subclass
    public abstract List<AstNode> getChildren();

    // Deprecated: Keep temporarily for compatibility
    @Deprecated
    protected Field[] getChildFields() { ... }
}

// Step 2: Implement in each subclass (58 classes)
public class BiExpression extends Expression {
    @Override
    public List<AstNode> getChildren() {
        List<AstNode> children = new ArrayList<>(2);
        if (expr1 != null) children.add(expr1);
        if (expr2 != null) children.add(expr2);
        return children;
    }
}

// Step 3: Update clone() to use getChildren()
// Step 4: Delete CHILD_FIELDS and reflection code
```

**Effort**: 1 week (58 classes, straightforward but tedious)

#### B.4 Phase 2: Error Handling (Weeks 5-8)

**B.4.1 Create DiagnosticCollector**

```java
// Immutable, append-only diagnostic collection
public interface DiagnosticCollector {
    void error(AstNode node, String code, String message);
    void warning(AstNode node, String code, String message);
    List<Diagnostic> getDiagnostics();
    boolean hasErrors();

    // Scoped collection for nested operations
    DiagnosticCollector scoped(AstNode scope);
}

public class DefaultDiagnosticCollector implements DiagnosticCollector {
    private final List<Diagnostic> diagnostics = new CopyOnWriteArrayList<>();

    @Override
    public void error(AstNode node, String code, String message) {
        diagnostics.add(new Diagnostic(
            Severity.ERROR, code, message, SourceRange.from(node)));
    }
    // ...
}
```

**B.4.2 Create Result Types**

```java
// Validation result
public sealed interface ValidationResult<T> permits Valid, Invalid {
    boolean isValid();
    Optional<T> getValue();
    List<Diagnostic> getDiagnostics();
}

public record Valid<T>(T value, List<Diagnostic> warnings) implements ValidationResult<T> {
    @Override public boolean isValid() { return true; }
    @Override public Optional<T> getValue() { return Optional.of(value); }
    @Override public List<Diagnostic> getDiagnostics() { return warnings; }
}

public record Invalid<T>(List<Diagnostic> errors) implements ValidationResult<T> {
    @Override public boolean isValid() { return false; }
    @Override public Optional<T> getValue() { return Optional.empty(); }
    @Override public List<Diagnostic> getDiagnostics() { return errors; }
}
```

**B.4.3 Migrate Key Methods**

```java
// Before
public Expression validate(Context ctx, TypeConstant required, ErrorListener errs) {
    if (error) {
        errs.log(...);
        return null;
    }
    return this;
}

// After
public ValidationResult<Expression> validate(Context ctx, @Nullable TypeConstant required) {
    var diagnostics = new DefaultDiagnosticCollector();
    if (error) {
        diagnostics.error(this, "E001", "Something went wrong");
        return new Invalid<>(diagnostics.getDiagnostics());
    }
    return new Valid<>(this, diagnostics.getDiagnostics());
}
```

**Effort**: 3 weeks (687 ErrorListener usages to migrate)

**B.4.4 Remove BLACKHOLE**

Replace every `ErrorListener.BLACKHOLE` with a real collector:

```java
// Before
Argument arg = resolveRawArgument(ctx, false, ErrorListener.BLACKHOLE);

// After (capture diagnostics even for speculative operations)
var specDiagnostics = new DefaultDiagnosticCollector();
var result = resolveRawArgument(ctx, false, specDiagnostics);
// specDiagnostics can be examined if needed
```

**Effort**: 1 week (20+ usages)

#### B.5 Phase 3: Type Safety (Weeks 9-14)

**B.5.1 Create Sealed Type Hierarchies**

Start with TypeConstant (180 instanceof checks):

```java
// Define sealed hierarchy
public sealed interface TypeConstant permits
    TerminalTypeConstant,
    ParameterizedTypeConstant,
    AnnotatedTypeConstant,
    UnionTypeConstant,
    IntersectionTypeConstant,
    FunctionTypeConstant,
    // ... all ~25 subtypes
    { }

// Add visitor
public interface TypeConstantVisitor<R> {
    R visitTerminal(TerminalTypeConstant type);
    R visitParameterized(ParameterizedTypeConstant type);
    R visitAnnotated(AnnotatedTypeConstant type);
    // ...
}

// In TypeConstant
public abstract <R> R accept(TypeConstantVisitor<R> visitor);
```

**B.5.2 Migrate instanceof to Visitor**

```java
// Before (scattered throughout codebase)
if (type instanceof UnionTypeConstant union) {
    // handle union
} else if (type instanceof AnnotatedTypeConstant anno) {
    // handle annotated
} else {
    // forgot a case?
}

// After
type.accept(new TypeConstantVisitor<Void>() {
    @Override public Void visitUnion(UnionTypeConstant union) {
        // handle union
        return null;
    }
    @Override public Void visitAnnotated(AnnotatedTypeConstant anno) {
        // handle annotated
        return null;
    }
    // Compiler enforces all cases
});
```

**Effort**: 4 weeks (180 instanceof → visitor migrations for TypeConstant alone)

**B.5.3 Eliminate Object Fields**

```java
// Create sealed type for Expression.m_oType
public sealed interface ExpressionType permits SingleType, MultiType, VoidType {}
public record SingleType(TypeConstant type) implements ExpressionType {}
public record MultiType(List<TypeConstant> types) implements ExpressionType {}
public record VoidType() implements ExpressionType {}

// Migrate Expression
public class Expression {
    // Old (keep temporarily)
    private Object m_oType;

    // New
    private ExpressionType exprType;

    public TypeConstant getType() {
        return switch (exprType) {
            case SingleType(var t) -> t;
            case MultiType(var list) -> list.isEmpty() ? null : list.get(0);
            case VoidType() -> null;
        };
    }
}
```

**Effort**: 2 weeks (10+ Object fields)

#### B.6 Phase 4: Semantic Separation (Weeks 15-20)

**B.6.1 Create SemanticModel**

```java
public class SemanticModel {
    private final Map<Expression, TypeConstant> types = new IdentityHashMap<>();
    private final Map<Expression, Constant> constants = new IdentityHashMap<>();
    private final Map<NameExpression, Symbol> symbols = new IdentityHashMap<>();
    private final Map<AstNode, Stage> stages = new IdentityHashMap<>();

    public void recordType(Expression expr, TypeConstant type) {
        types.put(expr, type);
    }

    public Optional<TypeConstant> typeOf(Expression expr) {
        return Optional.ofNullable(types.get(expr));
    }

    // ... similar for constants, symbols, stages
}
```

**B.6.2 Pass SemanticModel Through Validation**

```java
// Before
public Expression validate(Context ctx, TypeConstant required, ErrorListener errs) {
    m_oType = computedType;  // Store on node
    return this;
}

// After
public ValidationResult<Expression> validate(
        Context ctx,
        @Nullable TypeConstant required,
        SemanticModel model,
        DiagnosticCollector diagnostics) {

    model.recordType(this, computedType);  // Store in model
    return new Valid<>(this, diagnostics.getDiagnostics());
}
```

**B.6.3 Migrate All Readers**

```java
// Before (in code generator)
TypeConstant type = expr.getType();

// After
TypeConstant type = model.typeOf(expr).orElseThrow();
```

**Effort**: 6 weeks (fundamental change to validation architecture)

**B.6.4 Remove Transient Fields**

Once all code uses SemanticModel, delete the fields:

```java
public class Expression {
    // DELETE these
    // private Object m_oType;
    // private Object m_oConst;
    // private Stage m_stage;
}
```

#### B.7 Phase 5: Full Immutability (Weeks 21-26)

**B.7.1 Convert AST Nodes to Records**

```java
// Before
public class BiExpression extends Expression {
    protected Expression expr1;
    protected Token operator;
    protected Expression expr2;

    public void setExpr1(Expression e) { expr1 = e; }
}

// After
public record BiExpression(
    Expression left,
    Token operator,
    Expression right,
    SourceRange range
) implements Expression {

    public BiExpression withLeft(Expression newLeft) {
        return new BiExpression(newLeft, operator, right, range);
    }
}
```

**B.7.2 Update Parser to Produce Immutable Nodes**

```java
// Before
BiExpression expr = new BiExpression();
expr.setExpr1(left);
expr.setOperator(op);
expr.setExpr2(right);
return expr;

// After
return new BiExpression(left, op, right, computeRange(left, right));
```

**B.7.3 Delete Clone Infrastructure**

- Remove `Cloneable` from all classes
- Delete `clone()` methods
- Delete `CHILD_FIELDS` arrays
- Delete `fieldsForNames()` method
- Delete `getChildFields()` method

**Effort**: 6 weeks (80+ AST node classes)

---

### 26.3 Coordination Between Tracks

#### Weeks 1-4

| Track A (LSP) | Track B (Remediation) |
|---------------|----------------------|
| Define LspToken, SourceRange | Add @CopyIgnore, nullability |
| Build TokenExtractor | Start arrays → collections |
| Begin LspExpression hierarchy | Remove reflection traversal |

#### Weeks 5-8

| Track A (LSP) | Track B (Remediation) |
|---------------|----------------------|
| Complete AST extractors | Create DiagnosticCollector |
| Build SymbolIndex | Create Result types |
| Build PositionIndex | Migrate key validate() methods |

#### Weeks 9-14

| Track A (LSP) | Track B (Remediation) |
|---------------|----------------------|
| Document snapshot complete | Sealed type hierarchies |
| Basic LSP server working | Migrate instanceof → visitor |
| Hover, definition working | Eliminate Object fields |

#### Weeks 15-20

| Track A (LSP) | Track B (Remediation) |
|---------------|----------------------|
| Completion provider | Create SemanticModel |
| References provider | Migrate validation to use model |
| Full LSP feature set | Remove transient fields |

#### Weeks 21-26

| Track A (LSP) | Track B (Remediation) |
|---------------|----------------------|
| Polish and optimization | Convert nodes to records |
| Incremental parsing (if needed) | Update parser |
| Track A becomes primary | Delete clone infrastructure |

---

### 26.4 Convergence: Track A Becomes the Compiler

At the end of both tracks:

```
BEFORE (Current State):
┌─────────────────────────────────────────────────────────────┐
│  Source → Lexer → Parser → Mutable AST → Validation         │
│                             (clone nightmare)               │
│                                   ↓                         │
│                           Code Generation                   │
└─────────────────────────────────────────────────────────────┘

AFTER (Converged State):
┌─────────────────────────────────────────────────────────────┐
│  Source → Lexer → Parser → Immutable AST (LspNode)          │
│                                   │                         │
│                     ┌─────────────┼─────────────┐           │
│                     ↓             ↓             ↓           │
│              SemanticModel   SymbolIndex   PositionIndex    │
│                     │             │             │           │
│                     └─────────────┼─────────────┘           │
│                                   ↓                         │
│                      Code Generation & LSP                  │
└─────────────────────────────────────────────────────────────┘
```

The `org.xvm.lsp.model` classes **become** the compiler's AST. The extractors are deleted because the parser produces clean nodes directly. Track A and Track B converge into a single, modern codebase.

---

### 26.5 Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Introducing regressions | Comprehensive test suite runs after each change |
| Taking too long | Track A provides value immediately; Track B is incremental |
| Divergence between tracks | Weekly sync to ensure Track B moves toward Track A |
| Team resistance | Document benefits clearly; demonstrate LSP value early |
| Incomplete migration | Each phase stands alone; partial progress is still progress |

---

### 26.6 Success Metrics

**Track A Success**:
- [ ] LSP server provides hover info
- [ ] LSP server provides go-to-definition
- [ ] LSP server provides find-references
- [ ] LSP server provides completion
- [ ] Response time < 100ms for all operations

**Track B Success**:
- [ ] Zero `clone()` calls in codebase
- [ ] Zero `transient` fields in AST nodes
- [ ] Zero `Object` fields storing typed data
- [ ] Zero `ErrorListener.BLACKHOLE` usages
- [ ] All AST nodes are records

**Convergence Success**:
- [ ] Parser produces `LspNode` directly
- [ ] No extraction step needed
- [ ] Single set of data structures for compiler and LSP
- [ ] Thread-safe by construction

---

## References

- Bloch, Joshua. *Effective Java*, 3rd Edition. Item 13: "Override clone judiciously"
- Java Language Specification, Chapter 6.4.5: "The Members of an Object"
- Oracle Java Documentation: "Why doesn't Cloneable have a clone() method?"
- Okasaki, Chris. *Purely Functional Data Structures* - persistent data structures
- Language Server Protocol Specification - https://microsoft.github.io/language-server-protocol/
- Guava `Suppliers.memoize()` documentation
- JSpecify - https://jspecify.dev/
- "Java Concurrency in Practice" by Brian Goetz
- Rust Error Handling - https://doc.rust-lang.org/book/ch09-00-error-handling.html
- Red-Green Trees (Roslyn) - https://docs.microsoft.com/en-us/archive/blogs/ericlippert/persistence-facades-and-roslyns-red-green-trees
- Google Java Style Guide - https://google.github.io/styleguide/javaguide.html
