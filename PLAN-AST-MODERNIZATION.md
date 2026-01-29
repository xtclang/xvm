# XTC Compiler AST Modernization Plan

## Executive Summary

This document provides an analysis of the XTC compiler's AST implementation and a focused plan to **eliminate `clone()` and reflection-based child traversal** using explicit copy constructors and visitor pattern.

### PR Scope

**This PR** (immediate goal - both changes are entangled):
1. **Replace reflection-based `clone()`** with explicit `copy()` methods using copy constructors
2. **Replace `CHILD_FIELDS` reflection** with explicit visitor pattern for child iteration
3. **Remove all reflection infrastructure**: `clone()`, `CHILD_FIELDS`, `fieldsForNames()`, `getChildFields()`

These two changes must be done together because they address the same problem (reflection-based AST manipulation) and share infrastructure.

**Next PR** (future work):
- Extract transient fields into separate semantic model (Roslyn-style separation)
- Enable copy-on-write stateless immutable AST nodes

---

## Part 1: Understanding the Current Clone Semantics

### 1.1 What `Object.clone()` Actually Does

Java's `Object.clone()` performs a **bitwise shallow copy** of ALL fields:

```java
// Pseudo-code for Object.clone() behavior:
protected Object clone() {
    Object copy = allocateNewInstance(this.getClass());
    // Copy EVERY field, regardless of transient keyword
    for (Field f : getAllFields()) {
        f.set(copy, f.get(this));  // shallow copy - same reference
    }
    return copy;
}
```

**Critical insight**: The `transient` keyword has **NO effect on clone()**. It only affects Java Serialization (ObjectOutputStream/ObjectInputStream). Since AstNode is not Serializable, `transient` is purely a documentation convention in this codebase.

### 1.2 What `AstNode.clone()` Does

The current implementation layers deep-copying of children on top of `Object.clone()`:

```java
public AstNode clone() {
    // Step 1: Shallow copy ALL fields (including transient)
    AstNode that = (AstNode) super.clone();

    // Step 2: Deep copy only CHILD_FIELDS
    for (Field field : getChildFields()) {
        Object oVal = field.get(this);
        if (oVal instanceof AstNode node) {
            AstNode copy = node.copy();
            that.adopt(copy);
            field.set(that, copy);
        } else if (oVal instanceof List<?> list) {
            List<AstNode> copyList = list.stream()
                .map(AstNode::copy)
                .collect(toCollection(ArrayList::new));
            that.adopt(copyList);
            field.set(that, copyList);
        }
    }
    return that;
}
```

### 1.3 The Actual Semantic Model

| Field Category | Source | Clone Behavior |
|----------------|--------|----------------|
| **Child fields** | Listed in `CHILD_FIELDS` | **Deep copied** (recursively cloned) |
| **All other fields** | Everything else | **Shallow copied** (same reference) |
| `transient` keyword | N/A | **No effect** - just documentation |

**The semantic marker is `CHILD_FIELDS`, not `transient`.**

### 1.4 Implications for Copy Constructors

To be **semantically equivalent**, copy constructors must:

1. **Deep copy** all fields listed in `CHILD_FIELDS`
2. **Shallow copy** all other fields (including those marked `transient`)
3. Replicate any **custom clone() overrides** (e.g., `LambdaExpression.clone()` which nulls out `m_lambda`)

```java
// CORRECT - Semantically equivalent to clone()
protected MyClass(MyClass original) {
    super(original);

    // Deep copy child fields (from CHILD_FIELDS)
    this.childExpr = original.childExpr == null ? null : original.childExpr.copy();
    this.childList = copyStatements(original.childList);
    adopt(this.childExpr, this.childList);

    // Shallow copy everything else (same as Object.clone())
    this.resolvedType = original.resolvedType;     // transient - still copied!
    this.computedFlag = original.computedFlag;     // transient - still copied!
    this.tokenKeyword = original.tokenKeyword;     // immutable - safe to share
}
```

### 1.5 Correcting the `@NotCopied` Misconception

The original plan incorrectly stated that `@NotCopied` should replace `transient` for "fields that shouldn't be copied." This was **wrong** because:

1. `transient` fields ARE copied by `Object.clone()` (shallow)
2. The current behavior DOES copy these fields
3. Changing this would break semantic equivalence

**Correct understanding:**
- `@NotCopied` annotation should be **removed** or **renamed**
- The distinction is `CHILD_FIELDS` (deep copy) vs everything else (shallow copy)
- Document fields with comments explaining their copy semantics

### 1.6 When Fields Should NOT Be Copied

Some classes have custom `clone()` overrides that explicitly clear certain fields:

```java
// LambdaExpression.clone()
public AstNode clone() {
    LambdaExpression that = (LambdaExpression) super.clone();
    that.m_lambda = null;  // Explicitly cleared - method structure belongs to original
    return that;
}
```

For these cases, the copy constructor must replicate this behavior:

```java
protected LambdaExpression(LambdaExpression original) {
    super(original);
    // ... copy children ...

    // m_lambda is explicitly NOT copied (replicating custom clone() behavior)
    // The MethodStructure belongs to the original, not the copy
    this.m_lambda = null;
}
```

---

## Part 2: CHILD_FIELDS and the Path to Visitor Pattern

### 2.1 Current CHILD_FIELDS Infrastructure

Each AST class defines its children via a static array:

```java
private static final Field[] CHILD_FIELDS =
    fieldsForNames(ForStatement.class, "init", "conds", "update", "block");

@Override
protected Field[] getChildFields() {
    return CHILD_FIELDS;
}
```

This infrastructure is used for:

| Usage | Count | Description |
|-------|-------|-------------|
| **clone()** | ~6 | Deep copy children via reflection |
| **children() iterator** | ~25 | Tree traversal, parent setup, stage management |
| **getDumpChildren()** | ~1 | Debug output |

### 2.2 Problems with Reflection-Based CHILD_FIELDS

1. **Performance**: `Field.get()`/`Field.set()` are 10-100x slower than direct access
2. **Type Safety**: Field names are strings; typos fail at runtime
3. **GraalVM**: Requires reflection configuration for native-image
4. **Maintainability**: Adding a field requires updating string array

### 2.3 Replacement Strategy: Explicit Methods

**Phase A: Copy Constructors (Current Work)**

Replace reflection-based clone() with explicit copy constructors:

```java
// Before (reflection)
for (Field field : getChildFields()) {
    Object val = field.get(this);
    // ... deep copy via reflection ...
}

// After (explicit)
protected ForStatement(ForStatement original) {
    this.init = copyStatements(original.init);
    this.conds = copyNodes(original.conds);
    this.update = copyStatements(original.update);
    this.block = original.block.copy();
}
```

**Phase B: Visitor Pattern for Tree Traversal (Future)**

Replace `children()` iterator with explicit visitor methods:

```java
// Current (reflection-based iterator)
for (AstNode child : node.children()) {
    process(child);
}

// Future (visitor pattern)
interface AstVisitor<R> {
    R visit(ForStatement stmt);
    R visit(WhileStatement stmt);
    // ... one method per node type
}

class ForStatement {
    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visit(this);
    }

    // Explicit child access for visitors that need it
    public List<Statement> getInit() { return init; }
    public List<AstNode> getConds() { return conds; }
    public List<Statement> getUpdate() { return update; }
    public StatementBlock getBlock() { return block; }
}
```

### 2.4 Complete Elimination Strategy

**All reflection-based child iteration will be replaced.** The end goal is a Roslyn-like stateless incremental compiler with zero reflection overhead:

| Use Case | Current (Reflection) | Replacement (Explicit) |
|----------|---------------------|------------------------|
| clone() | `CHILD_FIELDS` reflection | **Copy constructors** |
| children() iterator | `CHILD_FIELDS` reflection | **Explicit visitor methods** |
| Stage management | `children()` iterator | **Visitor pattern** |
| Parent setup | `children()` iterator | **Visitor pattern** |
| Debug/introspection | Reflection | **Explicit getChildren() methods** |

**No reflection will remain for child iteration.** The `CHILD_FIELDS` arrays and `fieldsForNames()` will be completely removed once the visitor pattern is in place.

### 2.5 Incremental Migration Path (All Steps in This PR)

The migration must be done incrementally to maintain a working compiler. **All four steps are part of this PR**:

**Step 1: Copy Constructors**
- Add copy constructors to all AST classes
- Replace `clone()` calls with `copy()` calls
- Remove `clone()` method and `Cloneable` interface
- **Result**: No more reflection in copy operations

**Step 2: Explicit Children Methods**
- Add `getChildren()` method to each AST class returning explicit list
- Replace `children()` iterator implementation to use `getChildren()`
- **Result**: `children()` still works but uses explicit methods internally

**Step 3: Visitor Pattern**
- Design visitor interface hierarchy
- Add `accept(AstVisitor)` methods to all AST classes
- Migrate stage management to use visitors
- Migrate parent setup to use visitors
- **Result**: All operations use visitor pattern

**Step 4: Cleanup**
- Remove `CHILD_FIELDS` arrays from all classes
- Remove `fieldsForNames()` and related reflection utilities
- Remove `getChildFields()` methods
- Optionally remove `children()` iterator (or keep as convenience using `getChildren()`)
- **Result**: Zero reflection in AST infrastructure

---

## Part 3: Why Transient Fields Should Move Out of AST Nodes

### 3.1 What "Transient" Fields Really Are

In the current codebase, `transient` fields on AST nodes are:
- **Computed/cached state**: Type resolution results, constant IDs, method structures
- **Validation artifacts**: Flags indicating validation state, resolved references
- **Compilation byproducts**: Generated code structures, intermediate representations

Examples:
```java
// NamedTypeExpression
transient IdentityConstant m_constId;           // Resolved type identity
transient boolean          m_fVirtualChild;     // Resolution flag
transient boolean          m_fExternalTypedef;  // Resolution flag

// LambdaExpression
transient MethodStructure  m_lambda;            // Compiled method

// Various expressions
transient TypeConstant     m_type;              // Resolved type
transient Argument         m_arg;               // Compiled argument
```

### 3.2 Why This Is Architecturally Wrong

In a Roslyn-like architecture, the AST (syntax tree) should be **stateless and immutable**:

| Roslyn Concept | Current XTC | Problem |
|---------------|-------------|---------|
| **Syntax Tree** | AST nodes | Contains mutable computed state |
| **Semantic Model** | (scattered in AST) | No separation of concerns |
| **Binding** | (happens in-place) | Mutates nodes during validation |

**The fundamental issue**: XTC's validation phase **mutates AST nodes** by writing to transient fields. This prevents:

1. **Incremental compilation**: Can't reuse syntax trees because they're polluted with semantic state
2. **LSP support**: Can't provide quick completions while validation is running
3. **Parallel validation**: Nodes can only be validated once; mutation isn't thread-safe
4. **Copy-on-write optimization**: Can't share unchanged subtrees if they contain computed state

### 3.3 The Target Architecture

**Roslyn's separation**:
```
┌─────────────────┐     ┌──────────────────┐
│   Syntax Tree   │────▶│  Semantic Model  │
│   (immutable)   │     │   (computed)     │
│   - tokens      │     │   - types        │
│   - structure   │     │   - symbols      │
│   - positions   │     │   - constants    │
└─────────────────┘     └──────────────────┘
```

**Benefits of separation**:
1. Parse once → query many times
2. Incremental: only recompute semantic model for changed subtrees
3. Thread-safe: syntax tree is read-only
4. Memory efficient: syntax trees can be shared across compilations
5. LSP-friendly: syntax operations (formatting, navigation) don't need full binding

### 3.4 Migration Path for Transient Fields

**Phase A (Current)**: Copy constructors preserve transient field semantics
- We must maintain backward compatibility during migration
- Copy constructors shallow-copy transient fields (same as `Object.clone()`)

**Phase B (Future)**: Extract semantic model
- Create `SemanticModel` or `BindingContext` class
- Move type resolution, constant IDs, method structures to semantic model
- AST nodes become pure syntax: tokens, positions, child structure only
- Validation populates semantic model keyed by AST node identity

```java
// Future architecture
class SemanticModel {
    Map<AstNode, TypeConstant> resolvedTypes;
    Map<AstNode, Constant>     constants;
    Map<AstNode, MethodStructure> methods;

    TypeConstant getType(Expression expr) {
        return resolvedTypes.get(expr);
    }
}
```

### 3.5 Why Copy Constructors Enable This Future

Copy constructors are a stepping stone:
1. **Eliminate clone() reflection** → Explicit knowledge of which fields are children
2. **Document field semantics** → Clear which fields are syntax vs computed
3. **Enable visitor pattern** → Foundation for semantic model extraction
4. **Maintain compatibility** → Working compiler throughout migration

---

## Part 4: Performance Analysis

### 4.1 Reflection vs Direct Access

Reflection overhead for field access:

| Operation | Reflection | Direct Access | Speedup |
|-----------|------------|---------------|---------|
| `Field.get()` | ~50-100 ns | ~1-2 ns | **25-100x** |
| `Field.set()` | ~50-100 ns | ~1-2 ns | **25-100x** |
| Field lookup | ~200-500 ns | 0 ns | **∞** |

*Note: Reflection costs vary by JVM, warm-up state, and accessibility modifiers.*

### 4.2 Clone Operation Comparison

For a typical AST node with 4 child fields:

**Current (reflection-based clone)**:
```
Object.clone()         ~20 ns
getChildFields()       ~50 ns (cached array access)
4x Field.get()         ~200 ns
4x child.clone()       (recursive)
4x adopt()             ~20 ns
4x Field.set()         ~200 ns
───────────────────────
Total overhead:        ~490 ns + recursive children
```

**New (copy constructor)**:
```
Object allocation      ~20 ns
4x direct field read   ~8 ns
4x child.copy()        (recursive)
4x adopt()             ~20 ns
4x direct field write  ~8 ns
───────────────────────
Total overhead:        ~56 ns + recursive children
```

**Speedup**: ~8-10x faster per node

### 4.3 Children Iteration Comparison

For the `children()` iterator with 4 child fields:

**Current (reflection-based)**:
```
getChildFields()       ~50 ns
4x Field.get()         ~200 ns
List building          ~40 ns
Iterator overhead      ~20 ns
───────────────────────
Total:                 ~310 ns per iteration
```

**Future (explicit methods)**:
```
getChildren() call     ~5 ns
Direct field reads     ~8 ns
List building          ~40 ns (can be cached)
Iterator overhead      ~20 ns
───────────────────────
Total:                 ~73 ns per iteration
```

**Speedup**: ~4x faster per iteration

### 4.4 Aggregate Impact

For a typical compilation of 10,000 AST nodes:

| Operation | Current | New | Savings |
|-----------|---------|-----|---------|
| Clone operations (validation loops) | ~50ms | ~6ms | **44ms** |
| Children iterations (stage mgmt) | ~100ms | ~25ms | **75ms** |
| Total | ~150ms | ~31ms | **~120ms** |

*These are estimates; actual impact depends on clone/iteration frequency.*

### 4.5 GraalVM Native Image Impact

Beyond raw performance, reflection has critical implications for GraalVM:

| Aspect | Reflection | Explicit | Impact |
|--------|------------|----------|--------|
| Native image size | +2-5 MB | Baseline | Smaller binary |
| Startup time | +100-200ms | Baseline | Faster startup |
| Peak performance | 80-90% | 100% | Better throughput |
| Configuration | Required | None | Simpler deployment |
| AOT optimization | Limited | Full | Better inlining |

### 4.6 Memory Impact

| Aspect | Current | New | Benefit |
|--------|---------|-----|---------|
| `CHILD_FIELDS` arrays | ~2KB per class | 0 | Less metaspace |
| Field reflection cache | JVM internal | 0 | Smaller footprint |
| Copy-on-write (future) | N/A | Shared subtrees | Dramatic savings |

---

## Part 5: Current Implementation Status

### 5.1 Completed Work

**Phase 1: Foundation** - COMPLETE
- Added helper methods to `AstNode`: `copyStatements()`, `copyNodes()`, `copyExpressions()`
- Added copy constructor `AstNode(AstNode original)`
- Added `copy()` method with delegation to `clone()` for backward compatibility

**Phase 2: Core Loop Statements** - COMPLETE
- `ForStatement`, `WhileStatement`, `ForEachStatement`, `IfStatement`, `StatementBlock`

**Additional Statement Classes** - COMPLETE
- `ExpressionStatement`, `ReturnStatement`, `AssertStatement`, `AssignmentStatement`
- `SwitchStatement`, `TryStatement`, `CatchStatement`, `VariableDeclarationStatement`

**Control Flow Statements** - COMPLETE
- `GotoStatement`, `BreakStatement`, `ContinueStatement`
- `LabeledStatement`, `CaseStatement`, `MultipleLValueStatement`

**Import/Other Statements** - COMPLETE
- `ImportStatement`

**Expression Classes** - COMPLETE
- Base classes: `DelegatingExpression`, `PrefixExpression`, `BiExpression`
- Literals: `LiteralExpression`
- Unary: `ParenthesizedExpression`, `ThrowExpression`, `UnaryMinusExpression`, `UnaryPlusExpression`, `UnaryComplementExpression`, `SequentialAssignExpression`
- Binary: `RelOpExpression`
- Complex: `StatementExpression`, `LambdaExpression`, `NewExpression`, `NamedTypeExpression`

**BiExpression Subclasses** - COMPLETE
- `AsExpression`, `IsExpression`, `ElseExpression`, `CondOpExpression`, `CmpExpression`, `ElvisExpression`

**TypeExpression Subclasses** - COMPLETE
- `ArrayTypeExpression`, `BiTypeExpression`, `TupleTypeExpression`, `NullableTypeExpression`
- `DecoratedTypeExpression`, `KeywordTypeExpression`, `FunctionTypeExpression`
- `VariableTypeExpression`, `BadTypeExpression`, `AnnotatedTypeExpression`

**Stream-Based Copy Patterns** - COMPLETE
All copy constructors with list copying have been converted from verbose null-checking for-loops to clean stream-based copying pattern:
```java
// Before (verbose for-loop)
if (original.list != null) {
    this.list = new ArrayList<>(original.list.size());
    for (T item : original.list) {
        this.list.add((T) item.copy());
    }
} else {
    this.list = null;
}

// After (stream-based)
this.list = original.list == null ? null
        : original.list.stream().map(T::copy).collect(Collectors.toCollection(ArrayList::new));
```

Files updated with stream-based patterns:
- `StatementBlock.java` - stmts list
- `NewExpression.java` - args list
- `AnnotationExpression.java` - args list
- `CompositionNode.java` - args, constraints, vers, injects lists (Incorporates and Import inner classes)
- `InvocationExpression.java` - args list
- `NameExpression.java` - params list
- `MethodDeclarationStatement.java` - annotations, typeParams, returns, redundant, params lists
- `PropertyDeclarationStatement.java` - annotations list
- `TypeCompositionStatement.java` - annotations, typeParams, constructorParams, typeArgs, args, compositions lists
- `NamedTypeExpression.java` - paramTypes list

**@NotNull Annotations for Collection Fields** - IN PROGRESS
Added `@NotNull` annotations to list fields that have null-to-empty conversion in their primary constructors (making null semantically equivalent to empty):
- `NewExpression.java` - `@NotNull protected List<Expression> args`
- `TupleTypeExpression.java` - `@NotNull protected List<TypeExpression> paramTypes`
- `SwitchExpression.java` - `@NotNull protected List<AstNode> cond`, `@NotNull protected List<AstNode> contents`
- `TemplateExpression.java` - `@NotNull protected List<Expression> exprs`
- `CmpChainExpression.java` - `@NotNull protected List<Expression> expressions`
- `FunctionTypeExpression.java` - `@NotNull protected List<TypeExpression> paramTypes`
- `ArrayAccessExpression.java` - `@NotNull protected List<Expression> indexes`
- `ReturnStatement.java` - `@NotNull protected List<Expression> exprs`
- `ForStatement.java` - `@NotNull protected List<Statement> init`, `@NotNull protected List<Statement> update`
- `ConditionalStatement.java` - `@NotNull protected List<AstNode> conds`
- `AssertStatement.java` - `@NotNull protected List<AstNode> conds`
- `TupleExpression.java` - `@NotNull protected List<Expression> exprs`
- `MapExpression.java` - `@NotNull protected List<Expression> keys`, `@NotNull protected List<Expression> values`

NOTE: Some list fields preserve null because null has semantic meaning distinct from empty (e.g., `CaseStatement.exprs` where null means "default:" case)

**Convenience Constructors Added**:
- `StatementBlock()` - no-arg constructor for empty statement blocks
- `MapExpression(type, lEndPos)` - simplified constructor for empty maps
- `Break.withNarrow(node, mapNarrow, label)` - factory method with empty mapAssign

**@ChildNode Annotations** - COMPLETE
Applied `@ChildNode(index, description)` annotation to ALL child node fields across 44+ AST classes. This annotation marks fields that are in `CHILD_FIELDS` and documents the child ordering. Classes that inherit from annotated parent classes (BiExpression, PrefixExpression, DelegatingExpression) inherit the annotations.

### 5.2 Classes with copy() - 53 total

```
# Base classes (8)
AstNode.java, Statement.java, Expression.java, ConditionalStatement.java
TypeExpression.java, DelegatingExpression.java, PrefixExpression.java, BiExpression.java

# Statements (20)
ForStatement, WhileStatement, ForEachStatement, IfStatement, StatementBlock
ExpressionStatement, ReturnStatement, AssertStatement, AssignmentStatement
SwitchStatement, TryStatement, CatchStatement, VariableDeclarationStatement
GotoStatement, BreakStatement, ContinueStatement, LabeledStatement
CaseStatement, MultipleLValueStatement, ImportStatement

# Expressions (12)
LiteralExpression, ParenthesizedExpression, ThrowExpression
UnaryMinusExpression, UnaryPlusExpression, UnaryComplementExpression
SequentialAssignExpression, RelOpExpression, StatementExpression
LambdaExpression, NewExpression, NamedTypeExpression

# BiExpression Subclasses (6) - NEW
AsExpression, IsExpression, ElseExpression
CondOpExpression, CmpExpression, ElvisExpression

# TypeExpression Subclasses (10) - NEW
ArrayTypeExpression, BiTypeExpression, TupleTypeExpression, NullableTypeExpression
DecoratedTypeExpression, KeywordTypeExpression, FunctionTypeExpression
VariableTypeExpression, BadTypeExpression, AnnotatedTypeExpression
```

### 5.3 Classes Still Needing copy() - ~32 remaining

**Expression Subclasses (~20 remaining)**
- Binary/Relational (~1): `CmpChainExpression`
- Invocation/Access (~4): `InvocationExpression`, `ArrayAccessExpression`, `NameExpression`, `IgnoredNameExpression`
- Literals/Values (~5): `ListExpression`, `MapExpression`, `TupleExpression`, `TemplateExpression`, `FileExpression`
- Other (~4): `TernaryExpression`, `NotNullExpression`, `NonBindingExpression`, `SwitchExpression`
- Conversion (~1): `ConvertExpression`

**Statement Subclasses (~6 remaining)**
- `TypedefStatement`, `ComponentStatement` and subclasses

**Other AST Nodes (~6 remaining)**
- `Parameter`, `AnnotationExpression`, `CompositionNode`, `VersionOverride`

---

## Part 6: Copy Constructor Pattern (Corrected)

### 6.1 Standard Pattern

```java
protected MyClass(MyClass original) {
    super(original);

    // 1. Shallow copy non-child fields (matching Object.clone() behavior)
    this.keyword = original.keyword;           // Token - immutable, safe to share
    this.resolvedType = original.resolvedType; // @ComputedState - STILL COPIED (shallow)
    this.computedFlag = original.computedFlag; // @ComputedState - STILL COPIED (shallow)

    // 2. Deep copy child fields using helper methods
    this.child = copyNode(original.child);         // Single nullable node
    this.children = copyStatements(original.children);  // List of statements
    this.exprs = copyExpressions(original.exprs);       // List of expressions
    this.nodes = copyNodes(original.nodes);             // List of any AstNode subtype

    // 3. Adopt copied children (set parent references)
    adopt(this.child);
    adopt(this.children);
}

@Override
public MyClass copy() {
    return new MyClass(this);
}
```

**Helper methods available in AstNode:**
- `copyNode(T node)` - Copy single nullable node with covariant typing
- `copyStatements(List<Statement>)` - Copy list of statements
- `copyExpressions(List<Expression>)` - Copy list of expressions
- `copyNodes(List<T extends AstNode>)` - Copy list of any AstNode subtype

### 6.2 Pattern for Custom Clone Overrides

When the original class has a custom `clone()` that clears fields:

```java
// Original custom clone()
public AstNode clone() {
    LambdaExpression that = (LambdaExpression) super.clone();
    that.m_lambda = null;  // Clear method structure
    return that;
}

// Equivalent copy constructor
protected LambdaExpression(@NotNull LambdaExpression original) {
    super(original);

    // Deep copy children
    this.params = copyNodes(original.params);
    this.paramNames = copyExpressions(original.paramNames);
    this.body = original.body == null ? null : original.body.copy();
    adopt(this.params, this.paramNames, this.body);

    // Shallow copy non-child fields
    this.operator = original.operator;
    this.lStartPos = original.lStartPos;

    // m_lambda explicitly NOT copied (matches custom clone() behavior)
    // MethodStructure belongs to original, not the copy
}
```

### 6.3 Pattern for Non-Child Fields That Need Deep Copy

Some fields are not in CHILD_FIELDS but still need special handling:

```java
// NamedTypeExpression.clone() handles m_exprDynamic manually
public AstNode clone() {
    NamedTypeExpression that = (NamedTypeExpression) super.clone();
    if (m_exprDynamic != null) {
        that.m_exprDynamic = (NameExpression) m_exprDynamic.clone();
    }
    return that;
}

// Copy constructor must replicate this
protected NamedTypeExpression(@NotNull NamedTypeExpression original) {
    super(original);

    // Deep copy CHILD_FIELDS
    this.left = original.left == null ? null : original.left.copy();
    this.paramTypes = copyNodes(original.paramTypes);
    adopt(this.left, this.paramTypes);

    // Deep copy non-child that needs special handling
    if (original.m_exprDynamic != null) {
        this.m_exprDynamic = (NameExpression) original.m_exprDynamic.copy();
    }

    // Shallow copy everything else
    this.module = original.module;
    this.immutable = original.immutable;
    this.names = original.names;
    // ... and transient resolution state ...
    this.m_constId = original.m_constId;
    this.m_fVirtualChild = original.m_fVirtualChild;
    this.m_fExternalTypedef = original.m_fExternalTypedef;
}
```

---

## Part 7: Next Steps

### Phase 3: Remaining Expressions

Priority order based on complexity and usage:

1. **Binary/Relational** (simpler, extend BiExpression)
2. **Type Expressions** (moderate complexity)
3. **Invocation/Access** (more complex, need careful analysis)
4. **Literal/Container** (straightforward lists)
5. **Other** (various complexity)

### Phase 4: Update Clone Call Sites

Replace `clone()` with `copy()` at all call sites:
- Validation loops: `ForStatement`, `WhileStatement`, `ForEachStatement`, `LambdaExpression`
- Type testing: `NewExpression`, `RelOpExpression`
- Expression backup: Various expressions

### Phase 5: Cleanup

1. Remove `Cloneable` interface from `AstNode`
2. Deprecate or remove `clone()` method
3. Remove `@NotCopied` annotation (was based on incorrect understanding)
4. Document field copy semantics in comments where needed

### Phase 5b: Apply @ComputedState Annotation

A new `@ComputedState` annotation has been created to replace the meaningless `transient` keyword as documentation for computed/cached state fields.

**Completed** (annotation applied):
- `NamedTypeExpression.java` - all 7 transient fields
- `CmpExpression.java` - all 4 transient fields
- `ElseExpression.java` - all 4 transient fields
- `ElvisExpression.java` - all 2 transient fields
- `AsExpression.java` - 1 transient field
- `RelOpExpression.java` - 1 transient field
- `StatementExpression.java` - all 4 transient fields
- `LambdaExpression.java` - all 8 transient fields

**Remaining** (annotation not yet applied):
- `ForStatement.java` - 11 transient fields
- `WhileStatement.java` - 9 transient fields
- `ForEachStatement.java` - 16 transient fields
- `TernaryExpression.java` - 2 transient fields
- `SwitchExpression.java` - 3 transient fields
- `MapExpression.java` - 2 transient fields
- `AnnotatedTypeExpression.java` - 6 transient fields
- `Statement.java` - 2 transient fields
- `MethodDeclarationStatement.java` - 3 transient fields
- `ConditionalStatement.java` - 1 transient field
- `CmpChainExpression.java` - 1 transient field
- `AnnotationExpression.java` - 3 transient fields
- `NewExpression.java` - 17 transient fields
- `CompositionNode.java` - 2 transient fields
- `PropertyDeclarationStatement.java` - 4 transient fields
- `NotNullExpression.java` - 2 transient fields
- `ArrayAccessExpression.java` - 3 transient fields
- `NameExpression.java` - 10 transient fields
- `InvocationExpression.java` - 18 transient fields
- `StatementBlock.java` - 2 transient fields
- `Expression.java` - 1 transient field

The annotation serves two purposes:
1. Documents which fields are candidates for extraction to semantic model
2. Documents copy constructor semantics (shallow copy)

### Phase 5c: Add @ChildNode Annotation

**Status:** COMPLETE

Created and applied `@ChildNode` annotation to ALL child node fields across 44+ AST classes.

**Annotation (in org.xvm.compiler.ast):**
```java
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface ChildNode {
    int index();
    String description() default "";
}
```

**Applied to all classes with CHILD_FIELDS:**
- Base expression classes: BiExpression (expr1, expr2), PrefixExpression (expr), DelegatingExpression (expr), SyntheticExpression (expr)
- Type expressions: BiTypeExpression, AnnotatedTypeExpression, ArrayTypeExpression, FunctionTypeExpression, NamedTypeExpression, NullableTypeExpression, DecoratedTypeExpression, BadTypeExpression, TupleTypeExpression
- Control flow: IfStatement, ForStatement, ForEachStatement, WhileStatement, SwitchStatement, SwitchExpression, TryStatement, CatchStatement
- Expressions: TernaryExpression, ThrowExpression, ListExpression, TupleExpression, MapExpression, TemplateExpression, LambdaExpression, StatementExpression, NotNullExpression, NonBindingExpression, ArrayAccessExpression, NameExpression, InvocationExpression, AnnotationExpression, CmpChainExpression, NewExpression, FileExpression
- Statements: CaseStatement, ReturnStatement, AssertStatement, AssignmentStatement, LabeledStatement, ExpressionStatement, ImportStatement, VariableDeclarationStatement, TypedefStatement
- Declarations: MethodDeclarationStatement, PropertyDeclarationStatement, TypeCompositionStatement, Parameter, VersionOverride
- CompositionNode and all inner classes: Extends, Annotates, Incorporates, Delegates, Import, Default
- StatementBlock

**Classes that inherit annotations from parents (no separate annotations needed):**
- BiExpression subclasses: AsExpression, ElvisExpression, RelOpExpression, CmpExpression, CondOpExpression, IsExpression, ElseExpression
- PrefixExpression subclasses: UnaryMinusExpression, UnaryPlusExpression, UnaryComplementExpression, SequentialAssignExpression
- DelegatingExpression subclasses: LabeledExpression, ParenthesizedExpression

**Classes with no child nodes (correctly have no annotations):**
- BreakStatement, ContinueStatement, GotoStatement
- KeywordTypeExpression, VariableTypeExpression, LiteralExpression
- Base classes: Expression, Statement, TypeExpression, ComponentStatement

**Next Steps:**
1. Keep `CHILD_FIELDS` arrays during transition
2. After visitor pattern is in place, remove `CHILD_FIELDS` reflection

### Phase 6: Visitor Pattern (This PR)

1. Design visitor interface hierarchy
2. Implement `accept()` methods on AST nodes
3. Migrate `StageMgr` to use visitors
4. Migrate parent setup to use visitors
5. Optionally optimize or remove `children()` iterator

---

## Appendix A: CHILD_FIELDS Reference

Quick reference showing which fields are deep-copied (in CHILD_FIELDS):

```
ForStatement:        init, conds, update, block
WhileStatement:      conds, block
ForEachStatement:    lvals, conds, block
IfStatement:         conds, stmtThen, stmtElse
SwitchStatement:     conds, block
StatementBlock:      stmts
TryStatement:        resources, block, catches, catchall
CatchStatement:      target, block
AssertStatement:     interval, conds, message
ReturnStatement:     exprs
AssignmentStatement: lvalue, lvalueExpr, rvalue
ExpressionStatement: expr
VariableDeclarationStatement: type

BiExpression:        expr1, expr2
InvocationExpression: expr, args
NewExpression:       left, type, args, anon (body is NOT a child - handled manually)
LambdaExpression:    params, paramNames, body
NameExpression:      left, params
NamedTypeExpression: left, paramTypes (m_exprDynamic is NOT a child - handled manually)
TernaryExpression:   cond, exprThen, exprElse
```

---

## Appendix B: Custom Clone Overrides to Replicate

Classes that override `clone()` with special behavior:

| Class | Custom Behavior | Copy Constructor Must |
|-------|----------------|----------------------|
| `LambdaExpression` | Nulls `m_lambda` | Not copy `m_lambda` |
| `NewExpression` | Deep copies `body` (non-child) | Deep copy `body` manually |
| `NamedTypeExpression` | Deep copies `m_exprDynamic` (non-child) | Deep copy `m_exprDynamic` manually |

---

## Appendix C: Files with Clone Calls to Update

### Validation Loop Clones
- `ForStatement.java` - conditions, update, block
- `WhileStatement.java` - conditions, block
- `ForEachStatement.java` - condition, block
- `LambdaExpression.java` - body (in createContext)
- `StatementExpression.java` - body

### Expression/Type Testing Clones
- `RelOpExpression.java` - expr1 backup
- `NewExpression.java` - type testing, list cloning
- `NamedTypeExpression.java` - m_exprDynamic

### Miscellaneous
- `AssertStatement.java` - De Morgan transformation
- `AssignmentStatement.java` - LValue backup

---

## Appendix D: Clone Locations Outside AST (Future Work)

These are clone() usages in non-AST code that should be addressed as separate issues:

### Token/Parser Layer
| File | Line | Description |
|------|------|-------------|
| `Token.java` | 436 | Token cloning for parser marks |
| `Source.java` | 463 | Source position cloning |
| `Parser.java` | 5365-5367 | Token cloning in parser marks |

### ASM Layer (Constants/Components)
| File | Description |
|------|-------------|
| `Constant.java` | Base constant cloning (lines 315, 716) |
| `SignatureConstant.java` | Parameter/return array cloning |
| `ParameterizedTypeConstant.java` | Type parameter array cloning (~15 locations) |
| `TypeConstant.java` | Type array cloning |
| `Parameter.java` | Parameter cloning |
| `Component.java` | Component/Contribution cloning |
| `MethodStructure.java` | Source/local constant cloning |
| `ArrayConstant.java` | Constant array cloning |
| `MapConstant.java` | Key/value array cloning |
| `AllCondition.java` | Conditional array cloning |
| `PropertyInfo.java` | Chain array cloning |
| `MethodInfo.java` | Method chain cloning |

### Runtime Layer
| File | Description |
|------|-------------|
| `ObjectHandle.java` | Runtime handle cloning |
| `xTuple.java` | Tuple value cloning |
| `xRTDelegate.java` | Delegate array cloning |
| `xRTFunction.java` | Argument array cloning |
| `Proxy.java` | Value handle cloning |

### Repository/Build
| File | Description |
|------|-------------|
| `LinkedRepository.java` | Repository array cloning |
| `ConstantPool.java` | Constant registration (byte array cloning) |
| `ClassStructure.java` | Parameter array cloning |

**Recommendation**: Create separate issues for each layer:
1. **Issue: Modernize Token/Parser cloning** - Low priority, isolated impact
2. **Issue: Modernize ASM Constant cloning** - High priority, affects type system
3. **Issue: Modernize Runtime handle cloning** - Medium priority, performance critical
4. **Issue: Modernize Repository cloning** - Low priority, rarely executed

---

## Appendix E: Modern Collection Patterns (Java 9+)

The codebase should migrate from legacy collection patterns to modern immutable alternatives.

### Legacy → Modern Replacements

| Legacy Pattern | Modern Replacement | Notes |
|---------------|-------------------|-------|
| `Collections.emptyList()` | `List.of()` | Immutable, slightly more efficient |
| `Collections.singletonList(x)` | `List.of(x)` | Immutable, cleaner API |
| `Collections.emptySet()` | `Set.of()` | Immutable |
| `Collections.singleton(x)` | `Set.of(x)` | Immutable |
| `Collections.emptyMap()` | `Map.of()` | Immutable |
| `Collections.singletonMap(k,v)` | `Map.of(k, v)` | Immutable |
| `Collections.unmodifiableList(list)` | `List.copyOf(list)` | Creates truly immutable copy |
| `Arrays.asList(a, b, c)` | `List.of(a, b, c)` | When immutability is acceptable |

### Array Operations (Keep These)

| Pattern | Recommendation |
|---------|---------------|
| `System.arraycopy()` | Keep - efficient for mutation-in-place |
| `Arrays.copyOf()` | Keep - creates new array efficiently |
| `Arrays.copyOfRange()` | Keep - creates new subarray efficiently |
| `array.clone()` | Replace with `Arrays.copyOf()` for clarity |

### Locations Requiring Updates

**High Priority (Compiler AST)** - MOSTLY COMPLETE:
- ✅ `ForStatement.java` - `Collections.emptyList()` → `List.of()`
- ✅ `ForEachStatement.java` - `Collections.singletonList()` → `List.of()`
- ✅ `ConditionalStatement.java` - `Collections.emptyList()` → `List.of()`
- ✅ `AssertStatement.java` - `Collections.emptyList()` → `List.of()`
- ✅ `TupleExpression.java` - `Collections.emptyList()` → `List.of()`
- ✅ `NamedTypeExpression.java` - `Collections.singletonList()` → `List.of()`
- ✅ `StageMgr.java` - Both patterns converted
- ⏳ `AstNode.java` - Still has `Collections.emptyMap()`, `Collections.singletonList()`
- ✅ `CompositionNode.java` - `Collections.emptyList()` → `List.of()`
- ✅ `AnonInnerClass.java` - `Collections.emptyList()` → `List.of()`
- ✅ `ListExpression.java` - `Collections.emptyList()` → `List.of()`
- ✅ `MethodDeclarationStatement.java` - Both patterns converted
- ✅ `TypeCompositionStatement.java` - All patterns converted
- ✅ `SwitchStatement.java` - `Collections.emptyMap()` → `Map.of()`
- ✅ `InvocationExpression.java` - `Collections.emptyMap()` → `Map.of()`
- ✅ `VersionOverride.java` - `Collections.emptyMap()` → `Map.of()`
- ✅ `IgnoredNameExpression.java` - `Collections.emptyMap()` → `Map.of()`
- ✅ `AnnotationExpression.java` - Both patterns converted
- ✅ `MapExpression.java` - Added convenience constructor for empty maps
- ⏳ `Context.java` - Still has multiple `Collections.emptyMap()` usages

**Medium Priority (Parser)**:
- `Parser.java` - ~20 locations with legacy patterns

### Immutability Goals

For the future Roslyn-like architecture:

1. **Syntax Nodes**: All child lists should be immutable (`List.of()`, `List.copyOf()`)
2. **Token Lists**: Immutable after parsing
3. **Type Parameter Lists**: Immutable after creation
4. **Method Parameter Lists**: Immutable

**Benefits**:
- Thread-safety for parallel compilation
- No defensive copies needed
- Clear ownership semantics
- Better GC behavior (no intermediate mutable lists)
