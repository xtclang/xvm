# PLAN: Tree-sitter Integration for XTC Language Support

**Goal**: Use the existing `TreeSitterGenerator` to build a functional LSP with syntax-level
intelligence, without requiring compiler modifications.

**Risk**: Low (additive, no compiler changes)
**Prerequisites**: Working `TreeSitterGenerator` in `lang/dsl/` (already exists)

---

## ⚠️ CRITICAL: Java Version Compatibility Issue

> **Last Updated**: 2026-02-02

### The Problem

**ALL versions of jtreesitter require Java 22+** due to their use of the Foreign Function & Memory (FFM) API:

| jtreesitter Version | Required Java | Status |
|---------------------|---------------|--------|
| 0.24.x | Java 22+ | FFM API used |
| 0.25.x | Java 22+ | FFM API used |
| 0.26.x | Java 23+ | Enhanced FFM |

**IntelliJ 2025.1 ships with JBR 21 (JetBrains Runtime based on Java 21).**

This means tree-sitter integration **cannot work in-process** with the IntelliJ plugin until:
- IntelliJ 2026.x ships with JBR 22+ (expected late 2026)

### Current Behavior

The tree-sitter adapter has **never actually worked** in IntelliJ. When enabled:
1. `XtcParser` attempts to load native library using FFM API
2. Java 21 throws `UnsupportedClassVersionError` (class file version 66.0)
3. Fallback mechanism catches the error
4. `MockXtcCompilerAdapter` is used instead

The fallback works correctly, masking the underlying incompatibility. Users see "fallback" in the adapter name.

### Workarounds

#### Option 1: Out-of-Process LSP Server (RECOMMENDED)

Run the LSP server as a separate process with Java 25 (XDK toolchain):

```
┌─────────────────────────────────────────────────────────────┐
│                    IntelliJ Plugin                           │
│                  (JBR 21 - Java 21)                         │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │               LSP4IJ Client                          │   │
│  │        (communicates via stdio/socket)              │   │
│  └──────────────────────┬──────────────────────────────┘   │
└─────────────────────────┼───────────────────────────────────┘
                          │ LSP Protocol (JSON-RPC)
                          ▼
┌─────────────────────────────────────────────────────────────┐
│               XTC LSP Server (separate process)              │
│                      Java 25 Runtime                         │
│                                                             │
│  ┌─────────────────┐  ┌─────────────────┐                  │
│  │ TreeSitter      │  │ jtreesitter     │                  │
│  │ Adapter         │  │ (FFM API OK)    │                  │
│  └─────────────────┘  └─────────────────┘                  │
└─────────────────────────────────────────────────────────────┘
```

**Benefits**:
- Uses XDK's Java 25 toolchain (consistency)
- Full tree-sitter support with latest jtreesitter
- No IntelliJ JBR dependency
- VS Code extension already works this way

**Implementation**:
- Bundle Java 25 JRE with the IntelliJ plugin, or
- Require Java 25 installed and use system Java
- Launch via `ProcessBuilder` with explicit Java path

**See**: [JetBrains LSP Documentation](https://plugins.jetbrains.com/docs/intellij/language-server-protocol.html)

#### Option 2: Alternative Library (tree-sitter-ng)

[bonede/tree-sitter-ng](https://github.com/bonede/tree-sitter-ng) provides Java 8+ compatible tree-sitter bindings.

**Trade-offs**:
- ✅ Works with Java 21 (in-process)
- ✅ 100% tree-sitter API coverage
- ❌ Requires porting XtcParser code to different API
- ❌ Must build custom XTC parser as a separate native artifact
- ❌ Less maintained than official jtreesitter

**Investigation needed**: Can tree-sitter-ng load custom grammar `.so`/`.dylib` files at runtime?

#### Option 3: Wait for JBR 22+ (Passive)

IntelliJ 2026.1 is expected to ship with JBR 22+ (late 2026).

**Current timeline**:
- IntelliJ 2025.1: JBR 21 ✓
- IntelliJ 2025.x: JBR 21 (likely)
- IntelliJ 2026.1: JBR 22+ (expected)

Track: https://github.com/JetBrains/JetBrainsRuntime/releases

### Current Solution: Out-of-Process LSP Server ✅

**Status**: IMPLEMENTED (2026-02-05)

The out-of-process LSP server is now the default:

- **Default adapter**: `treesitter` (changed from `mock`)
- **Process model**: LSP server runs as separate Java process
- **Java requirement**: 25 (for FFM API)
- **JRE provisioning**: Automatic download via Foojay Disco API with persistent caching
- **Communication**: stdio (JSON-RPC)
- **Health monitoring**: Process monitor with crash notification and restart action

The IntelliJ plugin:
1. Checks IntelliJ's registered JDKs (`ProjectJdkTable`) for any Java 25+
2. Falls back to cached JRE at `{GRADLE_USER_HOME}/caches/xtc-jre/temurin-25-jre/`
3. If no JRE found, downloads Eclipse Temurin JRE 25 via Foojay Disco API
4. Stores metadata (package ID, platform, timestamp) for cache validation
5. Periodically checks for newer point releases (every 7 days)
6. Uses IntelliJ's built-in `Decompressor` for archive extraction
7. Launches `xtc-lsp-server.jar` as a subprocess
8. Communicates via LSP protocol over stdin/stdout
9. Shows notification with "Restart Server" action on crash

**JRE Cache Location** (persistent across IDE sessions):
- Default: `~/.gradle/caches/xtc-jre/`
- Override: Set `GRADLE_USER_HOME` environment variable
- Not affected by IntelliJ's "Invalidate Caches"

> **Documentation**:
> - [lang/tree-sitter/README.md](../../tree-sitter/README.md) - Usage and architecture
> - [tree-sitter/implementation.md](./tree-sitter/implementation.md) - Implementation history and challenges

---

## Implementation Status

> **Last Updated**: 2026-02-02

### Grammar (Phase 1) - COMPLETE

- [x] Grammar validates (`./gradlew :lang:tree-sitter:validateTreeSitterGrammar`)
- [x] **100% coverage** - All 692 XTC files from `lib_*` parse successfully
- [x] External scanner for template strings (`$"text {expr}"`, `$|multiline|`)
- [x] External scanner for TODO freeform text (`TODO message`)
- [x] Conflict optimization: 49 necessary conflicts, 0 warnings

### JVM Integration (Phase 2) - COMPLETE

- [x] `jtreesitter` dependency added to `lang/lsp-server/build.gradle.kts`
- [x] Parser wrappers implemented (`XtcParser`, `XtcTree`, `XtcNode`)
- [x] IntelliJ plugin supports adapter switching via `-Plsp.adapter=treesitter`
- [x] Native library build infrastructure (`tree-sitter/build.gradle.kts`)
- [x] **Zig cross-compilation** for all 5 platforms (darwin-arm64, darwin-x64, linux-x64, linux-arm64, windows-x64)
- [x] **`XtcParser.loadLanguageFromPath()` implemented** using jtreesitter's Foreign Function API
- [x] **On-demand native library build** with persistent caching in `~/.gradle/caches/tree-sitter-xtc/`
- [x] All platforms bundled in fatJar (cross-compiled from any host using Zig)
- [x] lsp-server wired to consume native library from tree-sitter project

#### Task: Native Library Loading in XtcParser

**Status**: COMPLETE

**Implementation** (in `lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/XtcParser.kt`):

```kotlin
private fun loadLanguageFromPath(path: Path): Language {
    val symbols = SymbolLookup.libraryLookup(path, arena)
    return Language.load(symbols, LANGUAGE_FUNCTION)
}
```

Uses jtreesitter's Foreign Function API:
1. `SymbolLookup.libraryLookup()` loads the native library into the process
2. `Language.load()` resolves the `tree_sitter_xtc` function symbol
3. Returns a `Language` instance for parser configuration

**Testing**:
```bash
# Run IntelliJ with tree-sitter adapter
./gradlew :lang:intellij-plugin:runIde -Plsp.adapter=treesitter
```

### Query Engine (Phase 3) - COMPLETE

- [x] Query patterns defined (`XtcQueries.kt`)
- [x] Query engine implemented (`XtcQueryEngine.kt`)
- [x] Tests for query accuracy (`LspIntegrationTest` runs tree-sitter against real `.x` files)

### LSP Features (Phase 4) - COMPLETE

- [x] `TreeSitterAdapter` implements all basic LSP methods
- [x] Document symbols shows class/method outline
- [x] Go-to-definition works for local variables
- [x] Find references works within same file
- [x] Completion shows keywords and locals
- [x] Integration tests verify all features against real `.x` files (`LspIntegrationTest`)

### Cross-File Support (Phase 5) - PARTIAL

- [x] `WorkspaceIndex` for cross-file symbol tracking (fuzzy search: exact, prefix, CamelCase, subsequence)
- [x] Cross-file go-to-definition (via workspace index)
- [x] Workspace symbol search (`workspace/symbol` with 4-tier fuzzy matching)
- [x] Background indexing via `WorkspaceIndexer` (dedicated parser/queryEngine, thread-safe)
- [ ] Incremental re-indexing on file change (currently re-scans on `didChangeWatchedFiles`)

---

## Adapter Architecture

The LSP server uses a pluggable adapter pattern with three available backends:

```
┌─────────────────────────────────────────────────────────────┐
│                     XtcLanguageServer                       │
│            (takes XtcCompilerAdapter via constructor)       │
└────────────────────────────┬────────────────────────────────┘
                             │
               ┌─────────────┴─────────────┐
               │    XtcCompilerAdapter     │  ← Interface with defaults
               └─────────────┬─────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ MockXtcCompiler │  │ TreeSitter      │  │ XtcCompiler     │
│ Adapter         │  │ Adapter         │  │ AdapterStub     │
│                 │  │                 │  │                 │
│ - Regex-based   │  │ - Tree-sitter   │  │ - All methods   │
│ - For testing   │  │ - Syntax only   │  │   logged        │
│ lsp.adapter=mock│  │ lsp.adapter=    │  │ lsp.adapter=    │
│                 │  │   treesitter    │  │   compiler      │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

The abstract base class provides default implementations for all methods that log
"not yet implemented" warnings. Adapters only override what they implement.

### Switching Adapters

> **Note:** All `./gradlew :lang:*` commands require `-PincludeBuildLang=true -PincludeBuildAttachLang=true` when run from the project root.

```bash
# Build with Tree-sitter adapter (default)
./gradlew :lang:lsp-server:fatJar

# Build with Mock adapter (for testing without native libraries)
./gradlew :lang:lsp-server:fatJar -Plsp.adapter=mock

# Build with Compiler stub (all LSP calls logged)
./gradlew :lang:lsp-server:fatJar -Plsp.adapter=compiler
```

### Shared Constants

Common XTC language data is centralized in `XtcLanguageConstants.kt`:

- `KEYWORDS` - 50 XTC keywords for completion
- `BUILT_IN_TYPES` - 61 built-in types
- `SYMBOL_TO_COMPLETION_KIND` - Symbol kind mapping

---

## Task: Build Native Tree-sitter Library

**Status**: COMPLETE

Native library build infrastructure is fully implemented using Zig cross-compilation.
See [tree-sitter/README.md → Native Library Build](../../tree-sitter/README.md#native-library-build).

### Native Libraries (On-Demand Build)

Two native libraries are built for each platform using Zig cross-compilation:

1. **XTC Grammar Library** (`libtree-sitter-xtc.*`) - The compiled XTC grammar
2. **Tree-sitter Runtime** (`libtree-sitter.*`) - The tree-sitter core library (required by jtreesitter)

| Platform | Grammar Library | Runtime Library |
|----------|-----------------|-----------------|
| darwin-arm64 | `libtree-sitter-xtc.dylib` | `libtree-sitter.dylib` |
| darwin-x64 | `libtree-sitter-xtc.dylib` | `libtree-sitter.dylib` |
| linux-x64 | `libtree-sitter-xtc.so` | `libtree-sitter.so` |
| linux-arm64 | `libtree-sitter-xtc.so` | `libtree-sitter.so` |
| windows-x64 | `libtree-sitter-xtc.dll` | `libtree-sitter.dll` |

**Build Strategy**: Libraries are built on-demand and cached in `~/.gradle/caches/tree-sitter-xtc/<hash>/<platform>/`.
No binaries are committed to source control. First build downloads Zig (~45MB) and compiles (~3s for all platforms).
Subsequent builds use the cache (instant).

The runtime library is loaded via `TreeSitterLibraryLookup` which provides a custom
`NativeLibraryLookup` implementation for jtreesitter's FFM API.

---

## Task: Zig Cross-Compilation for All Platforms

**Status**: COMPLETE (2026-02-02)

### Overview

Zig cross-compilation enables building native libraries for all 5 platforms from any development machine.
A developer on macOS can build Windows and Linux binaries locally without Docker or platform-specific toolchains.

### Implementation

See [tree-sitter/README.md → Native Library Build](../../tree-sitter/README.md#native-library-build) for full documentation.
See [tree-sitter/native-build-strategy.md](./tree-sitter/native-build-strategy.md) for the build strategy decision.

#### Gradle Tasks

| Task | Description |
|------|-------------|
| `buildAllNativeLibrariesOnDemand` | **Build all 5 platforms** with caching (main task for lsp-server) |
| `populateNativeLibraryCache` | Pre-warm cache for all platforms (for CI) |
| `buildNativeLibrary_<platform>` | Cross-compile for specific platform (individual tasks) |
| `downloadZig` | Download Zig compiler (~45MB, cached in `~/.gradle/caches/zig/`) |
| `extractZig` | Extract using Apache Commons Compress (pure Java) |

#### Platform Outputs

| Platform | Zig Target | Output |
|----------|------------|--------|
| darwin-arm64 | `aarch64-macos` | `libtree-sitter-xtc.dylib` |
| darwin-x64 | `x86_64-macos` | `libtree-sitter-xtc.dylib` |
| linux-x64 | `x86_64-linux-gnu` | `libtree-sitter-xtc.so` |
| linux-arm64 | `aarch64-linux-gnu` | `libtree-sitter-xtc.so` |
| windows-x64 | `x86_64-windows-gnu` | `libtree-sitter-xtc.dll` |

#### Usage

```bash
# Build lsp-server fatJar (automatically builds all platform native libs)
./gradlew :lang:lsp-server:fatJar

# Run IDE with tree-sitter adapter
./gradlew :lang:intellij-plugin:runIde -Plsp.adapter=treesitter
```

### Build Strategy: On-Demand with Persistent Caching

- **No binaries in source control** - Removed ~6MB of committed native libraries
- **Zig cached** in `~/.gradle/caches/zig/<version>/` (persistent across clean builds)
- **Native libs cached** in `~/.gradle/caches/tree-sitter-xtc/<hash>/<platform>/` (keyed by grammar hash)
- **First build**: Downloads Zig (~45MB) + compiles all platforms (~3s total)
- **Subsequent builds**: Instant (cache hit)
- Pure Java extraction using Apache Commons Compress (no shell commands)
- macOS binaries work but aren't signed (fine for development)
- All 5 platforms bundled in the fatJar for cross-platform distribution

---

## What This Plan Does NOT Cover

These features require compiler integration (future adapter):

| Feature | Why Compiler Needed |
|---------|---------------------|
| Type inference | `val x = foo()` - need to resolve `foo()` return type |
| Semantic errors | Type mismatches, missing methods, etc. |
| Smart completion | Members of a type require type resolution |
| Cross-file rename | Need to know which references are semantic matches across files |
| Cross-file navigation | Import resolution, module dependency tracking |
| Semantic tokens (full) | Distinguishing field vs local vs parameter by type (basic syntax-level tokens done via tree-sitter) |
| Inlay hints | Type inference annotations (`:Int`, `:String`) |

> **Note**: Same-file rename, code actions (organize imports), formatting, folding ranges,
> document highlights, document links, and signature help have all been implemented using
> tree-sitter and regex approaches. The compiler adapter will enhance these with
> cross-file and semantic capabilities.

---

## Feature Implementation Status: Tree-sitter vs Compiler

> **Last Updated**: 2026-02-12

This table shows the current implementation status for LSP features, and which require
the full compiler adapter for advanced capabilities.

| Feature | Tree-sitter | Mock | Compiler | Status |
|---------|:-----------:|:----:|:--------:|--------|
| **Rename/prepareRename** | ✅ | ✅ | 🔮 | Same-file text/AST-based; cross-file requires compiler |
| **Code actions** | ✅ | ✅ | 🔮 | Organize imports implemented; semantic quick fixes need compiler |
| **Document formatting** | ✅ | ✅ | 🔮 | Trailing whitespace + final newline; full formatter needs compiler |
| **Range formatting** | ✅ | ✅ | 🔮 | Range-scoped trailing whitespace removal |
| **Folding ranges** | ✅ | ✅ | 🔮 | TS: AST nodes; Mock: brace matching |
| **Document highlights** | ✅ | ✅ | 🔮 | TS: AST identifiers; Mock: text matching |
| **Document links** | ✅ | ✅ | 🔮 | Clickable import paths (regex or AST) |
| **Signature help** | ✅ | ❌ | 🔮 | Same-file method parameters; overload resolution needs compiler |
| **Selection ranges** | ✅ | ❌ | 🔮 | AST walk-up chain; requires AST (Mock returns empty) |
| **Semantic tokens** | ✅ | ❌ | 🔮 | Syntax-level classification via tree-sitter; type-based resolution needs compiler |
| **Inlay hints** | ❌ | ❌ | 🔮 | Type inference hints require compiler |
| **Call hierarchy** | ❌ | ❌ | 🔮 | Requires semantic analysis |
| **Type hierarchy** | ❌ | ❌ | 🔮 | Requires type system |
| **Workspace symbols** | ✅ | ❌ | 🔮 | Cross-file indexing with fuzzy search (4-tier matching) |

**Legend:**
- ✅ = Implemented
- ❌ = Not yet implemented
- 🔮 = Planned for compiler adapter (full semantic support)

### Remaining Tree-sitter Opportunities

Features that could be partially implemented with tree-sitter in the future:

1. ~~**Semantic tokens** (basic)~~ ✅ COMPLETE (2026-02-12) — See `SemanticTokenEncoder.kt`
   - Classifies 18 AST contexts: class/interface/mixin/service/const/enum declarations,
     methods, constructors, properties, variables, parameters, modules, packages,
     annotations, type expressions, call expressions, member expressions
   - Produces LSP delta-encoded `List<Int>` from single-pass O(n) tree walk
   - **Tier 2 opportunities** (still tree-sitter, not yet implemented):
     enum values (→ enumMember), import paths (→ namespace/type), lambda parameters,
     typedef declarations, local functions, conditional declaration variables,
     catch clause variables, safe/async call expressions
2. ~~**Workspace symbols**~~ ✅ COMPLETE (2026-02-19) — `WorkspaceIndex` with `WorkspaceIndexer`,
   4-tier fuzzy search (exact, prefix, CamelCase, subsequence), background scanning
3. **Inlay hints** (structural) - Basic structural hints without type inference

Features requiring compiler for any useful implementation:

1. Call hierarchy
2. Type hierarchy
3. Semantic code actions (type fixes, missing imports)
4. Type-aware inlay hints

---

## Success Criteria

### Phase 1-2 Complete When:
- [x] `tree-sitter generate` succeeds (grammar validates)
- [x] Native library compiled for all platforms
- [x] JVM can load native library and parse `.x` files

### Phase 3-4 Complete When:
- [x] Query engine implemented
- [x] TreeSitterAdapter implements all basic LSP methods
- [x] Document symbols shows class/method outline
- [x] Go-to-definition works for local variables
- [x] Find references works within same file
- [x] Completion shows keywords and locals

### Phase 5 Complete When:
- [x] Go-to-definition works across files (via workspace index)
- [x] Workspace symbol search works (4-tier fuzzy matching)
- [ ] Performance acceptable (<100ms for typical operations) — needs benchmarking

---

## Next Steps

1. ~~**Implement Language Loading**~~ ✅ COMPLETE
2. ~~**Enable TreeSitterAdapter**~~ ✅ COMPLETE
3. ~~**Build Libraries for All Platforms**~~ ✅ COMPLETE

4. ~~**Add LSP Server Logging**~~ ✅ COMPLETE
   - Added structured logging throughout LSP server core (common to all adapters)
   - Logs parse times, query execution, symbol resolution with sub-ms precision
   - See "Task: LSP Server Logging" below

5. ~~**Add Native Library Rebuild Verification**~~ ✅ COMPLETE
   - Build fails if libraries are stale (no auto-rebuild)
   - Avoids downloading Zig in CI
   - See "Task: Native Library Staleness Verification" below

6. **Conditional Tree-sitter Dependency** (MEDIUM PRIORITY)
   - When `lsp.adapter=mock`, skip all tree-sitter build tasks
   - Native library should not be built/bundled for mock adapter
   - Reduces build time for non-tree-sitter development
   - See "Task: Conditional Tree-sitter Build" below

7. ~~**Out-of-Process LSP Server with Java 23+**~~ ✅ COMPLETE
   - LSP server runs as separate process with Java 23+ (FFM API requirement)
   - Full tree-sitter support regardless of IntelliJ JBR version
   - See "Task: Out-of-Process LSP Server" below

8. ~~**End-to-End Testing**~~ ✅ PARTIAL - `LspIntegrationTest` verifies all LSP features against real `.x` files with tree-sitter native parsing. Manual IDE testing still needed for IntelliJ/VS Code.
9. **IDE Integration** - See [PLAN_IDE_INTEGRATION.md](./PLAN_IDE_INTEGRATION.md)
10. **Compiler Adapter** - Add semantic features (future)

---

## Completed Tasks

| Task | Status | Documentation |
|------|--------|---------------|
| LSP Server Logging | COMPLETE (2026-01-31) | [tree-sitter/lsp-logging.md](./tree-sitter/lsp-logging.md) |
| Native Library Staleness | SUPERSEDED (2026-02-02) | Replaced by on-demand build |
| Adapter Rebuild Behavior | COMPLETE (2026-02-02) | [tree-sitter/adapter-rebuild.md](./tree-sitter/adapter-rebuild.md) |
| On-Demand Native Build | COMPLETE (2026-02-02) | [tree-sitter/native-build-strategy.md](./tree-sitter/native-build-strategy.md) |
| Out-of-Process LSP Server | COMPLETE (2026-02-02) | [lsp-processes.md](./lsp-processes.md) |

---

## Task: Conditional Tree-sitter Build

**Status**: PENDING (MEDIUM PRIORITY)

**Goal**: Skip native library bundling when `lsp.adapter=mock` to reduce build time.

**Details**: [tree-sitter/conditional-build.md](./tree-sitter/conditional-build.md)

---

## Task: Out-of-Process LSP Server

**Status**: COMPLETE (2026-02-02)

**Full Plan**: [lsp-processes.md](./lsp-processes.md)

### Implementation Summary

The LSP server now runs out-of-process with full tree-sitter support:

- **JRE Resolution**: First checks IntelliJ's registered JDKs, then cached JRE, then downloads
- **Cache Location**: `{GRADLE_USER_HOME}/caches/xtc-jre/temurin-25-jre/` (persistent, not IDE-managed)
- **Cache Validation**: Stores package ID and checks Foojay for updates every 7 days
- **Download**: Eclipse Temurin JRE 25 via Foojay Disco API (same as Gradle toolchains)
- **Extraction**: Uses IntelliJ's built-in `Decompressor.Tar`/`Decompressor.Zip`
- **Default Adapter**: `treesitter` (changed from `mock`)
- **Fallback**: If tree-sitter fails, falls back to mock with error notification

### Architecture

```
IntelliJ Plugin (JBR 21)          XTC LSP Server (Java 23+)
┌──────────────────────┐          ┌──────────────────────┐
│  XtcLanguageServer   │──stdio──▶│  XtcLanguageServer   │
│  Factory             │◀──stdio──│                      │
│                      │          │  ┌────────────────┐  │
│  XtcLspConnection    │          │  │ TreeSitter     │  │
│  Provider            │          │  │ Adapter        │  │
│  └─ Process monitor  │          │  │ (jtreesitter)  │  │
│  └─ Crash notif.     │          │  └────────────────┘  │
└──────────────────────┘          └──────────────────────┘
```

### Key Files

| File | Purpose |
|------|---------|
| `XtcLspServerSupportProvider.kt` | Factory and connection provider |
| `jre/JreProvisioner.kt` | JRE resolution (SDK table → cache → Foojay download) |
| `TreeSitterLibraryLookup.kt` | Loads libtree-sitter runtime from JAR |

### JRE Resolution Order

1. **Registered JDKs**: `ProjectJdkTable.getInstance().getSdksOfType(JavaSdk)` for Java 25+
2. **Cached JRE**: `{GRADLE_USER_HOME}/caches/xtc-jre/temurin-25-jre/` with metadata validation
3. **Download**: Foojay Disco API → Eclipse Temurin JRE 25 (cached with package ID for future validation)

### Features

- **Health Check**: `xtc/healthCheck` custom LSP method
- **Fail-Fast**: TreeSitterAdapter throws if health check fails
- **Crash Notification**: Shows dialog with "Restart Server" action
- **FFM Warnings Suppressed**: `--enable-native-access=ALL-UNNAMED`

---

## Dependencies

### External

| Dependency | Version | Purpose | Java Req |
|------------|---------|---------|----------|
| tree-sitter CLI | 0.24.3 | Grammar validation (auto-downloaded) | N/A |
| jtreesitter | 0.24.1 | JVM bindings (FFM API) | **Java 22+** |
| lsp4j | 0.21.1 | LSP protocol implementation | Java 11+ |
| lsp4ij | 0.19.1 | IntelliJ LSP client | Java 17+ |

> **Note**: jtreesitter 0.24.x requires Java 22+ despite being the "oldest" version.
> See [Critical: Java Version Compatibility](#-critical-java-version-compatibility-issue) above.

### Internal

| Component | Location | Purpose |
|-----------|----------|---------|
| `TreeSitterGenerator` | `lang/dsl/.../generators/` | Generates `grammar.js` |
| `ScannerSpec.kt` | `lang/dsl/.../scanner/` | External scanner spec |
| `XtcLanguage.kt` | `lang/dsl/` | Language model (60+ AST concepts) |
| Tree-sitter wrappers | `lang/lsp-server/.../treesitter/` | JVM integration |

---

## Reference

The authoritative sources for XTC syntax:

| File | Purpose |
|------|---------|
| `javatools/.../Lexer.java` | Token definitions |
| `javatools/.../Parser.java` | Grammar rules |
| `javatools/.../Token.java` | Keyword/operator enums |

---

## Task: Grammar Field Definitions

**Status**: ✅ COMPLETE (2026-02-19, `lagergren/lsp-extend4` branch)

**Goal**: Add field() definitions to grammar.js to enable field-based query syntax, improve semantic token robustness, and simplify LSP adapter code.

### Implementation

Field definitions were added to ~30 grammar rules in `grammar.js.template` (184 lines modified).
Queries in `XtcQueries.kt` were migrated from positional to field-based syntax.
`SemanticTokenEncoder` uses `childByFieldName()` for robust node classification.

### Benefits

| Benefit | Description |
|---------|-------------|
| **Robust queries** | Queries match by field name, not child position |
| **Self-documenting** | `name: (type_name) @name` is clearer than positional |
| **API improvements** | `childByFieldName("name")` would work |
| **Best practices** | Aligns with tree-sitter community standards |
| **Tool compatibility** | Better support from tree-sitter tooling |

### Impact on Semantic Tokens

The `SemanticTokenEncoder` currently relies on `childByType()` to find children, which is
**fragile** — it returns the *first* child matching a type, which can be wrong when a node
has multiple children of the same type. Examples of current fragility:

| Problem | Current Code | With Fields |
|---------|-------------|-------------|
| Method return type vs body type | `node.childByType("type_expression")` finds the first one (return type — correct by accident) | `node.childByFieldName("return_type")` — unambiguous |
| Property type vs initializer type | `node.childByType("type_expression")` — correct only because type comes first | `node.childByFieldName("type")` — explicit |
| Multiple identifiers in member_expression | `node.children.filter { it.type == "identifier" }.lastOrNull()` — positional heuristic | `node.childByFieldName("member")` — semantic |
| Constructor "construct" keyword | Iterates all children checking `child.text == "construct"` | `node.childByFieldName("name")` |

With field definitions, the encoder could replace every `childByType()` call with
`childByFieldName()`, making classification both faster (direct lookup vs linear scan)
and more correct (no positional assumptions).

### Impact on Other LSP Features

| Feature | Current Fragility | With Fields |
|---------|-------------------|-------------|
| **Document symbols** | `XtcQueries.kt` uses positional query patterns that may match wrong children | Field-based queries are exact |
| **Go-to-definition** | `childByType("identifier")` may find the wrong identifier in complex nodes | `childByFieldName("name")` is unambiguous |
| **Signature help** | Finding parameter list requires `childByType("parameters")` — works but fragile | `childByFieldName("parameters")` |
| **Find references** | Identifier extraction relies on positional child matching | Field-based extraction |
| **Rename** | Must correctly identify the "name" child of a declaration to rename it | Direct field access |

### Scope of Grammar Changes

The grammar has **144 named rule types**. Field definitions should be added to the
**~30 rules** that have semantically important children:

**Priority 1 — Declarations** (directly used by semantic tokens, symbols, navigation):
- `class_declaration`: `name`, `type_parameters`, `extends`, `implements`, `body`
- `interface_declaration`: `name`, `type_parameters`, `extends`, `body`
- `mixin_declaration`: `name`, `type_parameters`, `into`, `body`
- `service_declaration`: `name`, `type_parameters`, `body`
- `const_declaration`: `name`, `type_parameters`, `body`
- `enum_declaration`: `name`, `type_parameters`, `body`
- `method_declaration`: `return_type`, `name`, `parameters`, `body`
- `constructor_declaration`: `name`, `parameters`, `body`
- `property_declaration`: `type`, `name`, `value`
- `variable_declaration`: `type`, `name`, `value`
- `parameter`: `type`, `name`, `default`
- `module_declaration`: `name`, `body`
- `package_declaration`: `name`, `body`
- `typedef_declaration`: `name`, `type`
- `enum_value`: `name`, `arguments`, `body`

**Priority 2 — Expressions** (used by call/member classification):
- `call_expression`: `function`, `arguments`
- `member_expression`: `object`, `member`
- `new_expression`: `type`, `arguments`
- `lambda_expression`: `parameters`, `body`
- `assignment_expression`: `left`, `right`

**Priority 3 — Statements** (used by variable classification, control flow):
- `for_statement`: `initializer`, `condition`, `update`, `body`
- `catch_clause`: `type`, `name`, `body`
- `if_statement`: `condition`, `consequence`, `alternative`
- `switch_statement`: `value`, `body`

### Migration

Queries were migrated from positional to field-based syntax:
```scheme
; Before (positional)
(class_declaration (type_name) @name) @declaration

; After (field-based)
(class_declaration name: (type_name) @name) @declaration
```

### Files Affected

| File | Change |
|------|--------|
| `dsl/.../TreeSitterGenerator.kt` | Add field() wrappers |
| `tree-sitter/build/generated/grammar.js` | Regenerated |
| `lsp-server/.../XtcQueries.kt` | Use field: syntax |
| `lsp-server/.../XtcNode.kt` | Remove childByType workaround |
| Native libraries (5 platforms) | Rebuild all |

### Remaining Tier 2 Opportunities

With field definitions in place, Tier 2 semantic token contexts (enum values, lambda
params, catch variables, typedef declarations) can now use `childByFieldName()` for
robust classification without positional fragility.

---

## Manual Test Plan

See [MANUAL_TEST_PLAN.md](../MANUAL_TEST_PLAN.md) for comprehensive testing instructions,
including out-of-process server startup, crash recovery, and health check verification.
