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

**Status**: IMPLEMENTED (2026-02-02)

The out-of-process LSP server is now the default:

- **Default adapter**: `treesitter` (changed from `mock`)
- **Process model**: LSP server runs as separate Java process
- **Java requirement**: 23+ (for FFM API - see `MIN_JAVA_VERSION` constant)
- **Communication**: stdio (JSON-RPC)
- **Health monitoring**: Process monitor with crash notification and restart action

The IntelliJ plugin:
1. Finds a Java 23+ runtime (JAVA_HOME, XTC_JAVA_HOME, or PATH)
2. Launches `xtc-lsp-server.jar` as a subprocess
3. Communicates via LSP protocol over stdin/stdout
4. Shows notification with "Restart Server" action on crash

> **Documentation**:
> - [lang/tree-sitter/README.md](../../tree-sitter/README.md) - Usage and architecture
> - [TREE_SITTER_IMPLEMENTATION_NOTES.md](./TREE_SITTER_IMPLEMENTATION_NOTES.md) - Implementation history

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
- [x] Pre-built libraries for all platforms committed to source control
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
- [ ] Tests for query accuracy (blocked on native library)

### LSP Features (Phase 4) - IMPLEMENTED, PENDING TESTING

- [x] `TreeSitterAdapter` implements all basic LSP methods
- [ ] Document symbols shows class/method outline
- [ ] Go-to-definition works for local variables
- [ ] Find references works within same file
- [ ] Completion shows keywords and locals

### Cross-File Support (Phase 5) - PENDING

- [ ] `WorkspaceIndex` for cross-file symbol tracking
- [ ] Cross-file go-to-definition
- [ ] Workspace symbol search
- [ ] Incremental re-indexing on file change

---

## Adapter Architecture

The LSP server uses a pluggable adapter pattern:

```
┌─────────────────────────────────────────────────────────────┐
│                     XtcLanguageServer                       │
│            (takes XtcCompilerAdapter via constructor)       │
└────────────────────────────┬────────────────────────────────┘
                             │
               ┌─────────────┴─────────────┐
               │    XtcCompilerAdapter     │  ← Interface
               └─────────────┬─────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ MockXtcCompiler │  │ TreeSitter      │  │ (Future)        │
│ Adapter         │  │ Adapter         │  │ CompilerAdapter │
│                 │  │                 │  │                 │
│ - Regex-based   │  │ - Tree-sitter   │  │ - Full XTC      │
│ - For testing   │  │ - Syntax only   │  │   compiler      │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

### Switching Adapters

```bash
# Build with Tree-sitter adapter (default)
./gradlew :lang:lsp-server:build

# Build with Mock adapter (for testing without native libraries)
./gradlew :lang:lsp-server:build -Plsp.adapter=mock
```

---

## Task: Build Native Tree-sitter Library

**Status**: COMPLETE

Native library build infrastructure is fully implemented using Zig cross-compilation.
See [tree-sitter/README.md → Native Library Build](../../tree-sitter/README.md#native-library-build).

### Pre-built Libraries

Two native libraries are built for each platform and committed to source control:

1. **XTC Grammar Library** (`libtree-sitter-xtc.*`) - The compiled XTC grammar
2. **Tree-sitter Runtime** (`libtree-sitter.*`) - The tree-sitter core library (required by jtreesitter)

| Platform | Grammar Library | Runtime Library |
|----------|-----------------|-----------------|
| darwin-arm64 | `libtree-sitter-xtc.dylib` | `libtree-sitter.dylib` |
| darwin-x64 | `libtree-sitter-xtc.dylib` | `libtree-sitter.dylib` |
| linux-x64 | `libtree-sitter-xtc.so` | `libtree-sitter.so` |
| linux-arm64 | `libtree-sitter-xtc.so` | `libtree-sitter.so` |
| windows-x64 | `libtree-sitter-xtc.dll` | `libtree-sitter.dll` |

Location: `tree-sitter/src/main/resources/native/<platform>/`

The runtime library is loaded via `TreeSitterLibraryLookup` which provides a custom
`NativeLibraryLookup` implementation for jtreesitter's FFM API.

---

## Task: Zig Cross-Compilation for All Platforms

**Status**: COMPLETE (2026-01-31)

### Overview

Zig cross-compilation enables building native libraries for all 5 platforms from any development machine.
A developer on macOS can build Windows and Linux binaries locally without Docker or platform-specific toolchains.

### Implementation

See [tree-sitter/README.md → Native Library Build](../../tree-sitter/README.md#native-library-build) for full documentation.

#### Gradle Tasks

| Task | Description |
|------|-------------|
| `ensureNativeLibraryUpToDate` | **Verify** pre-built library matches grammar (fails if stale) |
| `copyAllNativeLibrariesToResources` | Rebuild all platforms and copy to resources |
| `buildAllNativeLibraries` | Build for all 5 platforms |
| `buildNativeLibrary_<platform>` | Cross-compile for specific platform |
| `downloadZig` | Download Zig compiler (~45MB, cached) |
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
# Verify pre-built libraries are up-to-date (fails if stale)
./gradlew :lang:tree-sitter:ensureNativeLibraryUpToDate

# Rebuild all platforms (if stale) and copy to resources
./gradlew :lang:tree-sitter:copyAllNativeLibrariesToResources

# Run IDE with tree-sitter adapter
./gradlew :lang:intellij-plugin:runIde -Plsp.adapter=treesitter
```

### Notes

- Pure Java extraction using Apache Commons Compress (no shell commands)
- Pre-built binaries committed to source control (~6MB total)
- Staleness detection via SHA-256 hash of grammar inputs
- Build **fails** if stale (no auto-rebuild) to avoid Zig download in CI
- Zig only downloaded when explicitly running rebuild tasks
- macOS binaries work but aren't signed (fine for development)

---

## What This Plan Does NOT Cover

These features require compiler integration (future adapter):

| Feature | Why Compiler Needed |
|---------|---------------------|
| Type inference | `val x = foo()` - need to resolve `foo()` return type |
| Semantic errors | Type mismatches, missing methods, etc. |
| Smart completion | Members of a type require type resolution |
| Rename refactoring | Need to know which references are semantic matches |
| Import organization | Need to resolve qualified names |

The tree-sitter approach provides ~70% of LSP functionality. Add the compiler adapter
later for semantic features.

---

## Not-Yet-Implemented Features: Tree-sitter vs Compiler

The following LSP features are not yet implemented. This table shows which can be done with
tree-sitter alone vs which require the full compiler adapter.

| Feature | Tree-sitter | Compiler | Notes |
|---------|:-----------:|:--------:|-------|
| **Rename/prepareRename** | ❌ | ✅ | Requires semantic analysis to identify all references to the same symbol across scopes |
| **Code actions** | ⚠️ Partial | ✅ | Structural fixes (missing braces, formatting) with tree-sitter; semantic fixes (import, type) require compiler |
| **Document formatting** | ✅ | ✅ | Syntax-based formatting works with tree-sitter; type-aware formatting needs compiler |
| **Semantic tokens** | ⚠️ Partial | ✅ | Keyword/syntax highlighting with tree-sitter; type-based coloring (method vs property) needs compiler |
| **Signature help** | ⚠️ Partial | ✅ | Can show signature syntax with tree-sitter; parameter types and overload resolution need compiler |
| **Folding ranges** | ✅ | ✅ | Structural folding (blocks, functions, comments) is purely syntactic |
| **Inlay hints** | ⚠️ Partial | ✅ | Structural hints possible; type inference hints (`val x = ...` → `: Int`) require compiler |
| **Call hierarchy** | ❌ | ✅ | Requires semantic analysis to resolve function references across files |
| **Type hierarchy** | ❌ | ✅ | Requires type system to understand inheritance relationships |
| **Workspace symbols** | ⚠️ Partial | ✅ | Same-file with tree-sitter; cross-file requires indexing or compiler integration |

**Legend:**
- ✅ = Full support possible
- ⚠️ Partial = Basic functionality possible, advanced features need compiler
- ❌ = Requires compiler

### Recommended Implementation Order (Tree-sitter First)

Features that can be fully implemented with tree-sitter should be prioritized:

1. **Folding ranges** - Pure syntax, easy win
2. **Document formatting** - Syntax-based, high value
3. **Semantic tokens** (basic) - Better highlighting than TextMate
4. **Code actions** (structural) - Missing semicolons, brace fixes
5. **Signature help** (basic) - Show parameter names from syntax
6. **Workspace symbols** (same-file) - Index declarations per file

Features requiring compiler should wait for `XtcCompilerAdapterFull`:

1. Rename/prepareRename
2. Call hierarchy
3. Type hierarchy
4. Semantic code actions (imports, type fixes)
5. Type-aware inlay hints

---

## Success Criteria

### Phase 1-2 Complete When:
- [x] `tree-sitter generate` succeeds (grammar validates)
- [x] Native library compiled for all platforms
- [x] JVM can load native library and parse `.x` files

### Phase 3-4 Complete When:
- [x] Query engine implemented
- [x] TreeSitterAdapter implements all basic LSP methods
- [ ] Document symbols shows class/method outline
- [ ] Go-to-definition works for local variables
- [ ] Find references works within same file
- [ ] Completion shows keywords and locals

### Phase 5 Complete When:
- [ ] Go-to-definition works across files
- [ ] Workspace symbol search works
- [ ] Performance acceptable (<100ms for typical operations)

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

7. **Out-of-Process LSP Server with Java 24** (HIGH PRIORITY)
   - Run LSP server as separate process with Java 24 (Foojay Temurin)
   - Enables full tree-sitter support regardless of IntelliJ JBR version
   - See [PLAN_OUT_OF_PROCESS_LSP.md](./PLAN_OUT_OF_PROCESS_LSP.md) for full implementation plan

8. **End-to-End Testing** - Verify LSP features work in IntelliJ/VS Code
8. **IDE Integration** - See [PLAN_IDE_INTEGRATION.md](./PLAN_IDE_INTEGRATION.md)
9. **Compiler Adapter** - Add semantic features (future)

---

## Task: LSP Server Logging

**Status**: COMPLETE (2026-01-31)

**Goal**: Add comprehensive logging to the LSP server core, common to ALL adapters (Mock, TreeSitter, future Compiler).

### Core Logging (XtcLanguageServer)

These logging points apply regardless of which adapter is used:

1. **Server Lifecycle**
   - Server initialization: adapter type, configuration
   - Client capabilities received
   - Workspace folder changes
   - Server shutdown

2. **Document Events**
   - File open: URI, size, detected language version
   - File change: URI, change type (full/incremental)
   - File close: URI, session duration
   - File save: URI

3. **LSP Request/Response**
   - Request received: method, params summary
   - Response sent: method, result count, timing
   - Errors: method, error code, message

### Adapter-Specific Logging

Additional logging in each adapter implementation:

**MockXtcCompilerAdapter**:
- Regex pattern matches
- Declaration extraction

**TreeSitterAdapter**:
- Native library loading: path, platform, load time
- Parse operations: file path, source size, parse time, error count
- Query execution: query name, execution time, match count

**Future CompilerAdapter**:
- Type resolution, semantic analysis timing

### Implementation

Use SLF4J (already a dependency) with appropriate log levels:
- `DEBUG`: Detailed operation info (parse times, query results)
- `INFO`: High-level operations (server started, file opened)
- `WARN`: Recoverable issues (parse errors, missing symbols)
- `ERROR`: Failures (initialization failed, unhandled exceptions)

### Example Output

```
INFO  [XtcLanguageServer] Started with adapter: TreeSitterAdapter
INFO  [XtcLanguageServer] Client capabilities: completion, hover, definition
DEBUG [XtcLanguageServer] textDocument/didOpen: file:///project/MyClass.x (2,450 bytes)
DEBUG [TreeSitterAdapter] Parsed in 3.2ms, 0 errors
DEBUG [XtcLanguageServer] textDocument/documentSymbol: 12 symbols in 4.1ms
```

### Testing

```bash
# Run with debug logging (works with any adapter)
./gradlew :lang:intellij-plugin:runIde

# In IntelliJ: Help → Diagnostic Tools → Debug Log Settings
# Add: org.xvm.lsp
```

---

## Task: Native Library Staleness Verification

**Status**: COMPLETE (2026-01-31)

**Goal**: Verify the native library build system correctly detects when rebuild is needed.

### Behavior

The `ensureNativeLibraryUpToDate` task:
1. Computes SHA-256 hash of `grammar.js` + `scanner.c`
2. Compares to stored hash in `.inputs.sha256`
3. If match: logs version info and succeeds
4. If mismatch: **FAILS** with instructions to rebuild

This design avoids downloading Zig in CI (where libraries should always be up-to-date).

### Test Cases

1. **Fail on hash mismatch**
   - Corrupt the `.inputs.sha256` file
   - Run `ensureNativeLibraryUpToDate`
   - Verify task FAILS with "STALE" message

2. **Success when up-to-date**
   - Run `ensureNativeLibraryUpToDate` with correct hash
   - Verify task succeeds and logs version info

### Manual Testing

```bash
# Verify current libraries are up-to-date
./gradlew :lang:tree-sitter:ensureNativeLibraryUpToDate

# Simulate stale library (corrupt hash)
echo "0000" > lang/tree-sitter/src/main/resources/native/darwin-arm64/libtree-sitter-xtc.inputs.sha256
./gradlew :lang:tree-sitter:ensureNativeLibraryUpToDate  # Should FAIL

# Rebuild to fix
./gradlew :lang:tree-sitter:copyAllNativeLibrariesToResources
```

---

## Task: Conditional Tree-sitter Build

**Status**: PENDING

**Goal**: Skip tree-sitter native library build when using mock adapter, reducing build time.

### Current Behavior

- `lsp-server` always depends on `tree-sitter` native library
- Native library is always built/copied to resources
- Build time cost even when not using tree-sitter adapter

### Desired Behavior

When `lsp.adapter=mock` (default):
- Skip `ensureNativeLibraryUpToDate` task
- Skip `copyNativeLibToResources` task
- lsp-server JAR includes tree-sitter code but no native library
- Runtime gracefully handles missing native library (already does - falls back to mock)

When `lsp.adapter=treesitter`:
- Current behavior (build/bundle native library)

### Implementation Options

**Option A: Conditional task dependency**
```kotlin
// In lsp-server/build.gradle.kts
val copyNativeLibToResources by tasks.existing {
    onlyIf { lspAdapter == "treesitter" }
}
```

**Option B: Separate source sets**
- Create `treesitter` source set with native resources
- Only include when adapter is treesitter

**Option C: Feature flag in fat JAR**
- Always include code, conditionally include native library
- Use `fatJar` exclusion patterns based on adapter

### Recommendation

Option A is simplest. The tree-sitter Kotlin code is small (~50KB) and harmless to include.
Only skip the native library bundling (~1.2MB per platform).

### Testing

```bash
# Fast build for mock adapter (no native library)
./gradlew :lang:intellij-plugin:runIde
# JAR should NOT contain native/ directory

# Full build with tree-sitter
./gradlew :lang:intellij-plugin:runIde -Plsp.adapter=treesitter
# JAR should contain native/<platform>/libtree-sitter-xtc.*
```

---

## Task: Out-of-Process LSP Server

**Status**: COMPLETE (2026-02-02)

**Full Plan**: [PLAN_OUT_OF_PROCESS_LSP.md](./PLAN_OUT_OF_PROCESS_LSP.md)

### Implementation Summary

The LSP server now runs out-of-process with full tree-sitter support:

- **Java Resolution**: Searches `xtc.lsp.java.home` → `XTC_JAVA_HOME` → `JAVA_HOME` → PATH
- **Minimum Java**: 23+ (see `MIN_JAVA_VERSION` constant in `TreeSitterAdapter` and `XtcLspConnectionProvider`)
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
| `XtcLspConnectionProvider.MIN_JAVA_VERSION` | Java version requirement constant |
| `TreeSitterAdapter.MIN_JAVA_VERSION` | Java version requirement constant |
| `TreeSitterLibraryLookup.kt` | Loads libtree-sitter runtime from JAR |

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
> See [Critical: Java Version Compatibility](#️-critical-java-version-compatibility-issue) above.

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

**Status**: PENDING (LOW PRIORITY)

**Goal**: Add field() definitions to grammar.js to enable field-based query syntax and simplify LSP adapter code.

### Current State

The XTC grammar does NOT define field names. Rules use positional syntax:

```javascript
// Current: positional (no fields)
class_declaration: $ => seq(
    optional($.doc_comment),
    repeat($.annotation),
    optional($.visibility_modifier),
    optional('static'),
    optional('abstract'),
    'class',
    $.type_name,           // <-- No field name
    optional($.type_parameters),
    ...
),
```

### Proposed Change

Add `field()` wrappers to key children:

```javascript
// Proposed: with field names
class_declaration: $ => seq(
    optional($.doc_comment),
    repeat($.annotation),
    optional($.visibility_modifier),
    optional('static'),
    optional('abstract'),
    'class',
    field('name', $.type_name),     // <-- Named field
    optional(field('type_parameters', $.type_parameters)),
    ...
),
```

### Benefits

| Benefit | Description |
|---------|-------------|
| **Robust queries** | Queries match by field name, not child position |
| **Self-documenting** | `name: (type_name) @name` is clearer than positional |
| **API improvements** | `childByFieldName("name")` would work |
| **Best practices** | Aligns with tree-sitter community standards |
| **Tool compatibility** | Better support from tree-sitter tooling |

### Current Workaround

The LSP adapter uses positional matching in queries:
```scheme
; Positional (current)
(class_declaration (type_name) @name) @declaration

; Field-based (after migration)
(class_declaration name: (type_name) @name) @declaration
```

And `childByType()` helper in XtcNode instead of `childByFieldName()`.

### Implementation Notes

1. **Grammar changes** required in `lang/dsl/.../generators/TreeSitterGenerator.kt`
2. **Regenerate** grammar via `./gradlew :lang:tree-sitter:generateTreeSitterGrammar`
3. **Rebuild** native libraries for all 5 platforms
4. **Update** XtcQueries.kt to use field syntax
5. **Simplify** XtcNode (childByType no longer needed)

### Files Affected

| File | Change |
|------|--------|
| `dsl/.../TreeSitterGenerator.kt` | Add field() wrappers |
| `tree-sitter/build/generated/grammar.js` | Regenerated |
| `lsp-server/.../XtcQueries.kt` | Use field: syntax |
| `lsp-server/.../XtcNode.kt` | Remove childByType workaround |
| Native libraries (5 platforms) | Rebuild all |

### Decision

**Defer until Phase 5 (Cross-File Support)** - The current positional matching works correctly.
This refactor is a nice-to-have improvement, not blocking LSP functionality.

---

## Manual Test Plan

### Prerequisites

1. Java 23+ installed and available via `JAVA_HOME` or on PATH
2. XTC project with `.x` files

### Test: Out-of-Process LSP Server Startup

```bash
./gradlew :lang:intellij-plugin:runIde
```

**Expected in console:**
```
[XTC-LSP] XTC Language Server v0.4.4-SNAPSHOT
[XTC-LSP] Backend: Tree-sitter
[XTC-LSP] TreeSitterAdapter ready: native library loaded and verified
[XTC-LSP] XtcParser health check PASSED: parsed test module successfully
```

**Expected in IDE:**
- Notification: "XTC Language Server Started - Out-of-process server (v0.4.4-SNAPSHOT, adapter=treesitter)"

### Test: Health Check Verification

1. Open an `.x` file in the IDE
2. Look for console output:
   - `Native library: extracted libtree-sitter-xtc.dylib to ...`
   - `Native library: successfully loaded XTC tree-sitter grammar (FFM API)`
   - `XtcParser health check PASSED`

### Test: Document Symbols

1. Open an XTC file with classes/methods
2. View → Tool Windows → Structure (or Cmd+7)
3. Verify outline shows class/method hierarchy

### Test: Crash Recovery

1. Find the LSP server process: `ps aux | grep xtc-lsp-server`
2. Kill it: `kill -9 <pid>`
3. Verify notification appears: "XTC Language Server Crashed"
4. Click "Restart Server"
5. Verify server restarts (new notification)

### Test: Version Display

1. After LSP starts, check notification shows correct version
2. Version should NOT be "?" - should show actual version like "v0.4.4-SNAPSHOT"

### Test: Native Library Not Found

1. Temporarily rename/remove native libraries from JAR
2. Start IDE
3. Verify error notification about native library
4. Verify fallback to mock adapter (or fail-fast error)

### Test: Java Version Too Low

1. Set `JAVA_HOME` to Java 21 installation
2. Unset `XTC_JAVA_HOME`
3. Start IDE
4. Verify error: "No Java 23+ runtime found"
