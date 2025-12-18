# Part 3: The LSP Solution

This section provides concrete guidance for building tooling (LSP, Gradle plugins, etc.) despite the architectural issues documented in Part 2.

## The Core Insight

**Don't try to first fix the compiler to build tooling.** Instead:

1. Let the lexer, parser, compiler do their jobs (they works correctly)
2. After compilation, extract results into clean, immutable structures
3. Build tooling against these clean structures
4. The compiler and tooling never share mutable state

This approach seems to be the only practical one to deliver working tooling at an acceptable pace.

## Documents

### Core Architecture

| Document | Purpose |
|----------|---------|
| [Adapter Layer Design](./adapter-layer-design.md) | The architectural pattern that makes this "work" |
| [Complete Implementation](./implementation.md) | Full working LSP server code with all features |
| [IDE and Build Integration](./ide-and-build-integration.md) | VSCode, IntelliJ plugins & Gradle plugin architecture |
| [Distributed Architecture](./distributed-architecture.md) | Wire protocols and serialization for build tools |
| [Remediation Roadmap](./remediation-roadmap.md) | Fixing the broken XTC architecture in parallel |

### Language Formalization & Advanced Tooling

| Document | Purpose |
|----------|---------|
| [Grammar Formalization](./grammar-formalization.md) | Feasibility of Tree-sitter, ANTLR4, and why XTC is context-sensitive |
| [Kotlin DSL for Reflection](./kotlin-dsl-reflection.md) | Type-safe DSL for querying and analyzing XTC code |
| [Analysis Tool Integration](./analysis-tool-integration.md) | Adding XTC support to PMD, ErrorProne, Checkstyle, SonarQube, etc. |

## Reading Order

### Building an LSP Server

1. **[Adapter Layer Design](./adapter-layer-design.md)** - Understand the extraction pattern
2. **[Complete Implementation](./implementation.md)** - Use this as your starting point
3. **[Mutable AST](../2-problems/core/mutable-ast.md)** (Part 2) - Understand why the adapter is necessary as a first step

### Building a Gradle/Maven Plugin

1. **[Distributed Architecture](./distributed-architecture.md)** - Wire protocols and integration patterns
2. **[Error Handling](../2-problems/core/error-handling.md)** (Part 2) - How to capture diagnostics
3. **[Adapter Layer Design](./adapter-layer-design.md)** - Extract what you need

### Fixing the Codebase (Long-term)

1. **[Remediation Roadmap](./remediation-roadmap.md)** - What to fix first and why
2. **[Clone Migration](../2-problems/clone/migration.md)** (Part 2) - Step-by-step guide
3. **[Bad vs Good Reference](../2-problems/anti-patterns/bad-vs-good-reference.md)** (Part 2) - Pattern guide

### Adding XTC to External Tools

1. **[Grammar Formalization](./grammar-formalization.md)** - Why pure grammar approaches won't work, hybrid solutions
2. **[Analysis Tool Integration](./analysis-tool-integration.md)** - PMD, Checkstyle, SonarQube, SpotBugs, etc.
3. **[Kotlin DSL for Reflection](./kotlin-dsl-reflection.md)** - Type-safe code analysis DSL

## Key Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    LSP/Tooling Layer                        │
│  ┌─────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │
│  │  Hover  │  │Complete  │  │ GoToDef  │  │ Diagnostics  │  │
│  └────┬────┘  └────┬─────┘  └────┬─────┘  └───────┬──────┘  │
│       └────────────┴─────────────┴────────────────┘         │
│                          │                                  │
│                    ┌─────▼─────┐                            │
│                    │ Snapshot  │  (Immutable, thread-safe)  │
│                    │  Cache    │                            │
│                    └─────┬─────┘                            │
├──────────────────────────┼──────────────────────────────────┤
│                    ┌─────▼─────┐                            │
│                    │  Adapter  │  (Extraction layer)        │
│                    │   Layer   │                            │
│                    └─────┬─────┘                            │
├──────────────────────────┼──────────────────────────────────┤
│                    ┌─────▼─────┐                            │
│                    │  XTC      │  (Unchanged compiler)      │
│                    │ Compiler  │                            │
│                    └───────────┘                            │
└─────────────────────────────────────────────────────────────┘
```

## Timeline Estimates

| Deliverable | Effort | Prerequisites |
|-------------|--------|---------------|
| Basic LSP (hover, go-to-def) | 2-3 weeks | Adapter layer |
| Full LSP (completion, refs) | 4-6 weeks | Basic LSP |
| Gradle plugin integration | 2-3 weeks | Adapter layer |
| Incremental compilation | 6-8 weeks | Full LSP |

These estimates assume the adapter approach. Trying to fix the compiler first would add months.
