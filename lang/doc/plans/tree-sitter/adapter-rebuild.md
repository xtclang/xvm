# Adapter Selection and Rebuild Behavior

**Status**: COMPLETE (2026-02-02)

The LSP server supports multiple parsing backends (adapters). The adapter is selected at
build time and baked into the JAR.

---

## Available Adapters

| Adapter | Description | Use Case |
|---------|-------------|----------|
| `treesitter` | Native tree-sitter parser (default) | Production - full syntax analysis |
| `mock` | Regex-based parser | Testing - no native dependencies |

---

## Build Commands

```bash
# Build with tree-sitter adapter (default)
./gradlew :lang:lsp-server:fatJar

# Build with mock adapter
./gradlew :lang:lsp-server:fatJar -Plsp.adapter=mock

# Run IntelliJ with specific adapter
./gradlew :lang:intellij-plugin:runIde -Plsp.adapter=treesitter
```

---

## Automatic Rebuild on Adapter Change

When you change the `-Plsp.adapter` property, Gradle automatically rebuilds the JAR:

1. The `generateBuildInfo` task has `inputs.property("adapter", adapter)`
2. Changing the property invalidates the task's cache
3. This triggers `processResources` → `fatJar` rebuild cascade

---

## Verification

```bash
# Build with mock
./gradlew :lang:lsp-server:fatJar -Plsp.adapter=mock

# Check what's baked in
unzip -p lang/lsp-server/build/libs/lsp-server-*-all.jar lsp-version.properties
# Output: lsp.adapter=mock

# Switch to treesitter - observe generateBuildInfo runs (not UP-TO-DATE)
./gradlew :lang:lsp-server:fatJar -Plsp.adapter=treesitter

# Check again
unzip -p lang/lsp-server/build/libs/lsp-server-*-all.jar lsp-version.properties
# Output: lsp.adapter=treesitter
```

---

## Version Properties File

The adapter and version info are stored in `lsp-version.properties` inside the JAR:

```properties
lsp.build.time=2026-02-02T11:23:32.294087Z
lsp.version=0.4.4-SNAPSHOT
lsp.adapter=treesitter
```

This file is read by:
- `XtcLanguageServerFactory` - displays version in notifications
- `XtcLspConnectionProvider` - logs adapter type on startup