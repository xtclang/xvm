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

> **Last Updated**: 2026-01-30

### Grammar (Phase 1) - COMPLETE

- [x] Grammar validates (`./gradlew :lang:tree-sitter:validateTreeSitterGrammar`)
- [x] **100% coverage** - All 692 XTC files from `lib_*` parse successfully
- [x] External scanner for template strings (`$"text {expr}"`, `$|multiline|`)
- [x] External scanner for TODO freeform text (`TODO message`)
- [x] Conflict optimization: 49 necessary conflicts, 0 warnings

### JVM Integration (Phase 2) - IN PROGRESS

- [x] `jtreesitter` dependency added to `lang/lsp-server/build.gradle.kts`
- [x] Parser wrappers implemented (`XtcParser`, `XtcTree`, `XtcNode`)
- [x] IntelliJ plugin supports adapter switching via `-Plsp.adapter=treesitter`
- [x] Native library build infrastructure (`tree-sitter/build.gradle.kts`)
- [x] Pre-built library for darwin-arm64 (built 2026-01-28)
- [ ] **`XtcParser.loadLanguageFromPath()` implemented** (currently throws `UnsupportedOperationException`)
- [ ] Pre-built libraries for other platforms (darwin-x64, linux-x64, linux-arm64, windows-x64)
- [ ] End-to-end test parsing `.x` file in JVM

#### Task: Complete Native Library Loading in XtcParser

**Status**: READY TO IMPLEMENT - native library exists, just need loader code

**What exists** (in `lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/`):
- `XtcParser.kt` - Parser wrapper, detects platform, extracts library from resources
- `XtcTree.kt` - Tree wrapper with node access and position lookup
- `XtcNode.kt` - Node wrapper with full traversal API
- `XtcQueryEngine.kt` - Query execution against parsed trees
- `XtcQueries.kt` - S-expression query patterns for declarations, references, etc.

**What's missing in `XtcParser.kt`**:

1. **`loadLanguageFromPath(path: Path)`** (line 145-153):
   ```kotlin
   // Currently throws UnsupportedOperationException
   // Needs to:
   // 1. Call System.load(path) to load the native library
   // 2. Use jtreesitter's Language.load() or similar to get the tree_sitter_xtc symbol
   // 3. Return the Language instance
   ```

2. **`loadLanguageFromSystemPath()`** (line 156-160):
   ```kotlin
   // Currently throws UnsupportedOperationException
   // Needs to:
   // 1. Use System.loadLibrary("tree-sitter-xtc")
   // 2. Get the language symbol
   ```

**jtreesitter API for loading languages**:

Looking at jtreesitter 0.25.x, the `Language` class can be loaded via:
```kotlin
// Option 1: From a native library path
val language = Language.load(libraryPath, "tree_sitter_xtc")

// Option 2: If library is already loaded
val language = Language.load("tree_sitter_xtc")
```

**Prerequisites**:
1. ~~Build native library~~ ✅ Already exists at `tree-sitter/src/main/resources/native/darwin-arm64/`
2. **Wire lsp-server to consume the native library** (missing!)
   - Option A: Add to `lsp-server/build.gradle.kts`:
     ```kotlin
     val nativeLib by configurations.creating {
         isCanBeConsumed = false
         isCanBeResolved = true
         attributes {
             attribute(Usage.USAGE_ATTRIBUTE, objects.named("native-library"))
         }
     }
     dependencies {
         nativeLib(project(path = ":tree-sitter", configuration = "nativeLibraryElements"))
     }
     // Copy to processResources or add to sourceSets.main.resources
     ```
   - Option B: Have tree-sitter copy library to lsp-server's resources
3. Implement the two loader methods in `XtcParser.kt`

**Testing**:
```bash
# Run IntelliJ with tree-sitter adapter
./gradlew :lang:intellij-plugin:runIde -Plsp.adapter=treesitter

# Check logs for:
# - "XTC LSP Server started (in-process) with adapter: TreeSitterAdapter"
# vs fallback:
# - "XTC LSP Server started (in-process) with adapter: MockXtcCompilerAdapter (fallback...)"
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
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│ MockXtc-      │    │ TreeSitter-   │    │ (Future)      │
│ Compiler-     │    │ Adapter       │    │ Compiler-     │
│ Adapter       │    │               │    │ Adapter       │
│               │    │               │    │               │
│ - Regex-based │    │ - Tree-sitter │    │ - Full XTC    │
│ - For testing │    │ - Syntax only │    │   compiler    │
└───────────────┘    └───────────────┘    └───────────────┘
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

**Status**: COMPLETE for darwin-arm64, PENDING for other platforms

The native library build infrastructure is fully implemented in `tree-sitter/build.gradle.kts`:

### Available Gradle Tasks

| Task | Description |
|------|-------------|
| `buildTreeSitterLibrary` | Build native library for current platform |
| `copyNativeLibraryToResources` | Copy to `src/main/resources/native/{platform}/` with hash |
| `ensureNativeLibraryUpToDate` | Check staleness, rebuild if needed |
| `checkNativeLibraryStaleness` | Report if pre-built library needs updating |

### Current Pre-built Libraries

| Platform | Status | Location |
|----------|--------|----------|
| darwin-arm64 | ✅ Built 2026-01-28 | `tree-sitter/src/main/resources/native/darwin-arm64/` |
| darwin-x64 | ❌ Not built | - |
| linux-x64 | ❌ Not built | - |
| linux-arm64 | ❌ Not built | - |
| windows-x64 | ❌ Not built | - |

### Building for Current Platform

```bash
# Build and copy to resources (auto-detects platform)
./gradlew :lang:tree-sitter:buildTreeSitterLibrary :lang:tree-sitter:copyNativeLibraryToResources

# Verify it's up-to-date
./gradlew :lang:tree-sitter:checkNativeLibraryStaleness
```

### Cross-Platform Building with Zig

**Zig** enables building ALL platform binaries from ANY host machine. No need for platform-specific
CI runners or Docker. See [Zig cc as Drop-In Replacement](https://andrewkelley.me/post/zig-cc-powerful-drop-in-replacement-gcc-clang.html).

#### Why Zig?

1. **Single binary** - Download ~45MB, works immediately
2. **All targets from any host** - Build macOS from Linux, Windows from macOS, etc.
3. **Bundles libc** - Ships libc implementations for all platforms
4. **No SDK/sysroot needed** - Even macOS targets work without Xcode

#### Target Matrix

| Target | Zig Target Triple | Output |
|--------|-------------------|--------|
| macOS arm64 | `aarch64-macos` | `libtree-sitter-xtc.dylib` |
| macOS x64 | `x86_64-macos` | `libtree-sitter-xtc.dylib` |
| Linux x64 | `x86_64-linux-gnu` | `libtree-sitter-xtc.so` |
| Linux arm64 | `aarch64-linux-gnu` | `libtree-sitter-xtc.so` |
| Windows x64 | `x86_64-windows-gnu` | `tree-sitter-xtc.dll` |

#### Implementation Plan

See **Task: Zig Cross-Compilation for All Platforms** below.

---

## Task: Zig Cross-Compilation for All Platforms

**Status**: NOT STARTED
**Priority**: MEDIUM - Enables local testing of all platforms
**References**:
- [Zig Saves the Day for Tree-sitter](https://www.deusinmachina.net/p/zig-saves-the-day-for-cross-platform)
- [Cross-compile C/C++ with Zig](https://zig.news/kristoff/cross-compile-a-c-c-project-with-zig-3599)

### Overview

Replace platform-specific `cc`/`clang`/`gcc` with `zig cc` to build all native libraries
from any single machine. A developer on macOS can build Linux and Windows binaries locally.

### Prior Art

- **[ensody/native-builds](https://github.com/ensody/native-builds)** - Gradle plugin using Zig, but requires manual `PATH` install
- **No Gradle plugin** exists for auto-downloading Zig
- **Our tree-sitter CLI pattern** - We already auto-download in `tree-sitter/build.gradle.kts`

### Implementation Steps

#### 1. Add Zig Download Task (Same Pattern as tree-sitter CLI)

```kotlin
// In tree-sitter/build.gradle.kts

val zigVersion = "0.13.0"
val zigDir: Provider<Directory> = layout.buildDirectory.dir("zig")

// Zig distributes as .tar.xz (Linux/macOS) or .zip (Windows)
val zigPlatform = when {
    osName.contains("mac") && osArch in listOf("aarch64", "arm64") -> "aarch64-macos"
    osName.contains("mac") -> "x86_64-macos"
    osName.contains("linux") && osArch in listOf("amd64", "x86_64") -> "x86_64-linux"
    osName.contains("linux") && osArch in listOf("aarch64", "arm64") -> "aarch64-linux"
    osName.contains("windows") -> "x86_64-windows"
    else -> "unsupported"
}

val zigArchiveExt = if (osName.contains("windows")) "zip" else "tar.xz"
val zigExeName = if (osName.contains("windows")) "zig.exe" else "zig"

val downloadZig by tasks.registering(Download::class) {
    group = "zig"
    description = "Download Zig compiler for cross-compilation"

    src("https://ziglang.org/download/$zigVersion/zig-$zigPlatform-$zigVersion.$zigArchiveExt")
    dest(zigDir.map { it.file("zig-$zigPlatform-$zigVersion.$zigArchiveExt") })
    overwrite(false)
    onlyIfModified(true)
}

val extractZig by tasks.registering(Copy::class) {
    group = "zig"
    description = "Extract Zig compiler"
    dependsOn(downloadZig)

    // For .tar.xz, use tarTree with xz decompression
    // For .zip, use zipTree
    from(
        if (zigArchiveExt == "zip") {
            zipTree(downloadZig.map { it.dest })
        } else {
            tarTree(resources.xz(downloadZig.map { it.dest }))
        }
    )
    into(zigDir)
}

val zigExe: Provider<String> = zigDir.map {
    it.dir("zig-$zigPlatform-$zigVersion").file(zigExeName).asFile.absolutePath
}
```

#### 2. Add Cross-Compile Task

```kotlin
abstract class ZigCrossCompileTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {

    @get:Input
    abstract val zigPath: Property<String>

    @get:Input
    abstract val targetTriple: Property<String>  // e.g., "aarch64-macos"

    @get:InputFile
    abstract val parserC: RegularFileProperty

    @get:InputFile
    abstract val scannerC: RegularFileProperty

    @get:OutputFile
    abstract val outputLib: RegularFileProperty

    @TaskAction
    fun compile() {
        val ext = when {
            targetTriple.get().contains("macos") -> "dylib"
            targetTriple.get().contains("windows") -> "dll"
            else -> "so"
        }

        execOps.exec {
            executable(zigPath.get())
            args(
                "cc",
                "-shared", "-fPIC",
                "-target", targetTriple.get(),
                "-I", parserC.get().asFile.parentFile.absolutePath,
                parserC.get().asFile.absolutePath,
                scannerC.get().asFile.absolutePath,
                "-o", outputLib.get().asFile.absolutePath
            )
        }
    }
}
```

#### 3. Register Tasks for All Targets

```kotlin
val crossCompileTargets = mapOf(
    "darwin-arm64" to "aarch64-macos",
    "darwin-x64" to "x86_64-macos",
    "linux-x64" to "x86_64-linux-gnu",
    "linux-arm64" to "aarch64-linux-gnu",
    "windows-x64" to "x86_64-windows-gnu"
)

crossCompileTargets.forEach { (platform, zigTarget) ->
    tasks.register<ZigCrossCompileTask>("buildNativeLibrary_$platform") {
        group = "tree-sitter"
        description = "Cross-compile native library for $platform using Zig"
        dependsOn(extractZig, validateTreeSitterGrammar)

        zigPath.set(/* path to zig executable */)
        targetTriple.set(zigTarget)
        parserC.set(generatedDir.map { it.file("src/parser.c") })
        scannerC.set(generatedDir.map { it.file("src/scanner.c") })
        outputLib.set(layout.buildDirectory.file("native/$platform/libtree-sitter-xtc.${ext}"))
    }
}

val buildAllNativeLibraries by tasks.registering {
    group = "tree-sitter"
    description = "Build native libraries for all platforms using Zig"
    dependsOn(crossCompileTargets.keys.map { "buildNativeLibrary_$it" })
}
```

#### 4. Usage

```bash
# Build all platforms from any machine
./gradlew :lang:tree-sitter:buildAllNativeLibraries

# Build specific platform
./gradlew :lang:tree-sitter:buildNativeLibrary_linux-x64

# Copy all to resources for committing
./gradlew :lang:tree-sitter:copyAllNativeLibrariesToResources
```

### Caveats

1. **macOS Code Signing** - Binaries work but aren't signed. Fine for development;
   CI/release builds on actual macOS may want native compile + signing.

2. **Zig Download Size** - ~45MB tarball, but only downloaded once and cached.

3. **Windows DLLs** - May need `mingw` target variant (`x86_64-windows-gnu`) for
   compatibility with JVM's native loading.

### Alternative: Docker for Linux Only

If Zig proves problematic, Docker can cross-compile Linux targets:

```bash
docker run --rm -v $(pwd):/work -w /work gcc:latest \
    cc -shared -fPIC -o libtree-sitter-xtc.so src/parser.c src/scanner.c
```

But this doesn't help with macOS/Windows targets from Linux.

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
- [ ] Native library compiled for all platforms
- [ ] Kotlin test can parse `.x` file and traverse AST

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

1. **Implement Language Loading** (BLOCKING - single remaining blocker!)
   - Complete `XtcParser.loadLanguageFromPath()` using jtreesitter API
   - The native library exists at `tree-sitter/src/main/resources/native/darwin-arm64/`
   - See "Task: Complete Native Library Loading in XtcParser" above
   - Estimated: ~50 lines of code using jtreesitter's `Language` API

2. **Enable TreeSitterAdapter**
   - Already done: IntelliJ plugin reads `-Plsp.adapter=treesitter`
   - Test: `./gradlew :lang:intellij-plugin:runIde -Plsp.adapter=treesitter`
   - Verify logs show "TreeSitterAdapter" (not fallback to Mock)

3. **Build Libraries for Other Platforms** (choose one approach)
   - **Option A: Zig cross-compile** (preferred for local dev)
     - Implement "Task: Zig Cross-Compilation" above
     - `./gradlew :lang:tree-sitter:buildAllNativeLibraries` from any machine
   - **Option B: Native compile on each platform**
     - Run `buildTreeSitterLibrary` + `copyNativeLibraryToResources` on each
   - **Option C: GitHub Actions matrix** (for CI/releases)
     - Build on `macos-latest`, `ubuntu-latest`, `windows-latest`

4. **End-to-End Testing** - Verify LSP features work in IntelliJ/VS Code
5. **IDE Integration** - See [PLAN_IDE_INTEGRATION.md](./PLAN_IDE_INTEGRATION.md)
6. **Compiler Adapter** - Add semantic features (future)

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
