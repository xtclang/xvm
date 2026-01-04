# XVM Compiler2 - Roslyn-Style Incremental Compiler

## What Is This?

A parallel, drop-in replacement compiler for XVM using immutable red/green tree
architecture. Lives in `org.xvm.compiler2` and does not modify the original compiler.

## Why?

The current compiler has ~130 mutable `@Derived` fields scattered across AST nodes:

```java
// Current: Nodes accumulate mutable state during compilation
public class NameExpression extends Expression {
    @Derived private TargetInfo m_targetInfo;  // Set during ResolveName
    @Derived private Argument m_arg;           // Set during Validate
    @Derived private Plan m_plan;              // Set during Validate
}
```

This makes it impossible to:
- Share nodes between compilation runs
- Do incremental compilation (old state is stale)
- Run thread-safe parallel compilation
- Provide fast LSP/IDE responses

## The Solution: Separation of Concerns

| Layer | Contains | Mutable? | Shared? |
|-------|----------|----------|---------|
| **Green Tree** | Pure syntax (tokens, children) | Immutable | Yes |
| **Red Tree** | Navigation facade (parents) | Disposable | No |
| **Semantic Model** | Types, symbols, diagnostics | Cached | Per-compilation |

**Zero @Derived fields on syntax nodes.** Everything in external caches.

## Architecture

### Green Nodes (Immutable Syntax)

```java
public abstract sealed class GreenNode {
    private final GreenNode[] children;
    private final int fullWidth;

    // Copy-on-write: returns this if unchanged
    public GreenNode withChild(int index, GreenNode newChild) {
        if (children[index] == newChild) return this;
        // Create new node with replaced child
    }
}
```

Properties:
- `final` fields only
- No parent pointer
- Interned (same source = same object)
- Structural sharing on edit

### Red Nodes (Navigation)

```java
public class SyntaxNode {
    private final GreenNode green;
    private final SyntaxNode parent;  // Computed at wrap time
    private final int position;

    public SyntaxNode getChild(int index) {
        return wrap(green.getChild(index), this, offsetOf(index));
    }
}
```

Properties:
- Thin wrapper over green
- Parent/position computed
- Cheap to create, disposable

### Semantic Model (External Cache)

```java
public class SemanticModel {
    private final ConcurrentHashMap<GreenNode, Symbol> symbols;
    private final ConcurrentHashMap<GreenNode, TypeConstant> types;

    public TypeConstant getType(SyntaxNode expr) {
        return types.computeIfAbsent(expr.getGreen(),
            g -> computeType(expr));
    }
}
```

Properties:
- Keyed by green node identity
- Thread-safe via ConcurrentHashMap
- Survives red tree recreation
- Explicit invalidation on edit

## Incremental Compilation

```
Edit: "x + 1" → "x + 2"

After edit (structural sharing):
  GreenMethod     ← SAME (unchanged)
    GreenBlock    ← SAME
      GreenReturn ← SAME
        GreenAdd  ← NEW (child changed)
          x       ← SAME
          2       ← NEW

Only 2 new nodes created!
```

Semantic cache invalidation:
1. Walk up from changed node
2. Invalidate type/symbol caches
3. Stop when reaching stable ancestor
4. Recompute lazily on demand

## Package Structure

```
javatools/src/main/java/org/xvm/compiler2/
    syntax/
        green/
            GreenNode.java           # Base immutable node
            GreenToken.java          # Terminal (literal, identifier)
            GreenExpression.java     # Expression base
            GreenBinaryExpr.java     # Binary operations
            GreenLiteralExpr.java    # Literals
            GreenNameExpr.java       # Name references
            ... (one per syntax kind)
        red/
            SyntaxNode.java          # Navigation wrapper
            ExpressionSyntax.java    # Expression facade
        SyntaxKind.java              # Enum of all kinds
        SyntaxFactory.java           # Factory methods

    semantic/
        SemanticModel.java           # External caches
        Symbol.java                  # Type/method symbols
        Scope.java                   # Name scopes
        TypeChecker.java             # Validation
        NameResolver.java            # Name resolution

    compilation/
        Compilation.java             # Immutable compilation state
        IncrementalCompiler.java     # Edit handling
        DiagnosticBag.java           # Error collection
```

## Implementation Order

### Phase 1: Green Node Foundation
1. `SyntaxKind` enum with all syntax kinds
2. `GreenNode` base with child array, width
3. `GreenToken` for terminals
4. `GreenExpression` hierarchy (start with binary, literal, name)
5. Unit tests for each type

### Phase 2: Red Node Facade
1. `SyntaxNode` wrapper
2. Parent computation
3. Find-by-position API
4. Tests

### Phase 3: Parser Bridge
1. Emit green nodes from existing parser
2. OR: Convert AST to green post-parse
3. Round-trip tests

### Phase 4: Semantic Model
1. External caches
2. Simple type checking
3. Name resolution
4. Compile simple expressions

### Phase 5: Incremental
1. Token reuse
2. Node reuse
3. Cache invalidation
4. LSP integration

## Design Decisions

### Why new package, not migrate in-place?
- Original compiler keeps working
- Test independently
- Clean slate design
- Easy rollback

### Why green/red split?
- Green enables structural sharing (no parent pointers)
- Red provides navigation when needed
- Red is disposable (recreate on edit)

### Why external semantic caches?
- Nodes stay immutable
- Cache survives tree recreation
- Thread-safe access
- Explicit invalidation control

## Connector Architecture

The XVM uses a `Connector` pattern to bridge between Java host and XVM runtime:

```
Connector (base API)
├── Connector         - Standard interpreter-based execution
└── JitConnector      - JIT compilation to Java bytecode
```

Compiler2 can follow this pattern:

```java
public class Compiler2Connector extends Connector {
    private final IncrementalCompiler compiler2;

    @Override
    public void loadModule(String appName) {
        // Use compiler2 for compilation
        GreenNode tree = compiler2.parse(source);
        SemanticModel model = compiler2.analyze(tree);
        // Emit to existing structures for execution
    }
}
```

**Benefits**:
- Same execution API (start, invoke, join)
- Can switch between compilers at runtime
- Benchmark comparison easy
- LSP can use compiler2 while runtime uses standard

## Relationship to Existing Code

This does NOT modify:
- `org.xvm.compiler` (original compiler)
- `org.xvm.asm` (constants, components)
- `org.xvm.api.Connector` (extends it)
- Parser, Lexer (reuse as-is)

This REUSES:
- `Lazy<T>` from javatools_utils
- Token/Lexer infrastructure
- ConstantPool for emission
- Component hierarchy for output
- Connector pattern for integration

## Success Criteria

1. Compile simple module to same output as original
2. Incremental reparse in <10ms for typical edits
3. LSP hover/completion in <100ms
4. Thread-safe parallel compilation
5. Memory stable during long edit sessions
