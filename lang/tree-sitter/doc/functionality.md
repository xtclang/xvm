# Tree-sitter LSP Feature Matrix

This document describes what Tree-sitter can and cannot provide for LSP features, and exactly where
each feature is implemented in the codebase.

## Architecture Overview

The XTC LSP server uses a pluggable adapter architecture with three available backends:

```
┌─────────────────────────────────────────────────────────────────┐
│                    XtcLanguageServer                            │
│              (lsp-server/.../server/XtcLanguageServer.kt)       │
│                                                                 │
│  Receives LSP requests, delegates to adapter, returns results   │
└────────────────────────────┬────────────────────────────────────┘
                             │ uses XtcCompilerAdapter interface
┌────────────────────────────▼────────────────────────────────────┐
│                    XtcCompilerAdapter (interface)               │
│           (lsp-server/.../adapter/XtcCompilerAdapter.kt)        │
│  Defines core LSP operations with default stubs for optional    │
│  methods that log warnings.                                     │
├─────────────────────────────────────────────────────────────────┤
│                 AbstractXtcCompilerAdapter (abstract class)     │
│  Provides: shared logger, logPrefix, getHoverInfo(),            │
│  Location.contains(), Closeable with no-op close()              │
└─────────────────────────────────────────────────────────────────┘
         │                    │                     │
         ▼                    ▼                     ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐
│ MockAdapter     │  │ TreeSitterAdapter│  │ XtcCompilerAdapter  │
│ (regex-based)   │  │ (syntax-aware)   │  │ Stub                │
│                 │  │                  │  │                     │
│ For testing     │  │ ~80% features    │  │ Minimal placeholder │
│ lsp.adapter=mock│  │ lsp.adapter=     │  │ lsp.adapter=compiler│
│                 │  │   treesitter     │  │                     │
└─────────────────┘  └──────────────────┘  └─────────────────────┘
```

## Adapter Selection

> **Note:** All `./gradlew :lang:*` commands require `-PincludeBuildLang=true -PincludeBuildAttachLang=true` when run from the project root.

Build with any of the three adapters:

```bash
./gradlew :lang:lsp-server:fatJar -Plsp.adapter=mock        # Regex-based testing
./gradlew :lang:lsp-server:fatJar -Plsp.adapter=treesitter  # Syntax-aware (default)
./gradlew :lang:lsp-server:fatJar -Plsp.adapter=compiler    # Stub for future compiler
```

## LSP Feature Implementation Matrix

> **Last Updated**: 2026-02-08

| LSP Feature             | Tree-sitter | Mock | Compiler Stub | Implementation Location                        |
|-------------------------|:-----------:|:----:|:-------------:|------------------------------------------------|
| **Core Features**       |             |      |               |                                                |
| Syntax Highlighting     | Yes         | No   | No            | TextMate grammar (dsl-generated)               |
| Document Symbols        | Yes         | Yes  | No            | `XtcQueryEngine.findAllDeclarations()`         |
| Hover                   | Yes         | Part | No            | `AbstractXtcCompilerAdapter.getHoverInfo()`    |
| Completion              | Part        | Part | No            | `TreeSitterAdapter.getCompletions()`           |
| Go to Definition        | Part        | Part | No            | `TreeSitterAdapter.findDefinition()`           |
| Find References         | Part        | Part | No            | `TreeSitterAdapter.findReferences()`           |
| Diagnostics (Syntax)    | Yes         | Part | No            | `TreeSitterAdapter.collectSyntaxErrors()`      |
| **Navigation & Structure** |          |      |               |                                                |
| Document Highlights     | Yes         | Yes  | No            | `adapter.getDocumentHighlights()`              |
| Selection Ranges        | Yes         | No   | No            | `TreeSitterAdapter.getSelectionRanges()`       |
| Folding Ranges          | Yes         | Yes  | No            | `adapter.getFoldingRanges()`                   |
| Document Links          | Yes         | Yes  | No            | `adapter.getDocumentLinks()`                   |
| Signature Help          | Yes         | No   | No            | `TreeSitterAdapter.getSignatureHelp()`         |
| **Refactoring & Editing** |           |      |               |                                                |
| Rename / Prepare Rename | Yes         | Yes  | No            | `adapter.rename()`, `adapter.prepareRename()`  |
| Code Actions            | Yes         | Yes  | No            | `adapter.getCodeActions()`                     |
| Formatting              | Yes         | Yes  | No            | `adapter.formatDocument()`                     |
| Range Formatting        | Yes         | Yes  | No            | `adapter.formatRange()`                        |
| **Requires Compiler**   |             |      |               |                                                |
| Diagnostics (Semantic)  | No          | No   | Future        | Requires compiler                              |
| Semantic Tokens         | No          | No   | Future        | `adapter.getSemanticTokens()` (default)        |
| Inlay Hints             | No          | No   | Future        | `adapter.getInlayHints()` (default)            |
| Workspace Symbols       | No          | No   | Future        | `adapter.findWorkspaceSymbols()` (default)     |
| Call Hierarchy          | No          | No   | Future        | Requires semantic analysis                     |
| Type Hierarchy          | No          | No   | Future        | Requires semantic analysis                     |

Legend: Yes = Full support, Part = Partial/limited, No = Not implemented, Future = Requires compiler adapter

### Adapter Details

#### MockXtcCompilerAdapter (`lsp.adapter=mock`)

A regex-based implementation for testing. Uses simple patterns to extract:

- **Module declarations**: `MODULE_PATTERN` matches `module foo.bar {`
- **Class/Interface/Service**: `CLASS_PATTERN`, `INTERFACE_PATTERN`, `SERVICE_PATTERN`
- **Methods**: `METHOD_PATTERN` matches `ReturnType methodName(`
- **Properties**: `PROPERTY_PATTERN` matches `Type propName;` or `Type propName =`

**Implemented capabilities:**
- Document symbols, hover, completion (keywords), go-to-definition (same file)
- Find references (declaration only), document highlights (text matching)
- Folding ranges (brace matching), document links (import regex)
- Rename (same-file text replacement), code actions (organize imports)
- Formatting and range formatting (trailing whitespace removal, final newline)

**Limitations:**
- Uses regex patterns, misses many constructs
- `findSymbolAt()` only finds top-level symbols, no nested scope
- `getHoverInfo()` shows declared type only, no inferred types
- `getCompletions()` returns all keywords + document symbols, no context filtering
- `findDefinition()` returns symbol's own location, can't follow references
- `findReferences()` only returns the declaration, not actual usages
- Selection ranges: returns empty (requires AST)
- Signature help: returns null (requires AST)
- Inlay hints: returns empty (requires type inference)

#### TreeSitterAdapter (`lsp.adapter=treesitter`)

Syntax-aware parsing providing ~80% of LSP features without compiler integration:

- Fast incremental parsing (sub-millisecond for small changes)
- Error-tolerant (works with incomplete/invalid code)
- Document symbols and outline
- Go-to-definition (same file, by name)
- Find references (same file, by name)
- Completion (keywords, locals, visible names, after-dot member completion)
- Syntax error reporting
- Document highlights (AST identifier matching)
- Selection ranges (AST walk-up chain)
- Folding ranges (AST node boundaries)
- Document links (import AST nodes)
- Signature help (same-file method parameters)
- Rename and prepare rename (AST-based same-file)
- Code actions (organize imports)
- Formatting and range formatting (trailing whitespace removal)

#### XtcCompilerAdapterStub (`lsp.adapter=compiler`)

A placeholder for future compiler integration. All methods log warnings but return empty results.
Useful for verifying all LSP methods are properly wired up.

---

## Shared Constants

Common XTC language data is centralized in `XtcLanguageConstants.kt`:

| Constant                    | Purpose                                   |
|-----------------------------|-------------------------------------------|
| `KEYWORDS`                  | XTC keywords for completion (79 keywords) |
| `BUILT_IN_TYPES`            | Built-in types for completion (70+ types) |
| `SYMBOL_TO_COMPLETION_KIND` | Maps SymbolKind → CompletionKind          |
| `toCompletionKind()`        | Conversion helper                         |

Both Mock and TreeSitter adapters use these shared constants.

---

## What Tree-sitter CAN Do (Syntax-Level Features)

Tree-sitter operates below the LSP, providing fast, incremental *syntax trees*.
It does **not** understand language meaning, only structure.

### Syntax Highlighting (Excellent)

Tree-sitter excels at:

- Context-aware highlighting
- Nested and recursive structures
- Incremental updates
- Graceful handling of incomplete or broken code

**Implementation:** `dsl/.../generators/TreeSitterGenerator.kt` generates `grammar.js`,
which tree-sitter compiles to `parser.c`. Highlighting queries are in `highlights.scm`.

### Document Symbols / Outline (Excellent)

Pure syntax tree walking enables:

- Go to enclosing function / class
- Expand / shrink selection
- Code folding
- Breadcrumbs
- Outline / structure view

**Implementation:**
- Query: `XtcQueries.ALL_DECLARATIONS` in `lsp-server/.../treesitter/XtcQueries.kt`
- Engine: `XtcQueryEngine.findAllDeclarations()` in `lsp-server/.../treesitter/XtcQueryEngine.kt`
- LSP: `XtcLanguageServer.documentSymbol()` in `lsp-server/.../server/XtcLanguageServer.kt`

### Hover Information (Limited)

Tree-sitter can show:
- Declaration kind (class, method, property)
- Name of the symbol
- No type inference (only declared types)

**Implementation:**
- Adapter: `TreeSitterAdapter.getHoverInfo()`
- Uses: `findSymbolAt()` → `XtcQueryEngine.findDeclarationAt()`
- LSP: `XtcLanguageServer.hover()`

### Basic Completions (Limited)

With Tree-sitter only, completion includes:

- Keywords (from `XtcLanguageConstants.KEYWORDS`)
- Built-in types (from `XtcLanguageConstants.BUILT_IN_TYPES`)
- Visible declarations in current file
- Imported names (syntactically extracted)

**Cannot** provide: member completion (`obj.` → methods), type-aware suggestions.

### Go to Definition (Same-file only)

Tree-sitter can find declarations **within the same file** by name matching.

**Cannot** resolve: cross-file references, inherited members, imported types.

### Find References (Same-file, by name)

Tree-sitter can find all **textual occurrences** of an identifier in the same file.

**Cannot** provide: semantic references, cross-file references, distinguishing local shadowing.

### Syntax Error Reporting (Excellent)

Tree-sitter produces ERROR and MISSING nodes in the parse tree for syntax errors.

**Implementation:**
- Adapter: `TreeSitterAdapter.collectSyntaxErrors()`
- Walks tree recursively, collects nodes where `isError` or `isMissing`
- LSP: Published via `XtcLanguageServer.publishDiagnostics()`

---

## What Tree-sitter CANNOT Do (Requires Compiler)

Anything below requires **semantic knowledge** and cannot be solved with Tree-sitter alone.

### Name Resolution (Impossible)

Tree-sitter cannot answer:

- What does this identifier refer to?
- Where is this symbol defined (cross-file)?
- Which overload is selected?
- Is this method overridden?

Therefore, you cannot implement:

- Go to definition (cross-file)
- Find usages (project-wide)
- Rename across files
- Call hierarchy

### Type System Features (Impossible)

Tree-sitter has no concept of types.

You cannot implement:

- Type inference
- Type mismatch diagnostics
- Generic instantiation
- Nullability analysis
- Smart completion based on expected type

### Semantic Diagnostics (Impossible)

Without the compiler frontend, you cannot detect:

- Semantic errors (undefined variable, type mismatch)
- Dead code (semantic)
- Unreachable code
- Contract or effect violations

---

## File Reference

All paths relative to `lang/`:

| File                                                   | Purpose                                              |
|--------------------------------------------------------|------------------------------------------------------|
| `lsp-server/.../adapter/XtcCompilerAdapter.kt`         | Interface defining core LSP operations               |
| `lsp-server/.../adapter/AbstractXtcCompilerAdapter.kt` | Base class with shared logging, utilities            |
| `lsp-server/.../adapter/XtcLanguageConstants.kt`       | Shared keywords, types, mappings, hover formatting   |
| `lsp-server/.../adapter/TreeSitterAdapter.kt`          | Tree-sitter implementation                           |
| `lsp-server/.../adapter/MockXtcCompilerAdapter.kt`     | Regex-based mock                                     |
| `lsp-server/.../adapter/XtcCompilerAdapterStub.kt`     | Minimal placeholder for future compiler              |
| `lsp-server/.../server/XtcLanguageServer.kt`           | LSP protocol handler                                 |
| `lsp-server/.../server/XtcLanguageServerLauncher.kt`   | Adapter selection and startup                        |
| `lsp-server/.../treesitter/XtcParser.kt`               | Tree-sitter parser wrapper                           |
| `lsp-server/.../treesitter/XtcQueryEngine.kt`          | Query execution for symbol extraction                |
| `lsp-server/.../treesitter/XtcQueries.kt`              | Tree-sitter query definitions                        |
| `dsl/.../generators/TreeSitterGenerator.kt`            | Generates grammar.js                                 |

---

## Testing Tree-sitter

The tree-sitter adapter is tested against the entire XDK corpus (675+ `.x` files).

```bash
# Run full corpus test
./gradlew :lang:tree-sitter:testTreeSitterParse

# Filter to specific files
./gradlew :lang:tree-sitter:testTreeSitterParse -PtestFiles=ecstasy/numbers

# Check parse timing
./gradlew :lang:tree-sitter:testTreeSitterParse -PshowTiming=true
```

---

## Key Takeaway

> **Tree-sitter can make an IDE feel fast and polished, but it can never make it feel smart.
> Intelligence only comes from the language itself.**

Tree-sitter should be treated as a *performance and UX layer*, not as a replacement for the compiler frontend.

---

## Recommended Architecture for XTC

### Phase 1 – Tree-sitter First (Current)

Fast wins with minimal backend work:

- Syntax highlighting
- Structure view
- Folding
- Basic formatting
- Keyword and snippet completion

This already feels modern and usable.

### Phase 2 – Hybrid Tree-sitter + Compiler

- Tree-sitter for:
    - Incremental parsing
    - Editor responsiveness
    - Syntax-level features
- Compiler frontend for:
    - Symbol resolution
    - Typing
    - Semantic diagnostics
    - Cross-file navigation

This is the model used by tools like Rust Analyzer.

### Phase 3 – Full IDE Parity

- Incremental type checking
- Persistent project index
- Debug adapter (DAP)
- Build system integration
- Refactoring tools

This is where Java/Kotlin-level polish lives — and where most complexity lies.

