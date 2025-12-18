# Part 2: Architectural Problems

This section provides detailed analysis of each architectural issue in the XTC compiler that blocks tooling development. Each document explains:
- What the problem is
- Where it occurs in the codebase (specific files and line numbers)
- Why it blocks LSP and other tooling
- How it could be fixed (with effort/risk assessment)

## Sections

### [Core Blockers](./core/)

The fundamental issues that affect everything. Read these first.

| Document | Impact | Summary |
|----------|--------|---------|
| [Mutable AST](./core/mutable-ast.md) | **Critical** | AST nodes are modified in place during compilation, preventing concurrent access or querying while compilation runs |
| [Error Handling](./core/error-handling.md) | **Critical** | Three incompatible error mechanisms plus silent discarding make it impossible to collect all diagnostics |
| [Thread Safety](./core/thread-safety.md) | **Critical** | No synchronization on shared state, lazy init races, mutable HashMap keys |

### [Clone/Copy Disaster](./clone/)

The codebase's approach to copying objects is fundamentally broken, causing subtle corruption bugs.

| Document | Summary |
|----------|---------|
| [Why Clone is Broken](./clone/clone-broken.md) | Java's `Cloneable` is a well-known anti-pattern |
| [Clone Usage in XVM](./clone/clone-usage.md) | Where and how the codebase uses clone |
| [Transient Misconception](./clone/transient-misconception.md) | `transient` doesn't affect clone (only serialization) |
| [CopyIgnore Solution](./clone/copyignore-solution.md) | The proper annotation-based solution |
| [Migration Guide](./clone/migration.md) | Step-by-step migration from clone to copy |

### [Type Safety Failures](./type-safety/)

Loss of compile-time type safety leads to runtime errors and silent corruption.

| Document | Summary                                                                                     |
|----------|---------------------------------------------------------------------------------------------|
| [Null Handling](./type-safety/null-handling.md) | `return null` means "not found", "error", "not computed", "ok, but not constant", empty"... |
| [TypeInfo Bottleneck](./type-safety/typeinfo-bottleneck.md) | The type system's central chokepoint                                                        |
| [Raw Object Types](./type-safety/raw-types.md) | `Object` fields and `Map<Object,...>` lose type information                                 |

### [Data Structure Issues](./data-structures/)

Collection and traversal anti-patterns that leak state and prevent safe access.

| Document | Summary |
|----------|---------|
| [Arrays vs Collections](./data-structures/arrays-vs-collections.md) | Returning mutable arrays leaks internal state |
| [HashMap Mutable Keys](./data-structures/hashmap-mutable-keys.md) | Keys that change hashCode corrupt maps |
| [Reflection Traversal](./data-structures/reflection-traversal.md) | 10-100x slower than direct field access |
| [Lexer/Parser Safety](./data-structures/lexer-parser.md) | Thread safety issues in parsing |

### [Anti-Patterns Reference](./anti-patterns/)

General design problems and reference guides for fixing them.

| Document | Summary |
|----------|---------|
| [Constructor Anti-Patterns](./anti-patterns/constructor.md) | Constructors that do I/O, computation, or business logic |
| [Bad vs Good Reference](./anti-patterns/bad-vs-good-reference.md) | Quick side-by-side pattern comparisons |
| [Technical Debt](./anti-patterns/technical-debt.md) | Quantifying the compound cost of these issues |

## Reading Order

**For tooling developers**: Start with Core Blockers, then skip to [Part 3](../3-lsp-solution/).

**For codebase maintainers**: Read Core Blockers, then whichever section is most relevant to what you're working on.

## Key Insight

These problems aren't isolated - they compound. Mutable AST + no thread safety + error swallowing = impossible to 
build reliable tooling. The solution isn't to fix them one by one (that would take months), but to build an adapter 
layer that works around all of them at once.
