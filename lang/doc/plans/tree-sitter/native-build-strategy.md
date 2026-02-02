# Native Library Build Strategy

**Status**: IMPLEMENTED

**Goal**: Clean up the native library build situation - remove checked-in binaries, integrate
properly with Gradle lifecycle, and support conditional tree-sitter inclusion.

---

## Current Problems

1. **Binaries in source control** - ~6MB of `.dylib/.so/.dll` files committed
2. **Staleness check is hacky** - Build fails if hash doesn't match, manual rebuild required
3. **No conditional inclusion** - Tree-sitter always built even when `lsp.adapter=mock`
4. **Property change detection** - Switching adapters doesn't automatically trigger rebuild

---

## Solution Analysis

### Option A: Full Gradle Lifecycle Integration (Recommended)

**Approach**: Build native libraries on-demand as part of normal Gradle build, with proper
caching and incremental support.

```
┌─────────────────────────────────────────────────────────────────┐
│                    Gradle Build Graph                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  When lsp.adapter=treesitter:                                   │
│                                                                 │
│  generateTreeSitter ──► downloadZig ──► buildNativeLibrary     │
│         │                                      │                │
│         ▼                                      ▼                │
│  generateScannerC ─────────────────► copyNativeLibToResources  │
│                                              │                  │
│                                              ▼                  │
│                                      processResources           │
│                                              │                  │
│                                              ▼                  │
│                                           fatJar                │
│                                                                 │
│  When lsp.adapter=mock:                                         │
│                                                                 │
│  (tree-sitter tasks skipped entirely)                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Implementation**:

```kotlin
// In lsp-server/build.gradle.kts
val lspAdapter = project.findProperty("lsp.adapter")?.toString() ?: "mock"

// Conditional dependency on tree-sitter project
if (lspAdapter == "treesitter") {
    dependencies {
        treeSitterNativeLib(project(":tree-sitter", "nativeLibraryElements"))
    }
}

// Skip native lib copy when not using treesitter
val copyNativeLibToResources by tasks.registering(Copy::class) {
    onlyIf { lspAdapter == "treesitter" }
    // ...
}
```

```kotlin
// In tree-sitter/build.gradle.kts
val buildNativeLibrary by tasks.registering {
    inputs.files(grammarJs, scannerC)
    outputs.file(nativeLibFile)

    doLast {
        // Download Zig if needed (cached in ~/.gradle/caches/zig/)
        // Cross-compile for current platform
    }
}
```

**Pros**:
- Native libraries built automatically when needed
- Full Gradle caching - rebuild only when inputs change
- No binaries in source control
- Clean conditional skip when using mock adapter
- Property changes automatically invalidate cache

**Cons**:
- First build requires Zig download (~45MB) and compile (~30s)
- CI needs to build or cache the native library
- Cross-platform builds still need Zig

**Complexity**: Medium

---

### Option B: Separate Repository with Maven Artifacts

**Approach**: Move tree-sitter grammar to a separate repo that publishes native libraries
as Maven artifacts with platform classifiers.

```
┌─────────────────────────────────────────┐
│     xtc-tree-sitter (separate repo)     │
├─────────────────────────────────────────┤
│  grammar.js                             │
│  scanner.c                              │
│  build.gradle.kts                       │
│                                         │
│  Publishes to Maven Central:            │
│  - org.xtclang:tree-sitter-xtc:1.0.0    │
│    - tree-sitter-xtc-1.0.0-darwin-arm64.dylib
│    - tree-sitter-xtc-1.0.0-darwin-x64.dylib
│    - tree-sitter-xtc-1.0.0-linux-x64.so
│    - tree-sitter-xtc-1.0.0-linux-arm64.so
│    - tree-sitter-xtc-1.0.0-windows-x64.dll
└─────────────────────────────────────────┘
           │
           │ dependency
           ▼
┌─────────────────────────────────────────┐
│           xtc-init-wizard/lang          │
├─────────────────────────────────────────┤
│  dependencies {                         │
│    if (lspAdapter == "treesitter") {    │
│      implementation("org.xtclang:tree-sitter-xtc:1.0.0") {
│        artifact {                       │
│          classifier = currentPlatform() │
│        }                                │
│      }                                  │
│    }                                    │
│  }                                      │
└─────────────────────────────────────────┘
```

**Pros**:
- No binaries in main repo
- Standard Maven dependency resolution
- Version pinning and reproducible builds
- Easy for consumers to use
- CI builds the library once, users download pre-built

**Cons**:
- Separate repo to maintain
- Release process for grammar changes (version bump, publish, update dependency)
- Maven Central publishing setup required
- Tight coupling between grammar and language - changes need coordinated releases
- More moving parts

**Complexity**: High (infrastructure overhead)

---

### Option C: GitHub Releases / CDN Download

**Approach**: Build binaries in CI, publish to GitHub Releases, download on-demand.

```
┌─────────────────────────────────────────┐
│         GitHub Actions CI               │
├─────────────────────────────────────────┤
│  On push to main:                       │
│  1. Build grammar.js + scanner.c        │
│  2. Cross-compile for all 5 platforms   │
│  3. Compute SHA256 of inputs            │
│  4. Upload to GitHub Releases:          │
│     - libtree-sitter-xtc-<hash>.tar.gz │
└─────────────────────────────────────────┘
           │
           │ download on first build
           ▼
┌─────────────────────────────────────────┐
│           Local Build                   │
├─────────────────────────────────────────┤
│  1. Compute hash of grammar.js+scanner.c│
│  2. Check local cache (~/.gradle/caches/│
│     tree-sitter-xtc/<hash>/)           │
│  3. If not cached:                      │
│     - Download from GitHub Releases     │
│     - Extract to cache                  │
│  4. Copy to build output                │
└─────────────────────────────────────────┘
```

**Pros**:
- No binaries in source control
- Users download pre-built (fast)
- CI builds once, everyone benefits
- Version tied to grammar hash (automatic)
- No separate repo needed

**Cons**:
- Requires GitHub Actions setup
- Network dependency on first build
- Release management for pre-built binaries
- Fallback needed if download fails

**Complexity**: Medium

---

### Option D: Local Build with Persistent Cache (Simplest)

**Approach**: Build locally on first use, cache forever until inputs change. No pre-built
binaries anywhere.

```kotlin
// In tree-sitter/build.gradle.kts
val zigCacheDir = gradle.gradleUserHomeDir.resolve("caches/zig")
val nativeLibCacheDir = gradle.gradleUserHomeDir.resolve("caches/tree-sitter-xtc")

val buildNativeLibrary by tasks.registering {
    val inputHash = computeHash(grammarJs, scannerC)
    val cachedLib = nativeLibCacheDir.resolve("$inputHash/$platform/libtree-sitter-xtc.$ext")

    inputs.files(grammarJs, scannerC)
    outputs.file(cachedLib)

    doLast {
        if (!cachedLib.exists()) {
            // Download Zig (cached in zigCacheDir)
            // Build library
            // Store in nativeLibCacheDir
        }
    }
}
```

**Pros**:
- Simplest implementation
- No external dependencies (network optional after first Zig download)
- Pure Gradle, works offline after first build
- No release process
- No binaries anywhere in source control

**Cons**:
- First build for each platform takes ~30-60s
- Zig download required (~45MB one-time)
- Each developer builds their own (not shared across team)
- CI builds from scratch each time (unless cached)

**Complexity**: Low

---

## Recommendation: Option A + D Hybrid

Combine full Gradle lifecycle integration with persistent local caching:

1. **Default to mock adapter** - No tree-sitter build by default
2. **Conditional tree-sitter** - Only build when `lsp.adapter=treesitter`
3. **Local Zig cache** - Download once to `~/.gradle/caches/zig/`
4. **Local native lib cache** - Build once per input hash to `~/.gradle/caches/tree-sitter-xtc/`
5. **Gradle build cache** - Optional remote cache for CI (if configured)
6. **Remove checked-in binaries** - Delete from source control

### Build Flow

```
First build with -Plsp.adapter=treesitter:
┌──────────────────────────────────────────────────────────────┐
│ 1. Check ~/.gradle/caches/tree-sitter-xtc/<hash>/           │
│    └─ Not found (first build)                               │
│                                                              │
│ 2. Check ~/.gradle/caches/zig/                              │
│    └─ Not found → Download Zig (~45MB, ~10s)                │
│                                                              │
│ 3. Compile grammar.js + scanner.c → native library (~20s)   │
│                                                              │
│ 4. Store in ~/.gradle/caches/tree-sitter-xtc/<hash>/        │
│                                                              │
│ 5. Copy to build/generated/resources/native/                │
└──────────────────────────────────────────────────────────────┘

Subsequent builds (same grammar):
┌──────────────────────────────────────────────────────────────┐
│ 1. Check ~/.gradle/caches/tree-sitter-xtc/<hash>/           │
│    └─ Found! Skip build                                     │
│                                                              │
│ 2. Copy to build/generated/resources/native/ (instant)      │
└──────────────────────────────────────────────────────────────┘

Build with -Plsp.adapter=mock (default):
┌──────────────────────────────────────────────────────────────┐
│ (tree-sitter tasks skipped entirely - no native lib needed) │
└──────────────────────────────────────────────────────────────┘
```

### Property Change Detection

```kotlin
// generateBuildInfo already handles this
val generateBuildInfo by tasks.registering {
    inputs.property("adapter", lspAdapter)  // ← Invalidates when adapter changes
    // ...
}

// Native lib task only runs when treesitter adapter
val copyNativeLibToResources by tasks.registering(Copy::class) {
    onlyIf { lspAdapter == "treesitter" }
    // ...
}
```

---

## Implementation Tasks

### Phase 1: Conditional Tree-sitter

1. [ ] Make `lsp.adapter=mock` the default (not done - treesitter is default)
2. [ ] Add `onlyIf { lspAdapter == "treesitter" }` to native lib tasks (not done)
3. [ ] Skip tree-sitter dependency when using mock adapter (not done)
4. [ ] Test: `./gradlew :lang:lsp-server:fatJar` should not trigger tree-sitter at all

### Phase 2: Persistent Local Cache ✅

1. [x] Add Zig caching to `~/.gradle/caches/zig/<version>/`
2. [x] Add native lib caching to `~/.gradle/caches/tree-sitter-xtc/<hash>/<platform>/`
3. [x] Implement cache lookup before building
4. [x] Remove staleness check (replaced by Gradle up-to-date checks)
5. [x] Cross-compile ALL platforms using Zig (not just current platform)

### Phase 3: Remove Checked-in Binaries ✅

1. [x] Delete `tree-sitter/src/main/resources/native/*/libtree-sitter-xtc.*`
2. [x] Delete `.inputs.sha256` and `.version` files
3. [x] Update `.gitignore` to remove native lib exceptions
4. [x] Zig version moved to `libs.versions.toml`

### Phase 4: CI Integration (Optional)

1. [ ] Configure Gradle build cache for CI
2. [ ] Or: Pre-warm cache in CI before running tests
3. [ ] Document CI setup for maintainers

---

## Alternatives Considered

### Why not separate repo?

- Grammar is tightly coupled to XTC language definition
- Changes often need to be atomic (grammar + LSP adapter)
- Overhead of managing two repos not worth it for single consumer
- Consider only if tree-sitter is reused by other projects (VS Code, etc.)

### Why not GitHub Releases?

- Adds external dependency
- Release management overhead
- Local build with cache is simpler and works offline
- Consider only if build time becomes prohibitive (it's ~30s)

---

## Summary

| Aspect              | Before (pre-built)   | After (on-demand)        |
|---------------------|----------------------|--------------------------|
| Binaries in git     | Yes (~6MB)           | No ✅                    |
| Staleness check     | Fails build          | Gradle up-to-date ✅     |
| First build time    | 0s                   | ~3s (Zig cached)         |
| Subsequent builds   | Instant              | Instant (cached) ✅      |
| Cross-platform JAR  | Yes (committed)      | Yes (Zig cross-compile) ✅|
| Zig cache location  | build/zig            | ~/.gradle/caches/zig ✅  |
| Native lib cache    | None                 | ~/.gradle/caches/tree-sitter-xtc ✅ |
