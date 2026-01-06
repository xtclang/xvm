# Roslyn-Style Compiler Architecture for XVM

## Executive Summary

This document proposes a **parallel, drop-in replacement compiler** for XVM that uses Roslyn's
red/green tree architecture to achieve:

1. **True immutability** - No mutable state anywhere in the syntax tree
2. **Incremental compilation** - Only recompile what changed
3. **LSP compatibility** - First-class IDE integration with fast responses
4. **Zero copies** - Structural sharing via immutable nodes + external caches + `Lazy<T>`

The key insight: **separate the syntax tree (structure) from semantic information (derived state)**.

---

## Current Problems ("The Stateful Shit")

### 1. ~130 Mutable @Derived Fields on AST Nodes

```java
// Current: Each node accumulates mutable state during compilation
public class NameExpression extends Expression {
    // Structural fields
    private Expression left;
    private List<TypeExpression> params;

    // THE PROBLEM: Mutable derived state on each node
    @Derived private TargetInfo m_targetInfo;      // Set during ResolveName
    @Derived private Argument m_arg;               // Set during Validate
    @Derived private Plan m_plan = Plan.None;      // Set during Validate
    @Derived private MethodConstant m_idBjarnLambda;  // Set during Validate
    @Derived private TypeConstant m_typeCommon;    // Set during Validate
}
```

**Problems with mutable @Derived fields:**
- Can't share nodes between compilation runs
- Can't do incremental compilation (old state might be stale)
- Thread-unsafe (multiple threads could mutate same node)
- Testing is hard (must reset derived state between tests)
- `copy()` must decide what to preserve vs. recompute

### 2. Stage Processing Mutates Nodes In-Place

```java
// Current: StageMgr walks tree and mutates nodes
public void resolveNames(StageMgr mgr, ErrorListener errs) {
    // MUTATION: setting m_targetInfo on this node
    m_targetInfo = resolveTarget(ctx);

    // MUTATION: setting m_plan on this node
    m_plan = determinePlan(m_targetInfo);
}
```

### 3. Parent Pointers Create Reference Cycles

```java
// Current: Every node has a mutable parent reference
public class AstNode {
    private AstNode m_parent;  // Mutable, creates cycles
}
```

**Problems:**
- Can't structurally share subtrees (different parents)
- GC has to trace cycles
- Copying must fix up parent pointers

### 4. ConstantPool Interning Prevents Sharing

Constants are interned per-pool, so the same logical constant in different
compilation units can't be shared. Leads to:
- `transferTo()` complexity
- Memory waste from duplicate constants
- Thread contention on pool locks

---

## Roslyn Architecture Overview

Microsoft's Roslyn compiler separates syntax trees into two layers:

### Green Tree (Immutable Syntax)
- **Pure structure** - tokens, spans, children
- **No parent pointers** - enables structural sharing
- **Interned** - same source text = same green node
- **Persistent** - kept across edits (structural sharing)

### Red Tree (Navigation Facade)
- **Parent pointers** - computed on demand
- **Position info** - absolute positions computed lazily
- **Thrown away** - recreated when green tree changes
- **Cheap to create** - just wraps green nodes

### Semantic Model (External Cache)
- **Symbols** - types, methods, properties (cached externally)
- **Bound tree** - semantic version of syntax (lazy)
- **Diagnostics** - errors/warnings (cached per-node identity)

---

## Proposed XVM Architecture

### Layer 1: Green Syntax Nodes (Immutable)

```java
// NEW: Pure immutable syntax nodes
public abstract sealed class GreenNode permits GreenExpression, GreenStatement, ... {
    // Structural children only - no parent, no derived state
    private final List<GreenNode> children;
    private final int fullWidth;  // Cached span width

    // Factory method - interning via weak hash map
    public static <T extends GreenNode> T create(Class<T> type, Object... args) {
        return GreenNodeCache.intern(type, args);
    }

    // Navigation without parent pointers
    public GreenNode getChild(int index) {
        return children.get(index);
    }

    // Structural transformation (copy-on-write)
    public GreenNode withChild(int index, GreenNode newChild) {
        if (children.get(index) == newChild) return this;  // No change
        List<GreenNode> newChildren = new ArrayList<>(children);
        newChildren.set(index, newChild);
        return create(getClass(), newChildren, ...);  // Returns interned
    }
}

// Concrete green nodes are very simple
public final class GreenBinaryExpression extends GreenExpression {
    private final GreenExpression left;
    private final TokenKind operator;
    private final GreenExpression right;

    // Constructor validates and caches width
    private GreenBinaryExpression(GreenExpression left, TokenKind op, GreenExpression right) {
        this.left = left;
        this.operator = op;
        this.right = right;
    }
}
```

**Key properties:**
- `final` class with `final` fields
- No parent pointer
- No @Derived fields
- Factory method enables interning
- `withChild()` returns `this` if no change (structural sharing)

### Layer 2: Red Syntax Nodes (Navigation Facade)

```java
// NEW: Thin wrapper providing parent navigation
public class SyntaxNode {
    private final GreenNode green;
    private final SyntaxNode parent;  // Computed, not stored on green
    private final int position;       // Absolute position in source

    // Wrap a green node with navigation context
    public static SyntaxNode wrap(GreenNode green, SyntaxNode parent, int position) {
        return new SyntaxNode(green, parent, position);
    }

    // Navigation
    public SyntaxNode getParent() { return parent; }
    public int getPosition() { return position; }

    // Children are wrapped lazily
    public SyntaxNode getChild(int index) {
        GreenNode greenChild = green.getChild(index);
        int childPos = position + offsetOf(index);
        return wrap(greenChild, this, childPos);
    }

    // Walk up to root
    public SyntaxNode getRoot() {
        SyntaxNode node = this;
        while (node.parent != null) node = node.parent;
        return node;
    }
}
```

**Key properties:**
- Wraps immutable green node
- Parent computed at wrap time
- Thrown away on tree modification
- Cheap to recreate

### Layer 3: Semantic Model (External Cache)

```java
// NEW: All semantic info in external caches, keyed by green node identity
public class SemanticModel {
    // Caches keyed by GreenNode (identity, not equality)
    private final ConcurrentHashMap<GreenNode, Symbol> symbolCache;
    private final ConcurrentHashMap<GreenNode, TypeConstant> typeCache;
    private final ConcurrentHashMap<GreenNode, List<Diagnostic>> diagnosticCache;

    // Lazy computation with caching
    public Symbol getSymbol(SyntaxNode node) {
        return symbolCache.computeIfAbsent(node.getGreen(),
            green -> computeSymbol(node));
    }

    public TypeConstant getType(SyntaxNode expression) {
        return typeCache.computeIfAbsent(expression.getGreen(),
            green -> computeType(expression));
    }

    // Invalidation on source change
    public void invalidate(GreenNode oldNode) {
        symbolCache.remove(oldNode);
        typeCache.remove(oldNode);
        diagnosticCache.remove(oldNode);
        // Recursively invalidate children
        for (int i = 0; i < oldNode.getChildCount(); i++) {
            invalidate(oldNode.getChild(i));
        }
    }
}
```

**Key properties:**
- `ConcurrentHashMap.computeIfAbsent` for thread-safe lazy init
- Keyed by green node identity (not red wrapper)
- Survives red tree recreation
- Explicit invalidation on edit

---

## Incremental Compilation Strategy

### Edit Handling

```
Source Change: "x + 1" -> "x + 2"
                    ^
                    Only this token changed

1. PARSE incrementally:
   - Reuse green nodes for unchanged tokens
   - Only create new GreenLiteralExpression(2)
   - Parent GreenBinaryExpression gets new child -> new node
   - Grandparents unchanged -> structural sharing

2. INVALIDATE semantic cache:
   - Walk up from changed node
   - Invalidate type/symbol caches for affected nodes
   - Stop when reaching node whose type can't change

3. RECOMPUTE on demand:
   - LSP requests hover info -> recompute only that node's type
   - Full compile -> batch compute all invalidated nodes
```

### Structural Sharing Example

```
Before edit:
  GreenMethod("foo")           <- SHARED (unchanged)
    GreenBlock                 <- SHARED
      GreenReturn              <- SHARED
        GreenBinary("+")       <- NEW (child changed)
          GreenName("x")       <- SHARED
          GreenLiteral(1)      <- OLD (replaced)

After edit:
  GreenMethod("foo")           <- SAME OBJECT
    GreenBlock                 <- SAME OBJECT
      GreenReturn              <- SAME OBJECT
        GreenBinary("+")       <- NEW OBJECT (different child)
          GreenName("x")       <- SAME OBJECT
          GreenLiteral(2)      <- NEW OBJECT
```

**Memory: Only 2 new nodes created, rest are shared!**

---

## Implementation Strategy: Parallel Package

### Package Structure

```
org.xvm.compiler2/                    # NEW parallel package
    syntax/
        green/                        # Immutable green nodes
            GreenNode.java
            GreenExpression.java
            GreenStatement.java
            GreenBinaryExpression.java
            ... (one per syntax kind)
        red/                          # Navigation wrappers
            SyntaxNode.java
            ExpressionSyntax.java
            StatementSyntax.java
        SyntaxFactory.java            # Factory methods
        SyntaxKind.java               # Enum of all syntax kinds

    semantic/
        SemanticModel.java            # External caches
        Symbol.java                   # Type/method/property symbols
        BoundTree.java                # Semantic version of syntax
        TypeChecker.java              # Validation logic
        NameResolver.java             # Name resolution

    compilation/
        Compilation.java              # Immutable compilation state
        CompilationOptions.java       # Options
        DiagnosticBag.java           # Collect diagnostics
        IncrementalCompiler.java      # Edit -> recompile logic

    emit/
        Emitter.java                  # Code generation
        ConstantPoolBuilder.java      # Build constants
```

### Migration Path

```
Phase 1: Build compiler2 in parallel (no changes to compiler)
         - Start with green nodes for expressions
         - Add semantic model caches
         - Test with simple expressions

Phase 2: Bridge compiler2 to existing ConstantPool/Component
         - Emit to existing structures
         - Can compile simple modules

Phase 3: Add incremental support
         - Track dependencies
         - Invalidation logic
         - LSP integration

Phase 4: Feature parity
         - All syntax kinds
         - Full semantic analysis
         - Same output as compiler

Phase 5: Switch default
         - compiler2 becomes primary
         - Original compiler becomes legacy
```

---

## Specific Changes from Current Code

### 1. No @Derived Fields on Syntax Nodes

```java
// BEFORE (current)
public class NameExpression extends Expression {
    @Derived private TargetInfo m_targetInfo;
    @Derived private Argument m_arg;
    @Derived private Plan m_plan;
}

// AFTER (compiler2)
public final class GreenNameExpression extends GreenExpression {
    // ONLY structural fields, nothing derived
    private final List<GreenToken> names;
    private final List<GreenTypeExpression> typeParams;
}

// Derived info lives in SemanticModel
public class SemanticModel {
    private final Map<GreenNode, TargetInfo> targetInfoCache;
    private final Map<GreenNode, Argument> argumentCache;
    private final Map<GreenNode, Plan> planCache;
}
```

### 2. No Parent Pointers on Green Nodes

```java
// BEFORE (current)
public class AstNode {
    private AstNode m_parent;

    public AstNode getParent() { return m_parent; }

    // Must fix up parents after copy
    protected void adopt(List<AstNode> children) {
        for (AstNode child : children) {
            child.m_parent = this;
        }
    }
}

// AFTER (compiler2)
public abstract class GreenNode {
    // NO parent field at all
}

public class SyntaxNode {
    private final GreenNode green;
    private final SyntaxNode parent;  // Provided at wrap time

    public SyntaxNode getParent() { return parent; }
}
```

### 3. No Stages on Nodes

```java
// BEFORE (current)
public class AstNode {
    @Derived protected Stage m_stage = Stage.Initial;

    public Stage getStage() { return m_stage; }
    public void setStage(Stage stage) { m_stage = stage; }
}

// AFTER (compiler2)
public class SemanticModel {
    // Stage is tracked per-node in external map
    private final Map<GreenNode, Stage> stageCache;

    public Stage getStage(GreenNode node) {
        return stageCache.getOrDefault(node, Stage.Initial);
    }

    public void setStage(GreenNode node, Stage stage) {
        stageCache.put(node, stage);
    }
}
```

### 4. Lazy<T> for Expensive Computations

```java
// Use Lazy<T> within SemanticModel for expensive computations
public class SemanticModel {
    private final Map<GreenNode, Lazy<TypeInfo>> typeInfoCache = new ConcurrentHashMap<>();

    public TypeInfo getTypeInfo(SyntaxNode typeExpr) {
        return typeInfoCache
            .computeIfAbsent(typeExpr.getGreen(),
                g -> Lazy.of(() -> computeTypeInfo(typeExpr)))
            .get();
    }
}
```

---

## Incremental Parse Strategy

### Token-Level Reuse

```java
public class IncrementalParser {
    private final GreenNode oldTree;
    private final TextChange change;

    public GreenNode parse() {
        // Find affected span
        int changeStart = change.getStart();
        int changeEnd = change.getOldEnd();

        // Tokens before change -> reuse from old tree
        // Tokens in change region -> re-lex
        // Tokens after change -> reuse with adjusted positions

        return buildTree(reuseTokens(oldTree, changeStart),
                        lexNewTokens(change.getNewText()),
                        reuseTokens(oldTree, changeEnd, adjusted));
    }
}
```

### Node-Level Reuse

```java
public class IncrementalParser {
    // When re-parsing a method body, check if subtrees unchanged
    private GreenNode tryReuse(GreenNode oldNode, TokenStream newTokens) {
        // If tokens match exactly, return old node (structural sharing)
        if (tokensMatch(oldNode, newTokens)) {
            return oldNode;  // REUSE!
        }
        // Otherwise parse fresh
        return parseNew(newTokens);
    }
}
```

---

## LSP Integration

### Fast Responses via Caching

```java
public class XvmLanguageServer {
    private final Map<URI, Compilation> compilations = new ConcurrentHashMap<>();

    // Hover: Get type of expression under cursor
    public Hover hover(URI uri, Position pos) {
        Compilation comp = compilations.get(uri);
        SyntaxNode node = comp.findNode(pos);

        // Fast: Just lookup in cache
        TypeConstant type = comp.getSemanticModel().getType(node);
        return formatHover(type);
    }

    // Edit: Incremental recompile
    public void didChange(URI uri, TextChange change) {
        Compilation oldComp = compilations.get(uri);

        // Incremental parse (reuse unchanged nodes)
        GreenNode newTree = IncrementalParser.parse(oldComp.getSyntaxTree(), change);

        // Incremental semantic analysis (invalidate only affected)
        SemanticModel newModel = oldComp.getSemanticModel().withInvalidated(
            findAffectedNodes(oldComp.getSyntaxTree(), newTree));

        compilations.put(uri, new Compilation(newTree, newModel));

        // Diagnostics computed lazily on next request
    }
}
```

### Completion via Partial Analysis

```java
public class CompletionProvider {
    public List<CompletionItem> complete(SyntaxNode context) {
        // Don't need full compilation - just scope info
        Scope scope = semanticModel.getScope(context);

        return scope.getVisibleNames().stream()
            .map(this::toCompletionItem)
            .collect(toList());
    }
}
```

---

## Performance Benefits

### Memory

| Scenario | Current | Roslyn-style |
|----------|---------|--------------|
| Edit single expression | Copy entire tree | Share 99% of nodes |
| Multiple open files | Separate trees | Share common imports |
| Long editing session | Memory grows | Stable via sharing |

### Speed

| Operation | Current | Roslyn-style |
|-----------|---------|--------------|
| Parse after edit | Full reparse | Incremental ~O(edit size) |
| Type check after edit | Full check | Only affected nodes |
| Hover response | Compute type | Cache lookup |
| Completion | Full analysis | Scope lookup only |

### Thread Safety

| Current | Roslyn-style |
|---------|--------------|
| Single-threaded only | Lock-free reads |
| Defensive copying | Structural sharing |
| Race conditions possible | Immutable = safe |

---

## Implementation Milestones

### Milestone 1: Green Node Foundation (2-3 weeks)
- [ ] GreenNode base class with child list
- [ ] GreenToken for terminals
- [ ] GreenExpression hierarchy (20 node types)
- [ ] SyntaxKind enum
- [ ] Node interning/caching
- [ ] Unit tests for each node type

### Milestone 2: Red Node Facade (1 week)
- [ ] SyntaxNode wrapper
- [ ] Parent pointer computation
- [ ] Position tracking
- [ ] Find node by position
- [ ] Unit tests

### Milestone 3: Parser Integration (2 weeks)
- [ ] Modify parser to emit green nodes
- [ ] Or: Convert existing AST to green tree
- [ ] Round-trip tests (parse -> toString -> parse)

### Milestone 4: Semantic Model Core (2-3 weeks)
- [ ] External caches for types, symbols
- [ ] Name resolution with caching
- [ ] Type checking with caching
- [ ] Diagnostics collection
- [ ] Simple expressions compile

### Milestone 5: Incremental Parse (2 weeks)
- [ ] Token-level reuse
- [ ] Node-level reuse
- [ ] Edit -> minimal reparse
- [ ] Benchmarks showing improvement

### Milestone 6: Incremental Semantics (2 weeks)
- [ ] Dependency tracking
- [ ] Invalidation on edit
- [ ] Partial reanalysis
- [ ] LSP hover/completion

### Milestone 7: Feature Parity (4-6 weeks)
- [ ] All expression types
- [ ] All statement types
- [ ] All declaration types
- [ ] Full module compilation
- [ ] Output matches original compiler

### Milestone 8: Production Ready (2 weeks)
- [ ] Performance benchmarks
- [ ] Memory profiling
- [ ] Edge case testing
- [ ] Documentation
- [ ] Migration guide

---

## Risks and Mitigations

### Risk: Two compilers to maintain
**Mitigation**: Clear deprecation timeline, automated tests ensure parity

### Risk: Semantic model cache invalidation is complex
**Mitigation**: Start conservative (invalidate more than needed), optimize later

### Risk: Green node interning memory leaks
**Mitigation**: Use weak references, monitor in production

### Risk: Performance worse for full compile
**Mitigation**: Batch mode bypasses incremental machinery

---

## Decision: New Package vs. Migrate In-Place

**Recommendation: New Package**

| In-Place Migration | New Package |
|--------------------|-------------|
| Breaks existing code during migration | Original compiler keeps working |
| Hard to test incrementally | Can test compiler2 in isolation |
| Rollback is painful | Easy rollback (just don't use compiler2) |
| Must maintain compatibility | Can make clean breaks |
| Gradual = never done | Clear milestones |

The new `org.xvm.compiler2` package allows:
1. Original compiler keeps working for production
2. compiler2 developed and tested independently
3. Switch when compiler2 reaches parity
4. Can even run both and compare outputs

---

## Conclusion

This plan provides a path to a modern, incremental compiler that:

1. **Eliminates all mutable state** from syntax nodes
2. **Enables true incremental compilation** via structural sharing
3. **Supports LSP** with sub-100ms responses
4. **Is thread-safe by design** (immutable nodes, concurrent caches)
5. **Can be built incrementally** without breaking existing compiler

The key insight is **separation of concerns**:
- Green tree = pure syntax structure (immutable, shared)
- Red tree = navigation facade (cheap, disposable)
- Semantic model = derived information (cached, invalidatable)

No more "stateful shit" on nodes. No more copies. Just sharing and caching.
