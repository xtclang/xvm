# Multi-Module Bundled .xtc Support

## Overview

A **bundle** is a single `.xtc` file containing multiple XTC modules with a shared
constant pool. The XTC `FileStructure` already supports this internally (the runtime
uses `merge()` to combine `ecstasy`, `mack`, and `_native` at startup). Bundling
exposes this capability as a first-class tool for distribution and deployment.

## Why Bundle?

### The Problem Today

The XDK ships 22 individual `.xtc` files across two directories:

```
xdk/
  lib/               # 20 standard library modules
    ecstasy.xtc
    json.xtc
    web.xtc
    collections.xtc
    ...
  javatools/         # 2 native bridge modules + JARs
    javatools_turtle.xtc
    javatools_bridge.xtc
    javatools.jar
```

User projects like `platform` (11 modules) compile to another directory of loose files.
This creates complexity at multiple levels:

- **Distribution**: ship a directory tree instead of a single artifact
- **Module path**: `xec -L /path/to/xdk/lib -L /path/to/platform/lib kernel.xtc`
- **Gradle plugin**: `ModulePathResolver` (165 lines) combines XDK + dependencies + outputs
- **Integrity**: a directory of files can't be checksummed atomically
- **No standard artifact format**: Java has `.jar`, XTC has... a directory

### Why Individual .xtc Files Exist

There's no fundamental reason. The compiler produces one `.xtc` per module, and the
distribution just ships them as-is. This was the path of least resistance, not a
deliberate design choice.

### What Changes with Bundling

The runtime already resolves modules by name through the `ModuleRepository` interface.
`NativeContainer.loadNativeTemplates()` calls:

```java
f_repository.loadModule("ecstasy.xtclang.org")
f_repository.loadModule("mack.xtclang.org")
f_repository.loadModule("_native.xtclang.org")
```

It doesn't care whether these come from separate files or a single bundle.
`BundledFileRepository` exposes each module by name from a multi-module `.xtc`,
so the runtime works identically. The change is transparent.

## Architecture

### Module Resolution Chain

When a user runs `xec -L xdk.xtc myapp.xtc`, the runtime builds this repository chain:

```
LinkedRepository (readThrough=true)
  repos[0]: BuildRepository           ← writable cache (in-memory)
  repos[1]: BundledFileRepository      ← xdk.xtc (exposes all 22 modules by name)
  repos[2]: BundledFileRepository      ← myapp.xtc (single module, backward-compatible)
```

`LinkedRepository` searches sequentially. First hit wins, with read-through caching
to `BuildRepository` for subsequent lookups.

### Bootstrap Flow (Unchanged)

`NativeContainer` bootstrap works identically with bundled or individual files:

1. Load `ecstasy.xtclang.org` from repository (found in `xdk.xtc` bundle)
2. Load `mack.xtclang.org` from repository (found in same bundle)
3. Load `_native.xtclang.org` from repository (found in same bundle)
4. Create merged `FileStructure`, link, register native templates

The three modules are loaded by name regardless of physical file layout.

### Bundler Algorithm

```
  module1.xtc ─┐                     ┌──────────────────┐
  module2.xtc ─┤──► xtc bundle ───► │  bundle.xtc      │
  module3.xtc ─┘                     │  ├─ module1 (P)  │
                                      │  ├─ module2 (E)  │
                                      │  ├─ module3 (E)  │
                                      │  └─ shared pool  │
                                      └──────────────────┘
                                       P = Primary, E = Embedded
```

Steps (`Bundler.java`):

1. **Load** each input `.xtc` and extract its primary `ModuleStructure`
2. **Select primary** — first input by default, or `--primary` override
3. **Create** `FileStructure` from the primary module
4. **Merge** remaining modules via `FileStructure.merge()`
5. **Reclassify** merged modules from `Primary` to `Embedded`
6. **Resolve fingerprints** — remove fingerprints satisfied within the bundle
7. **Write** to output file
8. **Report** module count

## Tradeoffs: Bundling vs. Individual Modules

### Downsides of Bundling (Minor)

- **No granular patching**: can't ship a `web.xtc` hotfix without rebuilding the bundle.
  In practice the XDK is versioned as a unit, so this doesn't matter.
- **Memory at parse time**: loading the bundle parses all modules into `FileStructure`,
  even if you only need one. But the runtime already loads and merges most modules during
  linking, so the overhead is negligible.
- **Debugging**: individual files are easier to inspect. Mitigated by `xtc disass`.

### Upsides of Bundling (Significant)

- **One file instead of 22**: simpler distribution, simpler module path.
- **Shared constant pool**: `FileStructure.merge()` deduplicates constants. Twenty modules
  referencing `ecstasy.xtclang.org` types share one pool. Smaller on disk, faster to load.
- **No module path configuration**: `xec -L xdk.xtc myapp.xtc` instead of
  `xec -L /path/to/xdk/lib myapp.xtc`. The Gradle plugin's `ModulePathResolver` simplifies.
- **Atomic integrity**: one file, one checksum. No risk of mismatched module versions.
- **Turtle/bridge simplification**: today the XDK separates `javatools/*.xtc` from `lib/*.xtc`.
  In a bundle, `NativeContainer` still calls `loadModule("mack.xtclang.org")` and gets it
  from the same bundle. No more two-directory layout.

## Phased Rollout Plan

### Phase 1: Bundle the XDK (Highest Value, Zero User Impact)

Ship `xdk.xtc` containing all 22 standard library modules (including turtle and bridge)
instead of a `lib/` directory of individual files.

**XDK distribution layout changes from:**
```
xdk/
  lib/              (20 .xtc files)
  javatools/        (2 .xtc files + JARs)
  bin/
```
**to:**
```
xdk/
  lib/xdk.xtc       (1 file, all 22 modules)
  javatools/         (just JARs: javatools.jar, javatools-jitbridge.jar)
  bin/
```

Every user benefits immediately: simpler module path, simpler distribution, no
`DirRepository` scanning at startup.

**Implementation**: add a bundle step to `xdk/build.gradle.kts` after compilation.
Individual modules are still compiled separately (for incremental builds), then bundled
as a packaging step.

### Phase 2: User Project Bundling (Plugin Support)

For multi-module projects like `platform` (11 modules), the root build produces a single
`platform.xtc` for deployment. Toggle via project property:

```bash
./gradlew installDist -Pbundle=true    # produces platform.xtc
./gradlew installDist                  # produces individual .xtc files (default)
```

In `build.gradle.kts`:

```kotlin
val bundleModules = findProperty("bundle")?.toString()?.toBoolean() ?: false

val installDist by tasks.registering {
    dependsOn(tasks.build)
    val xtcModules = configurations.xtcModule
    val outputDir  = layout.buildDirectory.dir("install/platform/lib")
    val bundleFile = layout.buildDirectory.file("install/platform/lib/platform.xtc")

    inputs.files(xtcModules)
    outputs.dir(outputDir)

    doLast {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        if (bundleModules) {
            // Bundle all modules into platform.xtc
            val inputFiles = xtcModules.files.filter { it.name.endsWith(".xtc") }
            val args = mutableListOf("-o", bundleFile.get().asFile.absolutePath,
                                     "--primary", "kernel.xtclang.org")
            inputFiles.forEach { args.add(it.absolutePath) }
            val options = org.xvm.tool.LauncherOptions.BundlerOptions.parse(args.toTypedArray())
            val exitCode = org.xvm.tool.Launcher.launch(options, null, null)
            if (exitCode != 0) throw GradleException("Bundle failed: $exitCode")
        } else {
            xtcModules.files.forEach { it.copyTo(java.io.File(dir, it.name), overwrite = true) }
        }
    }
}
```

During development, individual files are the default (easier to iterate, better error
messages). For deployment, `bundle=true` produces one file.

### Phase 3: Fat Bundles (Future)

Bundle XDK + application modules into a single self-contained `.xtc`:

```bash
xec platform.xtc    # no -L flag, everything is self-contained
```

Like a fat JAR. Useful for deployment, not for development. Requires the XDK bundle
from Phase 1 as input plus all application modules.

### Phase 4: Plugin-Level DSL (Future)

When bundling is proven and multiple projects use it, promote to the Gradle plugin:

```kotlin
xtc {
    bundle {
        enabled.set(true)
        outputFile.set("platform.xtc")
        primaryModule.set("kernel.xtclang.org")
        includeXdk.set(false)                    // Phase 3: fat bundle
    }
}
```

This registers an `XtcBundleTask` automatically, wired into the `assemble` lifecycle.
Defer this until the ad-hoc approach is validated.

## CLI Usage

```bash
# Bundle two modules
xtc bundle -o bundle.xtc module1.xtc module2.xtc

# Bundle the entire XDK library directory
xtc bundle -o xdk.xtc ecstasy.xtc crypto.xtc net.xtc web.xtc json.xtc ...

# Override which module is the primary (first input is default)
xtc bundle -o platform.xtc --primary kernel.xtclang.org kernel.xtc web.xtc ...

# Verbose output
xtc bundle -v -o bundle.xtc *.xtc
```

### Options

| Option | Description |
|--------|-------------|
| `-o <file>` | Output `.xtc` file (required) |
| `--primary <module>` | Fully qualified module name to use as primary (defaults to first input) |
| `-L <path>` | Module path (same as other commands) |
| `-v` / `--verbose` | Enable verbose output |
| `-h` / `--help` | Display help |

### Using a Bundle as a Library

```bash
# Run with a bundled XDK
xec -L xdk.xtc myapp.xtc

# Compile against a bundled XDK
xcc -L xdk.xtc MyApp.x

# Test with a bundled XDK
xtc test -L xdk.xtc mytest.xtc
```

When `-L` points to a `.xtc` file, the launcher uses `BundledFileRepository` instead of
`FileRepository`. This transparently exposes all non-fingerprint modules in the file.
Single-module `.xtc` files work identically to before (fully backward-compatible).

## Components

### New Files

| File | Purpose |
|------|---------|
| `javatools/.../asm/BundledFileRepository.java` | Read-only `ModuleRepository` exposing all non-fingerprint modules from a single `.xtc` |
| `javatools/.../tool/Bundler.java` | `xtc bundle` command implementation |
| `javatools/.../tool/BundlerTest.java` | Comprehensive tests |

### Modified Files

| File | Change |
|------|--------|
| `javatools/.../asm/ModuleStructure.java` | Added `setModuleType()`, relaxed `isEmbeddedModule()` assertion |
| `javatools/.../tool/LauncherOptions.java` | Added `BundlerOptions` schema and inner class |
| `javatools/.../tool/Launcher.java` | Registered `bundle` command, switched `configureLibraryRepo()` to `BundledFileRepository` |

## Design Decisions

### Compilation Always Produces Individual Modules

The compiler should always produce one `.xtc` per module. Bundling at compile time would
break incremental compilation, break subproject isolation in Gradle, and complicate the
compiler for no benefit. Bundling is **distribution packaging** — like `jar` in Java.
The compiler produces `.class` files, then `jar` packages them.

### Why `setModuleType()` Instead of Immutable Builders?

The `ModuleStructure` API is deeply mutable (`setVersion()`, `setTimestamp()`,
`setSourceDir()`, `fingerprintRequired()`, etc.). `FileStructure.merge()` itself mutates
the target. Adding `setModuleType()` follows the existing pattern. An immutable builder
refactor would touch the entire ASM layer and is orthogonal to bundling.

### Why `BundledFileRepository` Instead of Extending `FileRepository`?

`FileRepository` assumes single-module files: `name` is a `String`, `checkCache()` returns
one module, `storeModule()` writes to the file. `BundledFileRepository` starts clean with
`Map<String, ModuleStructure>` while reusing the same caching pattern.

### Fingerprint Resolution

When modules A and B are bundled, and A has a fingerprint for B, that fingerprint is
removed — the dependency is satisfied internally. Fingerprints for modules *not* in
the bundle are preserved.

## Technical Debt

### `ModuleRepository.loadModule()` Error Handling

The `ModuleRepository.loadModule()` interface returns `null` for both "module doesn't
exist" and "I/O error reading it." This conflates absence with failure — a pervasive
antipattern across the codebase. Callers cannot distinguish between a missing module
(expected, recoverable) and a corrupted file or disk error (unexpected, should fail loud).

**Current state**: `DirRepository.loadModule()` wraps the internal `IOException` as
`UncheckedIOException` to at least propagate real errors with stack traces, but this is
a band-aid. The same null/absent/error conflation exists in `FileRepository`,
`LinkedRepository`, `BundledFileRepository`, and every caller.

**Proper fix**: Change `ModuleRepository.loadModule()` to throw `IOException` for I/O
failures and return `null` only for genuinely absent modules. Impact: 7 implementations,
~25 call sites. Not catastrophic, but a separate refactor from bundling. Many of these
callers already have error handling paths — they just can't reach them because errors
are silently swallowed as nulls.

This should be addressed as a follow-up to establish a clean error model for the
repository layer. Stop misusing RuntimeExceptions as a substitute for proper checked
exception propagation.

## Verification

```bash
# Build
./gradlew javatools:jar

# Run bundler tests
./gradlew javatools:test --tests "org.xvm.tool.BundlerTest"

# Manual test (after ./gradlew installDist)
cd xdk/build/install/xdk
bin/xtc bundle -v -o /tmp/xdk-bundle.xtc lib/ecstasy.xtc lib/json.xtc lib/collections.xtc

# Verify bundle contents
bin/xtc disass /tmp/xdk-bundle.xtc

# Use the bundle as a library
bin/xec -L /tmp/xdk-bundle.xtc myapp.xtc
```
