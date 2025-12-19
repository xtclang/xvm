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
| `children()` iteration | Just traversal | Partially converted to explicit |
| `replaceChild()` | Find + write-back | Now explicit per-class |
| `clone()` | Clone + write to clone | **Still uses reflection** |
| `getDumpChildren()` | Field names for debug | **Still uses reflection** |
ay ay 
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

4. **Time Travel**: Keep old trees around for undo, diff, incremental compilation.

5. **Thread Safety**: Pure functions, no mutation, parallel analysis is safe.

**Relationship to Roslyn Red-Green:**

This is essentially the Roslyn pattern expressed through the visitor API:
- Green tree = the immutable nodes returned from transform
- Red tree = manufactured on-demand with parent refs (could be a thin wrapper)
- Structural sharing = nodes returning `Continue` keep same identity

### Phase 2: Explicit Clone

Replace reflection-based clone with explicit copy:

```java
// Each class implements
protected abstract AstNode cloneImpl();

// Example
@Override
protected BiExpression cloneImpl() {
    return new BiExpression(
        expr1.clone(),
        operator,
        expr2.clone()
    );
}
```

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

**Considerations:**
- Stage information would need to move outside nodes (compilation context)
- Parent references would be manufactured on-demand (like Roslyn red tree)
- Significant refactoring of stage processing

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

1. [ ] Implement `forEachChild(Consumer<AstNode>)` pattern across all AST nodes
2. [ ] Keep `children()` as a convenience method built on `forEachChild`
3. [ ] Convert `clone()` to explicit per-class implementation
4. [ ] Remove `getChildFields()` and reflection machinery
5. [ ] Add `transformChildren()` for future copy-on-write support
6. [ ] Consider virtual list nodes for cleaner structure

---

## References

- [Roslyn Red-Green Trees](https://ericlippert.com/2012/06/08/red-green-trees/) - Eric Lippert
- [Roslyn SyntaxNode.cs](https://github.com/dotnet/roslyn/blob/main/src/Compilers/Core/Portable/Syntax/SyntaxNode.cs)
- [Clang RecursiveASTVisitor](https://clang.llvm.org/docs/RAVFrontendAction.html)
- [TypeScript Compiler API](https://github.com/microsoft/TypeScript/wiki/Using-the-Compiler-API)
- [Clang AST Introduction](https://clang.llvm.org/docs/IntroductionToTheClangAST.html)
