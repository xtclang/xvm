# Roslyn-Style Compiler Architecture

## Status: Planning

## Overview

A parallel compiler package (`org.xvm.compiler2`) using immutable green/red tree
architecture to enable incremental compilation and LSP support.

## Key Decisions

### 1. New Package vs. In-Place Migration
**Decision**: New package `org.xvm.compiler2`

**Rationale**:
- Original compiler keeps working during development
- Can test independently without breaking production
- Clean slate avoids carrying baggage
- Easy rollback (don't use compiler2)
- Can compare outputs for validation

### 2. Green Tree (Immutable Syntax)
- Pure structure: tokens, children, spans
- NO parent pointers (enables structural sharing)
- NO @Derived fields (all derived state external)
- Interned via weak hash map (same source = same node)
- `withChild()` returns `this` if unchanged

### 3. Red Tree (Navigation Facade)
- Wraps green nodes with parent context
- Cheap to create, disposable
- Parent pointers computed at wrap time
- Position tracking for source mapping

### 4. Semantic Model (External Caches)
- `ConcurrentHashMap<GreenNode, T>` for all derived state
- Types, symbols, diagnostics all keyed by green node identity
- Explicit invalidation on source changes
- `computeIfAbsent()` for lazy thread-safe init

## Implementation Milestones

### Phase A: Green Node Foundation (2-3 weeks)
- [ ] GreenNode base class
- [ ] GreenToken for terminals
- [ ] GreenExpression hierarchy (20 types)
- [ ] SyntaxKind enum
- [ ] Node interning
- [ ] Unit tests

### Phase B: Red Node Facade (1 week)
- [ ] SyntaxNode wrapper
- [ ] Parent computation
- [ ] Position tracking
- [ ] Find-by-position API

### Phase C: Parser Integration (2 weeks)
- [ ] Emit green nodes from parser
- [ ] OR: Convert existing AST to green
- [ ] Round-trip tests

### Phase D: Semantic Model (2-3 weeks)
- [ ] External caches
- [ ] Name resolution
- [ ] Type checking
- [ ] Simple expressions compile

### Phase E: Incremental Parse (2 weeks)
- [ ] Token-level reuse
- [ ] Node-level reuse
- [ ] Edit -> minimal reparse

### Phase F: Incremental Semantics (2 weeks)
- [ ] Dependency tracking
- [ ] Invalidation logic
- [ ] Partial reanalysis

### Phase G: Feature Parity (4-6 weeks)
- [ ] All syntax kinds
- [ ] Full module compilation
- [ ] Output matches original

## Detailed Design

See `/Users/marcus/src/xvm4/docs/roslyn-style-compiler-plan.md` for:
- Green/Red node class designs
- Semantic model caching strategy
- Incremental parse algorithm
- LSP integration approach
- Performance comparison tables

## Relationship to Existing Work

This builds on:
- **@Derived annotation** - We're eliminating these from syntax nodes entirely
- **Lazy<T>** - Will use in SemanticModel for computed values
- **forEachChild/withChildren** - Green nodes use similar pattern
- **Copyable interface** - Green nodes are implicitly copyable (immutable)

## Key Files to Create

```
org.xvm.compiler2/
    syntax/
        green/
            GreenNode.java
            GreenExpression.java
            GreenStatement.java
            GreenBinaryExpression.java
            ...
        red/
            SyntaxNode.java
        SyntaxKind.java
        SyntaxFactory.java
    semantic/
        SemanticModel.java
        Symbol.java
        TypeChecker.java
        NameResolver.java
    compilation/
        Compilation.java
        IncrementalCompiler.java
```

## Open Questions

1. **Interning strategy**: Weak refs? Size limits? Eviction policy?
2. **Cross-module sharing**: How much can green trees share across modules?
3. **Error recovery**: How does incremental parse handle syntax errors?
4. **Bridge to existing**: How to emit to existing ConstantPool/Component?
