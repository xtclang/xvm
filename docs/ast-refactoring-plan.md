# AST Child Node Refactoring Plan

## Current State

The XVM compiler AST uses reflection to enumerate and manipulate child nodes:

```java
// Old approach - reflection based
protected Field[] getChildFields() {
    return CHILD_FIELDS;  // static array of Field objects
}

// ChildIterator uses reflection to:nod
// 1. Read fields: field.get(AstNode.this)
// 2. Write fields: field.set(AstNode.this, newChild)
// 3. Handle both scalars and collections dynamically
```

### Where Reflection Is Used

| Use Case | What It Needs | Current Impl |
|----------|---------------|--------------|
| `children()` iteration | Just traversal | ✅ Uses `forEachChild()` |
| `replaceChild()` | Find + write-back | ✅ Now explicit per-class |
| `copy()` (was `clone()`) | Deep copy of subtree | **Still uses reflection** |
| `getDumpChildren()` | Field names for debug | **Still uses reflection** |

**Important**: We do NOT use Java's `Cloneable` interface or `clone()` method. That API is
fundamentally broken (see Effective Java). We use `copy()` for deep copying nodes. 
### The ChildIterator Cursor Pattern

The original `ChildIterator` is not just iteration - it's a **mutable cursor** that tracks:
- Which field the current child came from
- Whether it's a scalar or collection
- Position within collection (for ListIterator)
- Legality of `remove()` or `replaceWith()` at current position

This is **not replaceable with flatMap/streams** because streams lose location context.

---

## Modern Compiler AST Patterns

### Roslyn (C#) - Red-Green Trees

The gold standard for incremental, immutable AST design:

**Green Tree (Internal)**
- Immutable and truly persistent
- No parent references, no absolute positions
- Built bottom-up, stores only relative widths
- Only ~O(log n) nodes rebuilt per edit

**Red Tree (Public API)**
- Immutable facade built top-down on demand
- Manufactures parent references during traversal
- Computes absolute positions from widths
- Rebuilt after each edit, then discarded

**Key Insight**: Separation of concerns. Consumers only see the red tree; persistence is hidden.

**Transformation**: `With*` methods return new nodes; `ReplaceNode` re-spins parents up the tree.

### Clang (C++) - Visitor Pattern with CRTP

- No common AST ancestor - separate hierarchies (Stmt, Decl, Type)
- Each node implements `children()` returning `child_range` iterator
- **Mutable AST** - nodes can be modified in place
- `RecursiveASTVisitor<T>` with CRTP for custom traversal
- Explicit `Visit*`, `Traverse*`, `WalkUpFrom*` hooks

### TypeScript - forEachChild / visitEachChild

```typescript
// Read-only traversal
ts.forEachChild(node, callback)

// Transformation (returns new/modified node)
ts.visitEachChild(node, visitor, context)
```

**Key Insight**: Clear separation between iteration (forEachChild) and transformation (visitEachChild).

---

## XVM Compiler Stages - Detailed Analysis

The compiler processes AST nodes through multiple stages:

```
Initial
  ↓
Registering → Registered    // Pass 1: Create component structures
  ↓
Loading → Loaded            // Pass 2: Link modules (mostly no-op for AST)
  ↓
Resolving → Resolved        // Pass 2: Resolve names
  ↓
Validating → Validated      // Pass 3: Type checking, validation
  ↓
Emitting → Emitted          // Pass 4: Generate bytecode
  ↓
Discarded                   // Node no longer needed
```

### Stage 1: Registering → Registered

**Purpose**: Create the skeleton component structure (FileStructure, ModuleStructure, ClassStructure, etc.)

**What happens**:
- `introduceParentage()` - set parent references on all nodes
- Create Component structures for modules, packages, classes, methods, properties
- Register type parameters (needed before name resolution)
- NO name resolution yet - just structure creation

**Data produced**:
- `Component` structures in `org.xvm.asm` package
- Stored on AST nodes via `setComponent(component)`

**Example** (TypeCompositionStatement.registerStructures):
```java
// Creates FileStructure, ModuleStructure, PackageStructure, ClassStructure
FileStructure struct = new FileStructure(sModule);
component = struct.getModule();
setComponent(component);  // Store on AST node
```

### Stage 2: Loading → Loaded

**Purpose**: Link modules together based on declared dependencies

**What happens**:
- Mostly handled at Compiler level, not individual AST nodes
- Most AST nodes just pass through this stage
- Module imports are verified

**Data produced**:
- Module fingerprints and dependencies resolved

### Stage 3: Resolving → Resolved

**Purpose**: Resolve all names to their target components/types

**What happens**:
- Names resolved to Components, TypeConstants, etc.
- **Fixpoint iteration**: Nodes can call `mgr.requestRevisit()` if dependencies aren't ready
- Cross-module dependencies may require multiple passes

**Data produced** (stored on AST nodes):
- `NamedTypeExpression`: `m_constId` (resolved IdentityConstant)
- `NameExpression`: resolved meaning, target component
- Various `m_*` fields for resolved references

**The Revisit Pattern**:
```java
public void resolveNames(StageMgr mgr, ErrorListener errs) {
    if (!dependencyReady()) {
        mgr.requestRevisit();  // "Come back later"
        return;
    }
    // Do actual resolution
}
```

### Stage 4: Validating → Validated

**Purpose**: Type checking, constant evaluation, semantic validation

**What happens**:
- Expressions validated via `validate(Context, TypeConstant, ErrorListener)`
- Statements validated via `validate(Context, ErrorListener)`
- Type inference and checking
- Constant folding
- **Structural transforms**: TraceExpression wrapping, SyntheticExpression

**Data produced** (stored on Expression nodes):
```java
private TypeFit m_fit;      // How well expression fits expected type
private Object m_oType;     // Resolved type(s) - TypeConstant or TypeConstant[]
private Object m_oConst;    // Constant value(s) if compile-time constant
```

**Context object** carries:
- Variable assignments and narrowings
- Type inference state
- Error recovery state

**Context.enter()/exit() pattern**:
The Context uses a stack-based pattern for scope tracking during validation:

```java
ctx = ctx.enter();       // Push new scope
// validate children in nested scope
ctx = ctx.exit();        // Pop scope, promote info to parent
```

This is used for:
- **Variable scoping**: Track which variables are visible at each point
- **Definite assignment analysis**: Ensure variables are assigned before use
- **Type narrowing**: After `if (x instanceof T)`, x is known to be T in the if-block
- **Reachability tracking**: Mark code after `return`/`throw` as unreachable

Specialized contexts exist for different control flow: `enterIf()`, `enterFork(boolean)`,
`enterAnd()`, `enterOr()`, `enterLoop()`, `enterNot()`, etc.

**Implications for AST refactoring**: **NONE**
- Context is about **validation state**, not AST structure
- Context objects are NOT part of the AST tree - they're temporary during validation
- The AST immutability refactoring is orthogonal to Context mechanism
- Context will continue to work the same way regardless of AST mutability

### Stage 5: Emitting → Emitted

**Purpose**: Generate bytecode

**What happens**:
- `generateCode(StageMgr, ErrorListener)` on each node
- Method bodies compiled via `compileMethod(Code, ErrorListener)`
- Bytecode ops generated into `MethodStructure.Code`

**Data produced**:
- Bytecode in `MethodStructure`
- Constants registered in `ConstantPool`

---

## The StageMgr Orchestration Pattern

StageMgr manages the fixpoint iteration:

```java
public boolean processComplete() {
    for (AstNode node : takeRevisitList()) {
        processInternal(node);  // May add back to revisit list
    }
    return m_listRevisit.isEmpty();  // Done when nothing left
}
```

**Key mechanisms**:
- `requestRevisit()` - node can't complete yet, try again later
- `deferChildren()` - don't automatically process children
- `markLastAttempt()` - no more retries, report errors

**Why fixpoint?** Cross-module circular dependencies:
- Module A references type from Module B
- Module B references type from Module A
- Neither can fully resolve until the other does
- Iterate until stable (or max iterations exceeded)

---

## Data Stored on AST Nodes by Stage

| Stage | Data Stored | Location |
|-------|-------------|----------|
| Registered | Component reference | `AstNode.m_component` |
| Resolved | Resolved constants, types | Various `m_*` fields per node type |
| Validated | TypeFit, resolved types, constants | `Expression.m_fit`, `m_oType`, `m_oConst` |
| Validated | Context (transient) | `Statement.m_ctx` |
| Emitted | (bytecode in Component) | `MethodStructure.Code` |

---

## Problems with Current Stage Model

### For Current Use

1. **Nodes are mutable** - no structural sharing possible
2. **Stage per-node** - siblings can be at different stages
3. **Data mixed with syntax** - semantic info on syntax nodes
4. **Fixpoint complexity** - hard to reason about partial states

### For IntelliSense/IDE Support

1. **No incremental reparse** - any change rebuilds everything
2. **No cached results** - can't reuse type info for unchanged code
3. **No partial results** - must complete all stages
4. **Structural transforms break sharing** - TraceExpression wrapping mutates tree

---

## Alternative Stage Architecture (Future)

### Separation of Concerns

```
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│  Syntax Tree    │   │  Symbol Table   │   │  Semantic Model │
│  (immutable)    │──▶│  (Components)   │──▶│  (types, etc.)  │
│  from parser    │   │  from Register  │   │  from Validate  │
└─────────────────┘   └─────────────────┘   └─────────────────┘
         │                                           │
         │         ┌─────────────────┐              │
         └────────▶│  Lowered Tree   │◀─────────────┘
                   │  (with Traces)  │
                   │  for Emit       │
                   └─────────────────┘
```

### Stage Data External to Nodes

```java
// Instead of storing on nodes:
class CompilationContext {
    Map<AstNode, Component> components;      // from Register
    Map<AstNode, TypeConstant> resolvedTypes; // from Resolve
    Map<Expression, TypeFit> typeFits;        // from Validate
    Map<Expression, Constant> constants;      // from Validate
}

// Nodes remain pure syntax
public final class BiExpression extends Expression {
    private final Expression expr1;  // immutable
    private final Token operator;
    private final Expression expr2;
    // NO m_type, m_fit, etc.
}
```

### Benefits

1. **Syntax tree reusable** - unchanged code keeps same nodes
2. **Parallel stage processing** - no mutation conflicts
3. **Incremental updates** - only recompute changed parts
4. **Clear data flow** - each stage produces distinct output
5. **Testable stages** - can test each stage in isolation

---

## Unusual Patterns in Current Implementation

### 1. The "Current Model as Red-Green" Question

Could the current model be adapted to red-green?

**Conceptual mapping**:
- **Green = Pure syntax from parser** (but currently mutable)
- **Red = AstNode with parent refs** (currently the same object)
- **Semantic Model = m_type, m_fit, etc.** (currently stored on nodes)

**Blockers**:
1. Parent references stored on nodes (prevents sharing)
2. Stage data stored on nodes (prevents sharing)
3. Structural transforms mutate tree (TraceExpression, SyntheticExpression)
4. Fixpoint iteration assumes mutation

### 2. Structural Mutations During Validation

The AST isn't just annotated - it's **transformed**:

```java
// TraceExpression wrapping (Expression.java:1097)
parent.replaceChild(this, exprTrace);

// SyntheticExpression insertion
expr.getParent().adopt(this);
this.adopt(expr);

// TupleExpression adoption
expr0.getParent().adopt(this);
```

**In a red-green world**: These would create new nodes, not mutate existing ones.
The green tree would stay stable; a "lowered" tree would have the transforms.

### 3. The Component Hierarchy (Parallel Structure)

There are TWO tree structures:

1. **AST nodes** (`org.xvm.compiler.ast.*`) - syntax tree from parser
2. **Component structures** (`org.xvm.asm.*`) - semantic/output structure

The Component hierarchy is:
```
FileStructure
  └── ModuleStructure
        └── PackageStructure
              └── ClassStructure
                    ├── MethodStructure
                    └── PropertyStructure
```

AST nodes link to their Component via `getComponent()`.
This is already a form of separation - but the AST nodes still store semantic data.

### 4. Cross-Module Fixpoint (Unlike Roslyn)

Roslyn compiles one assembly at a time with explicit references.
XVM has **circular module dependencies** requiring fixpoint iteration.

```
Module A imports Module B
Module B imports Module A
```

Both must be partially resolved before either can complete.
StageMgr handles this with `requestRevisit()`.

**Implication**: Can't have simple "stage as function" model.
Need either:
- Multiple compilation units coordinated externally
- Or accept fixpoint iteration within a stage

### 5. Different Nodes at Different Stages

Unlike a pure stage-transformer model where the whole tree advances together,
XVM allows nodes to be at different stages:

```java
// One node fully validated, sibling still resolving
node1.getStage() == Stage.Validated
node2.getStage() == Stage.Resolving  // same parent!
```

This is because of the fixpoint model - some nodes complete while others wait.

---

## Proposed Direction

### Phase 1: Replace Reflection (Current Work)

Replace reflection-based child access with explicit implementations.

#### Design Decision: Function vs Consumer

We use `Function<AstNode, T>` rather than `Consumer<AstNode>` for the primitive operation.
This follows TypeScript's `forEachChild` pattern which returns the first truthy value.

**Why this matters:**

1. **Early termination / search** - Many traversals want to find the first match and stop:
   ```java
   // Find first expression that is traceworthy
   Expression found = node.forEachChild(child ->
       child instanceof Expression e && e.isTraceworthy() ? e : null);
   ```

2. **Zero allocation for simple traversal** - Return `null` to continue:
   ```java
   // Visit all children (same efficiency as Consumer)
   node.forEachChild(child -> { doSomething(child); return null; });
   ```

3. **Copy-on-write preparation** - The same pattern enables transformation:
   ```java
   // Transform children - return non-null to replace
   AstNode transformed = node.mapChildren(child ->
       shouldTransform(child) ? transform(child) : child);
   ```

4. **Aggregation patterns** - Find-and-accumulate becomes natural:
   ```java
   // Collect all errors
   List<Error> errors = new ArrayList<>();
   node.forEachChild(child -> {
       if (child.hasError()) errors.add(child.getError());
       return null;  // continue
   });
   ```

**Implementation:**

```java
/**
 * Visit each child of this node. If the visitor returns a non-null value,
 * iteration stops and that value is returned. This enables both traversal
 * (return null to continue) and search (return result to stop).
 *
 * @param visitor  the function to invoke on each child node
 * @param <T>      the result type
 * @return the first non-null result from the visitor, or null if all returned null
 */
public abstract <T> T forEachChild(Function<AstNode, T> visitor);

// Convenience overload for side-effect-only traversal
public void forEachChild(Consumer<AstNode> visitor) {
    forEachChild(child -> { visitor.accept(child); return null; });
}

// For read-only List access (compatibility)
public List<AstNode> children() {
    List<AstNode> list = new ArrayList<>();
    forEachChild((Consumer<AstNode>) list::add);
    return list;
}
```

**Example implementation in a node class:**

```java
@Override
public <T> T forEachChild(Function<AstNode, T> visitor) {
    T result;
    if (condition != null && (result = visitor.apply(condition)) != null) {
        return result;
    }
    if (annotations != null) {
        for (var anno : annotations) {
            if ((result = visitor.apply(anno)) != null) {
                return result;
            }
        }
    }
    if (params != null) {
        for (var param : params) {
            if ((result = visitor.apply(param)) != null) {
                return result;
            }
        }
    }
    return null;
}
```

**Benefits:**
- No intermediate list for simple iteration
- Each class handles its fields explicitly
- Type-safe, no reflection
- Early exit without exceptions
- Foundation for future transformation support
- Single primitive serves both iteration and search use cases

### Phase 1b: VisitResult and Transform (Copy-on-Write Foundation)

Building on `forEachChild`, add a sealed result type that enables copy-on-write transformation:

```java
sealed interface VisitResult<T> permits Continue, Stop, Replace, SkipChildren {
    record Continue<T>() implements VisitResult<T> {}
    record Stop<T>(T value) implements VisitResult<T> {}
    record Replace<T>(AstNode newNode) implements VisitResult<T> {}
    record SkipChildren<T>() implements VisitResult<T> {}
}
```

**Why sealed VisitResult matters:**

1. **Transformation via return values** - No mutation. The framework controls tree reconstruction.
2. **Structural sharing** - `Continue` means "keep this node". Only changed paths get new objects.
3. **Stateless visitors** - Pure functions: `node → VisitResult`. Thread-safe, cacheable, composable.

**The transform method:**

```java
// In AstNode
public AstNode transform(Function<AstNode, VisitResult<?>> visitor) {
    VisitResult<?> result = visitor.apply(this);

    return switch (result) {
        case Replace(var newNode) -> newNode;
        case SkipChildren() -> this;
        case Stop(var _) -> this;
        case Continue() -> {
            // Visit children, collect any replacements
            boolean[] changed = {false};
            List<AstNode> newChildren = new ArrayList<>();
            forEachChild(child -> {
                AstNode transformed = child.transform(visitor);
                newChildren.add(transformed);
                if (transformed != child) changed[0] = true;
            });
            // If any child changed, create new node with new children
            // Otherwise return this (structural sharing!)
            yield changed[0] ? withChildren(newChildren) : this;
        }
    };
}

// Each node implements this to create a copy with new children
protected abstract AstNode withChildren(List<AstNode> children);
```

**How this enables LSP/IntelliSense:**

1. **Edit → New Tree**: User types, you transform the affected subtree. Unchanged nodes are *the same objects*.

2. **Semantic Cache**: `Map<AstNode, TypeConstant>` keyed by node identity. Unchanged nodes = cache hit.

3. **Incremental Recomputation**:
   - Parse only the changed region
   - Transform returns `Continue` for unchanged, `Replace` for changed
   - Only recompute types for new nodes

4. **Time Travel / Undo**: Since transforms return new trees and don't mutate,
   you can keep references to old tree versions. This enables:
   - **Undo/Redo**: Just swap which tree version is "current"
   - **Diff**: Compare old and new trees structurally
   - **Incremental compilation**: Only recompile changed subtrees
   - **Debugging**: Step back through compilation stages
   - **Speculative parsing**: Try a parse, discard if it fails, no cleanup needed

5. **Thread Safety**: Pure functions, no mutation, parallel analysis is safe.

**Relationship to Roslyn Red-Green:**

This is essentially the Roslyn pattern expressed through the visitor API:
- Green tree = the immutable nodes returned from transform
- Red tree = manufactured on-demand with parent refs (could be a thin wrapper)
- Structural sharing = nodes returning `Continue` keep same identity

### Phase 1c: @Derived Annotation for Non-Structural Fields

Currently, fields like `m_label`, `m_ctx`, `m_type` are marked with Java's `transient` keyword.
This is wrong - `transient` is Java serialization baggage and doesn't convey our intent.

**Add a semantic annotation:**

```java
package org.xvm.compiler.ast;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as derived/computed rather than part of the AST structure.
 * These fields are populated during compilation stages and should NOT be
 * copied when creating a new node with different children.
 *
 * Examples: resolved types, jump labels, cached computations, validation context.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Derived {}
```

**Usage:**

```java
// Before (wrong - Java serialization concept)
protected transient Label m_label;

// After (semantic - describes our intent)
@Derived
protected Label m_label;
```

**Benefits:**
- Clear intent: "this field is computed, not structural"
- No dependency on Java serialization semantics
- Can be used for tooling, documentation, or runtime inspection if needed
- Prepares for future where structural fields become `final`

### Phase 2: Unified Copy Architecture

**Important**: We do NOT use Java's `Cloneable` interface or `clone()` method.
That API is fundamentally broken (see Effective Java, Item 13).

#### 2.1: The Copyable Interface

All three major hierarchies (AstNode, Constant, Component) share a common copying pattern:

```java
/**
 * Marker interface for structures that support explicit copying.
 * All implementations must:
 * 1. Only copy STRUCTURAL fields (not @Derived)
 * 2. Return a new instance, never mutate
 * 3. Handle child copying explicitly
 */
public interface Copyable<T> {
    /**
     * Create a structural copy. @Derived fields are NOT copied.
     */
    T copy();
}
```

#### 2.2: AstNode Copying

```java
public abstract class AstNode implements Copyable<AstNode> {

    @Override
    public final AstNode copy() {
        // Deep copy using withChildren - each child is recursively copied
        List<AstNode> copiedChildren = new ArrayList<>();
        forEachChild(child -> { copiedChildren.add(child.copy()); return null; });
        return withChildren(copiedChildren);
    }

    // Each class implements withChildren to create a new instance
    protected abstract AstNode withChildren(List<AstNode> children);
}

// Example implementation
@Override
protected BiExpression withChildren(List<AstNode> children) {
    return new BiExpression(
        (Expression) children.get(0),
        operator,  // Token is immutable, shared
        (Expression) children.get(1)
    );
}
```

#### 2.3: Constant Copying (with Pool Transfer)

```java
public abstract class Constant implements Copyable<Constant> {

    // Simple copy (same pool)
    @Override
    public final Constant copy() {
        return copyTo(getConstantPool());
    }

    /**
     * Copy to a different pool. This replaces adoptedBy().
     * Each subclass implements explicitly - no reflection.
     */
    public abstract Constant copyTo(ConstantPool pool);
}

// Example implementation
@Override
public IntConstant copyTo(ConstantPool pool) {
    return pool == getConstantPool()
        ? this
        : new IntConstant(pool, getFormat(), m_pint);
}

// Composite example
@Override
public ParameterizedTypeConstant copyTo(ConstantPool pool) {
    if (pool == getConstantPool()) return this;
    TypeConstant   typeCopied   = m_constType.copyTo(pool);
    TypeConstant[] paramsCopied = copyArrayTo(pool, m_atypeParams);
    return new ParameterizedTypeConstant(pool, typeCopied, paramsCopied);
}
```

#### 2.4: Component Copying

```java
public abstract class Component implements Copyable<Component> {

    @Override
    public abstract Component copy();

    /**
     * Copy for bifurcation (conditional compilation).
     * Creates a variant with a specific condition.
     */
    public abstract Component copyForCondition(ConditionalConstant condition);
}
```

#### 2.5: The @Derived Contract

All three hierarchies use the same annotation to mark non-structural fields:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Derived {
    // Fields marked @Derived are:
    // 1. NOT copied by copy()/copyTo()/withChildren()
    // 2. Recomputed lazily after copy
    // 3. Can use Lazy<T> for final + lazy pattern
}
```

| Hierarchy | Structural Fields | @Derived Fields |
|-----------|------------------|-----------------|
| AstNode | children, tokens, source positions | m_stage, m_ctx, m_label, resolved types |
| Constant | value data, child constants | m_iPos, m_cRefs, m_oValue, TypeInfo cache |
| Component | structure, children, names | cache maps, format state |

**Why withChildren() instead of copyImpl():**
- Same method serves both `copy()` and `transform()`
- Enables structural sharing in transforms
- Single implementation point per class
- Prepares for immutable nodes

### Phase 3: Transformation Support

Add transformation method alongside iteration:

```java
// Returns new node if any child changed, else 'this'
public AstNode transformChildren(UnaryOperator<AstNode> transformer) {
    // Each class implements - checks if children changed
    // If yes, creates new node with transformed children
    // If no, returns this
}
```

This prepares for copy-on-write without requiring it immediately.

### Phase 4: Copy-on-Write (Future)

Eventually move to immutable nodes with structural sharing:

```java
// All fields final
public final class BiExpression extends Expression {
    private final Expression expr1;
    private final Token operator;
    private final Expression expr2;

    // "With" methods for modification
    public BiExpression withExpr1(Expression newExpr1) {
        return expr1 == newExpr1 ? this
            : new BiExpression(newExpr1, operator, expr2);
    }
}
```

**Time Travel and Persistence Benefits:**

Copy-on-write with structural sharing is fundamentally a **persistent data structure** pattern.
This unlocks powerful capabilities:

1. **Undo/Redo Stack**: Each edit creates a new tree. Keep a list of tree roots.
   Undo = pop to previous root. No "undo logic" needed - it's just pointer swapping.

2. **Branching Edits**: Fork a tree, try multiple transformations in parallel,
   pick the best result. Failed attempts are garbage collected automatically.

3. **Incremental Recompilation**: When user edits line 50, only nodes on the
   path from root to line 50 are recreated. Nodes for lines 1-49 and 51+ are
   shared with the old tree. Semantic analysis can skip unchanged subtrees.

4. **Debugger Time Travel**: Store tree snapshots at each compilation stage.
   User can "step back" to see the AST before a transform was applied.

5. **Speculative Parsing**: Try parsing an ambiguous construct one way.
   If it fails, discard the new tree (GC handles cleanup) and try another way.
   No explicit "rollback" code needed.

6. **Diff/Delta Compression**: Two trees that share structure can be diffed
   efficiently - only walk paths where nodes differ.

**Considerations:**
- Stage information would need to move outside nodes (compilation context)
- Parent references would be manufactured on-demand (like Roslyn red tree)
- Significant refactoring of stage processing


### Phase 5: Component and Constant Hierarchy Refactoring

The `org.xvm.asm` package also uses Java's `Cloneable`/`clone()` pattern extensively.
This must be addressed to achieve the goals of incremental compilation and LSP support.

#### 5.1: Understanding the Current Clone Usage

**Who clones constants and why?**

1. **`adoptedBy(ConstantPool)`** - The primary use case:
   - When a constant from one module's pool is referenced in another module
   - Creates a shallow copy with the new pool as its container
   - Called from `ConstantPool.register()` when `constant.getContaining() != this`

2. **Defensive array cloning** - When modifying arrays that might be shared:
   - `TypeConstant[].clone()` in many places
   - `Parameter[].clone()` in MethodStructure
   - `PropertyBody[].clone()` in PropertyInfo
   - Pattern: clone-on-modify to avoid corrupting shared state

3. **Component bifurcation** - For conditional compilation:
   - `ComponentBifurcator` clones components to create conditional variants
   - Each condition gets its own component tree

**Affected classes implementing `Cloneable`:**

| Class | Clone Purpose | Transient Fields |
|-------|---------------|------------------|
| `Constant` | `adoptedBy()` for pool transfer | `m_iPos`, `m_cRefs`, `m_oValue` |
| `Component` | Bifurcation, conditional compilation | Format/cache fields |
| `Component.Contribution` | Component cloning | None |
| `Parameter` (asm) | Method cloning | None |
| `MethodStructure.Source` | Method cloning | None |

**TypeConstant and subclasses** - Many transient fields for caching:
```java
private transient boolean m_fValidated;
private transient volatile TypeInfo m_typeinfo;
private transient volatile int m_cInvalidations;
private transient volatile Map<TypeConstant, Relation> m_mapRelations;
private transient Map<String, Usage> m_mapConsumes;
private transient Map<String, Usage> m_mapProduces;
private transient TypeConstant m_typeNormalized;
// ... more
```

#### 5.2: The Problem with Current Design

**State juggling everywhere:**
- Constants appear immutable but have mutable cached state
- `transient` fields create invisible dependencies
- Clone creates new objects but cached state is lost/wrong
- No clear contract for what clone does vs doesn't copy

**Thread-safety issues:**
- `volatile` fields indicate concurrent access concerns
- Clone doesn't preserve happens-before relationships
- Cached TypeInfo can be stale after clone

**For LSP/Incremental compilation:**
- Can't share constant pools between compilation units
- Type resolution is tied to specific pool instance
- No way to incrementally update types

#### 5.3: Proposed Solution

**A. Replace `@Derived` pattern in asm package:**

Create `@Derived` annotation in `org.xvm.asm` (or share from ast package):

```java
// In Constant.java - replace transient with @Derived
@Derived private int m_iPos = -1;
@Derived private int m_cRefs;
@Derived private Object m_oValue;
```

```java
// In TypeConstant.java
@Derived private boolean m_fValidated;
@Derived private volatile TypeInfo m_typeinfo;
@Derived private volatile Map<TypeConstant, Relation> m_mapRelations;
// ... etc
```

**B. Replace `adoptedBy()` with explicit `copyTo()`:**

```java
// In Constant.java
/**
 * Create a copy of this constant for a different pool.
 * Only copies structural data - derived/cached fields are NOT copied.
 */
public Constant copyTo(ConstantPool pool) {
    // Each subclass implements this explicitly
    throw new UnsupportedOperationException(getClass().getName());
}

// Example in IntConstant
@Override
public IntConstant copyTo(ConstantPool pool) {
    return new IntConstant(pool, getFormat(), m_pint);
}

// Example in ParameterizedTypeConstant
@Override
public ParameterizedTypeConstant copyTo(ConstantPool pool) {
    TypeConstant   typeCopied   = m_constType.copyTo(pool);
    TypeConstant[] paramsCopied = copyArrayTo(pool, m_atypeParams);
    return new ParameterizedTypeConstant(pool, typeCopied, paramsCopied);
}

// Helper for array copying
protected static TypeConstant[] copyArrayTo(ConstantPool pool, TypeConstant[] atype) {
    TypeConstant[] result = new TypeConstant[atype.length];
    for (int i = 0; i < atype.length; i++) {
        result[i] = atype[i].copyTo(pool);
    }
    return result;
}
```

**C. Remove `Cloneable` interface:**

```java
// Before
public abstract class Constant
        extends XvmStructure
        implements Comparable<Constant>, Cloneable, Argument {

// After
public abstract class Constant
        extends XvmStructure
        implements Comparable<Constant>, Argument {
```

**D. Update `ConstantPool.register()`:**

```java
// Before
if (constant.getContaining() != this) {
    constant = (T) constant.adoptedBy(this);
}

// After
if (constant.getContaining() != this) {
    constant = (T) constant.copyTo(this);
}
```

#### 5.4: Migration Strategy for Constants

**Phase 5a: Add `@Derived` annotation usage**
- Tag all `transient` fields with `@Derived`
- Keep `transient` temporarily for compatibility
- Document which fields are derived

**Phase 5b: Implement `copyTo()` on leaf classes first**
- Start with simple constants: IntConstant, StringConstant, etc.
- These have no child constants

**Phase 5c: Implement `copyTo()` on composite constants**
- TypeConstant subclasses
- SignatureConstant
- MethodConstant, PropertyConstant

**Phase 5d: Update callers and remove `adoptedBy()`**
- Update ConstantPool.register()
- Remove Cloneable interface
- Remove old clone() overrides

**Phase 5e: Component hierarchy**
- Apply same pattern to Component
- Replace bifurcation clone with explicit copy

#### 5.5: Relationship Between AST and Constants

The AST and ConstantPool are tightly coupled:

```
AST Node (e.g., NamedTypeExpression)
    └── resolves to ──→ IdentityConstant (in pool)
                              └── has ──→ TypeConstant
                                            └── caches ──→ TypeInfo
```

**For LSP/Incremental to work:**
1. AST nodes must be copy-on-write (Phase 1-4)
2. Constants must support pool transfer via `copyTo()` (Phase 5)
3. Semantic caches (TypeInfo) must be keyed externally, not on the constant

**Future consideration:** Move TypeInfo and semantic caches to an external
`CompilationContext` rather than storing on TypeConstant. This enables:
- Sharing types across compilation units
- Invalidating only affected type info on edits
- Parallel analysis without lock contention

### Phase 6: Array to List Migration

#### 6.1: Investigation Results - Bridge/Runtime Array Requirements

**Arrays ARE required at the runtime/bridge boundary:**
- `Parameter[] getParamArray()` - Used by `xRTMethodTemplate` for indexed access
- `Parameter[] getReturnArray()` - Used for method return type handling
- `Annotation[] getAnnotations()` - Used by reflection APIs
- `TypeConstant[] getRawParams()` / `getRawReturns()` in `SignatureConstant`

**Why arrays at the boundary:**
1. **Indexed access protocol**: Bridge code uses `getParameter(Int index)` pattern
2. **Native interop**: C/Java interop requires fixed-size arrays
3. **Serialization**: Binary format naturally uses arrays
4. **Performance**: Direct indexed access without wrapper overhead

**Key conversion points:**
- `Utils.CreateParameters` (runtime/Utils.java:1474) - converts `Parameter[]` to handles
- `xRTFunction.java` - binds parameters to function calls
- `xRTType.java` - constructor parameter handling

#### 6.2: Migration Strategy

**Keep arrays at boundary, use Lists internally:**

```java
// BEFORE: Internal storage as array
private Annotation[] m_aAnnotations;

public Annotation[] getAnnotations() {
    return m_aAnnotations;  // Exposes mutable array!
}

// AFTER: Internal storage as List, array conversion at boundary
private final List<Annotation> annotations;  // immutable list

public List<Annotation> getAnnotations() {
    return annotations;  // Already immutable
}

// Bridge method - only for runtime/bridge callers
@Deprecated  // TODO: Remove once bridge is updated
public Annotation[] getAnnotationsAsArray() {
    return annotations.toArray(Annotation[]::new);
}
```

**Deprecation approach:**
1. Add new List-based API
2. Mark array API as `@Deprecated`
3. Update internal callers to use List API
4. Bridge/runtime callers can continue using array API
5. Eventually update bridge to use List API or keep thin wrapper

#### 6.3: Specific Migrations

| Current Array API | New List API | Bridge Compatibility |
|-------------------|--------------|----------------------|
| `getParamArray()` | `getParams()` | Keep `getParamArray()` deprecated |
| `getReturnArray()` | `getReturns()` | Keep `getReturnArray()` deprecated |
| `getAnnotations()` (array) | `annotations()` | Keep old as deprecated |
| `TypeConstant[]` params | `List<TypeConstant>` | Conversion at boundary |

---

### Phase 7: Null Safety and Defensive Coding

#### 7.1: The Problem with Null

Null is used with at least 5 different meanings in the codebase:
1. **"Not yet initialized"** - Field will be set later
2. **"Empty/absent"** - No value, same as empty collection
3. **"Unknown"** - Value hasn't been computed yet
4. **"Optional"** - May or may not have a value
5. **"Error state"** - Something went wrong

**Examples of confusion:**
```java
// Does null mean "no annotations" or "annotations not yet computed"?
private Annotation[] m_aAnnotations;

// Null and empty treated inconsistently
if (list == null || list.isEmpty()) { ... }  // Sometimes
if (list != null && !list.isEmpty()) { ... } // Other times
```

#### 7.2: Annotation Strategy

Add JetBrains annotations (already used in some files):

```java
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

// Constructor parameters - mark non-nullable
public NameExpression(
        @Nullable Expression left,      // Can be null (no prefix)
        @NotNull Token name,            // Required - never null
        @Nullable List<TypeExpression> params) {  // Optional
    this.left = left;
    this.name = Objects.requireNonNull(name);
    this.params = params == null ? List.of() : List.copyOf(params);
}

// Return values - mark nullability
public @Nullable Expression getLeft() { return left; }
public @NotNull Token getName() { return name; }
public @NotNull @Unmodifiable List<TypeExpression> getParams() { return params; }
```

#### 7.3: Never Null Collections Pattern

**BEFORE (antipattern):**
```java
List<Error> listErrors = null;
for (Node child : children) {
    if (child.hasError()) {
        if (listErrors == null) {
            listErrors = new ArrayList<>();  // Lazy init mid-loop
        }
        listErrors.add(child.getError());
    }
}
if (listErrors != null) { ... }  // Null check hell
```

**AFTER (clean):**
```java
List<Error> errors = new ArrayList<>();  // Always start with empty
for (Node child : children) {
    if (child.hasError()) {
        errors.add(child.getError());
    }
}
if (!errors.isEmpty()) { ... }  // Simple isEmpty check
// Or even better with streams:
List<Error> errors = children.stream()
    .filter(Node::hasError)
    .map(Node::getError)
    .toList();
```

#### 7.4: Common Antipatterns Found

**Lazy list initialization in loops** (~35 occurrences found):
- `PropertyStructure.java:296` - `List<Annotation> listPropAnno = null`
- `ClassStructure.java:205` - `List<Annotation> listAnnos = null`
- `MethodInfo.java:179,250,326,369` - Multiple `ArrayList<MethodBody> listMerge = null`
- `PropertyInfo.java:142,372` - Similar patterns
- `TypeConstant.java:2122,2701,4317` - Various list patterns

**Files needing cleanup:**
- `PropertyStructure.java` - 5 occurrences
- `ClassStructure.java` - 4 occurrences
- `MethodInfo.java` - 6 occurrences
- `TypeConstant.java` - 3 occurrences
- `Parser.java` - 2 occurrences

#### 7.5: Immutable Collection Returns

**BEFORE (dangerous):**
```java
public List<Parameter> getParams() {
    return m_listParams;  // Caller can modify!
}
```

**AFTER (safe):**
```java
public @NotNull @Unmodifiable List<Parameter> getParams() {
    return Collections.unmodifiableList(m_listParams);
    // Or better: store as immutable from the start
}
```

**Best practice: Store immutable, return immutable:**
```java
private final List<Parameter> params;  // Stored immutable

public Constructor(..., List<Parameter> params) {
    this.params = params == null ? List.of() : List.copyOf(params);
}

public @NotNull @Unmodifiable List<Parameter> getParams() {
    return params;  // Already immutable, no wrapping needed
}
```

---

### Phase 8: Generic Types and Raw Type Elimination

#### 8.1: The Cardinal Sins of Type Erasure

The codebase has several patterns that destroy type safety:

**Sin #1: Double-cast type erasure**
```java
// Found in Parser.java:1572, TypeCompositionStatement.java:933,1290
(List<Statement>) (List) init  // DESTROYS type information!
(List<Component>) (List) componentList  // Same antipattern

// This compiles but is type-unsafe. The intermediate (List) erases
// generic type, allowing any List to be cast to any other List.
```

**Sin #2: Raw Iterator cast in ListMap**
```java
// ListMap.java:127 - inside entrySet()
return (Iterator) m_list.iterator();  // Raw cast!
// Loses type: Iterator<SimpleEntry<K,V>> → Iterator → Iterator<Entry<K,V>>
```

**Sin #3: Raw list cast in ListMap.asList()**
```java
// ListMap.java:73
List<Entry<K,V>> list = (List) m_list;  // Raw cast!
```

**These patterns exist because the authors didn't parameterize properly.**

#### 8.2: LinkedIterator - A Misguided Utility

`LinkedIterator` combines multiple iterators into one. The problem is how it's used:

```java
// FileStructure.java:852 - loses type information
return new LinkedIterator(
    Collections.singleton(m_pool).iterator(),  // Iterator<ConstantPool>
    children().iterator());                     // Iterator<Component>
// Return type: Iterator<? extends XvmStructure>

// ClassStructure.java:3357 - same pattern
return new LinkedIterator(
    super.getContained(),      // Iterator<? extends XvmStructure>
    listAnno.iterator());      // Iterator<Annotation>
```

**The Real Problem:** These uses combine iterators of DIFFERENT types!
- If types were properly constrained, you couldn't do this
- The solution: DON'T combine heterogeneous iterators
- Use proper collection concatenation or explicit type handling

**Better Approach: Just use List concatenation**
```java
// BEFORE: LinkedIterator destroys type info
return new LinkedIterator(iter1, iter2);

// AFTER: Proper list concatenation (when types match)
List<XvmStructure> result = new ArrayList<>(collection1);
result.addAll(collection2);
return result.iterator();

// Or with streams:
return Stream.concat(stream1, stream2).iterator();
```

**When types don't match, it's a design smell** - the caller should handle
heterogeneous types explicitly, not hide it behind a raw iterator.

#### 8.3: ListMap - Should Be Deprecated

`ListMap` is a custom ordered map used **208 times** in the codebase.

**What it does:**
- Maintains insertion order (like `LinkedHashMap`)
- O(n) lookup (linear search through ArrayList)
- Has `asList()` and `entryAt()` methods for indexed access

**Why it's problematic:**
1. **O(n) lookup vs O(1)**: `LinkedHashMap` is O(1) for lookup
2. **Raw type usage**: `ListMap.EMPTY` is raw `ListMap` (no generics)
3. **Custom API**: `asList()`, `entryAt()` create API lock-in

**Bridge/Runtime dependency check:**
- `javatools_bridge` has 2 files mentioning ListMap but in `.x` files (Ecstasy)
- **ListMap is NOT used by native bridge code** - it's purely internal Java

**Memory comparison (per entry):**
| Structure | Memory Overhead |
|-----------|----------------|
| ListMap (ArrayList<SimpleEntry>) | ~32 bytes/entry (ArrayList + SimpleEntry objects) |
| LinkedHashMap | ~48 bytes/entry (Entry + before/after links) |

LinkedHashMap uses ~50% more memory per entry, but:
- Provides O(1) vs O(n) lookup
- For maps with >10 entries, time savings vastly outweigh memory cost
- Most ListMaps in the code are small, so memory difference is negligible

**Migration Strategy:**
1. Create `OrderedMap<K,V>` interface with `asList()` and `entryAt()` methods
2. Implement `OrderedMap` using `LinkedHashMap` with index access via `List.copyOf(entrySet())`
3. Deprecate `ListMap`
4. Migrate 208 usages over time

```java
// New interface
public interface OrderedMap<K, V> extends Map<K, V> {
    List<Entry<K, V>> asList();  // Returns unmodifiable list
    Entry<K, V> entryAt(int index);
}

// Implementation (thin wrapper)
public class LinkedOrderedMap<K, V>
        extends LinkedHashMap<K, V>
        implements OrderedMap<K, V> {

    @Override
    public List<Entry<K, V>> asList() {
        return List.copyOf(entrySet());
    }

    @Override
    public Entry<K, V> entryAt(int index) {
        // For small maps, linear is fine. For large, cache the list.
        return asList().get(index);
    }
}
```

#### 8.4: Raw Type Usage Patterns Found

**Files with `@SuppressWarnings("unchecked")`:**
- `AstNode.java:263` - Generic child handling
- `LambdaExpression.java:87` - Type casting
- `ListMap.java:35` - EMPTY_ARRAY_LIST cast
- `LinkedIterator.java:28-30` - Empty array cast

**Raw type casts (`(List)`, `(Map)`, `(Iterator)`):**
- `ListMap.java:73,127` - Internal raw casts
- `Parser.java:1572` - ForStatement init list
- `TypeCompositionStatement.java:933,1290` - Component lists
- `NativeContainer.java:494` - System.getProperties() cast
- `AstNode.java:2086` - Iterator cast in child iteration

#### 8.5: Generic Improvements

```java
// BEFORE: Casting everywhere
Object result = map.get(key);
if (result instanceof TypeConstant) {
    TypeConstant type = (TypeConstant) result;
}

// AFTER: Proper generics
Map<String, TypeConstant> typeMap = new HashMap<>();
TypeConstant type = typeMap.get(key);  // No cast needed
```

#### 8.6: Helper Method Generics

Many helper methods can be made generic for better type safety:

```java
// BEFORE
protected boolean tryReplaceInList(AstNode oldChild, AstNode newChild, List list) {
    // Unsafe casts inside
}

// AFTER
protected <T extends AstNode> boolean tryReplaceInList(
        T oldChild, T newChild, List<T> list) {
    // Type-safe
}
```

#### 8.7: Object as Generic Escape Hatch

**The `Map<Object, ...>` antipattern is used extensively:**

```java
// TypeInfo.java - fields typed as Object
Map<Object, PropertyInfo> f_mapVirtProps;   // keyed by "nested id"
Map<Object, MethodInfo>   f_mapVirtMethods; // keyed by "nested id"
Map<Object, ParamInfo>    f_mapTypeParams;  // keyed by String OR NestedIdentity
```

**What is a "nested identity"?**
The key can be one of:
- `String` - property name for directly nested properties
- `SignatureConstant` - method signature for directly nested methods
- `NestedIdentity` - inner class of IdentityConstant for deeply nested members

**Why Object was used:**
```java
// This is the pattern throughout TypeConstant.java
Object nid = id.resolveNestedIdentity(pool, resolver);
mapVirtProps.put(nid, propInfo);  // nid could be String OR NestedIdentity
```

**The Proper Solution: A common interface**
```java
// Define an interface for all things that can be nested identity keys
public sealed interface NestedId
        permits PropertyNestedId, MethodNestedId, DeepNestedId {
    int hashCode();
    boolean equals(Object obj);
}

// Wrapper for String property names
record PropertyNestedId(String name) implements NestedId {}

// Wrapper for method signatures
record MethodNestedId(SignatureConstant sig) implements NestedId {}

// The existing NestedIdentity refactored
record DeepNestedId(IdentityConstant id, GenericTypeResolver resolver)
        implements NestedId {}

// Then maps become properly typed:
Map<NestedId, PropertyInfo> mapVirtProps;
Map<NestedId, MethodInfo>   mapVirtMethods;
```

**Files with `Map<Object, ...>` patterns:**
- `TypeInfo.java` - f_mapVirtProps, f_mapVirtMethods, f_mapTypeParams, f_cacheByNid
- `TypeConstant.java` - mapVirtProps, mapVirtMethods throughout layerOn* methods
- `ConstantPool.java` - m_mapLocators (Map<Object, Constant>)
- `PropertyClassTypeConstant.java` - same patterns
- `ClassStructure.java` - mapFields (Map<Object, FieldInfo>)

#### 8.8: Mutable HashMap Keys - A Java Contract Violation

**The Problem:**

`NestedIdentity` is used as a HashMap key but is **mutable**:

```java
public class NestedIdentity {
    private final GenericTypeResolver m_resolver;  // FINAL but...

    // ... the resolver can be a TypeConstant which IS mutable!

    @Override
    public int hashCode() {
        // Uses m_resolver.resolve() which can return different values
        // if the resolver's state changes!
    }

    public boolean isCacheable() {
        return m_resolver == null || m_resolver instanceof TypeConstant;
    }
}
```

**Java HashMap Contract Violation:**
> "If an object's hashCode changes while it's a key in a HashMap,
> the entry becomes unreachable even though it's still there."

**Current workaround:** The `isCacheable()` check, but it's not enforced.

**Constant's Lazy HashCode:**
```java
// Constant.java
private int m_iHash;  // Lazy-computed, cached

public int hashCode() {
    int iHash = m_iHash;
    if (iHash == 0) {
        iHash = computeHashCode();  // Expensive computation
        m_iHash = iHash;
    }
    return iHash;
}
```

This is **safe** because:
1. hashCode never changes after first computation
2. Constants are structurally immutable (content doesn't change)

But NestedIdentity's resolver CAN affect the hash, making it **unsafe** as a key
when the resolver is mutable.

**Recommended Fix:**
1. Make NestedIdentity a record (immutable by design)
2. Pre-resolve the identity at construction time
3. Store only the resolved values, not the resolver
4. Use the `NestedId` sealed interface approach above

---

### Phase 9: Final Fields and Immutability Preparation

#### 9.1: The Lazy Utility

A new `Lazy<T>` utility class has been added to `org.xvm.util` to enable final fields
for lazily-computed values. This replaces the "null-check in getter" pattern:

```java
// BEFORE: Mutable field with null-check (can't be final)
private TypeInfo m_typeInfo;

public TypeInfo getTypeInfo() {
    if (m_typeInfo == null) {
        m_typeInfo = computeTypeInfo();  // Reassignment prevents final
    }
    return m_typeInfo;
}

// AFTER: Final field with Lazy
private final Lazy<TypeInfo> typeInfo = Lazy.of(this::computeTypeInfo);

public TypeInfo getTypeInfo() {
    return typeInfo.get();
}
```

**Lazy<T> Features:**
- `Lazy.of(supplier)` - Thread-safe memoization (double-checked locking)
- `Lazy.ofUnsafe(supplier)` - Non-thread-safe for single-threaded contexts
- `Lazy.ofValue(value)` - Pre-initialized (for testing/API compatibility)
- `isInitialized()` - Check if value has been computed

**Benefits:**
1. Field can be `final` - enables true immutability
2. Thread-safe without synchronization at every call site
3. Computation happens exactly once
4. Clear intent - "lazy" not "maybe null"
5. Delegate is released after computation (GC friendly)

#### 9.2: Fields That Could Be Final

Many constructor-initialized fields could be `final`:

```java
// BEFORE: Mutable field, never reassigned
private Token name;
private List<Parameter> params;

// AFTER: Immutable by design
private final Token name;
private final List<Parameter> params;
```

**Identification strategy:**
1. Find fields initialized in constructor
2. Check if ever reassigned (grep for `this.field =` outside constructor)
3. Mark as `final` if never reassigned
4. Add `// TODO: make final when state is removed` for fields that need refactoring

#### 9.3: Converting Lazy Null-Check Patterns

Many fields follow this pattern and can use `Lazy<T>`:

```java
// Pattern: Check for null, compute if needed
private TypeInfo m_typeInfo;

public TypeInfo getTypeInfo() {
    TypeInfo info = m_typeInfo;
    if (info == null) {
        m_typeInfo = info = buildTypeInfo();
    }
    return info;
}

// Convert to:
private final Lazy<TypeInfo> typeInfo = Lazy.of(this::buildTypeInfo);

public TypeInfo getTypeInfo() {
    return typeInfo.get();
}
```

**Candidates for Lazy conversion:**
- TypeConstant.m_typeinfo
- TypeInfo.m_mapOps
- TypeInfo cached method lookups
- Component.m_mapChildByName
- Various cached computations in constants

#### 9.4: State That Prevents Finality

Some fields can't be final because of the current stage-based model:

```java
// Can't be final - set during registration phase
private Component m_component;  // TODO: make final when stage data externalized

// Can't be final - computed during validation
private TypeConstant m_type;  // TODO: make final when using copy-on-write
```

Add comments to track these:

```java
// TODO: [IMMUTABILITY] This field prevents node immutability.
// Move to external CompilationContext or use copy-on-write transform.
private transient Context m_ctx;
```

---

### Phase 10: Thread Safety Documentation

#### 10.1: Current Thread Safety Issues

**The asm package has significant thread safety concerns:**

| Location | Issue | Impact |
|----------|-------|--------|
| `ConstantPool:178` | `synchronized (this)` during register | Contention during parallel compilation |
| `TypeConstant:1632` | `synchronized` on `ensureTypeInfo` | Blocks concurrent type resolution |
| `TypeInfo:962,1003,1758,1940,2020,2113,2142` | Multiple `synchronized` methods | Serializes type info access |
| `Component:727` | `synchronized ensureChildByNameMap()` | Blocks component traversal |

**Volatile fields indicating concurrent access:**
- `TypeConstant.m_typeinfo` - TypeInfo cache
- `TypeConstant.m_mapRelations` - Relation cache
- `TypeConstant.m_cInvalidations` - Invalidation counter
- `ConstantPool.m_mapConstants` - Constant lookup
- `ConstantPool.m_cInvalidated` - Invalidation tracking

#### 10.2: Why Immutability Fixes This

With immutable nodes and copy-on-write:

```java
// CURRENT: Mutable, requires synchronization
public synchronized TypeInfo ensureTypeInfo(ErrorListener errs) {
    if (m_typeinfo == null) {
        m_typeinfo = buildTypeInfo(errs);  // Race condition without sync
    }
    return m_typeinfo;
}

// FUTURE: Immutable, no synchronization needed
// TypeInfo stored in external cache keyed by immutable type identity
public TypeInfo getTypeInfo(CompilationContext ctx) {
    return ctx.getTypeInfoCache().computeIfAbsent(this, t -> buildTypeInfo(ctx));
}
```

**Benefits of immutability for thread safety:**
1. No synchronization needed for immutable data
2. Lock-free read access to type information
3. Parallel compilation without contention
4. Safe caching with identity-based keys

#### 10.3: Documentation Strategy

Add comments explaining thread safety issues:

```java
// THREAD-SAFETY: This synchronized block is required because TypeInfo
// is cached on the mutable TypeConstant. With immutable nodes and external
// caching, this synchronization would be unnecessary.
// See: Phase 10 in ast-refactoring-plan.md
public synchronized TypeInfo ensureTypeInfo(...) {
```

---

### Virtual List Nodes (Alternative/Addition)

Instead of `List<Parameter>` as a field, have explicit list nodes:

```java
class ParameterList extends AstNode {
    private final List<Parameter> elements;

    @Override
    public void forEachChild(Consumer<AstNode> visitor) {
        elements.forEach(visitor);
    }
}
```

**Benefits:**
- Every child is a single AstNode reference
- Clone is uniform
- Better matches grammar structure
- Cleaner for copy-on-write

---

## Open Questions

1. **Stage information**: Where does it live in a copy-on-write world?
   - External map keyed by node identity?
   - Separate "annotated tree" wrapper?

2. **Parent references**: How to handle?
   - Roslyn: manufacture on-demand during traversal
   - Trade-off: convenience vs memory

3. **replaceChild use case**: Only used for TraceExpression wrapping
   - Could this be handled differently?
   - Maybe a post-validation transform pass?

4. **Incremental compilation**: Is this a goal?
   - Roslyn-style persistence only matters for incremental
   - If not needed, simpler approaches work

---

## Next Steps

### AST Refactoring (Phase 1-4)

1. [x] Implement `forEachChild(Function<AstNode, T>)` across all AST nodes
2. [x] Keep `children()` as convenience method built on `forEachChild`
3. [x] Implement `replaceChild()` explicitly per class
4. [x] Add `@Derived` annotation for non-structural fields
5. [~] Implement `withChildren(List<AstNode>)` on each node class (in progress)
6. [x] Add `copy()` method in base class using `withChildren()`
7. [ ] Replace `transient` keyword with `@Derived` on computed fields in AST
8. [ ] Remove old reflection-based `clone()` method from AstNode
9. [ ] Remove `getChildFields()`, `fieldsForNames()`, `ChildIterator`, `ChildIteratorImpl`
10. [ ] Update `getDumpChildren()` to use explicit field access or remove
11. [ ] Add `transform()` method with `VisitResult` pattern
12. [ ] Consider virtual list nodes for cleaner structure

### Constant Pool Refactoring (Phase 5)

13. [ ] Create/share `@Derived` annotation for `org.xvm.asm` package
14. [ ] Tag `transient` fields with `@Derived` in Constant subclasses
15. [ ] Tag `transient` fields with `@Derived` in TypeConstant subclasses
16. [ ] Implement `copyTo(ConstantPool)` on leaf constants (IntConstant, StringConstant, etc.)
17. [ ] Implement `copyTo(ConstantPool)` on TypeConstant subclasses
18. [ ] Implement `copyTo(ConstantPool)` on IdentityConstant subclasses
19. [ ] Implement `copyTo(ConstantPool)` on SignatureConstant
20. [ ] Update `ConstantPool.register()` to use `copyTo()` instead of `adoptedBy()`
21. [ ] Remove `adoptedBy()` method from Constant
22. [ ] Remove `Cloneable` interface from Constant
23. [ ] Tag `transient` fields with `@Derived` in Component hierarchy
24. [ ] Implement explicit copy for Component bifurcation
25. [ ] Remove `Cloneable` interface from Component, Parameter, etc.

### Array to List Migration (Phase 6)

26. [ ] Add List-based `getParams()` alongside `getParamArray()` in MethodStructure
27. [ ] Add List-based `getReturns()` alongside `getReturnArray()` in MethodStructure
28. [ ] Mark array-returning methods as `@Deprecated`
29. [ ] Update internal callers to use List APIs
30. [ ] Convert internal array storage to List storage where safe
31. [ ] Document bridge/runtime boundary methods that must stay as arrays

### Null Safety (Phase 7)

32. [ ] Add `@NotNull` to constructor parameters that must not be null
33. [ ] Add `@Nullable` to parameters and returns that may be null
34. [ ] Convert null-or-empty collections to always-empty pattern in PropertyStructure
35. [ ] Convert null-or-empty collections to always-empty pattern in ClassStructure
36. [ ] Convert null-or-empty collections to always-empty pattern in MethodInfo
37. [ ] Convert null-or-empty collections to always-empty pattern in PropertyInfo
38. [ ] Convert null-or-empty collections to always-empty pattern in TypeConstant
39. [ ] Add `@Unmodifiable` to List/Set/Map return types
40. [ ] Store collections as immutable using `List.copyOf()` in constructors

### Generic Types and Raw Type Elimination (Phase 8)

41. [ ] Fix double-cast type erasure in Parser.java:1572 (`(List<Statement>) (List)`)
42. [ ] Fix double-cast type erasure in TypeCompositionStatement.java:933,1290
43. [ ] Fix raw type cast in NativeContainer.java:494
44. [ ] Create `OrderedMap<K,V>` interface with `asList()` and `entryAt()` methods
45. [ ] Create `LinkedOrderedMap<K,V>` implementation wrapping `LinkedHashMap`
46. [ ] Deprecate `ListMap` class
47. [ ] Migrate ListMap usages to LinkedOrderedMap (208 usages)
48. [ ] Fix raw Iterator cast in AstNode.java:2086
49. [ ] Fix raw casts in ListMap.java (internal)
50. [ ] Eliminate `LinkedIterator` usage - replace with proper list concatenation
51. [ ] Eliminate `@SuppressWarnings("unchecked")` in AstNode.java
52. [ ] Eliminate `@SuppressWarnings("unchecked")` in LambdaExpression.java
53. [ ] Add proper generics to helper methods
54. [ ] Create `NestedId` sealed interface for type-safe nested identity keys
55. [ ] Replace `Map<Object, PropertyInfo>` with `Map<NestedId, PropertyInfo>` in TypeInfo
56. [ ] Replace `Map<Object, MethodInfo>` with `Map<NestedId, MethodInfo>` in TypeInfo
57. [ ] Replace `Map<Object, ...>` patterns in TypeConstant, ConstantPool, ClassStructure
58. [ ] Convert NestedIdentity to immutable record (pre-resolve at construction)
59. [ ] Audit all HashMap key classes for mutability issues

### Final Fields and Immutability (Phase 9)

60. [x] Create `Lazy<T>` utility class in `org.xvm.util`
61. [ ] Identify constructor-initialized fields that can be `final`
62. [ ] Make Token fields final across AST nodes
63. [ ] Make child list fields final (store as immutable)
64. [ ] Convert TypeConstant.m_typeinfo to use `Lazy<TypeInfo>`
65. [ ] Convert TypeInfo cached lookups to use `Lazy<T>`
66. [ ] Convert Component.m_mapChildByName to use `Lazy<Map>`
67. [ ] Add `// TODO: [IMMUTABILITY]` comments to mutable state fields
68. [ ] Document which fields block copy-on-write transformation

### Thread Safety Documentation (Phase 10)

69. [ ] Add `// THREAD-SAFETY:` comments to synchronized methods in ConstantPool
70. [ ] Add `// THREAD-SAFETY:` comments to synchronized methods in TypeConstant
71. [ ] Add `// THREAD-SAFETY:` comments to synchronized methods in TypeInfo
72. [ ] Document volatile fields and their purpose
73. [ ] Create issue tracking thread safety concerns for future resolution

### Phase 11: Error Handling Architecture for LSP/DAP

The current error handling is fragmented and incompatible with LSP/DAP requirements.
This phase designs a unified error handling architecture with IDE integration as the
primary concern.

#### 11.1: Current Error Handling Problems

**Multiple conflicting patterns:**

| Pattern | Location | Problem |
|---------|----------|---------|
| `ErrorListener` interface | Compiler/AST | Can be null, swapped for BLACKHOLE |
| `RuntimeException` throws | Everywhere (~250 in compiler) | Breaks control flow, can't recover |
| `CompilerException` | Parser/Compiler | Extends RuntimeException, unchecked |
| `LauncherException` | Tool layer | Also RuntimeException |
| `log()` with FATAL throws | Launcher | Hides control flow in log method |
| BLACKHOLE listener | 40+ usages | Silently swallows errors |

**Specific issues:**

1. **ErrorListener can be null** - Many methods accept nullable ErrorListener
2. **BLACKHOLE swallows errors** - Used to "probe" operations but loses all diagnostics
3. **RuntimeExceptions everywhere** - `IllegalStateException`, `IllegalArgumentException`
4. **Severity-based abort** - ErrorList triggers abort after N errors, but abort via return value
5. **Log-throws-exception** - `log(FATAL, ...)` throws, breaking control flow analysis
6. **No structured diagnostic API** - ErrorInfo is a class, not structured for LSP

#### 11.2: What LSP/DAP Needs

**LSP (Language Server Protocol):**
- All diagnostics collected and returned, never thrown
- Diagnostics associated with source locations (file, line, column, range)
- Diagnostics persisted between compilations for unchanged files
- Incremental updates: only new/changed diagnostics sent to client
- Diagnostic severity: Error, Warning, Information, Hint
- Diagnostic codes for filtering and quick-fixes
- Related information (e.g., "did you mean X?")

**DAP (Debug Adapter Protocol):**
- Runtime errors as structured exceptions
- Stack traces with source locations
- Evaluation errors for watch expressions
- Clean error messages for user display

**Key insight from Roslyn:**
> "The Compiler API layer exposes diagnostics through an extensible API that allows
> user-defined analyzers to be plugged into the compilation process."
> - [Roslyn Architecture](https://learn.microsoft.com/en-us/dotnet/csharp/roslyn-sdk/compiler-api-model)

**Key insight from LLVM:**
> "`Expected<T>` is a tagged union holding either a T or an Error... All Error instances
> must be checked before destruction, even if they're Success values."
> - [LLVM Error Handling](https://llvm.org/doxygen/classllvm_1_1Expected.html)

#### 11.3: Proposed Architecture

**A. DiagnosticBag - The Core Abstraction**

```java
/**
 * Thread-safe, immutable-after-build collection of diagnostics.
 * This is the OUTPUT of compilation, not a mutable accumulator.
 */
public interface DiagnosticBag extends Iterable<Diagnostic> {

    /** All diagnostics in this bag */
    List<Diagnostic> all();

    /** Diagnostics for a specific source file */
    List<Diagnostic> forSource(Source source);

    /** Check severity levels */
    boolean hasErrors();
    boolean hasWarnings();
    Severity maxSeverity();

    /** For LSP: diagnostics changed since a previous compilation */
    DiagnosticDelta deltaFrom(DiagnosticBag previous);
}
```

**B. Diagnostic - Structured for LSP**

```java
/**
 * A single diagnostic (error, warning, hint).
 * Immutable record designed for LSP serialization.
 */
public record Diagnostic(
    @NotNull Severity severity,
    @NotNull String code,           // e.g., "XTC-101"
    @NotNull String message,        // Already formatted
    @NotNull Source source,
    @NotNull SourceRange range,     // Start and end positions
    @Nullable List<DiagnosticRelatedInfo> relatedInfo,
    @Nullable List<String> tags     // e.g., "deprecated", "unnecessary"
) {
    public record SourceRange(int startLine, int startCol, int endLine, int endCol) {}
    public record DiagnosticRelatedInfo(Source source, SourceRange range, String message) {}
}
```

**C. DiagnosticCollector - Mutable Builder**

```java
/**
 * Collects diagnostics during compilation. This is passed around
 * and MUST NOT be null. Use DiagnosticCollector.NULL_SAFE for probing.
 */
public final class DiagnosticCollector {

    /** Add a diagnostic */
    public void add(Diagnostic diag);

    /** Add with builder pattern */
    public DiagnosticBuilder error(String code);
    public DiagnosticBuilder warning(String code);

    /** Check if we should abort (after too many errors) */
    public boolean shouldAbort();

    /** Build immutable result */
    public DiagnosticBag build();

    /**
     * For probing operations - collects but doesn't report.
     * Unlike BLACKHOLE, diagnostics are still accessible!
     */
    public DiagnosticCollector fork();

    /** Merge forked diagnostics back (or discard) */
    public void merge(DiagnosticCollector forked);
    public void discard(DiagnosticCollector forked);

    /**
     * Safe "probing" pattern - replaces BLACKHOLE usage.
     * Returns both the result AND any diagnostics produced.
     */
    public static <T> ProbeResult<T> probe(Supplier<T> operation);

    public record ProbeResult<T>(T value, DiagnosticBag diagnostics) {
        public boolean succeeded() { return !diagnostics.hasErrors(); }
    }
}
```

**D. CompilationResult - Never Throws, Always Returns**

```java
/**
 * The result of a compilation operation.
 * This is what LSP receives - no exceptions to catch.
 */
public sealed interface CompilationResult permits
        CompilationResult.Success,
        CompilationResult.Failure {

    /** Diagnostics are ALWAYS available, even on failure */
    DiagnosticBag diagnostics();

    record Success(
        FileStructure module,
        DiagnosticBag diagnostics
    ) implements CompilationResult {}

    record Failure(
        DiagnosticBag diagnostics,
        @Nullable Throwable internalError  // Only for catastrophic failures
    ) implements CompilationResult {}
}
```

#### 11.4: Exception Strategy

**Checked exceptions for recoverable errors:**

```java
/**
 * Checked exception for errors that callers MUST handle.
 * Used for I/O errors, missing files, etc.
 */
public class CompilationIOException extends Exception {
    public CompilationIOException(String message, IOException cause) {
        super(message, cause);
    }
}
```

**Unchecked exceptions ONLY for programmer errors:**

```java
/**
 * For truly impossible states - bugs in the compiler itself.
 * NOT for user errors or recoverable situations.
 */
public class CompilerBugException extends RuntimeException {
    public CompilerBugException(String message) {
        super("INTERNAL ERROR: " + message + " - please report this bug");
    }
}
```

**Never throw for user errors:**

```java
// BEFORE: Throws for user errors
if (expr == null) {
    throw new CompilerException("expression required");
}

// AFTER: Collect diagnostic and return null/Optional
if (expr == null) {
    collector.error("XTC-101").message("expression required").at(node);
    return Optional.empty();
}
```

#### 11.5: LSP Integration API

```java
/**
 * API for Language Server implementation.
 */
public interface CompilerService {

    /**
     * Compile a source file and return diagnostics.
     * NEVER throws - all errors are in the result.
     */
    CompilationResult compile(
        Source source,
        ModuleRepository repo,
        CompilerOptions options
    );

    /**
     * Incremental recompile after edit.
     * Only recompiles affected files.
     */
    CompilationResult recompile(
        Source changedSource,
        CompilationResult previous
    );

    /**
     * Get diagnostics for a range (for LSP textDocument/diagnostic).
     * Returns cached diagnostics if available.
     */
    DiagnosticBag getDiagnostics(Source source);

    /**
     * Validate an expression in a context (for DAP evaluate).
     * Returns errors in result, never throws.
     */
    EvaluationResult validateExpression(
        String expression,
        Context context
    );
}
```

#### 11.6: Replacing BLACKHOLE

The 40+ usages of BLACKHOLE fall into categories:

| Category | Current | Replacement |
|----------|---------|-------------|
| Type probing | `type.ensureTypeInfo(BLACKHOLE)` | `DiagnosticCollector.probe(() -> ...)` |
| Tentative resolution | `resolveName(ctx, BLACKHOLE)` | `collector.fork()` / `merge()`/`discard()` |
| Suppressing cascading errors | `errs = BLACKHOLE` | `collector.suppressCascades(true)` |
| Expected failures | `selectCommonType(t1, t2, BLACKHOLE)` | Return `Optional<TypeConstant>` |

**Key principle:** Never lose diagnostic information. Even "probing" operations
should capture what went wrong for debugging and LSP hints.

#### 11.7: Logging Architecture

**Separate logging from diagnostics:**

```java
/**
 * Logging is for INTERNAL compiler debugging, not user-visible errors.
 * Use SLF4J for consistency with Java ecosystem.
 */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TypeResolver {
    private static final Logger LOG = LoggerFactory.getLogger(TypeResolver.class);

    public TypeConstant resolve(NameExpression name, DiagnosticCollector collector) {
        LOG.debug("Resolving name: {}", name);  // Debug output

        if (!canResolve(name)) {
            collector.error("XTC-202")
                .message("Cannot resolve name: {0}", name.getName())
                .at(name);
            return null;  // Don't throw!
        }

        // ...
    }
}
```

**Never throw from log methods:**

```java
// BEFORE: log() can throw for FATAL - breaks control flow analysis
log(FATAL, "Something bad happened");  // Throws LauncherException!

// AFTER: Explicit returns for control flow
if (catastrophicFailure) {
    LOG.error("Internal error: {}", details);
    collector.fatal("XTC-001").message("Internal compiler error").at(node);
    return CompilationResult.internalError(details);
}
```

#### 11.7a: Code Modernization (Java 17+ Idioms)

As refactoring proceeds, modernize legacy patterns incrementally:

**StringBuilder and String Construction:**

```java
// BEFORE: Java 1.0 style
StringBuilder sb = new StringBuilder();
boolean isFirst = true;
for (Element e : elements) {
    if (isFirst) {
        isFirst = false;
    } else {
        sb.append(", ");
    }
    sb.append(e.toString());
}
return sb.toString();

// AFTER: Modern Java
return elements.stream()
    .map(Object::toString)
    .collect(Collectors.joining(", "));
```

**Complex toString() methods:**
- Use text blocks for templates where appropriate
- Use `String.format()` or `MessageFormat` for complex formatting
- Replace imperative loops with stream collectors
- Use `var` for StringBuilder: `var sb = new StringBuilder()`

**When to use `var`:**
- Clear from RHS: `var pool = getConstantPool()` ✓
- Constructor call: `var sb = new StringBuilder()` ✓
- Generic instantiation: `var list = new ArrayList<String>()` ✓
- NOT when type is unclear: `var result = process()` ✗

**Patterns to flag for modernization:**
- `boolean isFirst = true` loops for comma-separating
- Manual StringBuilder concatenation of list elements
- Nested if-else chains that should be switch expressions
- Pre-Java 8 collection manipulation

**Immutability preferences:**
- Fields should be `final` unless there's a specific reason to mutate
- Prefer `List<T>` over `T[]` for parameters and return values
- Use `List.of()` or `Collections.unmodifiableList()` for immutable lists
- Consider `@Unmodifiable` annotation for non-modifiable collection returns

**Null safety patterns to investigate:**
- `Expression.getType()` can return null - callers should check after validation
- `TypeConstant.getParamType(0)` assumes parameterized type - needs null check
- Example in IsExpression.validateMulti(): `exprTest.getType().getParamType(0)` chains
  could NPE if getType() returns null - needs defensive check or @NotNull guarantee

**Ternary Operator (?:) Hygiene:**

The ternary operator combines dataflow and control flow in a single expression, which is
problematic when type and null hygiene is poor. Frivolous use creates hard-to-debug issues.

```java
// PROBLEMATIC - nested ternaries, unclear null semantics
String result = a != null ? a.getValue() : b != null ? b.getValue() : defaultValue;

// PROBLEMATIC - side effects in branches
TypeConstant type = expr == null ? getDefault() : expr.validate(ctx, errs).getType();

// PROBLEMATIC - null checks that hide control flow
return value != null ? value : computeExpensiveDefault();  // when is default called?
```

**Approved patterns:**
```java
// OK: Simple null guards with Objects utility
this.name = Objects.requireNonNull(name, "name is required");
this.value = Objects.requireNonNullElse(value, DEFAULT);
this.list = Objects.requireNonNullElseGet(list, ArrayList::new);

// OK: Simple selection with clear types and no null involvement
int max = (a > b) ? a : b;

// OK: Simple boolean-based selection
String label = isEnabled ? "ON" : "OFF";

// OK: Coalesce pattern when both sides are provably non-null
TypeConstant effectiveType = typeExplicit != null ? typeExplicit : typeInferred;
```

**Patterns to refactor:**
```java
// BEFORE: Nested ternaries
return a != null ? a : b != null ? b : c;

// AFTER: Explicit if-else (clearer control flow)
if (a != null) return a;
if (b != null) return b;
return c;

// AFTER: Or use Objects utility
return Stream.of(a, b, c).filter(Objects::nonNull).findFirst().orElse(c);

// BEFORE: Ternary with method calls that might NPE
TypeConstant type = expr.getType() != null ? expr.getType().getParamType(0) : null;

// AFTER: Explicit null check with early return
TypeConstant exprType = expr.getType();
if (exprType == null) {
    return null;
}
TypeConstant type = exprType.getParamType(0);

// BEFORE: Ternary hiding validation
return isValid ? process(value) : handleError();

// AFTER: Explicit branching
if (isValid) {
    return process(value);
}
return handleError();
```

**Audit criteria for ternary usage:**
1. Are both branches provably non-null or is null explicitly intended?
2. Is there any method call in the expression that could return null?
3. Are there nested ternaries? (Always refactor)
4. Does either branch have side effects? (Prefer if-else)
5. Is the condition itself complex? (Extract to named boolean)

**Files with complex ternary usage to audit:**
- TypeConstant.java - many type resolution ternaries
- NameExpression.java - resolution logic
- Context.java - variable narrowing

#### 11.8: Defensive Exception Elimination

Many `throw new IllegalArgumentException` and `throw new IllegalStateException` calls are
defensive checks that indicate:
1. **Lack of trust in callers** - Parameters should be sanitized at API boundaries
2. **Missing @NotNull annotations** - If null is never valid, declare it
3. **Redundant checks** - If caller already validated, receiver doesn't need to re-validate

**Strategy: Sanitize at API boundaries, trust internally**

```java
// BEFORE: Defensive checks everywhere
public ClassStructure(ConstantPool pool, ...) {
    if (pool == null) {
        throw new IllegalArgumentException("pool required");  // Redundant!
    }
    // ...
}

// AFTER: @NotNull at boundary, no runtime check
public ClassStructure(@NotNull ConstantPool pool, ...) {
    this.pool = pool;  // Trusted - caller validated
}
```

**When to keep checks:**
- Public API entry points (user-facing)
- Deserialization from untrusted sources
- Bridge layer receiving values from runtime

**When to remove checks:**
- Internal constructor calls where caller already validated
- Methods called only from within the same package
- Parameters that are clearly non-null from context

**Parameter lifecycle tracking:**
For each major class, document:
1. Where is this type created? (factory, parser, user)
2. What validates inputs at creation time?
3. What invariants hold after construction?

This enables removing downstream defensive checks.

#### 11.9: Migration Strategy

**Phase 11a: Add @NotNull to ErrorListener parameters**
- Find all nullable ErrorListener parameters
- Add @NotNull annotations
- Pass DiagnosticCollector.NO_OP for "don't care" cases (NOT null)

**Phase 11b: Create Diagnostic/DiagnosticBag**
- New classes in `org.xvm.asm` or new `org.xvm.diagnostic` package
- ErrorInfo → Diagnostic migration helper
- DiagnosticBag built from ErrorList

**Phase 11c: Replace RuntimeException throws in compiler**
- IllegalStateException for impossible states → CompilerBugException
- CompilerException for user errors → Diagnostic + return null
- LauncherException → explicit returns

**Phase 11d: Replace BLACKHOLE with fork/merge pattern**
- Create DiagnosticCollector.probe() utility
- Migrate each BLACKHOLE usage to capture diagnostics

**Phase 11e: Add SLF4J logging**
- Add SLF4J dependency
- Replace System.out/err debug output
- Keep log() for Launcher but never throw

**Phase 11f: Create CompilerService API**
- New interface for LSP/DAP consumers
- Wrap existing Compiler class
- Never expose exceptions in API

#### 11.9: Tasks

80. [ ] Add @NotNull to all ErrorListener parameters in compiler package
81. [ ] Add @NotNull to all ErrorListener parameters in asm package
82. [ ] Create Diagnostic record with LSP-compatible structure
83. [ ] Create DiagnosticBag interface and immutable implementation
84. [ ] Create DiagnosticCollector with fork/merge pattern
85. [ ] Create DiagnosticCollector.probe() for BLACKHOLE replacement
86. [ ] Replace BLACKHOLE in TypeConstant (18 usages)
87. [ ] Replace BLACKHOLE in NameExpression (6 usages)
88. [ ] Replace BLACKHOLE in Context (2 usages)
89. [ ] Replace BLACKHOLE in other locations (~14 usages)
90. [ ] Create CompilerBugException for impossible states
91. [ ] Replace IllegalStateException in asm package with CompilerBugException
92. [ ] Replace CompilerException throws with Diagnostic collection
93. [ ] Replace LauncherException throws with explicit returns
94. [ ] Remove throw from Launcher.log(FATAL, ...)
95. [ ] Add SLF4J dependency to build.gradle
96. [ ] Create Logger instances in major classes
97. [ ] Replace System.out debug output with LOG.debug
98. [ ] Create CompilerService interface
99. [ ] Implement CompilerService wrapping existing Compiler
100. [ ] Create CompilationResult sealed interface
101. [ ] Update tool.Compiler to use new error handling
102. [ ] Update tool.Runner to use new error handling
103. [ ] Document error handling patterns in CLAUDE.md or separate doc
104. [ ] Audit IllegalArgumentException in ConstantPool - identify redundant checks
105. [ ] Audit IllegalArgumentException in ClassStructure - identify redundant checks
106. [ ] Audit IllegalStateException in compiler/ast - categorize by removable vs needed
107. [ ] Document API boundary validation points for AST nodes
108. [ ] Document API boundary validation points for Constants
109. [ ] Add @NotNull to constructor parameters where callers always validate
110. [ ] Remove redundant null checks after adding @NotNull annotations
111. [ ] Create parameter lifecycle documentation for major types

---

### Future Work (Phase 12+)

104. [ ] Move TypeInfo cache from TypeConstant to external CompilationContext
105. [ ] Design incremental type invalidation strategy
106. [ ] Implement LSP-friendly compilation API
107. [ ] Remove deprecated array APIs (after bridge migration)
108. [ ] Remove deprecated ListMap (after migration complete)
109. [ ] Remove deprecated LinkedIterator (after migration complete)

---

## Phase 12: Static Analysis Tools Integration

### 12.1: Overview

Static analysis tools can automatically detect many of the antipatterns identified in this plan.
This section documents which tools to use, what they find, and how to integrate them without
disrupting the normal build.

**Key principle:** Tools are configured but **disabled by default**. Developers opt-in via
Gradle properties or explicit task invocation. This prevents "freaking out" other developers
with thousands of new warnings while allowing incremental adoption.

### 12.2: Tool Selection and Capabilities

| Tool | Focus | Build-time | Best For |
|------|-------|-----------|----------|
| **Error Prone** | Bug patterns in source | Low (~5%) | General bugs, API misuse |
| **NullAway** | Null safety | Low (~5%) | NPE prevention, `@Nullable` enforcement |
| **SpotBugs** | Bytecode analysis | Medium | Concurrency bugs, resource leaks |
| **PMD** | Source patterns | Low | Dead code, complexity, style |
| **Checkstyle** | Formatting/style | Very low | Coding standards enforcement |

**Redundancy analysis:**
- Error Prone + NullAway: Complementary, both required for null safety
- SpotBugs vs Error Prone: ~30% overlap on null checks; SpotBugs finds bytecode-level issues
- PMD vs Checkstyle: Minimal overlap; PMD = logic patterns, Checkstyle = formatting
- Recommendation: Use all four, they catch different classes of bugs

### 12.3: What Each Tool Finds (Mapped to Plan Goals)

#### Error Prone Checks Relevant to This Plan

| Check | Plan Goal | What It Catches |
|-------|-----------|-----------------|
| `NullAway` | Null safety (11.7a) | NPE chains like `getType().getParamType(0)` |
| `Var` | Use `var` (11.7a) | Suggests where `var` can replace verbose types |
| `ImmutableEnumChecker` | Immutability (11.7a) | Mutable fields in enums |
| `MutableConstantField` | Final fields (11.7a) | Non-final static fields |
| `UnnecessaryBoxedVariable` | Performance | Boxed primitives that should be primitives |
| `StringBuilderInitWithChar` | StringBuilder (11.7a) | `new StringBuilder('c')` bug |
| `LoopOverCharArray` | Modernization | `str.toCharArray()` loops → streams |
| `EqualsGetClass` | Bug patterns | `getClass()` in equals (should use instanceof) |

#### SpotBugs Checks Relevant to This Plan

| Bug Pattern | Plan Goal | What It Catches |
|-------------|-----------|-----------------|
| `NP_*` | Null safety | Null dereferences, null returns |
| `RCN_*` | Null safety | Redundant null checks (proves non-null) |
| `OS_*` | Resource management | Unclosed streams |
| `IS2_*` | Concurrency | Inconsistent synchronization |
| `EI_*` | Immutability | Exposing internal representation |
| `MS_*` | Immutability | Mutable static fields |
| `PZLA_*` | Arrays → Lists | Prefer zero-length arrays (signals array usage) |

#### PMD Checks Relevant to This Plan

| Rule | Plan Goal | What It Catches |
|------|-----------|-----------------|
| `UnusedPrivateField` | Dead code | Fields that can be removed |
| `UnusedPrivateMethod` | Dead code | Methods that can be removed |
| `UnusedLocalVariable` | Dead code | Variables to remove |
| `AvoidReassigningParameters` | Immutability | Parameter mutation |
| `UseVarargs` | Modernization | Array params that should be varargs |
| `UseTryWithResources` | Modernization | Manual close() calls |
| `SimplifiableTestAssertion` | Modernization | Assert patterns to simplify |

### 12.4: Gradle Integration (Opt-In by Default)

```kotlin
// build.gradle.kts (common configuration)

// ============================================================
// STATIC ANALYSIS - Disabled by default, opt-in via properties
// ============================================================
// Run with: ./gradlew build -Panalyze
// Or specific: ./gradlew build -Panalyze.nullaway

val analyzeEnabled = project.hasProperty("analyze")
val nullawayEnabled = analyzeEnabled || project.hasProperty("analyze.nullaway")
val spotbugsEnabled = analyzeEnabled || project.hasProperty("analyze.spotbugs")
val pmdEnabled = analyzeEnabled || project.hasProperty("analyze.pmd")

plugins {
    id("net.ltgt.errorprone") version "4.1.0"
    id("net.ltgt.nullaway") version "2.1.0"
    id("com.github.spotbugs") version "6.0.7"
    id("pmd")
}

dependencies {
    errorprone("com.google.errorprone:error_prone_core:2.28.0")
    errorprone("com.uber.nullaway:nullaway:0.11.0")
    compileOnly("org.jspecify:jspecify:1.0.0")  // Null annotations
}

// Configure Error Prone and NullAway
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        // Always-on lightweight checks (no annotations needed)
        enable("StringBuilderInitWithChar")
        enable("EqualsGetClass")

        if (nullawayEnabled) {
            enable("NullAway")
            option("NullAway:AnnotatedPackages", "org.xvm")
            option("NullAway:TreatGeneratedAsUnannotated", "true")
            // Start as warnings, promote to errors as code is cleaned
            option("NullAway:ErrorLevel", "WARN")
        } else {
            disable("NullAway")
        }
    }
}

// Configure SpotBugs (disabled by default)
spotbugs {
    ignoreFailures = true  // Report but don't fail build
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    enabled = spotbugsEnabled
    reports {
        html.required = true
        xml.required = false
    }
}

// Configure PMD (disabled by default)
pmd {
    isIgnoreFailures = true
    toolVersion = "7.0.0"
    rulesets = listOf(
        "category/java/bestpractices.xml",
        "category/java/errorprone.xml",
        "category/java/codestyle.xml"
    )
}

tasks.withType<Pmd>().configureEach {
    enabled = pmdEnabled
}
```

### 12.5: Usage Patterns

```bash
# Normal build (no static analysis overhead)
./gradlew build

# Full static analysis
./gradlew build -Panalyze

# Just null safety checks
./gradlew build -Panalyze.nullaway

# Just SpotBugs
./gradlew spotbugsMain -Panalyze.spotbugs

# Generate reports without failing
./gradlew check -Panalyze --continue
```

### 12.6: Incremental Adoption Strategy

**Phase 1: Baseline (No code changes)**
110. [ ] Add Error Prone plugin, enable only non-disruptive checks
111. [ ] Add SpotBugs plugin (disabled by default)
112. [ ] Add PMD plugin (disabled by default)
113. [ ] Run full analysis, capture baseline report

**Phase 2: Low-Hanging Fruit**
114. [ ] Fix all `StringBuilderInitWithChar` issues
115. [ ] Fix all `EqualsGetClass` issues
116. [ ] Review SpotBugs `NP_NULL_ON_SOME_PATH` for obvious NPEs

**Phase 3: Null Safety Foundation**
117. [ ] Add JSpecify dependency (`org.jspecify:jspecify:1.0.0`)
118. [ ] Enable NullAway in WARN mode on `org.xvm.util` (smallest package)
119. [ ] Add `@Nullable` annotations to `org.xvm.util`, fix issues
120. [ ] Expand to `org.xvm.asm.constants` (next largest impact)

**Phase 4: Full Null Safety**
121. [ ] Expand NullAway to `org.xvm.asm`
122. [ ] Expand NullAway to `org.xvm.compiler.ast`
123. [ ] Promote NullAway from WARN to ERROR
124. [ ] Add `@NullMarked` package annotations for annotated packages

**Phase 5: Code Quality Enforcement**
125. [ ] Enable PMD unused-code rules
126. [ ] Enable SpotBugs concurrency rules
127. [ ] Add pre-commit hook for Error Prone (optional)

### 12.7: Custom Rules for Plan-Specific Patterns

Some patterns from this plan need custom detection:

**Ternary Hygiene (Custom PMD Rule):**
```xml
<rule name="AvoidNestedTernary"
      message="Nested ternary operators are hard to read; use if-else"
      class="net.sourceforge.pmd.lang.rule.xpath.XPathRule">
  <properties>
    <property name="xpath">
      <value>//ConditionalExpression//ConditionalExpression</value>
    </property>
  </properties>
</rule>
```

**StringBuilder in Loops (Custom Error Prone Check):**
Consider writing a custom Error Prone check for:
- `new StringBuilder()` inside loops (should be outside)
- `boolean isFirst` pattern (should use `Collectors.joining`)

### 12.8: JSpecify Annotations Reference

JSpecify provides standard null annotations for Java 9+:

```java
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;      // Usually implicit (default)
import org.jspecify.annotations.NullMarked;   // Package-level annotation

// Package annotation (package-info.java)
@NullMarked
package org.xvm.compiler.ast;

// Field/parameter/return annotations
public class Example {
    private @Nullable TypeConstant cachedType;  // May be null

    public TypeConstant getType() {             // Never returns null (default)
        return Objects.requireNonNull(cachedType, "not validated");
    }

    public @Nullable TypeConstant tryGetType() { // May return null
        return cachedType;
    }
}
```

### 12.9: Report Locations

When analysis is enabled, reports are generated at:
- Error Prone: Inline in compiler output
- SpotBugs: `build/reports/spotbugs/main.html`
- PMD: `build/reports/pmd/main.html`

---

## Appendix A: Binary AST (BAST) Analysis

### A.1: What is BAST?

The `org.xvm.asm.ast` package contains **Binary AST** nodes - a serializable AST format
designed for "an AST interpreter or back end compiler" (per BinaryAST.java:24).

**Key insight:** BAST preserves structured control flow, unlike XTC bytecode which uses
goto-style jumps. This is similar to WebAssembly's structured control flow design and
avoids the "bytecode verification hell" that Java suffers from.

**Current BAST node count:** ~53 classes in `org.xvm.asm.ast`

### A.2: Current Architecture

```
Source Code
     ↓ (Parser)
AST (org.xvm.compiler.ast.*)
     ↓ (emit phase)
  ┌──┴──────────────┐
  ↓                 ↓
XTC Bytecode     BinaryAST (BAST)
(Op, Code)       (org.xvm.asm.ast.*)
  ↓                 ↓
Interpreter      ??? (unused currently)
(runtime/)
  ↓
JavaJIT
(javajit/)
```

**Critical finding:** The JavaJIT (`org.xvm.javajit`) does NOT use BAST. It uses the
legacy XTC bytecode (`Op` classes). The BAST is generated and serialized but not consumed
by any current backend.

### A.3: Storage in MethodStructure

```java
// MethodStructure.java
private byte[] m_abAst;           // Serialized BAST bytes
private transient BinaryAST m_ast; // Deserialized BAST tree
```

The BAST is:
1. Generated during compilation (`setAst()` called from AST emit phase)
2. Serialized to bytes for storage in `.xtc` files
3. Can be deserialized back via `getAst()`
4. But never actually consumed for execution

### A.4: Array Usage in BAST Nodes

BAST nodes extensively use arrays internally:

| File | Array Fields |
|------|--------------|
| `StmtBlockAST` | `BinaryAST[] stmts` |
| `CallableExprAST` | `TypeConstant[] retTypes`, `ExprAST[] args` |
| `MultiExprAST` | `ExprAST[] exprs` |
| `SwitchAST` | `Constant[] cases`, `BinaryAST[] bodies`, `TypeConstant[] resultTypes` |
| `TryCatchStmtAST` | `ExprAST[] resources`, `BinaryAST[] catches` |
| `MapExprAST` | `ExprAST[] keys`, `ExprAST[] values` |
| `ForEachStmtAST` | `ExprAST[] specialRegs` |
| `WhileStmtAST` | `ExprAST[] specialRegs`, `ExprAST[] declaredRegs` |
| ... | (many more) |

### A.5: Could BAST Nodes Be Records?

**Yes, with significant refactoring.** Analysis of IfStmtAST as example:

```java
// CURRENT: Mutable class with separate read/write
public class IfStmtAST extends BinaryAST {
    private ExprAST   cond;
    private BinaryAST thenStmt;
    private boolean   hasElse;
    private BinaryAST elseStmt;

    IfStmtAST(NodeType nodeType) { ... }  // For deserialization

    public IfStmtAST(ExprAST cond, BinaryAST thenStmt, BinaryAST elseStmt) { ... }

    protected void readBody(DataInput in, ...) { ... }  // Mutates fields
    protected void writeBody(DataOutput out, ...) { ... }
}

// IDEAL: Immutable record
public record IfStmtAST(
    ExprAST cond,
    BinaryAST thenStmt,
    @Nullable BinaryAST elseStmt  // null means no else
) implements BinaryAST {

    public static IfStmtAST read(DataInput in, ConstantResolver res) {
        // Factory method for deserialization
        res.enter();
        ExprAST cond = ExprAST.read(in, res);
        res.enter();
        BinaryAST thenStmt = BinaryAST.read(in, res);
        res.exit();
        BinaryAST elseStmt = in.readBoolean() ? BinaryAST.read(in, res) : null;
        if (elseStmt != null) res.exit();
        res.exit();
        return new IfStmtAST(cond, thenStmt, elseStmt);
    }
}
```

**Key changes needed:**
1. Replace mutable fields with record components
2. Replace `readBody()` mutation with static factory methods
3. Replace arrays with `List<T>` (immutable)
4. Make `BinaryAST` a sealed interface instead of abstract class

### A.6: Array → List Migration for BAST

```java
// CURRENT
public class StmtBlockAST extends BinaryAST {
    private BinaryAST[] stmts;

    public BinaryAST[] getStmts() {
        return stmts; // note: caller must not modify!
    }
}

// IDEAL
public record StmtBlockAST(
    List<BinaryAST> stmts,
    boolean hasScope
) implements BinaryAST {

    public StmtBlockAST {
        stmts = List.copyOf(stmts);  // Immutable
    }
}
```

### A.7: BAST vs XTC Bytecode for JIT

**Why BAST might be better for JavaJIT:**

| Aspect | XTC Bytecode | BAST |
|--------|-------------|------|
| Control flow | Unstructured (goto) | Structured (if/while/for) |
| Optimization | Harder (CFG reconstruction) | Easier (structure preserved) |
| Type info | Encoded in ops | Explicit TypeConstants |
| SSA conversion | Complex | Simpler (scopes explicit) |
| Inlining | Difficult | Natural (tree structure) |

**Current JavaJIT approach:**
```java
// Builder.java uses Op (XTC bytecode)
import org.xvm.asm.Op;
// ... generates Java bytecode from Op sequence
```

**Potential BAST approach:**
```java
// Hypothetical BastCompiler.java
public class BastCompiler {
    void compile(IfStmtAST ifStmt, CodeBuilder code) {
        compile(ifStmt.cond(), code);
        Label elseLabel = code.newLabel();
        code.ifeq(elseLabel);
        compile(ifStmt.thenStmt(), code);
        if (ifStmt.elseStmt() != null) {
            Label endLabel = code.newLabel();
            code.goto_(endLabel);
            code.labelBinding(elseLabel);
            compile(ifStmt.elseStmt(), code);
            code.labelBinding(endLabel);
        } else {
            code.labelBinding(elseLabel);
        }
    }
}
```

### A.8: Recommendation

**Priority:** This is orthogonal to the main refactoring but could be valuable long-term.

**Phase 1:** Convert BAST nodes to records with List<T> instead of arrays
- Start with leaf nodes (ConstantExprAST, RegisterAST)
- Move up to compound nodes (IfStmtAST, WhileStmtAST)
- Finally complex nodes (SwitchAST)

**Phase 2:** Create a BAST → Java bytecode compiler
- Would replace or complement the XTC bytecode → Java bytecode path
- Benefits: cleaner code, better optimization opportunities
- Can leverage the structured control flow

**Phase 3:** Consider removing XTC bytecode entirely
- BAST provides everything needed
- Simpler architecture
- Smaller `.xtc` files (potentially)

---

## Appendix B: Related Work

- [WebAssembly Structured Control Flow](https://webassembly.github.io/spec/core/syntax/instructions.html#control-instructions)
- [GraalVM Truffle AST Interpreter](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/)
- [Kotlin IR (similar concept)](https://kotlinlang.org/docs/whatsnew14.html#new-ir-compiler-backend)

---

## References

- [Roslyn Red-Green Trees](https://ericlippert.com/2012/06/08/red-green-trees/) - Eric Lippert
- [Roslyn SyntaxNode.cs](https://github.com/dotnet/roslyn/blob/main/src/Compilers/Core/Portable/Syntax/SyntaxNode.cs)
- [Clang RecursiveASTVisitor](https://clang.llvm.org/docs/RAVFrontendAction.html)
- [TypeScript Compiler API](https://github.com/microsoft/TypeScript/wiki/Using-the-Compiler-API)
- [Clang AST Introduction](https://clang.llvm.org/docs/IntroductionToTheClangAST.html)
