# Executive Summary: The Road to XTC Tooling

## The Goal

We want to build **tooling** for XTC that enables modern development workflows:

**Language Server Protocol (LSP)** for IDEs:
- Hover information: Mouse over a variable, see its type and documentation
- Go-to-definition: Click on a name, jump to where it's defined
- Find references: See everywhere a symbol is used
- Auto-completion: Type a dot, see available methods and properties
- Inline diagnostics: See errors and warnings as you type
- Refactoring: Rename symbols safely across the entire project

**Build Tool Integration** (Gradle, Maven):
- Incremental compilation
- Parallel builds
- Dependency analysis
- Error reporting to IDE

This is table-stakes for a modern programming language. Without proper tooling support, developers must use basic text editors and the command-line compiler, losing hours of productivity daily.

## The Problem

The XTC compiler was designed for **batch compilation**: read source files, compile them, produce output, exit. This is the "command line only" model.

Tooling requires the **opposite**: keep the compiler running, respond to queries in milliseconds, handle incomplete code gracefully, support concurrent operations, update incrementally as the user types.

**The XTC compiler's architecture is fundamentally hostile to these requirements.**

## Core Blockers

### 1. Mutable AST

The compiler's Abstract Syntax Tree is fully mutable. During compilation:
- Nodes are modified in place
- Type information is stored directly in expression nodes
- Parent/child relationships change
- Compilation state is mixed with syntax

**Impact**: Can't query the AST while compilation runs. Can't have multiple concurrent compilations. Can't share AST between operations.

See: [Mutable AST Problem](../2-problems/core/mutable-ast.md)

### 2. Clone/Copy Bugs

The codebase uses Java's broken `clone()` mechanism and misuses `transient` (which doesn't affect clone - only serialization).

**Impact**: Every "copy" of an AST subtree shares cached state with the original. Modifications to one corrupt the other. Bugs that are nearly impossible to track down.

See: [Clone/Copy section](../2-problems/clone/)

### 3. No Type Safety

Pervasive use of:
- `Object` fields that hold multiple types
- Raw `Map<Object, ...>` with mixed key types
- `return null` for "not found", "error", "not computed", etc.
- Mutable objects as HashMap keys (hashCode changes!)

**Impact**: Runtime ClassCastException and NullPointerException. Silent data corruption. Code that can't be safely refactored.

See: [Type Safety section](../2-problems/type-safety/)

### 4. Chaotic Error Handling

Three incompatible error mechanisms:
- `ErrorListener` parameter passed everywhere (side-effect based)
- `return null` to indicate failure (loses error information)
- `RuntimeException` throws (unpredictable aborts)

Plus `ErrorListener.BLACKHOLE` that **silently discards all errors**.

**Impact**: Can't collect all diagnostics for LSP. Don't know if operations succeeded. Can't provide meaningful error messages.

See: [Error Handling Chaos](../2-problems/core/error-handling.md)

### 5. Thread Safety Instabilities

- Mutable shared state common and pervasive
- No synchronization on critical data structures
- HashMap keys that change hashCode
- Lazy initialization that races

**Impact**: Can't run LSP operations concurrently. Can't compile in background while responding to queries.

See: [Thread Safety Anti-Patterns](../2-problems/core/thread-safety.md)

### 6. Reflection Anti-Patterns

Every AST node uses reflection to discover its children:
```java
private static final Field[] CHILD_FIELDS = fieldsForNames(ForStatement.class, "conds", "block");
```

**Impact**: 10-100x slower than direct field access. Strings instead of type-safe references. Breaks when fields are renamed. 58 classes with this boilerplate.

See: [Reflection Traversal](../2-problems/data-structures/reflection-traversal.md)

### 7. Imperative Design Throughout

The codebase is written in a legacy imperative Java style:
- Constructors that do business logic, I/O and computation
- Mutable state by default
- Ignoring modern Java features (Optional, records, sealed types)

**Impact**: Hard to reason about. Hard to test. Hard to make concurrent.

See: [Constructor Anti-Patterns](../2-problems/anti-patterns/constructor.md), [No Excuse](./no-excuse.md)

## The Path Forward

### Track A: Build an Adapter Layer (Recommended First)

Don't try to fix the compiler. Let it do its job, then **extract** results into clean structures:

1. Create new `org.xvm.lsp.model` package with immutable records
2. After compilation, snapshot results into these clean structures
3. Build tooling features on top of the clean structures
4. Have a working LSP server in 8-12 weeks

See: [Adapter Layer Design](../3-lsp-solution/adapter-layer-design.md), [Complete Implementation](../3-lsp-solution/implementation.md)

### Track B: Fix the Codebase (Long-term)

Incrementally, in parallel to Track A, remediate the architectural issues:

1. Add `@CopyIgnore` annotation, fix clone/copy bugs
2. Separate semantic model from AST
3. Replace `null` with `Optional` and `Result` types
4. Make AST nodes immutable (records)
5. Delete reflection-based traversal
6. Proper error handling (no swallowing, no RuntimeException abuse)

See: [Remediation Roadmap](../3-lsp-solution/remediation-roadmap.md), [Clone Migration](../2-problems/clone/migration.md)

## Reading Order

**If you're building tooling** (LSP, Gradle plugin, etc.):
1. This document (you're here)
2. [Adapter Layer Design](../3-lsp-solution/adapter-layer-design.md)
3. [Complete Implementation](../3-lsp-solution/implementation.md)
4. [Mutable AST](../2-problems/core/mutable-ast.md) - understand why the adapter is necessary

**If you're trying to understand what's wrong**:
1. This document (you're here)
2. [Mutable AST](../2-problems/core/mutable-ast.md) - the core problem
3. [Error Handling Chaos](../2-problems/core/error-handling.md) - why errors are lost
4. [No Excuse](./no-excuse.md) - why this matters
5. [Technical Debt](../2-problems/anti-patterns/technical-debt.md) - the compound cost

**If you want to fix the codebase**:
1. [Remediation Roadmap](../3-lsp-solution/remediation-roadmap.md) - what to fix first
2. [Clone Migration](../2-problems/clone/migration.md) - step-by-step guide
3. [Bad vs Good Reference](../2-problems/anti-patterns/bad-vs-good-reference.md) - pattern guide

## The Bottom Line

The XTC compiler works. It compiles code correctly. But its architecture is fundamentally incompatible with modern tooling.

**The solution is not to rewrite the compiler.** It's to build a clean **adapter layer** that extracts data from the compiler into proper data structures suitable for tooling.

This is achievable in weeks, not months. The compiler doesn't need to change. We just need to stop trying to use its internal data structures directly and instead snapshot them into something usable.
