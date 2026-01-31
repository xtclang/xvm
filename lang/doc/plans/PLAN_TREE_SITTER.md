# PLAN: Tree-sitter Integration for XTC Language Support

**Goal**: Use the existing `TreeSitterGenerator` to build a functional LSP with syntax-level
intelligence, without requiring compiler modifications.

**Risk**: Low (additive, no compiler changes)
**Prerequisites**: Working `TreeSitterGenerator` in `lang/dsl/` (already exists)

> **Documentation**:
> - [lang/tree-sitter/README.md](../../tree-sitter/README.md) - Usage and architecture
> - [TREE_SITTER_IMPLEMENTATION_NOTES.md](./TREE_SITTER_IMPLEMENTATION_NOTES.md) - Implementation history

---

## Implementation Status

> **Last Updated**: 2026-01-31

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

4. **Add LSP Server Logging** (HIGH PRIORITY)
   - Add structured logging throughout LSP server core (common to all adapters)
   - Log parse times, query execution, symbol resolution
   - Enable debugging of tree-sitter behavior in the plugin
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

7. **End-to-End Testing** - Verify LSP features work in IntelliJ/VS Code
8. **IDE Integration** - See [PLAN_IDE_INTEGRATION.md](./PLAN_IDE_INTEGRATION.md)
9. **Compiler Adapter** - Add semantic features (future)

---

## Task: LSP Server Logging

**Status**: PENDING

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

## Dependencies

### External

| Dependency | Version | Purpose |
|------------|---------|---------|
| tree-sitter CLI | 0.22.6 | Grammar validation (auto-downloaded) |
| jtreesitter | 0.25.3 | JVM bindings |
| lsp4j | 0.21.0+ | LSP protocol implementation |

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
