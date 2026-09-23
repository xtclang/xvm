# Ecstasy Language Support Implementation

> **Last Updated**: 2026-09-22 (adapter capability review)

This document describes the language tooling implemented in the `lang/` directory and what remains to be done.

## What's Implemented

### 1. Language Model DSL (`lang/dsl/`)

A Kotlin DSL that defines the complete Ecstasy language model and generates editor support files:

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
                      Adapter (interface)
                            │
         ┌──────────────────┼──────────────────┐
         │                  │                  │
         ▼                  ▼                  ▼
   MockAdapter       TreeSitter-         XdkAdapter
   (adapter.mock)    Adapter             (adapter.xdk)
   (regex-based)     (adapter.treesitter)(the XTC compiler)
```

All adapters extend `AbstractAdapter` which provides:
- Per-adapter `[displayName]` prefixed logging via `logPrefix`
- "Not yet implemented" defaults for all optional LSP features (with full input parameter logging)
- Shared formatting logic (trailing whitespace removal, final newline insertion)
- Utility method for position-in-range checking

`Adapter` is a pure interface (method signatures only). Concrete adapters override
only the methods they actually implement -- all others inherit traceable logging stubs.

| Adapter | Backend | LSP Feature Coverage | Status |
|---------|---------|----------------------|--------|
| `MockAdapter` | Regex patterns | ~60% (syntax-level, no AST) | Implemented |
| `TreeSitterAdapter` | Tree-sitter grammar | ~85% (syntax + structure + workspace index) | **DEFAULT** - Implemented |
| `XdkAdapter` | The XTC compiler, via `EmbeddingSupport` | Module diagnostics, cross-file semantic navigation, workspace symbols over current modules and direct source type hierarchy | **Opt-in** (`-Plsp.adapter=compiler`); Tree-sitter remains the shipped default |

**`XdkAdapter` is no longer a placeholder.** It compiles through the embedding API and reports
what the compiler actually says - syntax *and* semantics, with the compiler's own codes, messages
and spans - which is the thing no grammar can do: `COMPILER-38: Name "NoSuchTypeAnywhere" is
unresolvable` is not a syntax error and tree-sitter cannot find it. It also supplies the outline
and the symbol under the cursor.

It also answers the position questions across a compiled module: hover with the type the compiler
decided, go-to-definition and find-references by what a name *means* - two properties called `x`
on different classes are different things - plus document-local highlights, folding and selection.
The Kotlin semantic snapshot supplies types, symbol identities, declared signatures, inheritance and
source occurrences without retaining compiler objects. The compiler exposes the binding facts;
lambda capture associations live in a helper owned by the lambda compilation context.

The compiler backend still has no completion, signature help, rename, semantic tokens, document
links, formatting or code actions. Call hierarchy, go-to-type-definition, find-implementations,
inlay hints, code lenses and linked editing are also unimplemented. A trailing `.` can produce
`PARSER-03`; Java parser recovery preserves the surrounding method/module syntax, but the invalid
expression has no semantic result for member completion. Compiler mode stays Java-only.
The bundled XDK is part of the server; no external installation is required.

Navigation includes type-parameter declarations and anonymous-class captures. Module sessions
combine disk sources with unsaved overlays, including new member files, and build per-source views
in one identity domain. Definitions in bundled libraries still have no source target. Workspace
symbols search current completed modules by case-insensitive substring, including closed members;
edits invalidate those views and closing the last open member releases the session. This does not
index other workspace modules or join identities from separate compilations.

The current verification, reporting audit and remaining work are recorded in
[errs-integration-plan.md](../../../docs/errs-integration-plan.md).

The adapters provide different capabilities: tree-sitter maintains error-tolerant syntax results;
the compiler supplies validated semantic facts. A combined adapter has not been implemented.

**Note:** TreeSitterAdapter requires Java 25+ (FFM API). The IntelliJ plugin runs the LSP server
out-of-process for classloader and crash isolation (IntelliJ 2026.1 runs on JBR 25).

#### Adapter capability matrix

The **Compiler (XdkAdapter)** column describes the current implementation. The nine optional
features in [XdkAdapter.capabilities](../../lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAdapter.kt)
are filtered into the server's
[advertised capabilities](../../lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcLanguageServer.kt).
Diagnostics and document synchronization are provided separately. Unimplemented compiler features
are not advertised; inherited adapter stubs or basic formatting helpers do not enable them.

| Feature | Mock | Tree-sitter | Compiler (XdkAdapter) |
|---------|------|-------------|----------|
| Syntax highlighting | - | TextMate + semantic tokens (lexer) | No compiler semantic tokens; editor TextMate remains available |
| Document symbols | Full | Full | **Done** - from the AST, with real ranges |
| Go-to-definition (same file) | By name | By name | **Done** - semantic, incl. method calls |
| Go-to-definition (cross-file) | - | Via workspace index | **Done** - by resolved identity within the current module |
| Find references (same file) | Decl only | By name | **Done** - by identity, not by name |
| Find references (cross-file) | - | - | **Done** - across the current module, including closed member files |
| Completions | Keywords | Context-aware keywords/types/locals/members/imports | Not implemented - bounded partial receiver/member facts exist; module lifecycle integration and completion selection remain |
| Syntax errors | Markers | Full | **Done** - the compiler's own codes and spans |
| Semantic errors | - | - | **Done** - the reason this adapter exists |
| Hover (signature) | Basic | Basic | **Done** - declaration plus the resolved type |
| Document highlights | Text match | AST identifiers with READ/WRITE distinction | **Done** - by resolved identity; READ/WRITE not distinguished |
| Selection ranges | - | AST walk-up | **Done** - AST walk-up; zero-width cursor range if no AST is available |
| Folding ranges | Braces | AST nodes | **Done** - blocks and declarations |
| Document links | Regex | AST nodes + best-effort import targets | Not implemented |
| Signature help | - | Same-file | Not implemented - completed calls have instantiated signatures and argument mapping; incomplete calls have candidates/source slots, without overload selection |
| Rename (same file) | Text | AST | Not implemented |
| Rename (cross-file) | - | - | Not implemented - module references exist; workspace ownership, edit validation and rename rules remain |
| Code actions | Organize imports | Organize imports + auto-import + doc-comments | Not implemented |
| Document formatting | Trailing WS | Structural re-indent + whitespace cleanup | Not implemented |
| Range formatting | Trailing WS in range | Structural formatting in range | Not implemented |
| On-type formatting | - | Structural formatting on trigger characters | Not implemented |
| Workspace symbols | - | Fuzzy search (4-tier) | **Done** - substring search over completed module sessions, including closed members |
| Semantic tokens | - | Lexer-based (18 contexts) | Not implemented |
| Code lenses | - | Run action on module declarations | Not implemented |
| Linked editing | - | Same-file identifiers | Not implemented |
| Inlay hints | - | - | Not implemented |
| Go-to-declaration (separate LSP request) | - | - | Not implemented; module-local go-to-definition is available |
| Go-to-type-definition | - | - | Not implemented |
| Find implementations | - | - | Not implemented |
| Type hierarchy (supertypes/subtypes) | - | - | **Done** - direct declared extends/implements edges for source types in a successful module compilation; generic parent arguments retained |
| Call hierarchy (callers/callees) | - | - | Not implemented - needs resolved call edges and cross-file indexing |

Semantic results can be partial when validation fails. Parse errors prevent semantic compilation,
but `Compilation.sourceTrees()` retains available per-file syntax for outline, folding and selection.
Statement-boundary recovery omits malformed statements and preserves surrounding declarations;
missing braces retain completed method/module headers. Broken headers or lexer failures can still
leave gaps. Selection ranges retain one response per cursor, with a cursor-only fallback if no
syntax covers that position. An edit invalidates the old analysis; queries do not reuse semantic
positions from an older document version. No Tree-sitter fallback is used in compiler mode.

The separate embedding `analyzeIncomplete` probe can validate intact receivers and ordinary
arguments in a single trailing standalone statement at EOF. Consumer tests verify real method
scope, flow narrowing and source positions without selecting an overload or emitting the damaged
method. XdkAdapter does not yet invoke this probe; it does not change the capabilities above.
Its explicit Kotlin copier now supplies accessible instance methods/properties, receiver-substituted
candidate signatures, argument spans/labels/types and a source argument slot based on top-level
commas. Completed method calls separately copy the compiler's selected instantiated signature and
written argument-to-parameter mapping. These facts are tested consumer APIs, not advertised LSP
features. Module lifecycle integration, broader incomplete syntax, implicit/static receiver lookup,
applicable-overload selection and expected argument types remain follow-ups.

The snapshot records resolved types, type parameters, declaration/use ranges (including captures),
declared and selected-call signatures, written argument mappings and direct inheritance edges. The
compiler adapter compiles a module
root and its member tree together, taking unsaved source overlays ahead of disk. New unsaved member
files and implicit packages participate without temporary files. Source membership and text are
captured per attempt; member edits invalidate the module, and diagnostics publish with each open
file's own version. Closing an overlay reanalyses remaining members from disk; filesystem events
refresh membership and clear removed-file diagnostics.

Definitions and references share identities across one module compilation. Hierarchy items carry a
compilation token; items from before an edit return no results. Hierarchy currently includes declared
`extends` and `implements`, with source locations available in the module. It does not discover
library sources, conditional mixin relationships or other modules in the workspace. Method
implementation lookup, completion and signature help still need additional semantic contracts.

Module-root discovery follows the source-file/same-name-directory layout. Non-file URIs remain
single-source inputs. Opening a module does not establish a workspace-wide dependency build or
persistent index. Tree-sitter remains the shipped default. See the
[module and recovery hardening results](../../../docs/errs-integration-plan.md#ninth-pass-java-parser-recovery-2026-09-22).

**Data Model:** `lang/lsp-server/src/main/kotlin/org/xvm/lsp/model/`
- `CompilationResult` - Compilation output with diagnostics and symbols
- `Diagnostic` - Error/warning/info with location
- `Location` - File position
- `SymbolInfo` - Symbol metadata (name, kind, signature, location)

### 3. IntelliJ Plugin (`lang/intellij-plugin/`)

An IntelliJ IDEA plugin providing XTC support:

**Core Components:**
- **`XtcLanguageServerFactory`** / **`XtcLspConnectionProvider`** - Out-of-process LSP server integration via LSP4IJ
- **`XtcNewProjectWizard`** / **`XtcNewProjectWizardStep`** - New Project wizard
- **`XtcRunConfiguration`** / **`XtcRunConfigurationType`** / **`XtcRunConfigurationProducer`** - Run configurations
- **`XtcTextMateBundleProvider`** - TextMate grammar for syntax highlighting
- **`XtcIconProvider`** - XTC file icons
- **`XtcEnterHandlerDelegate`** - IntelliJ-local Enter repair for brace/empty-block indentation
- **`XtcLanguageCodeStyleSettingsProvider`** / **`XtcCodeStyleSettings`** - IntelliJ code-style integration consumed by editor-side indentation repair

**LSP Server Launch:**
- Uses LSP4IJ's `ProcessStreamConnectionProvider` to launch the LSP server as a separate process
- `JavaProcessCommandBuilder` resolves the JBR 25 runtime automatically (no custom JRE provisioning)
- Out-of-process architecture provides classloader and crash isolation

**Build Configuration:**
- Downloads IntelliJ Community 2026.1 by default (cached by Gradle)
- Use `-PintellijLocalPath=/path` to use a local IntelliJ installation instead
- Plugin bytecode target is Java 25
- Searchable-options indexing is disabled by default for ordinary builds
- IntelliJ Platform IDE caching is enabled under `lang/.intellijPlatform/ides`

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
   - Out-of-process LSP server runs with JBR 25 (FFM API for tree-sitter)

2. ~~**Implement semantic tokens (Phase 1)**~~ ✅ COMPLETE
   - `SemanticTokenEncoder` classifies 18 AST contexts via single-pass O(n) tree walk
   - `TreeSitterAdapter.getSemanticTokens()` implemented and wired
   - Server advertises capability when `lsp.semanticTokens=true` (default)
   - Token types: keyword, decorator, comment, string, number, operator, type (heuristic),
     method (call-site heuristic), class/interface/enum/property/variable/parameter/namespace

   **Phase 2 -- Compiler-based (requires pluggable compiler):**
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
   - Test out-of-process LSP server launch on all platforms
   - Continue reducing IntelliJ/TextMate/LSP overlap issues and stale sandbox failure modes
   - Keep the startup diagnostics in `XtcEditorStartupActivity` until the runIde sandbox behavior is fully stable
   - If sandbox editor/theme issues recur, log both UI theme and editor color scheme together instead of forcing only one side
   - Document or automate sandbox reset for stale color/folding state during local plugin development

5. **Highlighting and completion follow-up**
   - Improve semantic-token granularity for declaration names vs type references
   - Distinguish field/property references from locals and parameters more consistently
   - Improve generic/type-parameter highlighting in both semantic tokens and TextMate fallback
   - Continue aligning semantic-token classifications with TextMate scopes so the two paths do not fight each other in IntelliJ/LSP4IJ
   - Add focused regression tests for `TestModule.x`-style cases:
     - declaration-start completion should prefer `class` over `Class`
     - module/package hover should resolve
     - return type / parameter / property coloring should not collapse into one broad scope
   - Review auto-import suggestions for built-in/meta-types like `Class`, `Module`, and `Type` so they do not produce noisy quick-fix candidates in declaration contexts

### Medium-term (Compiler Integration)

6. **Extend the compiler adapter**
   - Diagnostics, bundled libraries, semantic snapshots, module overlays and cross-file navigation are implemented
   - Preserve regression coverage for type-parameter declarations and anonymous-class captures
   - Extend module ownership to dependency sources and other workspace modules before workspace-wide references/rename
   - Direct source type hierarchy is implemented; method implementation lookup still needs override relationships
   - Copy resolved call edges for call hierarchy
   - Extend Java parser recovery beyond structural results before type-aware completion
   - Add call-site facts before signature help; declared signatures alone are insufficient

   The selected implementation uses the existing javatools compiler. The older research-fork
   rewrite schedules are not the current integration plan.

7. **Hybrid adapter strategy**
   - Use tree-sitter for fast syntax feedback
   - Use compiler adapter for semantic features when available
   - Graceful degradation when compiler is unavailable

### Long-term (Advanced Features)

8. **Refactoring support (cross-file)**
   - Rename symbol (same file) is implemented by Mock and Tree-sitter; the compiler backend does not advertise it
   - Cross-file rename (requires compiler)
   - Extract method/variable
   - Safe delete

9. **Code actions (semantic)**
   - ~~Organize imports~~ ✅ COMPLETE (both adapters)
   - ~~Auto-import (workspace-index-backed)~~ ✅ COMPLETE (tree-sitter)
   - ~~Generate doc comment~~ ✅ COMPLETE (tree-sitter)
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

**DAP support.** IntelliJ has no built-in DAP (Debug Adapter Protocol) client. LSP4IJ provides a DAP client via the `debugAdapterServer` extension point, which is required for `lang/dap-server/` integration. Without it, we would need to write thousands of lines of IntelliJ-specific debug infrastructure (`XDebugProcess`, `XBreakpointHandler`, `ProcessHandler`, variable tree rendering, stack frame mapping, expression evaluation) -- the exact opposite of IDE independence.

**LSP feature coverage.** LSP4IJ supports LSP features that IntelliJ's built-in LSP (as of 2026.1) does not:

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

Server support depends on the adapter: Tree-sitter supplies run code lenses, the compiler supplies
direct source type hierarchy, and call hierarchy remains unimplemented. See the
[adapter matrix](#adapter-capability-matrix),
[`lsp-feature-tiers.md`](./lsp-feature-tiers.md) for the LSP capability tiering and
[`idea-specific.md`](./idea-specific.md) for IntelliJ-specific follow-up work.

**Standard protocol types.** LSP4IJ uses Eclipse LSP4J types (`org.eclipse.lsp4j.services.LanguageServer`, `IDebugProtocolServer`) -- the same library our LSP and DAP servers use. IntelliJ's built-in LSP uses internal IntelliJ types.

### What LSP4IJ Does Not Affect

IDE independence is preserved either way. The shared, IDE-independent code is:

```
lang/lsp-server/     -- LSP server (Eclipse LSP4J, stdio)
lang/dap-server/  -- DAP server (Eclipse LSP4J debug, stdio)
lang/dsl/            -- Language model, generates TextMate/tree-sitter/vim/emacs
lang/tree-sitter/    -- Grammar + native libs
```

The IntelliJ plugin (`lang/intellij-plugin/`) is inherently IntelliJ-specific. The choice between LSP4IJ and built-in LSP only affects which IntelliJ API the thin wrapper calls. The servers are unchanged.

### Costs

| Concern | Assessment |
|---------|-----------|
| User installs extra plugin | Minor -- one dependency (`com.redhat.devtools.lsp4ij`) |
| Duplicate server spawn race condition | Known LSP4IJ issue ([#888](https://github.com/redhat-developer/lsp4ij/issues/888)), harmless -- extras killed in milliseconds |
| Third-party maintenance risk | LSP4IJ is actively maintained by Red Hat, releases every ~2 weeks |

### Reference

The `xtc-intellij-plugin-dev` reference repo demonstrates IntelliJ's built-in LSP in ~29 lines. That is intentional -- it serves as a minimal "getting started" example. The production plugin requires DAP support, advanced LSP features, and the LSP Console, which are only available through LSP4IJ.

## Known Issues and Follow-ups

> **Last Updated**: 2026-04-09

### DAP Integration (Blocking for Debug Support)

1. **DAP server JAR not packaged into sandbox** -- The `plugin.xml` registers the
   `debugAdapterServer` extension point and the factory/descriptor classes compile, but
   `dap-server` has no fat JAR task, no consumable configuration, and no `copyDapServerToSandbox`
   task. At runtime, `PluginPaths.findServerJar("xtc-dap-server.jar")` will always throw
   `IllegalStateException`. To ship DAP support:
   - Add a `fatJar` task in `lang/dap-server/build.gradle.kts`
   - Add a `dapServerElements` consumable configuration
   - Add a `dapServerJar` consumer configuration in `intellij-plugin/build.gradle.kts`
   - Add a `copyDapServerToSandbox` task mirroring the LSP copy pattern
   - Wire `prepareSandbox` and `runIde` to depend on it

2. **DAP server launch** -- The DAP descriptor's `startServer()` needs to use
   `JavaProcessCommandBuilder` (matching the LSP connection provider pattern) to launch
   the DAP server out-of-process with the IDE's JBR 25.

3. **Formatting config / editor path split** -- LSP on-type formatting is implemented and
   well tested, but IntelliJ does not always send the first Enter-after-`{` path through
   `textDocument/onTypeFormatting`. The plugin now includes a local Enter repair that
   respects IntelliJ code style settings; keep treating that as an IntelliJ-specific
   complement to the LSP formatter, not as the primary formatting engine.

4. **Root composite configuration-cache reuse still misses with lang attached** --
   isolated `:lang:intellij-plugin:build` now reuses configuration cache with
   IntelliJ IDE caching enabled, but the outer root `./gradlew build` graph still
   misses on `JavaRuntimeMetadataValueSource`. This appears tied to the IntelliJ
   Platform Gradle plugin / composite-build runtime metadata path.

### Tree-sitter / Semantic Tokens

~~3. `XtcNode.text` byte-vs-char offset~~ -- FIXED: Added UTF-8 aware substring extraction.
~~4. `SemanticTokensVsTextMateTest` native memory leak~~ -- FIXED: Uses `.use {}` now.
~~5. `SemanticTokenEncoder.nodeKey` collision~~ -- FIXED: Key now includes node type hash.
~~8. Semantic tokens crash on rename~~ -- FIXED: `XtcParser.parse()` was passing `oldTree`
for incremental parsing without calling `Tree.edit()`, producing nodes with stale byte
offsets. Now always does full reparse (still sub-ms). Defensive bounds checking added to
`XtcNode.text`.
~~9. EDT violation in LSP connection setup~~ -- FIXED: `XtcLspConnectionProvider.init {}` called
`ProjectJdkTable.getInstance()` (prohibited on EDT). Moved to `start()` which runs off EDT.
~~10. Pipeline logging gaps~~ -- FIXED: `XtcQueryEngine.executeQuery()` now logs query name,
all find methods log per-match details (symbol kind, name, location). `TreeSitterAdapter`
methods (`getFoldingRanges`, `getSemanticTokens`, `getCodeActions`, `getDocumentLinks`) now
consistently log their inputs and results.
~~11. Unicode characters garbled in logs~~ -- FIXED: Replaced Unicode arrows (`U+2192`) and
em-dashes (`U+2014`) with ASCII `->` and `--` in all logger output, test display names, and
annotations. The 3-byte UTF-8 characters were rendering as `a-hat` in log viewers using
ISO-8859-1/Latin-1 encoding.

### Build System

~~6. Windows IDE path~~ -- FIXED: Updated to 2026.1.
~~7. Composite build property isolation~~ -- FIXED: `project.findProperty()` and
`providers.gradleProperty()` only see the included build's own `gradle.properties`,
which doesn't exist for `lang/`. Properties like `lsp.semanticTokens`, `lsp.adapter`,
`lsp.buildSearchableOptions`, and `log` were silently falling back to hardcoded defaults.
Fixed by using `xdkProperties` which resolves through `XdkPropertiesService` (loads from
composite root's `gradle.properties` at settings time).

## Related Documentation

- **[plan-tree-sitter.md](./plan-tree-sitter.md)** - Tree-sitter grammar status and development guide
- **[lsp-feature-tiers.md](./lsp-feature-tiers.md)** - LSP capability tiering: what tree-sitter can still add, what fits a future semantic-model layer, and what truly requires compiler-backed semantics
- **[idea-specific.md](./idea-specific.md)** - IntelliJ-specific roadmap beyond standard LSP behavior
- **[vscode-specific.md](./vscode-specific.md)** - VS Code-specific roadmap beyond standard LSP behavior
- *Internal documentation* - Comprehensive architecture analysis and compiler modification plans
