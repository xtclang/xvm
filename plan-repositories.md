# Plan: XTC Repositories — Analysis of Java Reference & Kotlin Architecture Proposal

**Date**: 2026-02-20
**Status**: Research & Design
**Context**: The Java XVM compiler uses a `ModuleRepository` abstraction for all module
loading, storage, and dependency resolution. This document analyzes the existing repository
architecture, how it integrates with compilation and runtime, and proposes whether/how to
port it to the Kotlin compiler.

**Companion document**: [artifact-multimodule-repository-plan.md](artifact-multimodule-repository-plan.md) —
concrete design for multi-module artifacts (`.xar` archive format, bundled `.xtc` files,
`XarRepository`, `BundledFileRepository`, `ArtifactRepository`). That plan was developed
after this analysis and defines the specific formats/types that the Kotlin compiler must
consume.

---

## Part 1: ModuleRepository Interface & Implementations

### 1.1 The Interface

**File**: `javatools/src/main/java/org/xvm/asm/ModuleRepository.java`

```java
public interface ModuleRepository {
    Set<String> getModuleNames();
    ModuleStructure loadModule(String sModule);
    ModuleStructure loadModule(String sModule, Version version, boolean fExact);
    void storeModule(ModuleStructure module) throws IOException;
}
```

Key design choices:
- Returns `ModuleStructure` (the in-memory AST representation of a compiled `.xtc` module)
- Version-aware loading with exact/compatible match
- Writable (`storeModule`) — not all implementations support this
- No explicit close/dispose lifecycle

### 1.2 Implementation Hierarchy

```
ModuleRepository (interface)
    │
    ├── FileRepository          -- Single .xtc file
    ├── DirRepository           -- Directory of .xtc files
    ├── LinkedRepository        -- Chain of repositories (first-match-wins)
    ├── BuildRepository         -- In-memory compilation workspace
    ├── InstantRepository       -- Immutable single-module wrapper
    ├── ModuleInfoRepository    -- Build-tool bridge (backed by FileRepository)
    └── xCoreRepository         -- Runtime native bridge (XTC type implementation)
```

### 1.3 FileRepository

**File**: `javatools/src/main/java/org/xvm/asm/FileRepository.java`

- Wraps a single `.xtc` file
- Lazy loading: reads the file only on first `loadModule()` call
- Has `fReadOnly` flag — writable repos can `storeModule()` by writing to the file
- Implements change detection via file timestamp comparison (`isModified()`)
- Caches the loaded `FileStructure` in memory after first load

### 1.4 DirRepository

**File**: `javatools/src/main/java/org/xvm/asm/DirRepository.java`

- Wraps a directory of `.xtc` files
- Scans the directory for `.xtc` files to populate `getModuleNames()`
- Each module maps to a `FileRepository` internally
- Has `fReadOnly` flag
- Supports version management via `VersionTree<Boolean>` — can track multiple versions
  of the same module in the directory

### 1.5 LinkedRepository

**File**: `javatools/src/main/java/org/xvm/asm/LinkedRepository.java`

- Chains multiple repositories with **first-match-wins** semantics
- `getModuleNames()` returns the union of all repos' module names
- `loadModule()` iterates repos in order, returns first non-null result
- `storeModule()` always writes to `repos[0]` (the first repository)
- `readThrough` flag: if true, any module loaded from a later repo is immediately
  stored in `repos[0]` for caching
- This is the composition mechanism — the compiler always works with a `LinkedRepository`

### 1.6 BuildRepository

**File**: `javatools/src/main/java/org/xvm/compiler/BuildRepository.java`

- Pure in-memory repository using `Map<String, ModuleStructure>`
- No persistence — exists only during compilation
- Always writable — serves as the "workspace" during compilation
- `getModuleNames()` returns only module names where a `ModuleStructure` has been stored
- Tracks "module info" metadata (compilation targets, dependencies) separately from
  the actual module structures

### 1.7 InstantRepository

**File**: `javatools/src/main/java/org/xvm/compiler/InstantRepository.java`

- Wraps a single `ModuleStructure` already in memory
- Read-only — `storeModule()` throws
- Used when a module has been parsed and is immediately available (no file backing)
- The "anonymous struct" of repositories — just wraps an existing value

### 1.8 ModuleInfoRepository

**File**: `javatools/src/main/java/org/xvm/tool/ModuleInfoRepository.java`

- Build-tool bridge: maps `ModuleInfo` metadata to file-backed repositories
- Each `ModuleInfo` maps to a `FileRepository` pointing at the module's output `.xtc` file
- Used by `Compiler.emitModules()` for writing compiled output
- Serves as the bridge between the compiler's internal representation and the
  build system's file-based outputs

---

## Part 2: How The Compiler Uses Repositories

### 2.1 Repository Assembly

**File**: `javatools/src/main/java/org/xvm/tool/Launcher.java` (lines 685-716)

The compiler assembles a `LinkedRepository` chain during startup:

```
LinkedRepository(readThrough=true) {
  [0] BuildRepository          -- writable in-memory workspace
  [1] DirRepository(xdk/lib)   -- read-only XDK libraries
  [2] DirRepository(userPath1) -- read-only user library paths
  [3] FileRepository(dep.xtc)  -- read-only individual deps
  ...
}
```

The `readThrough=true` flag ensures that any module loaded from a disk repository is
immediately cloned and cached in the `BuildRepository` at index 0.

### 2.2 Compilation Flow

**Step 1: Repository assembly** (`Launcher.configureLibraryRepo()`)
- Creates `BuildRepository` at position 0
- Adds `DirRepository`/`FileRepository` for each path entry

**Step 2: System library pre-linking** (`Launcher.prelinkSystemLibraries()`)
- Force-loads `ecstasy.xtclang.org` and `mack.xtclang.org`
- Links their dependencies via `FileStructure.linkModules(repo, false)`
- Because `readThrough` is active, these get cached in the `BuildRepository`

**Step 3: Module skeleton creation** (`Compiler.resolveCompilers()`)
- For each module being compiled, the parser generates an initial `FileStructure`
- The module is stored into the repository via `repo.storeModule(struct.getModule())`
- Because writes go to `repos[0]` (BuildRepository), the skeleton is immediately
  available for cross-module reference resolution

**Step 4: Module linking** (`FileStructure.linkModules()`)
- Two-phase operation:
  1. `findMissing()`: walks all module dependencies, checks repository availability
  2. `linkModules()`: loads real modules from repository, replaces fingerprint modules

**Step 5: Output emission** (`Compiler.emitModules()`)
- Stores compiled modules to a `ModuleInfoRepository`
- Which delegates to `FileRepository` instances that write `.xtc` files to disk

### 2.3 The `extractBuildRepo` Pattern

```java
protected static BuildRepository extractBuildRepo(ModuleRepository repoLib) {
    if (repoLib instanceof BuildRepository repoBuild) {
        return repoBuild;
    }
    LinkedRepository repoLinked = (LinkedRepository) repoLib;
    return (BuildRepository) repoLinked.asList().getFirst();
}
```

Used throughout the compiler to get the writable `BuildRepository` back from the
`LinkedRepository` chain. The compiler needs direct access to check what modules
are being compiled vs. what are library dependencies.

### 2.4 Runtime Compilation (xRTCompiler)

When Ecstasy code invokes the compiler at runtime:
1. Gets the container's repository (a `LinkedRepository`)
2. Creates a new `BuildRepository`
3. Takes all read-only repositories from the container's chain
4. Assembles a new `LinkedRepository` with the fresh BuildRepository at front
5. Runs the compiler with this new chain

This demonstrates that repository chains are designed to be composed and recomposed dynamically.

---

## Part 3: XTC Versioning Architecture

### 3.1 Version Representation

**File**: `javatools/src/main/java/org/xvm/asm/Version.java`

XTC uses a hierarchical version numbering system compatible with Semantic Versioning 2.0.0:

```
Version := VersionPart ('.' VersionPart)*
VersionPart := NonNegativeInteger | PreReleaseLabel
PreReleaseLabel := "CI" | "Dev" | "QC" | "alpha" | "beta" | "rc"
```

Internally, versions are stored as `int[]` where:
- Non-negative integers represent numeric parts (major, minor, patch, etc.)
- Negative integers represent pre-release labels (CI=-6, Dev=-5, QC=-4, alpha=-3, beta=-2, rc=-1)

Example: `"2.1.0-beta3"` → `[2, 1, 0, -2, 3]`

Optional build metadata (`+suffix`) is stored separately and ignored for comparison,
matching SemVer rules.

### 3.2 Version Comparison and Substitutability

`Version.isSubstitutableFor(Version that)`: A version X is substitutable for Y when X
is the same version or a derived (newer/patched) version. The rules:

1. X must be at least as long as Y (can have additional version parts)
2. X and Y must agree on all of Y's parts, except X may be GA where Y is pre-release
3. X can extend Y's version (e.g., `2.1.0.1` substitutes for `2.1.0`)

This creates a tree structure where `2.1.0` is substitutable for `2.1` which is
substitutable for `2`.

### 3.3 VersionTree

**File**: `javatools/src/main/java/org/xvm/asm/VersionTree.java`

A trie-like data structure mapping `Version → V` using the version parts as path segments.
Operations: `get(version)`, `put(version, value)`, `iterateKeys()`, `findHighestVersion()`.

Used in two contexts:
1. `DirRepository`: `VersionTree<Boolean>` tracks which versions of a module exist in a directory
2. `ModuleStructure` fingerprints: `VersionTree<Boolean>` for allowed/avoided import versions

### 3.4 Versioning at Multiple Levels

**Level 1 — Version as a constant**: `VersionConstant` extends `LiteralConstant`, stored
in the constant pool. Represents a version value like `"2.1.0"`.

**Level 2 — Module identity version**: `ModuleConstant` has an optional `VersionConstant`.
A versioned module constant represents a specific version of a module (`ecstasy.xtclang.org@2.1`).

**Level 3 — Module structure version**: `ModuleStructure` stores a `VersionConstant` for
the module's own version, set via `setVersion()`, read via `getVersion()`.

**Level 4 — Fingerprint version metadata**: When a module imports another, the fingerprint
carries `VersionTree<Boolean>` of allowed/avoided versions and a `List<Version>` of
preferred versions. These are serialized into `.xtc` binary files.

**Level 5 — Conditional version labels on components**: Any `Component` can have a
`ConditionalConstant` condition. When this is a `VersionedCondition`, the component
exists only for specific module versions.

### 3.5 Multi-Version Modules

A single `.xtc` file can contain multiple versions of a module:

1. Components are labeled with `VersionedCondition` conditions
2. `ModuleStructure.getVersions()` collects all version labels → `VersionTree<Boolean>`
3. `ModuleStructure.extractVersion(version)` clones the module and calls
   `purgeVersionsExcept(version)` to strip non-matching components
4. Creates a versioned `ModuleConstant` and re-registers constants

```
loadModule(name, version, exact)
  → module = loadModule(name)                  // load full multi-version module
  → iterate module.getVersions()               // find best matching version
  → check isSubstitutableFor(requestedVersion) // compatibility test
  → module.extractVersion(bestVersion)          // clone + purge non-matching
  → return single-version ModuleStructure
```

### 3.6 Conditional Compilation with Versions

Three terminal condition types:
- **VersionedCondition**: Tests if this module's version matches a specified version
- **PresentCondition**: Tests if a VM structure is present (optional dependencies)
- **NamedCondition**: Tests if a named value is defined (like `#ifdef`)

Composed with `AllCondition` (AND), `AnyCondition` (OR), `NotCondition` (NOT).

The compiler validates ALL version paths at compile time — unlike C's `#ifdef` which
compiles only one path.

### 3.7 Implications for Gradle/Maven Artifact Publishing

- XTC's `isSubstitutableFor()` provides its own compatibility model that differs from
  Maven/Gradle's version constraint resolution
- A single `.xtc` artifact can contain multiple API versions via conditional components
- Fingerprint metadata (allowed/avoided/preferred versions) is a dependency constraint
  mechanism with no direct Gradle equivalent

**Recommended approach**: Publish each version as a separate Gradle artifact (standard
practice). Use Gradle's dependency resolution for artifact-level version selection. Let
XTC's internal resolution handle fine-grained compatibility after artifact selection.

---

## Part 4: How Repositories Are Used at Runtime

### 3.1 Connector -> NativeContainer -> Repository

The `Connector` is the entry point from Java to the XVM runtime:

```java
public Connector(ModuleRepository repository) {
    f_repository      = repository;
    f_runtime         = new Runtime();
    f_containerNative = new NativeContainer(f_runtime, repository);
}
```

### 3.2 NativeContainer Initialization

The `NativeContainer` constructor:
1. Stores the repository as `f_repository`
2. Loads `ecstasy.xtclang.org`, `mack.xtclang.org`, `_native.xtclang.org` from repository
3. Creates a merged `FileStructure` combining all three
4. Calls `fileRoot.linkModules(f_repository, true)` to link runtime dependencies
5. Initializes all native Java class templates

### 3.3 Module Loading at Runtime

```java
public void loadModule(String sAppName) {
    ModuleStructure moduleApp = f_repository.loadModule(sAppName);
    FileStructure structApp = f_containerNative.createFileStructure(moduleApp);
    ModuleConstant idMissing = structApp.linkModules(f_repository, true);
    m_containerMain = new MainContainer(f_runtime, f_containerNative, structApp.getModuleId());
}
```

The runtime also uses the repository to load additional system modules (crypto, net, web)
that are merged into every application's file structure.

### 3.4 Container Repository Access

All containers delegate to the `NativeContainer`'s repository. The repository is accessible
throughout the runtime for dynamic module loading, injection, and the `mgmt.ModuleRepository`
XTC type.

---

## Part 5: Gradle Build Integration

### 4.1 Current Plugin Architecture

The Gradle plugin does **not** interact with `ModuleRepository` directly. Instead:

1. **`ModulePathResolver`** resolves a list of `File` objects from:
   - XDK contents directory
   - Custom module path (user-configured)
   - `xtcModule` dependency configurations
   - Source set output directories

2. **`LauncherOptionsBuilder`** converts resolved paths into `-L` arguments

3. **`DirectStrategy`** invokes `Launcher.launch(options, console, err)`
   - The `Launcher` creates repositories internally via `configureLibraryRepo()`

### 4.2 Current Flow

```
Gradle Task
  -> ModulePathResolver.resolveFullModulePath()     // returns List<File>
  -> LauncherOptionsBuilder.buildCompilerOptions()   // converts to CompilerOptions
  -> Launcher.launch(options, console, err)           // creates repos internally
  -> Launcher.configureLibraryRepo(opts.getModulePath()) // File -> Repository
```

The plugin treats module paths as `List<File>`. The conversion from files to repositories
happens inside `Launcher`.

### 4.3 Artifact Format

The build system produces `.xtc` binary files — one per module. These are stored in build
output directories and can be consumed as dependencies by other projects. The `.xtc` format
is read by `FileStructure(File)` and written by `FileStructure.writeTo(File)`.

---

## Part 6: Analysis for Kotlin Compiler

### 6.1 Current Kotlin Compiler State

The Kotlin LSP compiler has its own module path abstraction:

**`ModulePath`** (`lang/lsp-compiler/compiler/src/main/kotlin/org/xtclang/compiler/module/ModulePath.kt`):
- Discovers modules from source directories and composite roots
- Provides topological ordering (dependency-first)
- Uses sealed interfaces (`ModulePathEntry`, `DiscoveredModule`)
- Has `XtcFile` and `XtcDirectory` entry types stubbed out (logged as "not yet supported")
- Matches the Java `LinkedRepository`'s first-match-wins semantics
- Fully immutable and thread-safe

**`SourceModuleLoader`** (`lang/lsp-compiler/compiler/src/main/kotlin/org/xtclang/compiler/module/SourceModuleLoader.kt`):
- Loads modules from source only — no `.xtc` binary support
- Uses `ModulePath` for discovery and ordering

### 6.2 Feature Comparison: ModulePath vs ModuleRepository

| Feature | Kotlin `ModulePath` | Java `ModuleRepository` |
|---------|---------------------|------------------------|
| Module discovery | Scans source dirs | Scans .xtc files |
| Source loading | Yes (primary purpose) | No |
| Binary loading | No (stubbed) | Yes (primary purpose) |
| In-memory modules | No | Yes (BuildRepository) |
| Module storage | No | Yes (storeModule) |
| Version management | No | Yes (VersionTree) |
| Repository chaining | First-match-wins in discovery | LinkedRepository chain |
| Thread safety | Immutable, thread-safe | Not thread-safe |
| Dependency ordering | Topological sort | FileStructure.linkModules |
| Read-through caching | N/A | Yes (LinkedRepository) |

### 6.3 What Value Would Repository Add Now vs Later?

**Value NOW:**
- **Binary .xtc loading.** The Kotlin compiler cannot currently consume pre-compiled `.xtc`
  modules as dependencies. Adding `FileRepository`/`DirRepository` equivalents would enable this.
- **Incremental compilation.** An in-memory repository (like `BuildRepository`) could cache
  compiled module symbol tables between LSP operations.
- **Build integration.** A Kotlin-native repository would allow the Kotlin compiler to
  participate directly in the build pipeline.

**Value LATER (but now concretely designed — see [artifact-multimodule-repository-plan.md](artifact-multimodule-repository-plan.md)):**
- **`.xar` archive consumption.** The artifact plan defines a ZIP-based `.xar` format for
  distributing multi-module projects (like Platform). The Kotlin compiler should be able to
  consume `.xar` files as dependency sources, either via JVM interop with the Java
  `XarRepository`, or via a Kotlin-native reader. The format is simple (ZIP + JSON manifest)
  so a Kotlin reader is straightforward.
- **Bundled `.xtc` consumption.** The artifact plan also defines `BundledFileRepository` for
  merged `.xtc` files containing multiple modules. The Kotlin compiler's `.xtc` reader
  (Phase 2 below) should handle merged files from day one — it's the same `FileStructure`
  binary format, just with `moduleIds()` returning multiple entries.
- **LSP workspace integration.** Managing modules from multiple sources (workspace, dependencies,
  XDK) with a unified view via repository chaining.
- **Module versioning.** Version-aware compilation using repository version substitution.

### 6.4 Could Repository Replace/Subsume ModulePath?

**Partially, but they should coexist.**

`ModulePath` excels at source-based module discovery and ordering — something `ModuleRepository`
does not do at all. The Java compiler's source discovery is handled by `ModuleInfo` and
`Launcher.selectTargets()`, which is a separate concern.

The right relationship is:
- `ModulePath` discovers and orders modules (source and binary)
- `ModuleRepository` provides the loading/storage abstraction
- `SourceModuleLoader` or `BinaryModuleLoader` bridges between them

`ModulePath` already has `XtcFile` and `XtcDirectory` entry types stubbed out. These could
be backed by repository equivalents.

### 6.5 How Would Repository Fit with SymbolTable/MemberIndex?

Current architecture:
```
ModulePath -> SourceModuleLoader -> SymbolTable + symbolTypes
```

With repository layer:
```
ModulePath -> [SourceModuleLoader | BinaryModuleLoader] -> SymbolTable + symbolTypes
                                                             ^
                                                    ModuleRepository
                                                    (load/store compiled symbols)
```

A Kotlin `ModuleRepository` would not return `ModuleStructure` (Java compiler AST type).
Instead, it would return a Kotlin-native representation — either:
- **Option A:** Serialized `SymbolTable` fragments that can be merged
- **Option B:** A lightweight module descriptor (name, version, exported symbols, types)
- **Option C:** The raw `.xtc` binary bytes, with a reader that extracts type information

Option C is the most practical for interop with the Java compiler's `.xtc` format.

### 6.6 Relationship to the Artifact Plan

The [artifact-multimodule-repository-plan.md](artifact-multimodule-repository-plan.md) defines
concrete formats and Java repository types that the Kotlin compiler must interoperate with:

**Java-side types the Kotlin compiler will encounter:**

| Java Type | What It Is | Kotlin Compiler Interaction |
|-----------|-----------|---------------------------|
| `FileRepository` | Single `.xtc` file, primary module only | Kotlin reader produces the same format |
| `DirRepository` | Directory of `.xtc` files | Kotlin XtcDirectory entry type in ModulePath |
| `BundledFileRepository` | Single `.xtc` file, ALL merged modules | Kotlin reader must handle `moduleIds()` > 1 |
| `XarRepository` | `.xar` ZIP archive + JSON manifest | Kotlin needs a reader (simple: ZIP + JSON) |
| `ArtifactRepository` | Decorator adding group:name:version | Kotlin equivalent wraps any Kotlin repo |
| `LinkedRepository` | Chain of repos | Kotlin `ChainedRepository` equivalent |

**Consumption strategy for the Kotlin compiler:**

1. **For individual `.xtc` files and directories**: The Kotlin `.xtc` reader (Phase 2)
   handles these directly. This is the same `FileStructure` binary format.

2. **For `.xar` archives**: Two options:
   - **JVM interop**: Call Java's `XarRepository` from Kotlin code. Works immediately
     but couples to the Java implementation.
   - **Kotlin-native reader**: Trivial to implement — read ZIP entries, parse JSON manifest,
     pass `.xtc` bytes to the Kotlin `.xtc` reader. Preferred for the stateless/functional
     architecture.

3. **For bundled `.xtc` files**: The Kotlin `.xtc` reader handles these automatically IF
   it reads `moduleIds()` (all modules in the file) rather than just `getModule()` (primary
   only). This should be built in from the start.

---

## Part 7: Proposed Architecture

### 7.1 Recommendation: Port a Simplified Repository with Version Awareness

Port the **interface and composition pattern** but not the Java implementations. The Kotlin
compiler needs:

1. A `ModuleRepository` interface (adapted for Kotlin types)
2. `MemoryRepository` equivalent of `BuildRepository` (in-memory, for active compilation)
3. A way to read `.xtc` binary files (for consuming dependencies — both single-module and
   bundled multi-module `.xtc` files)
4. `ChainedRepository` equivalent of `LinkedRepository` (repository chaining)
5. An `XtcVersion` value class with substitutability logic (needed from day one)
6. A `.xar` reader for consuming multi-module archive artifacts (simple: ZIP + JSON)

The interface MUST include version-aware loading from the start because:
- The `loadModule(name, version, exact)` overload is the primary way modules are loaded
  during linking (see `FileStructure.linkModules()`)
- XDK libraries ship with version labels, so even reading `.xtc` files requires version awareness
- Adding version support later would require breaking the interface

It does NOT need (yet):
- `DirRepository` equivalent with filesystem scanning and change detection (not needed
  for an LSP compiler that receives file change events from the editor)
- `VersionTree` (a simple map suffices initially)
- Multi-version module support (not producing multi-version `.xtc` files)
- `ModuleInfoRepository` (build tool bridge)
- Write/store support (not producing `.xtc` files yet)
- `ArtifactRepository` decorator (Gradle handles artifact coordinates before the compiler
  sees anything — see [artifact-multimodule-repository-plan.md](artifact-multimodule-repository-plan.md)
  Part 7 for the two-level versioning model)

### 7.2 Proposed Interface

```kotlin
/** Kotlin equivalent of XTC's Version class. */
@JvmInline
value class XtcVersion(val parts: IntArray) : Comparable<XtcVersion> {
    val isGA: Boolean get() = parts.none { it < 0 }
    fun isSubstitutableFor(that: XtcVersion): Boolean { /* ... */ }
    fun isSameAs(that: XtcVersion): Boolean { /* ... */ }
    override fun compareTo(other: XtcVersion): Int { /* ... */ }
    companion object {
        fun parse(literal: String): XtcVersion { /* ... */ }
    }
}

interface ModuleRepository {
    val moduleNames: Set<String>
    fun loadModule(name: String): ModuleDescriptor?
    fun loadModule(name: String, version: XtcVersion, exact: Boolean): ModuleDescriptor? {
        val module = loadModule(name) ?: return null
        val moduleVersion = module.version ?: return module
        return if (exact) {
            if (moduleVersion.isSameAs(version)) module else null
        } else {
            if (moduleVersion.isSubstitutableFor(version)) module else null
        }
    }
    fun storeModule(descriptor: ModuleDescriptor): Unit =
        throw UnsupportedOperationException("Read-only repository")
}

data class ModuleDescriptor(
    val name: String,
    val version: XtcVersion?,
    val dependencies: Set<ModuleDependency>,
    val symbols: List<ExportedSymbol>,
)

data class ModuleDependency(
    val name: String,
    val version: XtcVersion?,
    val optional: Boolean = false,
)

data class ExportedSymbol(
    val name: String,
    val kind: SymbolKind,
    val type: Type?,
    val children: List<ExportedSymbol>,
)
```

### 7.3 Proposed Implementations

```kotlin
class MemoryRepository : ModuleRepository {
    private val modules = mutableMapOf<String, ModuleDescriptor>()
    override val moduleNames: Set<String> get() = modules.keys
    override fun loadModule(name: String) = modules[name]
    override fun storeModule(descriptor: ModuleDescriptor) {
        modules[descriptor.name] = descriptor
    }
}

class ChainedRepository(
    private val repos: List<ModuleRepository>,
) : ModuleRepository {
    override val moduleNames: Set<String>
        get() = repos.flatMapTo(mutableSetOf()) { it.moduleNames }

    override fun loadModule(name: String): ModuleDescriptor? =
        repos.firstNotNullOfOrNull { it.loadModule(name) }

    override fun storeModule(descriptor: ModuleDescriptor) =
        repos.first().storeModule(descriptor)
}
```

### 7.4 Integration with Existing Code

The existing `ModulePath` would not be replaced:

```
ModulePath (discovery + ordering)
    |
    v
SourceModuleLoader.loadAll(modulePath, symbolTable)
    |
    v
SymbolTable (populated with all modules)
    |
    v
SourceRepository(modulePath, symbolTable)  -- wraps as ModuleRepository
    |
    v
ChainedRepository([MemoryRepository, SourceRepository, ...])
```

For `.xtc` binary and `.xar` archive support:
```
ChainedRepository([
    MemoryRepository,         // in-memory compilation workspace (LSP session)
    SourceRepository,         // source-based modules (workspace)
    XtcFileRepository,        // single or bundled .xtc binary dependencies
    XarRepository,            // .xar multi-module archives (e.g., platform-0.1.0.xar)
    XtcDirRepository,         // directory of .xtc files (e.g., XDK lib/)
])
```

**Key design note**: The Kotlin `XtcFileRepository` should handle both single-module and
bundled (merged) `.xtc` files from the start. A bundled file contains multiple real modules
in one `FileStructure` — the reader checks `moduleIds()` and exposes all non-fingerprint
modules. Unlike the Java side, which has separate `FileRepository` (primary-only) and
`BundledFileRepository` (all modules) for backward compatibility, the Kotlin compiler can
use a single unified type since there is no legacy API to preserve.

The `XarRepository` reads `.xar` ZIP archives: parse `META-INF/xar.json`, then read
individual `.xtc` entries from the ZIP and pass them to the `.xtc` reader. See
[artifact-multimodule-repository-plan.md](artifact-multimodule-repository-plan.md) Part 4
for the `.xar` format specification.

---

## Part 8: Implementation Roadmap

### Phase 0: Version + Interface Definition (~2 days)
- Implement `XtcVersion` value class with parsing, comparison, and `isSubstitutableFor()`
- Define `ModuleRepository` interface with version-aware `loadModule()` overload
- Define `ModuleDescriptor` and `ModuleDependency` data classes with version fields
- Create `MemoryRepository` and `ChainedRepository` implementations
- Unit tests for version parsing, comparison, and substitutability
- Pure addition, no existing code changes

### Phase 1: Wrap Existing Code (~1 day)
- Create `SourceRepository` wrapping `SourceModuleLoader.loadAll()` results
- Ensure `ModuleDescriptor` can be populated from `SymbolTable` data
- Add tests verifying round-trip

### Phase 2: .xtc Binary Reader (~3-5 days)
- Implement `.xtc` format reader (or wrap `FileStructure` via JVM interop)
- Extract module name, dependencies, public type signatures
- **Must handle bundled (multi-module) `.xtc` files** — check `moduleIds()` for all
  non-fingerprint modules, not just the primary. This is the same binary format; the
  only difference is `moduleIds().size > 1`.
- Create `XtcFileRepository` implementation (unified: handles both single and bundled)
- Enable `XtcFile`/`XtcDirectory` entry types in `ModulePath`
- **This is the key enabler** for consuming pre-built XDK libraries

### Phase 3: .xar Archive Reader (~1-2 days)
- Implement `.xar` reader: ZIP + JSON manifest parsing
- This is a thin layer on top of Phase 2's `.xtc` reader — each ZIP entry is a `.xtc` file
- Create `XarRepository` implementation
- See [artifact-multimodule-repository-plan.md](artifact-multimodule-repository-plan.md) for
  the `.xar` format spec and manifest schema
- **This enables consuming multi-module projects** (Platform, XDK libraries) as single
  versioned artifacts from Maven/Gradle

### Phase 4: Build System Integration (~2-3 days)
- Expose `ModuleRepository` creation from compiler public API
- Allow Gradle plugin to pass module paths as repository entries
- Support `.xar` artifacts in the `xtcModule` Gradle configuration
- Gradle resolves artifact version; Kotlin compiler handles module-level version
  compatibility (two-level versioning model — see artifact plan Part 7)

### Phase 5: LSP Session Repository (~2-3 days)
- LSP-aware repository tracking open files and workspace modules
- Incremental invalidation on file changes (the LSP receives `didChangeWatchedFiles`
  notifications — no need for filesystem polling like Java's `DirRepository`)
- `ChainedRepository` composing workspace + library dependencies + XDK

### Phase 6: Full Version Management (Future)
- Port `VersionTree` for efficient multi-version storage and lookup
- Multi-version extraction from `.xtc` files (`extractVersion`/`purgeVersionsExcept`)
- Version-conditional component support in the symbol table
- Bridge Gradle dependency constraints to XTC fingerprint version preferences

---

## Appendix A: Repository Class Hierarchy (Java Reference Compiler)

```
ModuleRepository (interface)
    |
    |   Existing:
    +-- FileRepository          -- Single .xtc file, PRIMARY module only
    +-- DirRepository           -- Directory of .xtc files (filesystem scanning)
    +-- LinkedRepository        -- Chain of repositories (first-match-wins)
    +-- BuildRepository         -- In-memory compilation workspace
    +-- InstantRepository       -- Immutable single-module wrapper
    +-- ModuleInfoRepository    -- Build-tool bridge (backed by FileRepository)
    +-- xCoreRepository         -- Runtime native bridge (XTC type implementation)
    |
    |   New (see artifact-multimodule-repository-plan.md):
    +-- BundledFileRepository   -- Single .xtc file, ALL merged modules
    +-- XarRepository           -- .xar archive (ZIP of .xtc files + JSON manifest)
    +-- ArtifactRepository      -- Decorator: group:name:version identity
```

## Appendix B: Versioning Class Hierarchy (Java Reference Compiler)

```
Version                                -- Parsed version with int[] parts + build string
VersionTree<V>                         -- Hierarchical trie mapping Version → V

Constant
    +-- LiteralConstant
    |   +-- VersionConstant            -- Version as a constant pool entry
    +-- ConditionalConstant            -- Conditional compilation base
        +-- VersionedCondition         -- "is this module version X?"
        +-- PresentCondition           -- "is structure X present?"
        +-- NamedCondition             -- "is name X defined?"
        +-- AllCondition / AnyCondition / NotCondition  -- Boolean combinators
```

## Appendix C: File Reference Index

| File | Role |
|------|------|
| `javatools/src/main/java/org/xvm/asm/ModuleRepository.java` | Interface definition |
| `javatools/src/main/java/org/xvm/asm/FileRepository.java` | Single .xtc file repository |
| `javatools/src/main/java/org/xvm/asm/DirRepository.java` | Directory repository |
| `javatools/src/main/java/org/xvm/asm/LinkedRepository.java` | Chain/composite repository |
| `javatools/src/main/java/org/xvm/compiler/BuildRepository.java` | In-memory build workspace |
| `javatools/src/main/java/org/xvm/compiler/InstantRepository.java` | Read-only single module |
| `javatools/src/main/java/org/xvm/tool/ModuleInfoRepository.java` | Build tool bridge |
| `javatools/src/main/java/org/xvm/tool/Compiler.java` | Compiler orchestration |
| `javatools/src/main/java/org/xvm/tool/Launcher.java` | Repository assembly + system library loading |
| `javatools/src/main/java/org/xvm/tool/Runner.java` | Runtime module loading |
| `javatools/src/main/java/org/xvm/api/Connector.java` | Java-to-XVM runtime bridge |
| `javatools/src/main/java/org/xvm/runtime/NativeContainer.java` | Runtime container initialization |
| `javatools/src/main/java/org/xvm/runtime/Container.java` | Base container |
| `javatools/src/main/java/org/xvm/asm/FileStructure.java` | Module linking via repository |
| `javatools/src/main/java/org/xvm/asm/Version.java` | Version parsing and comparison |
| `javatools/src/main/java/org/xvm/asm/VersionTree.java` | Hierarchical version trie |
| `javatools/src/main/java/org/xvm/asm/ModuleStructure.java` | Module version management |
| `javatools/src/main/java/org/xvm/asm/constants/VersionConstant.java` | Version as constant pool entry |
| `javatools/src/main/java/org/xvm/asm/constants/VersionedCondition.java` | Version-based conditional |
| `javatools/src/main/java/org/xvm/asm/constants/ConditionalConstant.java` | Conditional compilation base |
| `javatools/src/main/java/org/xvm/runtime/template/_native/mgmt/xCoreRepository.java` | Native ModuleRepository for XTC |
| `javatools/src/main/java/org/xvm/runtime/template/_native/lang/src/xRTCompiler.java` | Runtime compiler (repo recomposition) |
| `lang/lsp-compiler/compiler/src/main/kotlin/org/xtclang/compiler/module/ModulePath.kt` | Kotlin module discovery |
| `lang/lsp-compiler/compiler/src/main/kotlin/org/xtclang/compiler/module/SourceModuleLoader.kt` | Kotlin source-based module loading |
| `plugin/src/main/java/org/xtclang/plugin/launchers/ModulePathResolver.java` | Gradle module path resolution |
| `plugin/src/main/java/org/xtclang/plugin/launchers/LauncherOptionsBuilder.java` | Gradle options building |
| `plugin/src/main/java/org/xtclang/plugin/launchers/DirectStrategy.java` | Gradle direct execution strategy |
