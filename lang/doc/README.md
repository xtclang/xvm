# XTC Compiler Architecture Analysis

## Purpose

This document series analyzes the XTC compiler architecture from the perspective of **building tooling**:
- Language Server Protocol (LSP) implementations for IDEs
- Gradle/Maven plugins for build systems
- Documentation generators
- Code analysis and refactoring tools
- Distributed compilation services

The XTC compiler works correctly for batch compilation. However, its architecture presents significant challenges for programmatic use. This documentation explains those challenges and provides solutions.

---

## Document Structure

### [Part 1: Introduction](./1-introduction/)

Start here. Understand what we're trying to build and why the current architecture makes it difficult.

| Document | Description |
|----------|-------------|
| [Executive Summary](./1-introduction/executive-summary.md) | The goal, the blockers, and the path forward |
| [No Excuse for Ignoring Modern Java](./1-introduction/no-excuse.md) | Why these problems shouldn't exist in 2024 |

### [Part 2: Architectural Problems](./2-problems/)

Detailed analysis of each architectural issue that blocks tooling development. Each chapter explains:
- What the problem is
- Where it occurs in the codebase
- Why it blocks LSP/tooling
- How it could be fixed (if relevant)

#### Core Blockers

The fundamental issues that must be understood first.

| Document | Impact | Description |
|----------|--------|-------------|
| [Mutable AST](./2-problems/core/mutable-ast.md) | **Critical** | AST modified during compilation prevents concurrent access |
| [Error Handling Chaos](./2-problems/core/error-handling.md) | **Critical** | Three incompatible error mechanisms, silent discarding |
| [Thread Safety](./2-problems/core/thread-safety.md) | **Critical** | Pervasive race conditions, no safe concurrent access |

#### Clone/Copy Disaster

How the codebase's approach to copying objects is fundamentally broken.

| Document | Description |
|----------|-------------|
| [Why Clone is Broken](./2-problems/clone/clone-broken.md) | Java's `Cloneable` is a trap |
| [Clone Usage in XVM](./2-problems/clone/clone-usage.md) | Where and how clone is used |
| [Transient Misconception](./2-problems/clone/transient-misconception.md) | `transient` doesn't mean what you think |
| [CopyIgnore Solution](./2-problems/clone/copyignore-solution.md) | The proper way to handle copy semantics |
| [Migration Guide](./2-problems/clone/migration.md) | How to migrate from clone to copy |

#### Type Safety Failures

Loss of compile-time type safety throughout the codebase.

| Document | Description |
|----------|-------------|
| [Null Handling Chaos](./2-problems/type-safety/null-handling.md) | `return null` means 5 different things |
| [TypeInfo Bottleneck](./2-problems/type-safety/typeinfo-bottleneck.md) | The type system's central chokepoint |
| [Raw Object Types](./2-problems/type-safety/raw-types.md) | `Object` fields and `Map<Object,...>` everywhere |

#### Data Structure Issues

Collection and data structure anti-patterns.

| Document | Description |
|----------|-------------|
| [Arrays vs Collections](./2-problems/data-structures/arrays-vs-collections.md) | Mutable arrays leak internal state |
| [HashMap Mutable Keys](./2-problems/data-structures/hashmap-mutable-keys.md) | Keys that change hashCode corrupt maps |
| [Reflection Traversal](./2-problems/data-structures/reflection-traversal.md) | 10-100x slower than direct access |
| [Lexer/Parser Safety](./2-problems/data-structures/lexer-parser.md) | Thread safety in parsing |
 
#### Anti-Patterns Reference

General design problems and quick reference guides.

| Document | Description |
|----------|-------------|
| [Constructor Anti-Patterns](./2-problems/anti-patterns/constructor.md) | Constructors that do too much |
| [Bad vs Good Reference](./2-problems/anti-patterns/bad-vs-good-reference.md) | Side-by-side pattern comparisons |
| [Technical Debt](./2-problems/anti-patterns/technical-debt.md) | Quantifying the cost of these issues |

### [Part 3: The LSP Solution](./3-lsp-solution/)

Concrete guidance for building tooling despite these architectural issues.

#### Core Architecture

| Document | Description |
|----------|-------------|
| [Adapter Layer Design](./3-lsp-solution/adapter-layer-design.md) | The extraction pattern that makes LSP possible |
| [Complete Implementation](./3-lsp-solution/implementation.md) | Working LSP server code |
| [IDE and Build Integration](./3-lsp-solution/ide-and-build-integration.md) | VSCode, IntelliJ plugins & Gradle integration |
| [Distributed Architecture](./3-lsp-solution/distributed-architecture.md) | Wire protocols and serialization |
| [Remediation Roadmap](./3-lsp-solution/remediation-roadmap.md) | Prioritized fixes with effort/risk assessment |

#### Language Formalization & Advanced Tooling

| Document | Description |
|----------|-------------|
| [Grammar Formalization](./3-lsp-solution/grammar-formalization.md) | Tree-sitter, ANTLR4 feasibility; why XTC is context-sensitive |
| [Kotlin DSL for Reflection](./3-lsp-solution/kotlin-dsl-reflection.md) | Type-safe DSL for querying and analyzing XTC code |
| [Analysis Tool Integration](./3-lsp-solution/analysis-tool-integration.md) | Adding XTC to PMD, Checkstyle, SonarQube, SpotBugs, etc. |

---

## Reading Paths

### "We need to build an LSP for XTC"

1. [Executive Summary](./1-introduction/executive-summary.md) - Understand the challenge
2. [Adapter Layer Design](./3-lsp-solution/adapter-layer-design.md) - The architectural solution
3. [Complete Implementation](./3-lsp-solution/implementation.md) - Working code
4. [Mutable AST](./2-problems/core/mutable-ast.md) - Why the adapter is necessary, at least to begin with

### "We want to understand what prevents us from getting there"

1. [Executive Summary](./1-introduction/executive-summary.md) - The big picture
2. [No Excuse](./1-introduction/no-excuse.md) - Why this matters
3. [Mutable AST](./2-problems/core/mutable-ast.md) - The core problem
4. [Error Handling](./2-problems/core/error-handling.md) - Why errors are lost
5. [Technical Debt](./2-problems/anti-patterns/technical-debt.md) - The compound cost

### "We (objectively) have to improve the code base"

1. [Remediation Roadmap](./3-lsp-solution/remediation-roadmap.md) - What to fix first
2. [Clone Migration](./2-problems/clone/migration.md) - Fixing copy semantics
3. [Bad vs Good Reference](./2-problems/anti-patterns/bad-vs-good-reference.md) - Pattern guide
4. [Technical Debt](./2-problems/anti-patterns/technical-debt.md) - Prioritization rationale

### "We want to build plugins and other language support tools"

1. [Executive Summary](./1-introduction/executive-summary.md) - Understand the compiler
2. [Distributed Architecture](./3-lsp-solution/distributed-architecture.md) - Integration patterns
3. [Error Handling](./2-problems/core/error-handling.md) - Capturing diagnostics
4. [Thread Safety](./2-problems/core/thread-safety.md) - Concurrent builds

### "We want to add XTC support to external analysis tools"

1. [Analysis Tool Integration](./3-lsp-solution/analysis-tool-integration.md) - PMD, Checkstyle, SonarQube guide
2. [Grammar Formalization](./3-lsp-solution/grammar-formalization.md) - Why Tree-sitter alone won't work
3. [Kotlin DSL for Reflection](./3-lsp-solution/kotlin-dsl-reflection.md) - Type-safe code analysis
4. [Adapter Layer Design](./3-lsp-solution/adapter-layer-design.md) - The shared foundation

---

## Quick Reference: Problem Severity; existing code base 

| Problem | Severity | Workaround | Fix Difficulty |
|---------|----------|------------|----------------|
| Mutable AST | Critical | Snapshot after compile | Very Hard |
| Error handling | Critical | Adapter collects errors | Hard |
| Thread safety | Critical | Single-threaded access | Medium |
| Clone bugs | High | Use CopyIgnore pattern | Medium |
| Null returns | High | Wrap with Optional | Medium |
| Raw Object types | High | Sealed types | Medium |
| HashMap keys | High | IdentityHashMap | Easy |
| Reflection traversal | Medium | Explicit methods | Easy |
| Array returns | Medium | Return List.copyOf() | Easy |

---

## The Bottom Line

The XTC compiler is **architecturally hostile to tooling**. It was designed for batch mode: read files, compile, exit. 
Modern tooling requires interactive mode: long-running, concurrent, error-tolerant, queryable, distributed / remote.

**The solution is not to rewrite the compiler.** Instead:

1. **Build an adapter layer** that extracts data from the compiler into clean, immutable structures
2. **Let the compiler do its job** without modification
3. **Build tooling against the clean, near-immutable structures**, not the compiler internals
4. **Incrementally improve** the compiler as resources allow, unifying new applicable architecture.

This approach should (best case) have some working tooling in full-time man weeks, not man months or man years, while 
keeping (and using) the option open to improve the underlying architecture over time.
