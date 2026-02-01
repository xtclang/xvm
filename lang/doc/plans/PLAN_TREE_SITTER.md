# PLAN: Tree-sitter Integration for XTC Language Support

**Goal**: Use the existing `TreeSitterGenerator` to build a functional LSP with syntax-level
intelligence, without requiring compiler modifications.

**Risk**: Low (additive, no compiler changes)
**Prerequisites**: Working `TreeSitterGenerator` in `lang/dsl/` (already exists)

---

## ⚠️ CRITICAL: Java Version Compatibility Issue

> **Last Updated**: 2026-02-01

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

### Recommendation

**Short-term**: Use mock adapter (current default). The fallback mechanism works correctly.

**Medium-term**: Implement out-of-process LSP server with Java 25. This aligns with:
- XDK toolchain (Java 25)
- VS Code extension architecture (already out-of-process)
- Future-proofing (can use latest jtreesitter)

**Long-term**: When IntelliJ ships JBR 22+, consider in-process option for lower latency.

> **Documentation**:
> - [lang/tree-sitter/README.md](../../tree-sitter/README.md) - Usage and architecture
> - [TREE_SITTER_IMPLEMENTATION_NOTES.md](./TREE_SITTER_IMPLEMENTATION_NOTES.md) - Implementation history

---

## Implementation Status

> **Last Updated**: 2026-02-01

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
# Build with Mock adapter (default)
./gradlew :lang:lsp-server:build

# Build with Tree-sitter adapter
./gradlew :lang:lsp-server:build -Plsp.adapter=treesitter
```

---

## Task: Build Native Tree-sitter Library

**Status**: COMPLETE

Native library build infrastructure is fully implemented using Zig cross-compilation.
See [tree-sitter/README.md → Native Library Build](../../tree-sitter/README.md#native-library-build).

### Pre-built Libraries

All platforms built and committed to source control:

| Platform | Zig Target | Output |
|----------|------------|--------|
| darwin-arm64 | `aarch64-macos` | `libtree-sitter-xtc.dylib` |
| darwin-x64 | `x86_64-macos` | `libtree-sitter-xtc.dylib` |
| linux-x64 | `x86_64-linux-gnu` | `libtree-sitter-xtc.so` |
| linux-arm64 | `aarch64-linux-gnu` | `libtree-sitter-xtc.so` |
| windows-x64 | `x86_64-windows-gnu` | `libtree-sitter-xtc.dll` |

Location: `tree-sitter/src/main/resources/native/<platform>/`

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

7. **Out-of-Process LSP Server with Java 25** (HIGH PRIORITY)
   - Run LSP server as separate process with Java 25 (XDK toolchain)
   - Enables full tree-sitter support regardless of IntelliJ JBR version
   - See "Task: Out-of-Process LSP Server" below

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

**Status**: PENDING (HIGH PRIORITY)

**Goal**: Run the XTC LSP server as a separate process with Java 25, enabling full tree-sitter
support regardless of IntelliJ's JBR version.

### Background

The [Java Version Compatibility Issue](#️-critical-java-version-compatibility-issue) prevents
tree-sitter from working in-process with IntelliJ. The solution is to run the LSP server as
a separate process with its own JVM.

This architecture is common for LSP implementations and is already used by:
- VS Code extensions (always out-of-process)
- Many IntelliJ LSP plugins (Rust Analyzer, Erlang LS, etc.)

### Architecture

```
IntelliJ Plugin (JBR 21)          XTC LSP Server (Java 25)
┌──────────────────────┐          ┌──────────────────────┐
│  XtcLspServerSupport │──stdio──▶│  XtcLanguageServer   │
│  Provider            │◀──stdio──│                      │
│                      │          │  ┌────────────────┐  │
│  Uses LSP4IJ to      │          │  │ TreeSitter     │  │
│  communicate         │          │  │ Adapter        │  │
└──────────────────────┘          │  │ (jtreesitter)  │  │
                                  │  └────────────────┘  │
                                  └──────────────────────┘
```

### Implementation Steps

1. **Build lsp-server as standalone fat JAR**
   - Already builds as fat JAR
   - Ensure it's runnable: `java -jar xtc-lsp-server.jar`
   - Add main class manifest

2. **Modify IntelliJ plugin to launch external process**
   - Replace in-process `XtcLanguageServer` instantiation
   - Use LSP4IJ's `ProcessBuilder` server definition
   - Configure stdio communication

3. **Java 25 Runtime Resolution**
   - Option A: Bundle minimal JRE with plugin (~50MB compressed)
   - Option B: Use system Java 25 if available
   - Option C: Download JRE on first use (like VS Code Java extension)

4. **Compile lsp-server with Java 25 toolchain**
   - Update `lsp-server/build.gradle.kts` to target Java 25
   - Use latest jtreesitter (0.26.x for Java 25)
   - intellij-plugin stays on Java 21 (JBR compatibility)

### Trade-offs

| Aspect | In-Process | Out-of-Process |
|--------|------------|----------------|
| Latency | Lower (~1-5ms) | Higher (~10-50ms) |
| Memory | Shared with IDE | Separate process |
| Java version | Constrained by JBR | Any version |
| Debugging | Easier | Requires remote debug |
| Deployment | Simpler | JRE bundling needed |

### Recommended Approach

**Phase 1**: Implement out-of-process with system Java 25 requirement
- Simpler initial implementation
- Users must have Java 25 installed
- Log clear error if Java 25 not found

**Phase 2**: Bundle JRE for zero-configuration
- Use `jlink` to create minimal JRE (~40MB)
- Bundle per platform in plugin
- Increases plugin size but improves UX

### Automatic Server Management

The plugin does NOT require users to manually start the LSP server. LSP4IJ handles this:

1. **User opens `.x` file** in IntelliJ
2. **LSP4IJ detects** XTC language association
3. **Plugin's `XtcLspServerSupportProvider`** is invoked
4. **Server is started automatically** via the `StreamConnectionProvider`
5. **Server runs in background** until IDE closes or project closes

Users never see or interact with the server process directly.

### JRE Bundling Strategy

For production deployment, the plugin should bundle a minimal Java 25 JRE:

```
intellij-plugin/
└── src/main/resources/
    └── jre/
        ├── darwin-arm64/   # macOS Apple Silicon
        ├── darwin-x64/     # macOS Intel
        ├── linux-x64/      # Linux x86_64
        ├── linux-arm64/    # Linux ARM64
        └── windows-x64/    # Windows x86_64
```

**Creating minimal JRE with jlink** (~40MB per platform):
```bash
# Example for darwin-arm64
jlink \
    --add-modules java.base,java.logging,java.management,jdk.unsupported \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=2 \
    --output jre-minimal
```

**Build integration**:
```kotlin
// In intellij-plugin/build.gradle.kts
val downloadJre by tasks.registering {
    // Download pre-built minimal JREs for all platforms
    // Or use jlink to create them during build
}
```

### Example Code

```kotlin
// In XtcLspServerSupportProvider.kt
override fun createServer(project: Project): StreamConnectionProvider {
    val java25 = resolveBundledJre()
        ?: findSystemJava25()
        ?: throw IllegalStateException(
            "Java 25 required for XTC tree-sitter support. " +
            "Install Java 25 or update the XTC IntelliJ plugin."
        )

    val serverJar = extractLspServerJar()  // Extract from plugin resources

    return ProcessStreamConnectionProvider(
        listOf(java25.toString(), "-jar", serverJar.toString()),
        project.basePath
    )
}

private fun resolveBundledJre(): Path? {
    val platform = detectPlatform()  // darwin-arm64, linux-x64, etc.
    val jrePath = PluginManager.getPlugin(PluginId.getId("org.xtclang.idea"))
        ?.pluginPath
        ?.resolve("jre/$platform/bin/java")
    return jrePath?.takeIf { Files.exists(it) }
}

private fun findSystemJava25(): Path? {
    // Check JAVA_HOME
    System.getenv("JAVA_HOME")?.let { javaHome ->
        val java = Path.of(javaHome, "bin", "java")
        if (Files.exists(java) && isJava25OrHigher(java)) return java
    }

    // Check common install locations
    listOf(
        "/usr/lib/jvm/java-25",
        "/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home",
        "C:\\Program Files\\Java\\jdk-25",
    ).forEach { path ->
        val java = Path.of(path, "bin", "java")
        if (Files.exists(java)) return java
    }

    return null
}
```

### Testing

```bash
# Test standalone server
java -jar lang/lsp-server/build/libs/xtc-lsp-server-fat.jar

# Test with IntelliJ
./gradlew :lang:intellij-plugin:runIde
# Should see: "XTC Language Server Started (out-of-process, Java 25)"
```

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
