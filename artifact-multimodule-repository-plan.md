# Plan: Multi-Module Artifacts — XTC Archive & Bundled Binary

**Date**: 2026-02-20
**Status**: Design Proposal
**Context**: The XTC ecosystem needs a way to distribute multi-module projects (like the
Platform or XDK) as single, versioned binary artifacts — analogous to Maven/Gradle JARs or
npm packages. This document proposes two complementary approaches:

1. **`.xar` (XTC Archive)** — a ZIP-based format for distributable, inspectable artifacts
2. **Bundled `.xtc`** — a single merged binary blob for optimized deployment

Both are backed by new `ModuleRepository` implementations (`XarRepository` and
`BundledFileRepository`), plus an `ArtifactRepository` decorator for Maven/Gradle
semantic versioning.

**Prerequisite**: [plan-repositories.md](plan-repositories.md) — analysis of the existing
Java `ModuleRepository` architecture.

---

## Part 1: Problem Statement

### 1.1 What Exists Today

**Platform project** (`../platform/`):
- 11 Ecstasy modules compiled into individual `.xtc` files
- Gradle `installDist` copies all `.xtc` files into `build/install/platform/lib/`
- Runtime invocation: `xec -L build/install/platform/lib kernel.xtc`
- No single distributable artifact — the "distribution" is a loose directory of files
- Version `0.1.0` set in `gradle.properties`, but `.xtc` files carry no artifact version

**XDK distribution** (`xdk/`):
- 19+ `.xtc` library modules + Java tooling JARs
- Gradle `distribution` plugin creates `xdk-0.4.4-SNAPSHOT.zip`
- Internal structure: `lib/*.xtc`, `javatools/*.jar`, `bin/*`
- Consumed by downstream projects via `xdkDistribution` configuration
- Not a repository — it's an opaque archive that must be unzipped

**The gap**: There is no standard, self-describing binary format for "a set of `.xtc`
modules that belong together as a versioned artifact." The runtime has `DirRepository`
(loads from a directory of `.xtc` files) but nothing for a single distributable file
containing multiple modules with metadata.

### 1.2 Why This Matters

1. **Distribution**: Projects like Platform need to ship as a single versioned artifact,
   not a directory of loose files.

2. **Dependency resolution**: Gradle/Maven can resolve a single artifact with coordinates
   like `platform.xqiz.it:platform:0.1.0`. There's no format for what that artifact contains.

3. **Runtime loading**: The XVM runtime should be able to load a multi-module artifact
   directly, without requiring extraction to a temporary directory.

4. **Integrity**: A single file can be checksummed, signed, and verified. A directory of
   files requires a manifest.

5. **Ecosystem consistency**: Just as Java has `.jar` (multi-class archive), XTC needs
   `.xar` (multi-module archive).

### 1.3 Design Goals

- **Simple**: Easy to understand, implement, and debug
- **Self-describing**: Contains all metadata needed to use it (module list, versions,
  dependencies, entry points)
- **Compatible**: Works with existing `ModuleRepository` interface — a `.xar` file is
  just another repository
- **Tooling-friendly**: Standard format (ZIP-based) that existing tools can inspect
- **Version-aware**: Artifact-level semantic version distinct from per-module XTC versions
- **Composable**: Can be used in `LinkedRepository` chains alongside existing repos
- **Non-invasive**: No changes to existing repository implementations

---

## Part 2: Existing Repository Architecture

Before proposing new types, we need to understand what already exists, what each
implementation actually does, and why none of them need to change.

### 2.1 The `ModuleRepository` Interface

**File**: `javatools/src/main/java/org/xvm/asm/ModuleRepository.java`

```java
public interface ModuleRepository {
    Set<String> getModuleNames();
    ModuleStructure loadModule(String sModule);
    ModuleStructure loadModule(String sModule, Version version, boolean fExact);  // default
    VersionTree<Boolean> getAvailableVersions(String sModule);                    // default
    void storeModule(ModuleStructure module) throws IOException;
}
```

**Key observation**: The interface already supports multiple modules — `getModuleNames()`
returns `Set<String>`. There is no assumption of single-module-ness anywhere in the
interface. Every existing implementation except `FileRepository` and `InstantRepository`
can hold multiple modules. "Multi-module" is already the norm, not the exception.

The version-aware `loadModule(name, version, exact)` default implementation handles
XTC's substitutability logic: it loads the module, iterates its version tree, and calls
`module.extractVersion(bestMatch)` to produce a version-specific clone.

### 2.2 FileRepository — Single `.xtc` File

**File**: `javatools/src/main/java/org/xvm/asm/FileRepository.java`

A wrapper around a single `.xtc` file on disk.

- **Construction**: Takes a `File` (asserts `!file.isDirectory()`). Forces `.xtc` extension
  — if you pass `foo.x` it rewrites to `foo.xtc`.
- **Loading**: Calls `new FileStructure(file)` which reads the binary `.xtc` format
  (magic header `0xEC57A5EE`, constant pool, module data). Returns the single
  `ModuleStructure` via `struct.getModule()`.
- **Caching**: Caches the loaded module. Tracks file timestamp + size for change detection.
  Re-checks at most once per second (`isCacheValid()`). If the file changed on disk,
  invalidates the cache and reloads.
- **Storage**: When writable, `storeModule()` writes via `module.getFileStructure().writeTo(file)`.
- **Cardinality**: Always exactly one module. `getModuleNames()` returns a singleton set.

**Why it can't handle `.xar`**: It hardcodes `.xtc` extension. It calls `new FileStructure(file)`
which expects the `0xEC57A5EE` magic header at byte 0 — a `.xar` file is a ZIP and would
fail immediately with "not an .xtc format file; invalid magic header." The entire class
assumes one module per file.

### 2.3 DirRepository — Directory of `.xtc` Files

**File**: `javatools/src/main/java/org/xvm/asm/DirRepository.java`

A wrapper around a directory on the local filesystem. It is purely a live filesystem
mapping — it has no binary format, no manifest, no archive concept.

- **Construction**: Takes a `File dir` (asserts `dir.isDirectory()`), plus `fReadOnly` flag.
- **Discovery** (`ensureCache()`): Calls `dir.listFiles(ModulesOnly)` — a literal filesystem
  scan filtering for files ending in `.xtc` that are readable and non-empty. Each discovered
  file is partially loaded via `new FileStructure(file)` to extract the module name and
  `VersionTree<Boolean>` into a `ModuleInfo` cache entry.
- **Loading** (`loadModule()`): Looks up the module name in its `modulesByName` map, then
  calls `ensureModule()` which does `new FileStructure(file).getModule()` to deserialize
  the `.xtc` binary into a full `ModuleStructure`.
- **Storage** (`storeModule()`): Writes via `module.getFileStructure().writeTo(file)`,
  creating or overwriting a `.xtc` file in the directory. Only works if not read-only.
- **Change detection** (`isCacheValid()`): Compares file timestamps and sizes against
  cached values. Re-scans at most once per second. If a file changes on disk, the cached
  `ModuleStructure` is invalidated and reloaded on next access.
- **No metadata**: Has no concept of entry points, external dependencies, resources,
  artifact identity, or any manifest. It just sees `.xtc` files.

**How it's used today** — exactly 3 call sites:

1. **`Launcher.configureLibraryRepo()` (line 695)**: When the `-L` library path points
   to a directory, it becomes `new DirRepository(file, true)` (read-only) in the
   `LinkedRepository` chain. This is how the XDK `lib/` directory is consumed.

2. **`Launcher` output repo (line 739)**: When the compiler needs a writable output
   directory, it creates `new DirRepository(resolved, false)`.

3. **`xRTCompiler` (line 147)**: The runtime compiler checks `instanceof DirRepository`
   and `repoDir.isReadOnly()` to decide which repos to carry forward when recomposing
   a chain for runtime compilation.

Typical runtime usage:
```
xec -L /path/to/xdk/lib -L /path/to/platform/lib kernel.xtc

→ LinkedRepository [
    BuildRepository,                              // writable in-memory workspace
    DirRepository(/path/to/xdk/lib, readOnly),    // scans dir, finds ecstasy.xtc, web.xtc, ...
    DirRepository(/path/to/platform/lib, readOnly) // scans dir, finds kernel.xtc, auth.xtc, ...
  ]
```

### 2.4 LinkedRepository — Chain of Repositories

**File**: `javatools/src/main/java/org/xvm/asm/LinkedRepository.java`

The composition mechanism. Chains multiple `ModuleRepository` instances with first-match-wins
semantics.

- `getModuleNames()` returns the union of all repos' module names.
- `loadModule()` iterates repos in order, returns first non-null result.
- `storeModule()` always writes to `repos[0]`.
- `readThrough` flag: when enabled, any module loaded from a later repo is cloned and
  stored into `repos[0]` for caching.

This is the glue — the compiler and runtime always work with a `LinkedRepository`.

### 2.5 BuildRepository — In-Memory Workspace

**File**: `javatools/src/main/java/org/xvm/compiler/BuildRepository.java`

Pure in-memory `Map<String, ModuleStructure>`. No persistence, always writable. Exists
only during compilation as the first element of a `LinkedRepository` chain.

### 2.6 InstantRepository — Single In-Memory Module

**File**: `javatools/src/main/java/org/xvm/compiler/InstantRepository.java`

Wraps a single `ModuleStructure` already in memory. Read-only. Used when a module has
been parsed and is immediately available with no file backing.

### 2.7 The FileStructure.merge() Capability

**File**: `javatools/src/main/java/org/xvm/asm/FileStructure.java` (line 173)

A single `.xtc` file CAN contain multiple modules via `FileStructure.merge()`:
```java
public void merge(ModuleStructure module, boolean fSynthesize, boolean fTakeFile)
```

Used in `NativeContainer` to merge `ecstasy` + `turtle` + `native` into one `FileStructure`
at runtime. However, merged modules share a single constant pool and cannot be loaded
individually — it's all or nothing. This is designed for runtime linking, not distribution.

---

## Part 3: Why No Existing Repository Type Changes

### 3.1 Why NOT Introduce a `MultiModuleRepository` Abstract Class

The initial version of this plan proposed a `MultiModuleRepository` abstract class that
`XarRepository` would extend, and asked whether `DirRepository` should also extend it.

**Answer: No abstract class is needed.** Here's why:

1. **`ModuleRepository` already handles multiple modules.** The interface has
   `getModuleNames() → Set<String>`. `DirRepository`, `BuildRepository`, and
   `LinkedRepository` all hold multiple modules today. "Multi-module" is not a new
   capability — it's the existing default.

2. **What `.xar` adds isn't "multiple modules" — it's manifest metadata.** Entry points,
   external dependency declarations, resources, and artifact identity are specific to the
   archive format. `DirRepository` has none of these and shouldn't be forced to implement
   stub methods for them.

3. **DirRepository is fundamentally different from XarRepository.**

   | Concern | `DirRepository` | `XarRepository` |
   |---------|-----------------|-----------------|
   | Discovery | Filesystem scan (`listFiles`) | Manifest-driven (declared contents) |
   | Mutability | Writable (`storeModule` writes `.xtc` to disk) | Read-only (archive is immutable) |
   | Change detection | Polls file timestamps every second | None needed (archive is immutable) |
   | Metadata | None — just raw `.xtc` files | Manifest with versions, deps, entry points |
   | Entry points | No concept | Declared in manifest |
   | Resources | No concept | `resources/` section in archive |
   | Backing store | Live filesystem directory | Single ZIP file |

   These share the `ModuleRepository` interface but have nothing else in common worth
   abstracting. An intermediate class would be a [leaky abstraction](https://en.wikipedia.org/wiki/Leaky_abstraction)
   — DirRepository would need to return empty lists for `getExternalDependencies()` and
   null for `loadResource()`, violating the spirit of those methods.

4. **Existing `instanceof` checks would break.** `xRTCompiler` (line 147) does
   `instanceof DirRepository` to check if a repo is read-only during runtime compilation.
   Inserting an abstract class changes the type hierarchy and could introduce subtle bugs
   in these checks.

### 3.2 Why NOT Change FileRepository to Handle `.xar`

`FileRepository` is hard-wired to the `.xtc` single-module format:

- Asserts `!file.isDirectory()` and forces `.xtc` extension in the constructor
- Calls `new FileStructure(file)` which reads the `0xEC57A5EE` magic header
- Assumes exactly one module (`getModuleNames()` returns a singleton)
- Tracks a single `ModuleStructure module` field
- Has writable `storeModule()` that writes one module to one file

Making it also handle `.xar` would violate single-responsibility. A `.xar` is a ZIP
containing multiple `.xtc` entries plus a JSON manifest — a completely different format
with completely different semantics (multiple modules, read-only, manifest-driven).

### 3.3 Where the Dispatch Happens

The `Launcher.configureLibraryRepo()` method already dispatches on file type:

```java
// Current code (line 693-697)
for (File file : path) {
    repos.add(file.isDirectory()
        ? new DirRepository(file, true)
        : new FileRepository(file, true));
}
```

Adding `.xar` is a one-line change in the dispatch, not a class hierarchy restructure:

```java
for (File file : path) {
    if (file.isDirectory()) {
        repos.add(new DirRepository(file, true));
    } else if (file.getName().endsWith(".xar")) {
        repos.add(new XarRepository(file));
    } else {
        repos.add(new FileRepository(file, true));
    }
}
```

The three repository types (`FileRepository`, `DirRepository`, `XarRepository`) are
**peers** — all direct implementations of `ModuleRepository`, differentiated by their
backing store format. The `LinkedRepository` composes them uniformly.

### 3.4 The Correct Hierarchy

```
ModuleRepository (interface) — UNCHANGED
    │
    ├── FileRepository          -- Single .xtc file, PRIMARY module only (UNCHANGED)
    ├── DirRepository           -- Directory of .xtc files               (UNCHANGED)
    ├── LinkedRepository        -- Chain of repositories                 (UNCHANGED)
    ├── BuildRepository         -- In-memory workspace                   (UNCHANGED)
    ├── InstantRepository       -- Single in-memory module               (UNCHANGED)
    ├── ModuleInfoRepository    -- Build tool bridge                     (UNCHANGED)
    │
    ├── BundledFileRepository   -- NEW: Single .xtc file, ALL merged modules
    ├── XarRepository           -- NEW: .xar archive (ZIP + manifest)
    │       has-a XarManifest
    │
    └── ArtifactRepository      -- NEW: Decorator adding group:name:version
            wraps any ModuleRepository
```

All three new types implement `ModuleRepository` directly — no intermediate abstract class.

- `BundledFileRepository` is the natural complement to `FileRepository`: same `.xtc` file
  format, but exposes ALL non-fingerprint modules instead of just the primary.
- `XarRepository` is for the new `.xar` archive format. Its manifest-related methods
  (`getEntryPoints()`, `getExternalDependencies()`, `loadResource()`) are its own public
  API. Code that needs archive metadata works with `XarRepository` directly; code that
  just needs module loading works through `ModuleRepository`.
- `ArtifactRepository` is a decorator that can wrap any of the above.

---

## Part 4: The `.xar` Format

### 4.1 Format Choice: ZIP-based

The `.xar` format is a **ZIP file** with a defined internal structure. ZIP is chosen because:

- Java/Kotlin have native ZIP support (`java.util.zip`, `java.nio.file.FileSystems`)
- Maven/Gradle already handle ZIP-based artifacts (JARs are ZIPs)
- Standard tooling can inspect contents (`unzip -l`, any archive viewer)
- Supports individual entry compression (each `.xtc` module compressed independently)
- Supports streaming reads (no need to extract to disk)

### 4.2 Internal Structure

```
artifact-name-1.0.0.xar
├── META-INF/
│   └── xar.json              -- Artifact manifest (required)
├── modules/
│   ├── auth.xtc              -- Compiled module
│   ├── common.xtc            -- Compiled module
│   ├── kernel.xtc            -- Compiled module
│   ├── host.xtc              -- Compiled module
│   └── ...                   -- All modules in the artifact
└── resources/                -- Optional non-module resources
    └── cfg.json              -- Configuration files, etc.
```

### 4.3 The Manifest (`META-INF/xar.json`)

```json
{
  "formatVersion": 1,
  "artifact": {
    "group": "platform.xqiz.it",
    "name": "platform",
    "version": "0.1.0"
  },
  "modules": {
    "auth": {
      "path": "modules/auth.xtc",
      "xtcVersion": "0.1.0",
      "entryPoint": false
    },
    "common": {
      "path": "modules/common.xtc",
      "xtcVersion": "0.1.0",
      "dependencies": ["auth"],
      "entryPoint": false
    },
    "kernel": {
      "path": "modules/kernel.xtc",
      "xtcVersion": "0.1.0",
      "dependencies": ["auth", "common", "platformDB"],
      "entryPoint": true,
      "entryMethod": "run"
    }
  },
  "externalDependencies": [
    {
      "module": "ecstasy.xtclang.org",
      "minVersion": "0.4.4"
    },
    {
      "module": "web.xtclang.org",
      "minVersion": "0.4.4"
    }
  ],
  "resources": [
    "resources/cfg.json"
  ]
}
```

Key manifest fields:

| Field | Purpose |
|-------|---------|
| `formatVersion` | Manifest schema version (for forward compatibility) |
| `artifact.group` | Maven-style group ID |
| `artifact.name` | Artifact name |
| `artifact.version` | Semantic version of the artifact as a whole |
| `modules` | Map of module name -> module descriptor |
| `modules.*.path` | ZIP entry path to the `.xtc` file |
| `modules.*.xtcVersion` | The XTC-level version stamped in the module |
| `modules.*.dependencies` | Intra-artifact module dependencies (for load ordering) |
| `modules.*.entryPoint` | Whether this module can be executed directly |
| `externalDependencies` | Modules NOT included in the archive (must come from elsewhere) |
| `resources` | Non-module files included in the archive |

### 4.4 Why JSON (not binary)?

- Human-readable for debugging and tooling
- Easy to generate from Gradle
- Small relative to the `.xtc` module payloads (typically < 1KB manifest vs. MB of modules)
- Can be extended without breaking readers (unknown fields are ignored)
- Ecstasy already has `json.xtclang.org` as a core library

### 4.5 File Extension

`.xar` — **X**TC **Ar**chive. Short, memorable, and distinct from `.xtc` (single module).

Alternative considered: `.xtcr` (XTC Repository) — rejected as too similar to `.xtc`.

---

## Part 5: XarRepository Implementation

### 5.1 XarRepository

`XarRepository` implements `ModuleRepository` directly. Its manifest-related methods
are its own public API — not inherited from any abstract class.

```java
/**
 * A ModuleRepository backed by a .xar (XTC Archive) file.
 *
 * A .xar file is a ZIP containing:
 *   - META-INF/xar.json  -- manifest with artifact identity, module descriptors,
 *                            external dependencies, and entry points
 *   - modules/*.xtc      -- compiled XTC modules
 *   - resources/*         -- optional non-module resources
 *
 * This is analogous to Java's .jar format but for XTC modules. Like DirRepository,
 * it holds multiple modules behind the ModuleRepository interface. Unlike
 * DirRepository, it is:
 *   - Manifest-driven (declared contents, not filesystem scanning)
 *   - Immutable (read-only, no storeModule)
 *   - Self-describing (artifact identity, entry points, external dependencies)
 *   - A single distributable file (not a directory tree)
 *
 * XarRepository is always read-only. Archives are produced by build tools
 * (Gradle XarTask) and consumed by the compiler and runtime.
 */
public class XarRepository
        implements ModuleRepository {

    private final File                         f_file;
    private final XarManifest                  f_manifest;
    private final Map<String, ModuleStructure> f_cache;

    /**
     * Construct an XarRepository from a .xar file.
     *
     * @param file  the .xar file
     *
     * @throws IOException if the file cannot be read or has an invalid manifest
     */
    public XarRepository(File file)
            throws IOException {
        assert file != null && file.isFile() && file.getName().endsWith(".xar");

        f_file     = file;
        f_manifest = XarManifest.read(file);
        f_cache    = new ConcurrentHashMap<>();
    }

    // -- ModuleRepository interface -------------------------------------------

    @Override
    public Set<String> getModuleNames() {
        return f_manifest.moduleNames();
    }

    @Override
    public ModuleStructure loadModule(String sModule) {
        if (!f_manifest.containsModule(sModule)) {
            return null;
        }
        return f_cache.computeIfAbsent(sModule, this::readModule);
    }

    @Override
    public void storeModule(ModuleStructure module) throws IOException {
        throw new IOException("XarRepository is read-only: " + this);
    }

    // -- XarRepository-specific API -------------------------------------------

    /**
     * @return the .xar file backing this repository
     */
    public File getFile() {
        return f_file;
    }

    /**
     * @return the parsed manifest
     */
    public XarManifest getManifest() {
        return f_manifest;
    }

    /**
     * @return artifact coordinates (group:name:version), or null if not specified
     */
    public String getArtifactCoordinates() {
        return f_manifest.artifactCoordinates();
    }

    /**
     * @return the set of module names declared as entry points
     */
    public Set<String> getEntryPoints() {
        return f_manifest.entryPoints();
    }

    /**
     * @return external module dependencies not included in this archive
     */
    public List<XarManifest.ExternalDependency> getExternalDependencies() {
        return f_manifest.externalDependencies();
    }

    /**
     * Load a non-module resource from this archive.
     *
     * @param sPath  the resource path (e.g., "resources/cfg.json")
     *
     * @return the resource bytes, or null if not found
     */
    public byte[] loadResource(String sPath) {
        return readZipEntry(sPath);
    }

    // -- Object methods -------------------------------------------------------

    @Override
    public String toString() {
        return "XarRepository(Path=" + f_file + ")";
    }

    // -- internal -------------------------------------------------------------

    private ModuleStructure readModule(String sModule) {
        String sPath = f_manifest.modulePath(sModule);
        if (sPath == null) {
            return null;
        }

        byte[] abXtc = readZipEntry(sPath);
        if (abXtc == null) {
            return null;
        }

        try {
            return new FileStructure(new ByteArrayInputStream(abXtc)).getModule();
        } catch (IOException e) {
            System.out.println("Error loading module " + sModule
                    + " from archive: " + f_file + "; " + e.getMessage());
            return null;
        }
    }

    private byte[] readZipEntry(String sPath) {
        try (ZipFile zip = new ZipFile(f_file)) {
            ZipEntry entry = zip.getEntry(sPath);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            return null;
        }
    }
}
```

### 5.2 XarManifest

A simple data class representing the parsed `META-INF/xar.json`:

```java
/**
 * Parsed representation of a .xar manifest (META-INF/xar.json).
 */
public class XarManifest {

    public record ModuleDescriptor(
        String       name,
        String       path,
        String       xtcVersion,
        boolean      entryPoint,
        String       entryMethod,
        List<String> dependencies
    ) {}

    public record ExternalDependency(
        String moduleName,
        String minVersion
    ) {}

    public record ArtifactIdentity(
        String group,
        String name,
        String version
    ) {}

    // -- fields --
    private final int                          formatVersion;
    private final ArtifactIdentity             artifact;
    private final Map<String, ModuleDescriptor> modules;
    private final List<ExternalDependency>     externalDeps;
    private final List<String>                 resources;

    // -- accessors --
    public Set<String>              moduleNames()           { return modules.keySet(); }
    public boolean                  containsModule(String s){ return modules.containsKey(s); }
    public String                   modulePath(String s)    { var d = modules.get(s); return d == null ? null : d.path(); }
    public String                   artifactCoordinates()   { return artifact == null ? null : artifact.group() + ":" + artifact.name() + ":" + artifact.version(); }
    public List<ExternalDependency> externalDependencies()  { return externalDeps; }
    public List<String>             resourcePaths()         { return resources; }

    public Set<String> entryPoints() {
        return modules.values().stream()
            .filter(ModuleDescriptor::entryPoint)
            .map(ModuleDescriptor::name)
            .collect(Collectors.toUnmodifiableSet());
    }

    /** Read and parse the manifest from a .xar file. */
    public static XarManifest read(File xarFile) throws IOException { /* ... */ }
}
```

### 5.3 ArtifactRepository — Decorator

A thin wrapper that adds artifact identity (group:name:version) to any `ModuleRepository`.
This is orthogonal to the `.xar` format — any repository can be tagged with artifact
coordinates.

```java
/**
 * Decorates a ModuleRepository with artifact-level identity and version.
 * This is the bridge between Maven/Gradle artifact coordinates and the XTC
 * module system.
 *
 * The artifact version is distinct from individual module XTC versions:
 *   - Artifact version: Maven/Gradle coordinate (e.g., "platform:0.1.0")
 *   - Module version: XTC version stamped in the .xtc binary
 * These may or may not be the same value.
 *
 * Use cases:
 *   - Wrap a DirRepository:   ArtifactRepository("org.xtclang", "xdk", "0.4.4", dirRepo)
 *   - Wrap a FileRepository:  ArtifactRepository("com.acme", "mylib", "1.0", fileRepo)
 *   - Wrap an XarRepository:  the XarRepository already carries artifact identity in its
 *     manifest, but ArtifactRepository can normalize access for code that doesn't want to
 *     know about XarRepository specifically.
 */
public class ArtifactRepository
        implements ModuleRepository {

    private final String           f_group;
    private final String           f_name;
    private final Version          f_version;
    private final ModuleRepository f_delegate;

    public ArtifactRepository(String group, String name, Version version,
                              ModuleRepository delegate) {
        f_group    = group;
        f_name     = name;
        f_version  = version;
        f_delegate = delegate;
    }

    public String  getGroup()       { return f_group; }
    public String  getName()        { return f_name; }
    public Version getVersion()     { return f_version; }
    public String  getCoordinates() { return f_group + ":" + f_name + ":" + f_version; }

    /** @return the wrapped repository */
    public ModuleRepository getDelegate() { return f_delegate; }

    // -- ModuleRepository delegation --

    @Override
    public Set<String> getModuleNames()                { return f_delegate.getModuleNames(); }
    @Override
    public ModuleStructure loadModule(String sModule)  { return f_delegate.loadModule(sModule); }
    @Override
    public void storeModule(ModuleStructure module) throws IOException { f_delegate.storeModule(module); }

    @Override
    public String toString() {
        return "ArtifactRepository(" + getCoordinates() + ", delegate=" + f_delegate + ")";
    }
}
```

---

## Part 6: Bundled Binary Blob — BundledFileRepository

### 6.1 The Concept

The `.xar` format keeps modules as separate entries in a ZIP. But there's a simpler
approach for when you just want "one file = entire compilation output":

**Merge all modules into a single `.xtc` file using `FileStructure.merge()`.**

This already works at the binary level. `NativeContainer` merges ecstasy + turtle +
native into one `FileStructure` at runtime. The merged file has:
- One shared constant pool (more compact than separate pools)
- All modules accessible via `FileStructure.getModule(ModuleConstant id)`
- A primary module (`getModule()`) plus additional modules in `f_moduleById`
- A single `writeTo(file)` call serializes everything

The problem is that **no existing repository type exposes all modules** from such a file.
`FileRepository` only serves the primary module — it returns `Collections.singleton(name)`
from `getModuleNames()` and only matches the primary name in `loadModule()`.

### 6.2 What Exists in FileStructure Today

```java
// FileStructure already supports this:
FileStructure file = new FileStructure(primaryModule);
file.merge(secondModule, true, false);   // adds secondModule into the file
file.merge(thirdModule, true, false);    // adds thirdModule

file.moduleIds();                        // → {primary, second, third}
file.getModule();                        // → primary module only
file.getModule(secondId);               // → second module by ModuleConstant
file.children();                         // → all modules (f_moduleById.values())
file.writeTo(outputFile);               // → writes ALL modules to one .xtc file
```

Reading it back:
```java
FileStructure file = new FileStructure(inputFile);
file.moduleIds();                        // → all module IDs are restored
file.getModule(id);                      // → any specific module
```

The binary format already round-trips multiple modules. The missing piece is a
`ModuleRepository` implementation that exposes them.

### 6.3 BundledFileRepository

A new repository type that wraps a single `.xtc` file containing multiple merged modules
and exposes ALL of them (not just the primary):

```java
/**
 * A ModuleRepository backed by a single .xtc file that contains multiple
 * merged modules. Unlike FileRepository (which only exposes the primary module),
 * BundledFileRepository exposes every module in the FileStructure.
 *
 * This is the repository counterpart of FileStructure.merge() — it reads a
 * merged .xtc file and serves individual modules by name.
 *
 * Use case: A project like Platform compiles 11 modules, merges them into a
 * single .xtc file at build time, and distributes that one file. The runtime
 * wraps it in a BundledFileRepository to load individual modules from it.
 */
public class BundledFileRepository
        implements ModuleRepository {

    private final File          f_file;
    private FileStructure       m_struct;
    private Map<String, ModuleConstant> m_nameToId;

    public BundledFileRepository(File file) {
        assert file != null && file.isFile();
        f_file = file;
    }

    @Override
    public Set<String> getModuleNames() {
        ensureLoaded();
        return m_nameToId.keySet();
    }

    @Override
    public ModuleStructure loadModule(String sModule) {
        ensureLoaded();
        ModuleConstant id = m_nameToId.get(sModule);
        if (id == null) {
            return null;
        }
        return m_struct.getModule(id);
    }

    @Override
    public void storeModule(ModuleStructure module) throws IOException {
        throw new IOException("BundledFileRepository is read-only: " + this);
    }

    /**
     * @return the primary module (the one the file was originally created for)
     */
    public ModuleStructure getPrimaryModule() {
        ensureLoaded();
        return m_struct.getModule();
    }

    /**
     * @return the underlying FileStructure containing all merged modules
     */
    public FileStructure getFileStructure() {
        ensureLoaded();
        return m_struct;
    }

    private void ensureLoaded() {
        if (m_struct == null) {
            try {
                m_struct = new FileStructure(f_file);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load bundled file: " + f_file, e);
            }

            m_nameToId = new HashMap<>();
            for (ModuleConstant id : m_struct.moduleIds()) {
                ModuleStructure module = m_struct.getModule(id);
                if (module != null && !module.isFingerprint()) {
                    m_nameToId.put(id.getName(), id);
                }
            }
        }
    }

    @Override
    public String toString() {
        return "BundledFileRepository(Path=" + f_file + ")";
    }
}
```

**Key implementation details:**

1. **Exposes all non-fingerprint modules.** After merge, a `FileStructure` contains
   real modules AND fingerprint stubs for external dependencies. `BundledFileRepository`
   only exposes real modules — fingerprints are internal linking artifacts.

2. **Lazy loading.** The file is read on first access, same pattern as `FileRepository`.

3. **Read-only.** A bundled file is a build output — you don't write back into it.

4. **Shares the FileStructure's constant pool.** All returned `ModuleStructure` instances
   share one pool. This is efficient for memory but means callers should not mutate
   returned modules. (This matches how `DirRepository` works in practice — callers
   clone modules before mutation.)

### 6.4 How to Produce a Bundled File

A Gradle task (or command-line tool) that:
1. Loads each compiled `.xtc` module as a `FileStructure`
2. Picks one as primary (the entry point module)
3. Calls `primary.merge(other, true, false)` for each additional module
4. Calls `primary.writeTo(outputFile)`

```java
// Pseudocode for a Gradle task or CLI tool
FileStructure bundle = new FileStructure(kernelFile);  // primary
for (File xtcFile : otherModuleFiles) {
    FileStructure other = new FileStructure(xtcFile);
    bundle.merge(other.getModule(), true, false);
}
bundle.writeTo(new File("platform-bundled.xtc"));
```

The output is a standard `.xtc` file — it has the `0xEC57A5EE` magic header and uses
the existing binary format. The only difference is that it contains more than one real
module. Existing tools that only look at the primary module still work; new tools
(using `BundledFileRepository`) can access all modules.

### 6.5 Comparison: `.xar` Archive vs. Bundled `.xtc`

| Concern | `.xar` (ZIP archive) | Bundled `.xtc` (merged binary) |
|---------|---------------------|-------------------------------|
| Format | ZIP with JSON manifest | Standard `.xtc` binary format |
| Modules | Independent `.xtc` entries | Merged into shared constant pool |
| Metadata | Rich manifest (entry points, deps, resources) | None (just the binary) |
| Inspectability | `unzip -l` to see contents | Requires XTC tooling |
| Resources | Supports non-module files | No resource support |
| Artifact identity | In manifest (`group:name:version`) | None (needs `ArtifactRepository` wrapper) |
| Module independence | Each module has own constant pool | Shared constant pool |
| Loading granularity | Load one module at a time from ZIP | Load all at once (shared pool) |
| Tool compatibility | New format, needs new tooling | Works with existing `.xtc` tools |
| File size | Slightly larger (ZIP overhead + separate pools) | Smaller (shared pool, no overhead) |
| Mutability | Read-only | Read-only |
| Best for | Distribution, dependency resolution, Maven/Gradle | Optimized deployment, single-file execution |

**They serve different purposes and both belong in the plan:**

- **`.xar`** is the **distribution format** — what you publish to Maven, what Gradle resolves,
  what has metadata for tooling. Think of it as Java's `.jar`.

- **Bundled `.xtc`** is the **deployment format** — what you drop onto a server to run.
  Minimal overhead, fastest possible loading (one file read, one deserialization, shared
  constant pool). Think of it as a "fat binary" or a Docker image.

A project like Platform might produce both:
```
platform-0.1.0.xar          -- for Gradle dependency resolution
platform-bundled.xtc        -- for server deployment
```

### 6.6 Where BundledFileRepository Fits in the Hierarchy

```
ModuleRepository (interface) — UNCHANGED
    │
    │   Existing — UNCHANGED:
    ├── FileRepository          -- Single .xtc, exposes PRIMARY module only
    ├── DirRepository           -- Directory of .xtc files
    ├── LinkedRepository        -- Chain of repositories
    ├── BuildRepository         -- In-memory workspace
    ├── InstantRepository       -- Single in-memory module
    ├── ModuleInfoRepository    -- Build tool bridge
    │
    │   New:
    ├── BundledFileRepository   -- Single .xtc, exposes ALL merged modules
    ├── XarRepository           -- .xar archive (ZIP of .xtc files + manifest)
    └── ArtifactRepository      -- Decorator: group:name:version identity
```

`BundledFileRepository` is the natural complement to `FileRepository`:
- `FileRepository`: one file, one module (the primary)
- `BundledFileRepository`: one file, many modules (all non-fingerprint modules)

They're peers, not parent/child. Both wrap a single `.xtc` file. The difference is
which modules they expose.

### 6.7 Runtime Integration

The Launcher dispatch gains one more case:

```java
for (File file : path) {
    if (file.isDirectory()) {
        repos.add(new DirRepository(file, true));
    } else if (file.getName().endsWith(".xar")) {
        repos.add(new XarRepository(file));
    } else {
        // For a single .xtc file, check if it contains multiple real modules
        FileStructure struct = new FileStructure(file);
        long realModules = struct.moduleIds().stream()
            .filter(id -> { var m = struct.getModule(id); return m != null && !m.isFingerprint(); })
            .count();
        if (realModules > 1) {
            repos.add(new BundledFileRepository(file));
        } else {
            repos.add(new FileRepository(file, true));
        }
    }
}
```

Or more simply, with a naming convention (e.g., file contains "bundled" in its name)
or a command-line flag (`-B` for bundled). The auto-detection approach is elegant but
requires reading the file header twice (once here, once in the repo). A practical
alternative is to always use `BundledFileRepository` for `.xtc` files — since it falls
back to single-module behavior when there's only one real module, it's a strict superset
of `FileRepository`'s read path.

---

## Part 7: Two-Level Versioning Model (Artifact vs. Module)

### 6.1 The Problem

XTC has its own version system (`Version` class, `VersionTree`, `isSubstitutableFor()`).
Maven/Gradle have their own (`1.0.0-SNAPSHOT`, version ranges, resolution strategies).
These are different systems with different rules. A multi-module artifact needs both.

### 6.2 The Two Levels

**Level 1 — Artifact Version** (Maven/Gradle world):
- Follows Semantic Versioning 2.0.0 (e.g., `0.1.0`, `1.0.0-SNAPSHOT`)
- Used for dependency resolution in Gradle/Maven
- Lives in the `.xar` manifest and in the Maven POM/Gradle metadata
- Resolved BEFORE the XTC compiler/runtime sees anything

**Level 2 — Module Version** (XTC world):
- Uses XTC's `Version` class with its own hierarchy and pre-release labels
- Stored inside each `.xtc` binary via `ModuleStructure.setVersion()`
- Used by `FileStructure.linkModules()` for compatibility checking
- Resolved AFTER artifact resolution, during module linking

**Example:**

```
Artifact:  platform.xqiz.it:platform:0.1.0       <- Gradle resolves this
Contains:  kernel.xtc (XTC version 0.1.0)         <- XTC links against this
           common.xtc (XTC version 0.1.0)
           auth.xtc   (XTC version 0.1.0)
```

In practice, artifact version and module version will often be the same. But they CAN
diverge — for example, a hotfix artifact `1.0.1` might contain modules still at XTC
version `1.0.0` if no module API changed.

### 6.3 Version Alignment Convention

**Recommended practice**: Keep artifact version and module versions in sync.

The Gradle build should stamp both:
```kotlin
// In build.gradle.kts
version = "0.1.0"  // Gradle artifact version

tasks.withType<XtcCompileTask>().configureEach {
    xtcVersion = version.toString()  // XTC module version = artifact version
}
```

The `.xar` manifest records both for transparency.

### 6.4 Implications for Dependency Resolution

```
┌─────────────────────────────────────────────┐
│  Gradle Dependency Resolution               │
│                                             │
│  platform.xqiz.it:platform:0.1.0           │
│      → resolves to platform-0.1.0.xar      │
│                                             │
│  org.xtclang:xdk:0.4.4                     │
│      → resolves to xdk-0.4.4.xar           │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│  XTC Module Linking                         │
│                                             │
│  XarRepository(platform-0.1.0.xar)         │
│  XarRepository(xdk-0.4.4.xar)             │
│      → LinkedRepository chain              │
│      → linkModules() resolves fingerprints │
│      → XTC version compatibility checked   │
└─────────────────────────────────────────────┘
```

Gradle handles artifact-level resolution (which `.xar` to use). XTC handles module-level
resolution (which modules within those archives are compatible).

---

## Part 8: Runtime Integration

### 7.1 How the Runtime Loads Modules Today

The current `-L` (library path) mechanism:
```
xec -L /path/to/libs -L /path/to/more module.xtc
```

`Launcher.configureLibraryRepo()` converts each `-L` path entry:
- Directory → `new DirRepository(file, true)`
- File → `new FileRepository(file, true)`

Then chains them all in a `LinkedRepository` with `BuildRepository` at position 0.

### 7.2 Adding `.xar` Support to the Launcher

The change is a single `else if` in the dispatch:

```java
protected ModuleRepository configureLibraryRepo(List<File> path) {
    var repos = new ArrayList<ModuleRepository>(path.size() + 1);
    repos.add(makeBuildRepo());
    for (File file : path) {
        if (file.isDirectory()) {
            repos.add(new DirRepository(file, true));
        } else if (file.getName().endsWith(".xar")) {
            repos.add(new XarRepository(file));           // NEW
        } else {
            repos.add(new FileRepository(file, true));
        }
    }
    return new LinkedRepository(true, repos.toArray(ModuleRepository[]::new));
}
```

Usage becomes:
```
xec -L platform-0.1.0.xar -L xdk-0.4.4.xar kernel
```

Or even simpler with entry point metadata:
```
xec platform-0.1.0.xar
```

### 7.3 Entry Point Auto-Detection

When the argument is a `.xar` file, the launcher can read entry points from the manifest:

```java
if (moduleArg.endsWith(".xar")) {
    var xar = new XarRepository(new File(moduleArg));
    Set<String> entryPoints = xar.getEntryPoints();
    if (entryPoints.size() == 1) {
        moduleName = entryPoints.iterator().next();
    } else if (entryPoints.isEmpty()) {
        log(FATAL, "Archive {} has no entry point modules", moduleArg);
    } else {
        log(FATAL, "Archive {} has multiple entry points: {}; specify one with -M",
            moduleArg, entryPoints);
    }
}
```

### 7.4 How It Composes in the LinkedRepository Chain

The runtime doesn't need to know it's using an `XarRepository`. It's just a
`ModuleRepository` in the chain:

```
xec -L platform-0.1.0.xar -L /path/to/xdk/lib kernel

→ LinkedRepository(readThrough=true) [
    BuildRepository,                        // writable workspace (always [0])
    XarRepository(platform-0.1.0.xar),     // reads from ZIP, has manifest
    DirRepository(/path/to/xdk/lib, RO),   // scans directory for .xtc files
  ]
```

`linkModules()` iterates this chain to resolve fingerprints. It doesn't care whether
the `ModuleStructure` came from a ZIP entry or a directory file — it's the same type
either way.

### 7.5 Resource Access from XTC Code

The `.xar` format includes a `resources/` section. Ecstasy code could access these
via the `FileStore` injection mechanism:

```ecstasy
@Inject FileStore resources;
File config = resources.find("cfg.json") ?: assert;
```

The runtime would back this `FileStore` with the `.xar`'s resource entries via
`XarRepository.loadResource()`.

---

## Part 9: Gradle Build Integration

### 8.1 Producing a `.xar` Artifact

A new Gradle task type `XarTask` (or extending `Zip`) would assemble `.xar` files:

```kotlin
// In platform's build.gradle.kts
plugins {
    alias(libs.plugins.xtc)
    alias(libs.plugins.xar)  // or built into the xtc plugin
}

xar {
    archiveBaseName = "platform"
    // artifact version comes from project.version

    // Modules are auto-discovered from xtcModule configuration
    // Entry point can be specified explicitly
    entryPoint("kernel")

    // Optional resources
    resources {
        from("src/main/resources") {
            include("cfg.json")
        }
    }
}

// Standard Gradle artifact publishing
publishing {
    publications {
        create<MavenPublication>("xar") {
            artifact(tasks.named("xar"))
            artifactId = "platform"
            groupId = "platform.xqiz.it"
        }
    }
}
```

### 8.2 Consuming a `.xar` Dependency

```kotlin
// In a downstream project's build.gradle.kts
dependencies {
    xtcModule("platform.xqiz.it:platform:0.1.0")  // resolves the .xar
}
```

The XTC plugin would need to recognize `.xar` artifacts and configure them as module
repositories rather than individual `.xtc` files.

### 8.3 Migration Path for Platform

Current Platform build (`installDist` copies loose `.xtc` files):
```kotlin
val installDist by tasks.registering(Copy::class) {
    from(configurations.named("xtcModule"))
    into(layout.buildDirectory.dir("install/platform/lib"))
}
```

New Platform build (produces a `.xar`):
```kotlin
val xar by tasks.registering(XarTask::class) {
    from(configurations.named("xtcModule"))
    entryPoint("kernel")
    manifest {
        group = project.group.toString()
        name = project.name
        version = project.version.toString()
    }
    destinationDirectory = layout.buildDirectory.dir("distributions")
    archiveBaseName = "platform"
}
```

Both approaches coexist. The `installDist` task remains for development/debugging,
while the `xar` task produces the distributable artifact.

### 8.4 Migration Path for XDK

The XDK currently uses Gradle's `distribution` plugin to create a ZIP with `lib/`,
`javatools/`, `bin/`, etc. This is more than just modules — it includes Java JARs and
launcher scripts.

**Approach**: The XDK distribution remains as-is (it's a full SDK installation). But
the XDK's `.xtc` module libraries could ALSO be published as a `.xar`:

```
xdk-libs-0.4.4.xar          -- Just the .xtc modules (for dependency resolution)
xdk-0.4.4-SNAPSHOT.zip       -- Full SDK (for installation)
```

This lets downstream projects declare:
```kotlin
dependencies {
    xtcModule("org.xtclang:xdk-libs:0.4.4")  // Just the modules
}
```

---

## Part 10: CLI Tooling — `xtc bundle` and `xtc archive`

### 10.1 Why Not a Compiler Flag?

Adding `--bundle` to `xcc` would be architecturally wrong:

1. **Compilation and packaging are separate concerns.** The compiler's job is
   source → validated binary. Packaging is a downstream step. Every ecosystem
   separates these: `javac` + `jar`, `rustc` + `cargo package`, `tsc` + `npm pack`.

2. **You often bundle modules you didn't compile together.** The Platform bundles
   11 modules it compiled, but might also include pre-built dependencies from other
   projects. A standalone tool can take `.xtc` files from anywhere.

3. **The compiler has a clean output contract.** `emitModules()` iterates `allNodes`
   and writes one `.xtc` per module via `repoOutput.storeModule(module)`. Changing
   this for one output mode creates a second code path and complicates the build.

### 10.2 The `xtc` CLI Command Dispatch

The `xtc` tool already dispatches subcommands via a command table in `Launcher.java`:

```java
// Launcher.java lines 96-106
private static final Map<String, CommandHandler> COMMANDS = Map.of(
    CMD_BUILD,  (args, console, err) -> launch(CompilerOptions.parse(args), console, err),
    CMD_INIT,   (args, console, err) -> launch(InitializerOptions.parse(args), console, err),
    CMD_RUN,    (args, console, err) -> launch(RunnerOptions.parse(args), console, err),
    CMD_TEST,   (args, console, err) -> launch(TestRunnerOptions.parse(args), console, err),
    CMD_DISASS, (args, console, err) -> launch(DisassemblerOptions.parse(args), console, err)
);
```

Each command has:
- A `Launcher<T>` subclass (e.g., `Compiler extends Launcher<CompilerOptions>`)
- A `LauncherOptions` subclass for CLI argument parsing
- Registration in the `COMMANDS` table

Adding `bundle` and `archive` follows this exact pattern.

### 10.3 `xtc bundle` — Produce a Bundled `.xtc`

```
xtc bundle -e <entry-module> -o <output.xtc> [-L <libpath>...] <input.xtc>...

Options:
  -e <module>    Entry point module name (becomes the FileStructure primary)
  -o <file>      Output file (default: <entry-module>-bundled.xtc)
  -L <path>      Library path for resolving fingerprints (same as xcc/xec)
  <input.xtc>    One or more .xtc files to merge into the bundle
```

**Examples:**
```bash
# Compile first (produces individual .xtc files)
xtc build -o build/lib src/main/x/*.x

# Bundle into a single file for deployment
xtc bundle -e kernel -o platform-bundled.xtc build/lib/*.xtc

# Run from the bundle
xtc run -L platform-bundled.xtc kernel
```

**Implementation** — a `Bundler` class extending `Launcher<BundlerOptions>`:

```java
public class Bundler
        extends Launcher<BundlerOptions> {

    @Override
    protected int run() {
        var opts        = options();
        var entryModule = opts.getEntryModule();
        var outputFile  = opts.getOutputFile();
        var inputFiles  = opts.getInputFiles();

        // Load all input modules
        FileStructure bundle = null;
        for (File inputFile : inputFiles) {
            FileStructure struct = new FileStructure(inputFile);
            ModuleStructure module = struct.getModule();

            if (module.getIdentityConstant().getName().equals(entryModule)) {
                // This is the primary — it becomes the FileStructure root
                bundle = struct;
            }
        }

        if (bundle == null) {
            log(FATAL, "Entry module '{}' not found in input files", entryModule);
            return 1;
        }

        // Merge all other modules into the primary's FileStructure
        for (File inputFile : inputFiles) {
            FileStructure struct = new FileStructure(inputFile);
            ModuleStructure module = struct.getModule();
            if (!module.getIdentityConstant().getName().equals(entryModule)) {
                bundle.merge(module, true, false);
            }
        }

        // Write the bundled output
        bundle.writeTo(outputFile);
        log(INFO, "Bundled {} modules into {}", inputFiles.size(), outputFile);
        return 0;
    }
}
```

### 10.4 `xtc archive` — Produce a `.xar` File

```
xtc archive -o <output.xar> [-e <entry-module>...] [-g <group>] [-n <name>]
            [-v <version>] [-r <resource-dir>] <input.xtc>...

Options:
  -o <file>      Output .xar file (required)
  -e <module>    Entry point module name (repeatable for multiple entry points)
  -g <group>     Artifact group ID (e.g., "platform.xqiz.it")
  -n <name>      Artifact name (e.g., "platform")
  -v <version>   Artifact version (e.g., "0.1.0")
  -r <dir>       Directory of resources to include in resources/
  -L <path>      Library path for determining external dependencies
  <input.xtc>    One or more .xtc files to include in the archive
```

**Examples:**
```bash
# Compile first
xtc build -o build/lib src/main/x/*.x

# Archive into a .xar for distribution
xtc archive -o platform-0.1.0.xar \
    -e kernel \
    -g platform.xqiz.it -n platform -v 0.1.0 \
    -r src/main/resources \
    build/lib/*.xtc

# Use the archive
xtc run platform-0.1.0.xar              # auto-detects kernel as entry point
xtc run -L platform-0.1.0.xar kernel    # explicit module selection
```

**Implementation** — an `Archiver` class extending `Launcher<ArchiverOptions>`:

```java
public class Archiver
        extends Launcher<ArchiverOptions> {

    @Override
    protected int run() {
        var opts = options();

        // Build manifest from inputs
        var manifest = new XarManifest.Builder()
            .artifact(opts.getGroup(), opts.getName(), opts.getVersion());

        for (File inputFile : opts.getInputFiles()) {
            FileStructure struct = new FileStructure(inputFile);
            ModuleStructure module = struct.getModule();
            String name = module.getIdentityConstant().getName();

            manifest.addModule(name,
                "modules/" + inputFile.getName(),
                module.getVersion(),
                opts.getEntryModules().contains(name));
        }

        // Determine external dependencies by scanning fingerprints
        for (File inputFile : opts.getInputFiles()) {
            FileStructure struct = new FileStructure(inputFile);
            for (ModuleConstant id : struct.moduleIds()) {
                ModuleStructure mod = struct.getModule(id);
                if (mod != null && mod.isFingerprint()) {
                    String depName = id.getName();
                    if (!manifest.containsModule(depName)) {
                        manifest.addExternalDependency(depName, id.getVersion());
                    }
                }
            }
        }

        // Write the ZIP
        File outputFile = opts.getOutputFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
            // Write manifest
            zos.putNextEntry(new ZipEntry("META-INF/xar.json"));
            zos.write(manifest.build().toJson());
            zos.closeEntry();

            // Write modules
            for (File inputFile : opts.getInputFiles()) {
                zos.putNextEntry(new ZipEntry("modules/" + inputFile.getName()));
                Files.copy(inputFile.toPath(), zos);
                zos.closeEntry();
            }

            // Write resources
            if (opts.getResourceDir() != null) {
                for (File resource : listResources(opts.getResourceDir())) {
                    String path = "resources/" + relativePath(opts.getResourceDir(), resource);
                    zos.putNextEntry(new ZipEntry(path));
                    Files.copy(resource.toPath(), zos);
                    zos.closeEntry();
                }
            }
        }

        log(INFO, "Created archive {} with {} modules", outputFile, opts.getInputFiles().size());
        return 0;
    }
}
```

### 10.5 Command Registration

```java
// Launcher.java — updated COMMANDS table
private static final Map<String, CommandHandler> COMMANDS = Map.of(
    CMD_BUILD,   (args, console, err) -> launch(CompilerOptions.parse(args), console, err),
    CMD_INIT,    (args, console, err) -> launch(InitializerOptions.parse(args), console, err),
    CMD_RUN,     (args, console, err) -> launch(RunnerOptions.parse(args), console, err),
    CMD_TEST,    (args, console, err) -> launch(TestRunnerOptions.parse(args), console, err),
    CMD_DISASS,  (args, console, err) -> launch(DisassemblerOptions.parse(args), console, err),
    CMD_BUNDLE,  (args, console, err) -> launch(BundlerOptions.parse(args), console, err),   // NEW
    CMD_ARCHIVE, (args, console, err) -> launch(ArchiverOptions.parse(args), console, err)   // NEW
);
```

### 10.6 The Full Toolchain

```
Source (.x files)
    │
    ▼  xtc build (xcc)               — compilation
Individual modules (.xtc files)
    │
    ├──▶  xtc bundle   → bundled.xtc  — single merged binary for deployment
    │                                    (FileStructure.merge, shared constant pool)
    │
    └──▶  xtc archive  → name.xar     — ZIP archive for Maven/Gradle distribution
                                         (independent .xtc entries + JSON manifest)
```

The compiler does NOT change. `xtc bundle` and `xtc archive` are pure post-processing
tools that consume compiled `.xtc` files. This matches the universal pattern:

| Ecosystem | Compile | Package | Distribute |
|-----------|---------|---------|------------|
| **Java** | `javac` → `.class` | `jar` → `.jar` | Maven publish |
| **Rust** | `rustc` → `.rlib` | `cargo package` → `.crate` | `cargo publish` |
| **Go** | `go build` → `.o` | `go build` → binary | Module proxy |
| **XTC** | `xtc build` → `.xtc` | `xtc bundle` / `xtc archive` | Gradle publish |

### 10.7 Gradle Integration

Both CLI tools have Gradle task equivalents that call the same underlying logic:

```kotlin
// BundleXtcTask wraps Bundler logic
val bundleXtc by tasks.registering(BundleXtcTask::class) {
    from(configurations.named("xtcModule"))
    entryPoint("kernel")
    outputFile = layout.buildDirectory.file("distributions/platform-bundled.xtc")
}

// XarTask wraps Archiver logic
val xar by tasks.registering(XarTask::class) {
    from(configurations.named("xtcModule"))
    entryPoint("kernel")
    artifactGroup = project.group.toString()
    artifactName = project.name
    artifactVersion = project.version.toString()
    resources {
        from("src/main/resources")
    }
    destinationDirectory = layout.buildDirectory.dir("distributions")
}
```

The CLI tools exist for users without Gradle (or for scripting). The Gradle tasks
exist for build automation. Both produce identical output.

---

## Part 11: Implementation Roadmap

Ordered so that the bundled `.xtc` work (uses existing binary format, CLI tool)
comes first, and `.xar` archive support (new format, Gradle plugin, Maven publishing)
comes last.

### Phase 1: BundledFileRepository (~1-2 days)

**Goal**: Load all modules from a merged `.xtc` file.

The simplest new type — uses the existing `FileStructure` binary format, requires no
new file format. Ideal starting point.

1. Implement `BundledFileRepository` wrapping a single `.xtc` file, exposing all
   non-fingerprint modules via `getModuleNames()` and `loadModule()`
2. Unit tests: merge 2-3 modules into one `FileStructure`, write to file, read back
   via `BundledFileRepository`, verify all modules are accessible

**Files to create:**
- `javatools/src/main/java/org/xvm/asm/BundledFileRepository.java`

**No existing code changes** — pure addition.

### Phase 2: `xtc bundle` CLI Command (~2-3 days)

**Goal**: `xtc bundle -e kernel -o platform-bundled.xtc build/lib/*.xtc` works.

1. Implement `Bundler extends Launcher<BundlerOptions>` — loads `.xtc` files, calls
   `FileStructure.merge()`, writes single output file
2. Implement `BundlerOptions extends LauncherOptions` — CLI argument parsing for
   `-e` (entry point), `-o` (output), and input `.xtc` files
3. Register `CMD_BUNDLE` in the `COMMANDS` table in `Launcher.java`
4. Test with Platform: merge 11 modules into `platform-bundled.xtc`
5. Verify `BundledFileRepository` can serve all modules from the output

**Files to create:**
- `javatools/src/main/java/org/xvm/tool/Bundler.java`

**Files to modify:**
- `javatools/src/main/java/org/xvm/tool/LauncherOptions.java` (add `BundlerOptions`)
- `javatools/src/main/java/org/xvm/tool/Launcher.java` (register command + dispatch)

### Phase 3: Launcher Integration for Bundled Files (~1 day)

**Goal**: `xtc run -L platform-bundled.xtc kernel` works.

1. Update `Launcher.configureLibraryRepo()` to use `BundledFileRepository` for `.xtc`
   files containing multiple real modules (or always — it's a superset of
   `FileRepository`'s read path)
2. Integration test: compile Platform, bundle, run via `xtc run`

**Files to modify:**
- `javatools/src/main/java/org/xvm/tool/Launcher.java` (dispatch logic)

### Phase 4: Gradle `BundleXtcTask` (~1-2 days)

**Goal**: `./gradlew bundleXtc` produces a bundled `.xtc` in the Gradle build.

1. Create `BundleXtcTask` that invokes `Bundler` logic (or calls it via the
   `DirectStrategy` launcher pattern already used for compilation)
2. Wire into the XTC plugin with `from(configurations.xtcModule)` and `entryPoint()`
3. Test with Platform project

**Files to create:**
- `plugin/src/main/java/org/xtclang/plugin/tasks/BundleXtcTask.java`

### Phase 5: ArtifactRepository Decorator (~0.5 days)

**Goal**: Any `ModuleRepository` can be tagged with Maven/Gradle coordinates.

1. Implement `ArtifactRepository` wrapping any `ModuleRepository` with
   group:name:version identity
2. Unit tests

**Files to create:**
- `javatools/src/main/java/org/xvm/asm/ArtifactRepository.java`

**No existing code changes** — pure addition.

### Phase 6: XarManifest + XarRepository (~2-3 days)

**Goal**: Read `.xar` files and use them as module repositories.

1. Define `XarManifest` class with JSON parsing
2. Implement `XarRepository` implementing `ModuleRepository` directly
3. Unit tests: create a `.xar` in memory, read modules back, verify manifest parsing

**Files to create:**
- `javatools/src/main/java/org/xvm/asm/XarRepository.java`
- `javatools/src/main/java/org/xvm/asm/XarManifest.java`

**No existing code changes** — pure additions.

### Phase 7: `xtc archive` CLI Command (~2-3 days)

**Goal**: `xtc archive -e kernel -g platform.xqiz.it -n platform -v 0.1.0 -o platform.xar build/lib/*.xtc` works.

1. Implement `Archiver extends Launcher<ArchiverOptions>` — reads `.xtc` files,
   generates `xar.json` manifest, writes ZIP archive
2. Implement `ArchiverOptions extends LauncherOptions` — CLI argument parsing for
   `-e` (entry points), `-g` (group), `-n` (name), `-v` (version), `-r` (resources),
   `-o` (output), and input `.xtc` files
3. Register `CMD_ARCHIVE` in the `COMMANDS` table in `Launcher.java`
4. Test: produce `.xar` from Platform modules, verify `XarRepository` can read it

**Files to create:**
- `javatools/src/main/java/org/xvm/tool/Archiver.java`

**Files to modify:**
- `javatools/src/main/java/org/xvm/tool/LauncherOptions.java` (add `ArchiverOptions`)
- `javatools/src/main/java/org/xvm/tool/Launcher.java` (register command + dispatch)

### Phase 8: Launcher Integration for `.xar` (~0.5 days)

**Goal**: `xtc run -L platform-0.1.0.xar kernel` and `xtc run platform-0.1.0.xar` work.

1. Add `.xar` dispatch to `Launcher.configureLibraryRepo()`
2. Add entry point auto-detection from manifest for `xtc run artifact.xar`

**Files to modify:**
- `javatools/src/main/java/org/xvm/tool/Launcher.java` (one `else if`)
- `javatools/src/main/java/org/xvm/tool/Runner.java` (entry point detection)

### Phase 9: Gradle `XarTask` Plugin (~3-5 days)

**Goal**: `./gradlew xar` produces a `.xar` artifact.

1. Create `XarTask` that invokes `Archiver` logic (or extends Gradle's `Zip`)
2. Generate `xar.json` manifest from compilation outputs and `xtcModule` configuration
3. Register artifact for Gradle publishing
4. Test with the Platform project

**Files to create:**
- `plugin/src/main/java/org/xtclang/plugin/tasks/XarTask.java`

**Files to modify:**
- `plugin/src/main/java/org/xtclang/plugin/XtcProjectDelegate.java` (register xar task)

### Phase 10: `.xar` Dependency Consumption (~2-3 days)

**Goal**: `xtcModule("group:name:version")` resolves `.xar` artifacts.

1. Register `.xar` as a known artifact type in the XTC plugin
2. Configure Gradle's artifact transform to handle `.xar` → module repository mapping
3. Test end-to-end: publish Platform `.xar`, consume in another project

### Phase 11: `.xar` Resource Support (~2 days)

**Goal**: Ecstasy code can access resources bundled in the `.xar`.

1. Implement `loadResource()` in `XarRepository`
2. Bridge to the runtime's `FileStore` injection mechanism
3. Test: bundle `cfg.json` in Platform `.xar`, access from Ecstasy code

### Phase 12: XDK as `.xar` (~2-3 days)

**Goal**: XDK libraries published as `org.xtclang:xdk-libs:0.4.4.xar`.

1. Add `xar` task to XDK build
2. Include all 19+ library `.xtc` modules
3. Publish alongside existing ZIP distribution
4. Test: downstream project uses `.xar` instead of extracted XDK

---

## Part 12: Design Decisions & Trade-offs

### 12.1 Why Not Reuse FileStructure.merge()?

`FileStructure.merge()` can combine multiple modules into one `.xtc` file (used in
`NativeContainer` to merge ecstasy + turtle + native). However:

- Merged modules share a single constant pool, which couples them tightly
- Merged modules cannot be loaded individually — it's all or nothing
- The merge is designed for runtime linking, not distribution
- No metadata (artifact version, entry points, resources)

The `.xar` format keeps modules as independent `.xtc` entries that can be loaded
individually, while adding artifact-level metadata.

### 12.2 Why Not Just Use a ZIP of `.xtc` Files?

A plain ZIP would work but lacks:
- Self-describing metadata (which module is the entry point?)
- External dependency declarations (what else does this archive need?)
- Artifact identity (what are the Maven coordinates?)
- Convention enforcement (where are modules vs. resources?)

The manifest adds ~1KB of overhead and makes the format self-documenting.

### 12.3 Why Not a Custom Binary Format?

A custom binary format would be more compact and faster to parse, but:
- Harder to debug (can't `unzip -l` to see contents)
- Requires custom tooling for every operation
- No benefit — ZIP overhead is negligible for module-sized files
- Would need its own compression (ZIP provides this for free)

### 12.4 Thread Safety

`XarRepository` uses `ConcurrentHashMap` for its module cache, making it safe for
concurrent access. This is important for:
- LSP servers handling multiple requests
- Runtime containers loading modules from multiple threads
- Gradle workers accessing the same repository

---

## Appendix A: Full Class Diagram

```
ModuleRepository (interface)
    │
    │   Existing — UNCHANGED:
    ├── FileRepository             -- Single .xtc file on disk
    │       ONE module (primary only), writable, change-detecting
    │
    ├── DirRepository              -- Directory of .xtc files
    │       multiple modules, writable, filesystem-scanning, change-detecting
    │
    ├── LinkedRepository           -- Chain of repos, first-match-wins
    │       composite, delegates to child repos, readThrough caching
    │
    ├── BuildRepository            -- In-memory Map<String, ModuleStructure>
    │       multiple modules, writable, no persistence
    │
    ├── InstantRepository          -- Single ModuleStructure wrapper
    │       one module, read-only, no file backing
    │
    ├── ModuleInfoRepository       -- Build tool bridge
    │       delegates to FileRepository instances
    │
    │   New:
    ├── BundledFileRepository      -- Single .xtc file with merged modules
    │       ALL modules (non-fingerprint), read-only, shared constant pool
    │       complement to FileRepository: same file, exposes everything
    │
    ├── XarRepository              -- .xar archive (ZIP of .xtc files + manifest)
    │       multiple modules, read-only, manifest-driven, immutable
    │       has-a XarManifest (entry points, external deps, resources)
    │
    └── ArtifactRepository         -- Decorator: group:name:version identity
            wraps any ModuleRepository
            bridges Maven/Gradle coordinates to XTC module system
```

## Appendix B: Example `.xar` for Platform

```
platform-0.1.0.xar (estimated ~2MB)
│
├── META-INF/
│   └── xar.json
│       {
│         "formatVersion": 1,
│         "artifact": {
│           "group": "platform.xqiz.it",
│           "name": "platform",
│           "version": "0.1.0"
│         },
│         "modules": {
│           "auth":        { "path": "modules/auth.xtc",        "entryPoint": false },
│           "challenge":   { "path": "modules/challenge.xtc",   "entryPoint": false },
│           "common":      { "path": "modules/common.xtc",      "entryPoint": false },
│           "githubCLI":   { "path": "modules/githubCLI.xtc",   "entryPoint": false },
│           "host":        { "path": "modules/host.xtc",         "entryPoint": false },
│           "kernel":      { "path": "modules/kernel.xtc",       "entryPoint": true,
│                            "entryMethod": "run" },
│           "platformCLI": { "path": "modules/platformCLI.xtc", "entryPoint": false },
│           "platformDB":  { "path": "modules/platformDB.xtc",  "entryPoint": false },
│           "platformUI":  { "path": "modules/platformUI.xtc",  "entryPoint": false },
│           "proxy":       { "path": "modules/proxy.xtc",        "entryPoint": false },
│           "stub":        { "path": "modules/stub.xtc",         "entryPoint": false }
│         },
│         "externalDependencies": [
│           { "module": "ecstasy.xtclang.org", "minVersion": "0.4.4" },
│           { "module": "web.xtclang.org",     "minVersion": "0.4.4" },
│           { "module": "json.xtclang.org",    "minVersion": "0.4.4" },
│           { "module": "crypto.xtclang.org",  "minVersion": "0.4.4" },
│           { "module": "net.xtclang.org",     "minVersion": "0.4.4" }
│         ],
│         "resources": [ "resources/cfg.json" ]
│       }
│
├── modules/
│   ├── auth.xtc
│   ├── challenge.xtc
│   ├── common.xtc
│   ├── githubCLI.xtc
│   ├── host.xtc
│   ├── kernel.xtc
│   ├── platformCLI.xtc
│   ├── platformDB.xtc
│   ├── platformUI.xtc
│   ├── proxy.xtc
│   └── stub.xtc
│
└── resources/
    └── cfg.json
```

## Appendix C: Comparison with Other Ecosystems

| Ecosystem | Single Module | Multi-Module Archive | Manifest |
|-----------|--------------|---------------------|----------|
| **Java** | `.class` | `.jar` (ZIP) | `MANIFEST.MF` |
| **Python** | `.py` | `.whl` (ZIP) | `METADATA` |
| **Rust** | `.rlib` | `.crate` (tar.gz) | `Cargo.toml` |
| **Node.js** | `.js` | `.tgz` (tar.gz) | `package.json` |
| **XTC** | `.xtc` | `.xar` (ZIP) | `xar.json` |

The pattern is universal: single-file module format + archive format + metadata manifest.
XTC's `.xar` follows established convention.

## Appendix D: Open Questions

1. **Should `.xar` include transitive dependencies?** The current proposal only includes
   the artifact's own modules. Transitive deps (XDK modules, etc.) are listed as
   `externalDependencies`. An alternative "fat archive" approach would embed everything.
   **Recommendation**: Keep it lean. Let Gradle handle transitive resolution.

2. **Should the manifest include a dependency graph within the archive?** Currently
   `dependencies` is listed per module. For small archives (< 20 modules) this is fine.
   For larger archives, a separate dependency section might be cleaner.
   **Recommendation**: Keep per-module dependencies for now. Revisit if archives grow large.

3. **Compression**: Should individual `.xtc` entries be compressed (DEFLATED) or stored
   uncompressed (STORED) within the ZIP? `.xtc` files are binary but not pre-compressed.
   **Recommendation**: DEFLATED. The `.xtc` binary format compresses well (~40-60% reduction).

4. **Signing**: Should `.xar` files support digital signatures?
   **Recommendation**: Not in v1. Can be added later via a `META-INF/signatures.json`
   entry, following the JAR signing pattern.

5. **How does this relate to the Kotlin compiler plan?** The Kotlin compiler plan in
   `plan-repositories.md` proposes a `ModuleRepository` interface for the Kotlin side.
   The `.xar` format would be consumed by both Java and Kotlin implementations — the
   format is implementation-agnostic.

---

## Task List

### Bundled `.xtc` — Repository + CLI + Gradle (Phases 1-4)

- [ ] Implement `BundledFileRepository` — `ModuleRepository` wrapping a single `.xtc`
      file, exposing ALL non-fingerprint modules (not just the primary).
      File: `javatools/src/main/java/org/xvm/asm/BundledFileRepository.java`

- [ ] Unit tests for `BundledFileRepository` — merge 2-3 modules via `FileStructure.merge()`,
      write to file, read back, verify all modules accessible by name.

- [ ] Implement `Bundler extends Launcher<BundlerOptions>` — the `xtc bundle` command.
      Loads `.xtc` files, calls `FileStructure.merge()`, writes single output.
      File: `javatools/src/main/java/org/xvm/tool/Bundler.java`

- [ ] Implement `BundlerOptions extends LauncherOptions` — CLI parsing for `-e` (entry
      point), `-o` (output file), `-L` (library path), and input `.xtc` files.
      File: `javatools/src/main/java/org/xvm/tool/LauncherOptions.java`

- [ ] Register `CMD_BUNDLE` in `Launcher.COMMANDS` table.
      File: `javatools/src/main/java/org/xvm/tool/Launcher.java`

- [ ] Test `xtc bundle` with Platform — merge 11 modules into `platform-bundled.xtc`,
      verify all modules accessible via `BundledFileRepository`.

- [ ] Launcher dispatch for bundled `.xtc` — update `Launcher.configureLibraryRepo()`
      to use `BundledFileRepository` for `.xtc` files with multiple real modules.
      File: `javatools/src/main/java/org/xvm/tool/Launcher.java`

- [ ] Integration test — `xtc run -L platform-bundled.xtc kernel` runs the Platform.

- [ ] Implement Gradle `BundleXtcTask` — invokes `Bundler` logic from the build.
      File: `plugin/src/main/java/org/xtclang/plugin/tasks/BundleXtcTask.java`

- [ ] Test `./gradlew bundleXtc` with Platform project.

### Shared Infrastructure (Phase 5)

- [ ] Implement `ArtifactRepository` — decorator wrapping any `ModuleRepository` with
      group:name:version identity.
      File: `javatools/src/main/java/org/xvm/asm/ArtifactRepository.java`

### `.xar` Archive — Repository + CLI + Gradle (Phases 6-12)

- [ ] Define `.xar` manifest schema — finalize the `META-INF/xar.json` format
      (formatVersion, artifact identity, module descriptors, external deps, resources).

- [ ] Implement `XarManifest` — JSON parser/writer for the manifest.
      File: `javatools/src/main/java/org/xvm/asm/XarManifest.java`

- [ ] Implement `XarRepository` — `ModuleRepository` backed by a `.xar` ZIP file.
      Lazy loading, `ConcurrentHashMap` cache, `ZipFile` random access.
      File: `javatools/src/main/java/org/xvm/asm/XarRepository.java`

- [ ] Unit tests for `XarRepository` — create `.xar` in memory, read modules back,
      verify manifest parsing, test entry point detection.

- [ ] Implement `Archiver extends Launcher<ArchiverOptions>` — the `xtc archive` command.
      Reads `.xtc` files, generates manifest, writes ZIP archive.
      File: `javatools/src/main/java/org/xvm/tool/Archiver.java`

- [ ] Implement `ArchiverOptions extends LauncherOptions` — CLI parsing for `-e` (entry
      points), `-g` (group), `-n` (name), `-v` (version), `-r` (resources dir),
      `-o` (output file), and input `.xtc` files.
      File: `javatools/src/main/java/org/xvm/tool/LauncherOptions.java`

- [ ] Register `CMD_ARCHIVE` in `Launcher.COMMANDS` table.
      File: `javatools/src/main/java/org/xvm/tool/Launcher.java`

- [ ] Test `xtc archive` with Platform — produce `platform-0.1.0.xar`, verify
      `XarRepository` reads it correctly.

- [ ] Launcher dispatch for `.xar` — add `.xar` case to `Launcher.configureLibraryRepo()`.
      File: `javatools/src/main/java/org/xvm/tool/Launcher.java`

- [ ] Entry point auto-detection — `xtc run platform.xar` reads manifest, finds single
      entry point, runs it. Multiple entry points -> error with guidance.
      File: `javatools/src/main/java/org/xvm/tool/Runner.java`

- [ ] Integration test — `xtc run -L platform-0.1.0.xar kernel` runs the Platform.

- [ ] Implement Gradle `XarTask` — produces `.xar` from `xtcModule` configuration,
      generates manifest, registers as publishable artifact.
      File: `plugin/src/main/java/org/xtclang/plugin/tasks/XarTask.java`

- [ ] Register `.xar` artifact type in XTC Gradle plugin so downstream projects can
      declare `xtcModule("group:name:version")` and resolve `.xar` files.
      File: `plugin/src/main/java/org/xtclang/plugin/XtcProjectDelegate.java`

- [ ] Gradle artifact transform — handle `.xar` -> module resolution in the build.

- [ ] Test with Platform — `./gradlew xar` produces `platform-0.1.0.xar`, publish
      to local Maven, consume from another project, verify round-trip.

- [ ] `XarRepository.loadResource()` — read non-module entries from the ZIP.

- [ ] Runtime `FileStore` bridge — back Ecstasy `@Inject FileStore` with `.xar`
      resource entries.

- [ ] Test resources — bundle `cfg.json` in Platform `.xar`, access from Ecstasy code.

- [ ] XDK `.xar` task — add `xar` task to XDK build producing `xdk-libs-0.4.4.xar`
      with all 19+ library modules.

- [ ] Publish XDK `.xar` alongside existing ZIP distribution.

- [ ] End-to-end test — downstream project depends on `org.xtclang:xdk-libs:0.4.4`,
      resolves `.xar`, compiles and runs.
