j# Conditional Tree-sitter Build

**Status**: PENDING (MEDIUM PRIORITY)

**Goal**: Skip tree-sitter native library build when using mock adapter, reducing build time.

---

## Current Behavior

- `lsp-server` always depends on `tree-sitter` native library
- Native library is always built/copied to resources
- Build time cost even when not using tree-sitter adapter

---

## Desired Behavior

When `lsp.adapter=mock`:
- Skip `ensureNativeLibraryUpToDate` task
- Skip `copyNativeLibToResources` task
- lsp-server JAR includes tree-sitter code but no native library
- Runtime gracefully handles missing native library (already does - falls back to mock)

When `lsp.adapter=treesitter`:
- Current behavior (build/bundle native library)

---

## Implementation Options

### Option A: Conditional task dependency (Recommended)

```kotlin
// In lsp-server/build.gradle.kts
val copyNativeLibToResources by tasks.existing {
    onlyIf { lspAdapter == "treesitter" }
}
```

### Option B: Separate source sets

- Create `treesitter` source set with native resources
- Only include when adapter is treesitter

### Option C: Feature flag in fat JAR

- Always include code, conditionally include native library
- Use `fatJar` exclusion patterns based on adapter

---

## Recommendation

Option A is simplest. The tree-sitter Kotlin code is small (~50KB) and harmless to include.
Only skip the native library bundling (~1.2MB per platform).

---

## Testing

```bash
# Fast build for mock adapter (no native library)
./gradlew :lang:intellij-plugin:runIde
# JAR should NOT contain native/ directory

# Full build with tree-sitter
./gradlew :lang:intellij-plugin:runIde -Plsp.adapter=treesitter
# JAR should contain native/<platform>/libtree-sitter-xtc.*
```