# XTC Language Support Implementation

> **Last Updated**: 2026-02-05

This document describes the language tooling implemented in the `lang/` directory and what remains to be done.

## What's Implemented

### 1. Language Model DSL (`lang/dsl/`)

A Kotlin DSL that defines the complete XTC language model and generates editor support files:

**Source:** `XtcLanguage.kt` - Complete language definition including:
- Keywords (reserved and context-sensitive)
- Operators with precedence and associativity
- Built-in types
- Token patterns for lexical analysis
- AST concept definitions

**Generators:**
| Generator | Output | Purpose |
|-----------|--------|---------|
| `TextMateGenerator` | `xtc.tmLanguage.json` | Syntax highlighting for VS Code, IntelliJ, Sublime |
| `TreeSitterGenerator` | `grammar.js`, `highlights.scm` | Incremental parsing, structural queries |
| `VimGenerator` | `xtc.vim` | Vim syntax highlighting |
| `EmacsGenerator` | `xtc-mode.el` | Emacs major mode |
| `SublimeSyntaxGenerator` | `xtc.sublime-syntax` | Sublime Text highlighting |
| `VSCodeConfigGenerator` | `language-configuration.json` | Bracket matching, comments, folding |

**Generated Files:** See `lang/generated-examples/`

### 2. LSP Server (`lang/lsp-server/`)

A Language Server Protocol implementation providing IDE features:

**Server:** `XtcLanguageServer.kt`, `XtcLanguageServerLauncher.kt`

**Adapter Architecture:**

The LSP server uses a pluggable adapter pattern to support different backends:

```
                     XtcLanguageServer
                            │
                   XtcCompilerAdapter (interface)
                            │
         ┌──────────────────┼──────────────────┐
         │                  │                  │
         ▼                  ▼                  ▼
  MockXtcCompiler-    TreeSitter-      XtcCompiler-
  Adapter             Adapter          AdapterStub
  (regex-based)       (syntax-aware)   (future: semantic)
```

All adapters extend `AbstractXtcCompilerAdapter` which provides:
- Per-adapter `[displayName]` prefixed logging via `logPrefix`
- "Not yet implemented" defaults for all optional LSP features (with full input parameter logging)
- Shared formatting logic (trailing whitespace removal, final newline insertion)
- Utility method for position-in-range checking

`XtcCompilerAdapter` is a pure interface (method signatures only). Concrete adapters override
only the methods they actually implement — all others inherit traceable logging stubs.

| Adapter | Backend | LSP Feature Coverage | Status |
|---------|---------|----------------------|--------|
| `MockXtcCompilerAdapter` | Regex patterns | ~60% (syntax-level, no AST) | Implemented |
| `TreeSitterAdapter` | Tree-sitter grammar | ~80% (syntax + structure) | **DEFAULT** - Implemented |
| `XtcCompilerAdapterStub` | XTC Compiler | 100% (semantic) | Placeholder |

**Note:** TreeSitterAdapter requires Java 25+ (FFM API). The IntelliJ plugin runs the LSP server
out-of-process with an auto-provisioned JRE to meet this requirement (IntelliJ runs on JBR 21).

**What Each Adapter Provides:**

| Feature | Mock | Tree-sitter | Compiler |
|---------|------|-------------|----------|
| Syntax highlighting | - | TextMate + semantic tokens (lexer) | Full semantic tokens |
| Document symbols | Full | Full | Full |
| Go-to-definition (same file) | By name | By name | Semantic |
| Go-to-definition (cross-file) | - | Via workspace index | Full |
| Find references (same file) | Decl only | By name | Full |
| Completions | Keywords | Keywords + locals + members | Types + members |
| Syntax errors | Markers | Full | Full |
| Semantic errors | - | - | Full |
| Hover (signature) | Basic | Basic | Full types |
| Document highlights | Text match | AST identifiers | Semantic |
| Selection ranges | - | AST walk-up | AST walk-up |
| Folding ranges | Braces | AST nodes | AST nodes |
| Document links | Regex | AST nodes | Resolved URIs |
| Signature help | - | Same-file | Cross-file |
| Rename (same file) | Text | AST | Semantic |
| Code actions | Organize imports | Organize imports | Quick fixes |
| Formatting | Trailing WS | Trailing WS | Full formatter |
| Workspace symbols | - | Fuzzy search (4-tier) | Full |
| Semantic tokens | - | Lexer-based (18 contexts) | Full semantic |

**Data Model:** `lang/lsp-server/src/main/kotlin/org/xvm/lsp/model/`
- `CompilationResult` - Compilation output with diagnostics and symbols
- `Diagnostic` - Error/warning/info with location
- `Location` - File position
- `SymbolInfo` - Symbol metadata (name, kind, signature, location)

### 3. IntelliJ Plugin (`lang/intellij-plugin/`)

An IntelliJ IDEA plugin providing XTC support:

**Core Components:**
- **`XtcLspServerSupportProvider`** - Out-of-process LSP server integration via LSP4IJ
- **`XtcProjectGenerator`** / **`XtcNewProjectWizardStep`** - New Project wizard
- **`XtcRunConfiguration`** / **`XtcRunConfigurationType`** / **`XtcRunConfigurationProducer`** - Run configurations
- **`XtcTextMateBundleProvider`** - TextMate grammar for syntax highlighting
- **`XtcIconProvider`** - XTC file icons

**JRE Provisioning (`jre/JreProvisioner.kt`):**
- Auto-downloads Eclipse Temurin JRE 25 for out-of-process LSP server (FFM API requirement)
- Checks IntelliJ's registered JDKs first, then cached JRE, then downloads from Foojay
- Persistent cache in `{GRADLE_USER_HOME}/caches/xtc-jre/` (not cleared by IDE cache invalidation)
- Metadata tracking with periodic update checks (every 7 days)

**Build Configuration:**
- Downloads IntelliJ Community by default (cached by Gradle)
- Use `-PuseLocalIde=true` to use local IntelliJ installation instead
- LSP server runs as separate Java 25+ process (not constrained by JBR 21)

### 4. VS Code Extension (`lang/vscode-extension/`)

A VS Code extension stub with:
- Extension manifest (`package.json`)
- Language configuration
- TextMate grammar inclusion
- LSP client setup (scaffolded)

### 5. Tree-sitter Integration ✅ COMPLETE

Full tree-sitter support for fast, incremental parsing:

**Grammar:** Generated by `TreeSitterGenerator` -> `grammar.js`
- 100% coverage: All 692 XTC files from `lib_*` parse successfully
- External scanner for template strings and TODO freeform text

**Native Libraries (`lang/tree-sitter/`):**
- Zig cross-compilation for all 5 platforms (darwin-arm64, darwin-x64, linux-x64, linux-arm64, windows-x64)
- On-demand build with persistent caching in `~/.gradle/caches/tree-sitter-xtc/`
- All platforms bundled in LSP server fatJar

**Kotlin Bindings:** `lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/`
- `XtcParser` - Parser wrapper with FFM-based native library loading
- `XtcTree` - Parse tree
- `XtcNode` - Tree node
- `XtcQueryEngine` - Pattern matching queries
- `XtcQueries` - Predefined queries for declarations, references
- `TreeSitterLibraryLookup` - Custom library lookup for bundled native libs

## What Remains To Be Done

### Short-term (Complete LSP Features)

1. ~~**Wire up TreeSitterAdapter in LSP server**~~ ✅ COMPLETE
   - TreeSitterAdapter is now the default adapter
   - Out-of-process LSP server runs with Java 25+ (FFM API for tree-sitter)
   - JRE auto-provisioning via Foojay Disco API

2. ~~**Implement semantic tokens (Phase 1)**~~ ✅ COMPLETE
   - `SemanticTokenEncoder` classifies 18 AST contexts via single-pass O(n) tree walk
   - `TreeSitterAdapter.getSemanticTokens()` implemented and wired
   - Server advertises capability when `lsp.semanticTokens=true` (default)
   - Token types: keyword, decorator, comment, string, number, operator, type (heuristic),
     method (call-site heuristic), class/interface/enum/property/variable/parameter/namespace

   **Phase 2 — Compiler-based (requires pluggable compiler):**
   - Distinguish classes vs interfaces vs enums vs type parameters
   - Distinguish variables vs parameters vs properties
   - Add modifiers: `declaration`, `definition`, `readonly`, `static`, `deprecated`
   - Cross-file type resolution for accurate identifier classification

3. **Complete VS Code extension**
   - Finish LSP client integration
   - Add commands (new project, run, build)
   - Package and test

4. **Polish IntelliJ plugin**
   - Test project wizard with `xtc init`
   - Verify run configurations work
   - Build and test plugin ZIP
   - Test JRE provisioning on all platforms

### Medium-term (Compiler Integration)

5. **Implement full compiler adapter**
   - Replace `XtcCompilerAdapterStub` with real compiler integration
   - Extract type information from XTC compiler
   - Provide cross-file go-to-definition
   - Provide semantic error reporting
   - Provide type-aware completions

   Two approaches are documented in the research repo:
   - **Quick Path**: State externalization (~4-6 weeks)
   - **Parallel Path**: Modern rewrite / parallel implementation of needed components (~3 months, recommended)

   See *Internal documentation* for detailed plans.

6. **Hybrid adapter strategy**
   - Use tree-sitter for fast syntax feedback
   - Use compiler adapter for semantic features when available
   - Graceful degradation when compiler is unavailable

### Long-term (Advanced Features)

7. **Refactoring support (cross-file)**
   - ~~Rename symbol (same file)~~ ✅ COMPLETE (both adapters)
   - Cross-file rename (requires compiler)
   - Extract method/variable
   - Safe delete

8. **Code actions (semantic)**
   - ~~Organize imports~~ ✅ COMPLETE (both adapters)
   - Quick fixes for common errors (requires compiler)
   - Generate code (getters, toString, etc.)

9. **Debugging (DAP)**
   - Debug Adapter Protocol integration
   - Breakpoints, stepping, variable inspection

## Architecture Principle

**CLI is source of truth**: IDE plugins shell out to `xtc` CLI commands.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         IDE Plugins (Thin Wrappers)                     │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐  ┌─────────────┐   │
│  │   IntelliJ   │  │    VS Code   │  │     Zed     │  │   Eclipse   │   │
│  └──────┬───────┘  └──────┬───────┘  └──────┬──────┘  └──────┬──────┘   │
│         │                 │                 │                 │         │
│         └─────────────────┴─────────────────┴─────────────────┘         │
│                                   │                                     │
│                          (shell out to CLI)                             │
├───────────────────────────────────┼─────────────────────────────────────┤
│                           ┌───────▼───────┐                             │
│                           │   xtc CLI     │                             │
│                           │  init | run   │                             │
│                           │  build | test │                             │
│                           └───────┬───────┘                             │
├───────────────────────────────────┼─────────────────────────────────────┤
│         ┌─────────────────────────┼─────────────────────────┐           │
│         │                         │                         │           │
│  ┌──────▼──────┐          ┌───────▼───────┐         ┌───────▼───────┐   │
│  │ Initializer │          │   Compiler    │         │    Runner     │   │
│  │  (templates)│          │   (xcc)       │         │    (xec)      │   │
│  └─────────────┘          └───────────────┘         └───────────────┘   │
├─────────────────────────────────────────────────────────────────────────┤
│                           ┌───────────────┐                             │
│                           │  LSP Server   │ ◄── IDE language features   │
│                           │ (hover, etc.) │                             │
│                           └───────────────┘                             │
└─────────────────────────────────────────────────────────────────────────┘
```

## Design Decision: LSP4IJ over IntelliJ Built-in LSP

The IntelliJ plugin uses Red Hat's [LSP4IJ](https://github.com/redhat-developer/lsp4ij) (`com.redhat.devtools.lsp4ij`) rather than IntelliJ's built-in LSP support (`com.intellij.modules.lsp` / `ProjectWideLspServerDescriptor`).

### Why LSP4IJ

**DAP support.** IntelliJ has no built-in DAP (Debug Adapter Protocol) client. LSP4IJ provides a DAP client via the `debugAdapterServer` extension point, which is required for `lang/dap-server/` integration. Without it, we would need to write thousands of lines of IntelliJ-specific debug infrastructure (`XDebugProcess`, `XBreakpointHandler`, `ProcessHandler`, variable tree rendering, stack frame mapping, expression evaluation) — the exact opposite of IDE independence.

**LSP feature coverage.** LSP4IJ supports LSP features that IntelliJ's built-in LSP (as of 2025.3) does not:

| Feature | LSP4IJ | Built-in LSP |
|---------|--------|-------------|
| Code Lens | Yes | No |
| Call Hierarchy | Yes | No |
| Type Hierarchy | Yes | No |
| On-Type Formatting | Yes | No |
| Selection Range | Yes | No |
| Semantic Tokens | Full | Limited |
| LSP Console (debug traces) | Yes | No |
| DAP Client | Yes | No |

Code Lens, Call Hierarchy, and Type Hierarchy are on the roadmap (see `plan-next-steps-lsp.md`).

**Standard protocol types.** LSP4IJ uses Eclipse LSP4J types (`org.eclipse.lsp4j.services.LanguageServer`, `IDebugProtocolServer`) — the same library our LSP and DAP servers use. IntelliJ's built-in LSP uses internal IntelliJ types.

### What LSP4IJ Does Not Affect

IDE independence is preserved either way. The shared, IDE-independent code is:

```
lang/lsp-server/     — LSP server (Eclipse LSP4J, stdio)
lang/dap-server/  — DAP server (Eclipse LSP4J debug, stdio)
lang/dsl/            — Language model, generates TextMate/tree-sitter/vim/emacs
lang/tree-sitter/    — Grammar + native libs
```

The IntelliJ plugin (`lang/intellij-plugin/`) is inherently IntelliJ-specific. The choice between LSP4IJ and built-in LSP only affects which IntelliJ API the thin wrapper calls. The servers are unchanged.

### Costs

| Concern | Assessment |
|---------|-----------|
| User installs extra plugin | Minor — one dependency (`com.redhat.devtools.lsp4ij`) |
| Duplicate server spawn race condition | Known LSP4IJ issue ([#888](https://github.com/redhat-developer/lsp4ij/issues/888)), harmless — extras killed in milliseconds |
| Third-party maintenance risk | LSP4IJ is actively maintained by Red Hat, releases every ~2 weeks |

### Reference

The `xtc-intellij-plugin-dev` reference repo demonstrates IntelliJ's built-in LSP in ~29 lines. That is intentional — it serves as a minimal "getting started" example. The production plugin requires DAP support, advanced LSP features, and the LSP Console, which are only available through LSP4IJ.

## Known Issues and Follow-ups

> **Last Updated**: 2026-02-19 (from `lagergren/lsp-extend4` code review)

### DAP Integration (Blocking for Debug Support)

1. **DAP server JAR not packaged into sandbox** — The `plugin.xml` registers the
   `debugAdapterServer` extension point and the factory/descriptor classes compile, but
   `dap-server` has no fat JAR task, no consumable configuration, and no `copyDapServerToSandbox`
   task. At runtime, `PluginPaths.findServerJar("xtc-dap-server.jar")` will always throw
   `IllegalStateException`. To ship DAP support:
   - Add a `fatJar` task in `lang/dap-server/build.gradle.kts`
   - Add a `dapServerElements` consumable configuration
   - Add a `dapServerJar` consumer configuration in `intellij-plugin/build.gradle.kts`
   - Add a `copyDapServerToSandbox` task mirroring the LSP copy pattern
   - Wire `prepareSandbox` and `runIde` to depend on it

2. **DAP has no JRE provisioning progress UI** — The LSP connection provider shows a
   progress dialog during first-time JRE download, but the DAP descriptor's `startServer()`
   simply checks `provisioner.javaPath` and throws if null. Users must open an `.x` file
   first (triggering LSP + JRE download) before debugging will work.

### Tree-sitter / Semantic Tokens

~~3. `XtcNode.text` byte-vs-char offset~~ — FIXED: Added UTF-8 aware substring extraction.
~~4. `SemanticTokensVsTextMateTest` native memory leak~~ — FIXED: Uses `.use {}` now.
~~5. `SemanticTokenEncoder.nodeKey` collision~~ — FIXED: Key now includes node type hash.

### Build System

~~6. Windows IDE path "2025.1"~~ — FIXED: Updated to 2025.3.
~~7. Composite build property isolation~~ — FIXED: `project.findProperty()` and
`providers.gradleProperty()` only see the included build's own `gradle.properties`,
which doesn't exist for `lang/`. Properties like `lsp.semanticTokens`, `lsp.adapter`,
`lsp.buildSearchableOptions`, and `log` were silently falling back to hardcoded defaults.
Fixed by using `xdkProperties` which resolves through `XdkPropertiesService` (loads from
composite root's `gradle.properties` at settings time).

## Related Documentation

- **[PLAN_TREE_SITTER.md](./PLAN_TREE_SITTER.md)** - Tree-sitter grammar status and development guide
- **[plan-next-steps-lsp.md](../plan-next-steps-lsp.md) § 13** - Multi-IDE strategy with market share data, priority rankings, effort estimates, and configuration examples for Neovim, Helix, Eclipse, Sublime Text, Zed, Emacs, Vim, and Kate
- *Internal documentation* - Comprehensive architecture analysis and compiler modification plans
