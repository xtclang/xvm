# XTC LSP Server

Language Server Protocol (LSP) implementation for the Ecstasy/XTC programming language.

## Overview

This project provides the LSP server that powers IDE features like:
- Syntax error highlighting
- Hover information
- Code completion
- Go to definition
- Find references
- Document outline
- And many more (see feature matrix below)

The server is used by both the [IntelliJ plugin](../intellij-plugin/) and
[VS Code extension](../vscode-extension/).

## Adapter Architecture

The LSP server uses a pluggable adapter pattern to support different parsing backends:

```
┌───────────────────────────────────────────────────────────────┐
│                      LSP Client (IDE)                         │
└───────────────────────────┬───────────────────────────────────┘
                            │ JSON-RPC over stdio
┌───────────────────────────▼───────────────────────────────────┐
│                    XtcLanguageServer                          │
│              (takes XtcCompilerAdapter via constructor)       │
└───────────────────────────┬───────────────────────────────────┘
                            │
              ┌─────────────┴─────────────┐
              │    XtcCompilerAdapter     │  ← Interface with defaults
              └─────────────┬─────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
┌───────┴───────┐   ┌───────┴───────┐   ┌───────┴───────┐
│ MockXtc-      │   │ TreeSitter-   │   │ XtcCompiler-  │
│ Compiler-     │   │ Adapter       │   │ AdapterStub   │
│ Adapter       │   │               │   │               │
│               │   │               │   │               │
│ - Regex-based │   │ - Tree-sitter │   │ - Stub for    │
│ - For testing │   │ - Syntax AST  │   │   future      │
│               │   │ - Default     │   │   compiler    │
└───────────────┘   └───────────────┘   └───────────────┘
```

## Adapter Selection

The adapter is selected at **build time** via the `lsp.adapter` Gradle property.
The selection is embedded in `lsp-version.properties` inside the JAR.

### Available Adapters

| Adapter | Value | Description |
|---------|-------|-------------|
| **Mock** | `mock` | Regex-based parsing. No native dependencies. Good for testing. |
| **Tree-sitter** (default) | `treesitter` | AST-based parsing using tree-sitter. Requires native library. |
| **Compiler** | `compiler` | Stub adapter. All methods logged but return empty. For testing infrastructure. |

### Build Commands

```bash
# Build with Tree-sitter adapter (default)
./gradlew :lang:lsp-server:fatJar -PincludeBuildLang=true

# Build with Mock adapter (no native dependencies)
./gradlew :lang:lsp-server:fatJar -Plsp.adapter=mock -PincludeBuildLang=true

# Build with Compiler stub (all calls logged)
./gradlew :lang:lsp-server:fatJar -Plsp.adapter=compiler -PincludeBuildLang=true

# Run IntelliJ with specific adapter
./gradlew :lang:intellij-plugin:runIde -Plsp.adapter=treesitter -PincludeBuildLang=true
```

### Setting a Default Adapter

Create or edit `gradle.properties`:

```properties
lsp.adapter=treesitter
```

### Verifying the Active Backend

The server logs the active backend at startup:

```
========================================
XTC Language Server v1.0.0
Backend: TreeSitter
Built: 2026-02-04T15:30:00Z
========================================
```

In IntelliJ: **View -> Tool Windows -> Language Servers** (LSP4IJ) to see server logs.

### Backend Comparison

| Feature | Mock | Tree-sitter | Compiler Stub |
|---------|:----:|:-----------:|:-------------:|
| Symbol detection | Regex (basic) | AST-based (accurate) | None (logged) |
| Nested symbols | ❌ Limited | ✅ Full hierarchy | ❌ None |
| Syntax errors | ❌ Basic patterns | ✅ Precise location | ❌ None |
| Error recovery | ❌ None | ✅ Continues parsing | ❌ None |
| Rename | ✅ Same-file (text) | ✅ Same-file (AST) | ❌ None |
| Code actions | ✅ Organize imports | ✅ Organize imports | ❌ None |
| Formatting | ✅ Trailing WS | ✅ Trailing WS | ❌ None |
| Folding ranges | ✅ Brace matching | ✅ AST node boundaries | ❌ None |
| Signature help | ❌ None | ✅ Same-file methods | ❌ None |
| Document links | ✅ Import regex | ✅ Import AST nodes | ❌ None |
| Native library | Not needed | Required | Not needed |
| All LSP calls logged | ✅ | ✅ | ✅ |

## Supported LSP Features

All 17 LSP capabilities are advertised by the server and wired up in
`XtcLanguageServer`. Each method delegates to the active `XtcCompilerAdapter`.
Capabilities not yet implemented in an adapter use default interface methods
(returning empty results or null).

| Feature | Mock | TreeSitter | Compiler | LSP Method |
|---------|:----:|:----------:|:--------:|------------|
| **Navigation** |
| Go to Definition | ✅ | ✅ | 🔮 | `textDocument/definition` |
| Find References | ⚠️ | ✅ | 🔮 | `textDocument/references` |
| Document Symbols | ✅ | ✅ | 🔮 | `textDocument/documentSymbol` |
| Document Highlight | ✅ | ✅ | 🔮 | `textDocument/documentHighlight` |
| Selection Ranges | ❌ | ✅ | 🔮 | `textDocument/selectionRange` |
| Document Links | ✅ | ✅ | 🔮 | `textDocument/documentLink` |
| **Editing** |
| Hover | ✅ | ✅ | 🔮 | `textDocument/hover` |
| Completion | ⚠️ | ✅ | 🔮 | `textDocument/completion` |
| Signature Help | ❌ | ✅ | 🔮 | `textDocument/signatureHelp` |
| **Refactoring** |
| Rename / Prepare Rename | ✅ | ✅ | 🔮 | `textDocument/rename` |
| Code Actions | ✅ | ✅ | 🔮 | `textDocument/codeAction` |
| **Formatting** |
| Format Document | ✅ | ✅ | 🔮 | `textDocument/formatting` |
| Format Selection | ✅ | ✅ | 🔮 | `textDocument/rangeFormatting` |
| **Code Intelligence** |
| Diagnostics | ⚠️ | ✅ | 🔮 | `textDocument/publishDiagnostics` |
| Folding Ranges | ✅ | ✅ | 🔮 | `textDocument/foldingRange` |
| **Code Intelligence (cont.)** |
| Semantic Tokens | ❌ | ✅ | 🔮 | `textDocument/semanticTokens/full` |
| Workspace Symbols | ❌ | ✅ | 🔮 | `workspace/symbol` |
| **Future (Requires Compiler)** |
| Inlay Hints | ❌ | ❌ | 🔮 | `textDocument/inlayHint` |

Legend: ✅ = Implemented, ⚠️ = Partial/limited, ❌ = Not implemented, 🔮 = Future (compiler adapter)

## Key Components

| Component | Description |
|-----------|-------------|
| `XtcLanguageServer` | LSP protocol handler, wires all LSP methods to adapter |
| `XtcCompilerAdapter` | Interface defining core LSP operations |
| `AbstractXtcCompilerAdapter` | Base class with shared logging, hover formatting, utilities |
| `XtcLanguageConstants` | Shared keywords, built-in types, symbol mappings |
| `MockXtcCompilerAdapter` | Regex-based implementation for testing |
| `TreeSitterAdapter` | Tree-sitter based syntax intelligence |
| `XtcCompilerAdapterStub` | Minimal placeholder for future compiler integration |

## Building

```bash
# Build the project (with lang enabled)
./gradlew :lang:lsp-server:build -PincludeBuildLang=true

# Run tests
./gradlew :lang:lsp-server:test -PincludeBuildLang=true

# Create fat JAR with all dependencies
./gradlew :lang:lsp-server:fatJar -PincludeBuildLang=true
```

## Tree-sitter Native Library

The tree-sitter adapter requires native libraries (`libtree-sitter-xtc`). These are built
on-demand using Zig cross-compilation for all 5 platforms and cached in
`~/.gradle/caches/tree-sitter-xtc/`.

See [tree-sitter/README.md](../tree-sitter/README.md) for details.

## Configuration Reference

All configurable properties for the LSP server and IntelliJ plugin, in one place.

Properties are resolved via `xdkProperties` (the `ProjectXdkProperties` extension),
which checks sources in this order (first match wins):

1. Environment variable (key converted to `UPPER_SNAKE_CASE`)
2. Gradle property (`-P` flag or `gradle.properties`)
3. JVM system property (`-D` flag)
4. `XdkPropertiesService` (composite root's `gradle.properties`, `xdk.properties`, `version.properties`)

This ensures properties set in the root `gradle.properties` are visible to the `lang/`
included build, which has no `gradle.properties` of its own.

### Gradle Properties (`-P` flags or `gradle.properties`)

| Property | Default | Description |
|----------|---------|-------------|
| `log` | `INFO` | Log level for XTC LSP/DAP servers. Valid: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` |
| `lsp.adapter` | `treesitter` | Parsing backend. Valid: `treesitter`, `mock`, `compiler` |
| `lsp.semanticTokens` | `true` | Enable semantic token highlighting (tree-sitter lexer-based) |
| `includeBuildLang` | `false` | Include `lang` as a composite build (IDE visibility, task addressability) |
| `includeBuildAttachLang` | `false` | Wire lang lifecycle tasks to root build (requires `includeBuildLang=true`) |
| `lsp.buildSearchableOptions` | `false` | Build IntelliJ searchable options index |

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `XTC_LOG_LEVEL` | `INFO` | Log level override. Same valid values as `-Plog`. Useful for CI or shell profiles. |

### Precedence for Log Level

The log level is resolved via `xdkProperties` in this order (first match wins):

1. `LOG=<level>` environment variable
2. `-Plog=<level>` Gradle property
3. `-Dlog=<level>` JVM system property
4. `log=<level>` in root `gradle.properties`
5. `XTC_LOG_LEVEL=<level>` environment variable (backward-compatible fallback)
6. Default: `INFO`

### Examples

```bash
# Run IntelliJ sandbox with DEBUG logging and tree-sitter
./gradlew :lang:intellij-plugin:runIde -PincludeBuildLang=true -Plog=DEBUG

# Run LSP server tests with TRACE logging
./gradlew :lang:lsp-server:test -Plog=TRACE

# Build with mock adapter (no native dependencies)
./gradlew :lang:lsp-server:fatJar -Plsp.adapter=mock

# Set log level via environment (persists across commands)
export XTC_LOG_LEVEL=DEBUG
./gradlew :lang:intellij-plugin:runIde -PincludeBuildLang=true
```

## Logging

The LSP server logs to both stderr (for IntelliJ's Language Servers panel) and a file.

### Log File Location

```bash
~/.xtc/logs/lsp-server.log
```

All log messages use a `[Module]` prefix to identify their source:

| Prefix | Source |
|--------|--------|
| `[Server]` | `XtcLanguageServer` -- LSP protocol handler |
| `[Launcher]` | `XtcLanguageServerLauncher` -- server startup |
| `[TreeSitter]` | `TreeSitterAdapter` -- syntax-level intelligence |
| `[Mock]` | `MockXtcCompilerAdapter` -- regex-based adapter |
| `[Parser]` | `XtcParser` -- tree-sitter native parser |
| `[QueryEngine]` | `XtcQueryEngine` -- tree-sitter query execution |
| `[WorkspaceIndexer]` | `WorkspaceIndexer` -- background file scanner |
| `[WorkspaceIndex]` | `WorkspaceIndex` -- symbol index |

### Tailing Logs

```bash
tail -f ~/.xtc/logs/lsp-server.log
```

## Known Issues: IntelliJ Platform and LSP4IJ

Issues in third-party dependencies that affect the XTC plugin. These are not bugs in our code
but behaviors we must work around.

### LSP4IJ Issues

| Issue | Impact | Workaround | Reference |
|-------|--------|------------|-----------|
| **Duplicate server spawning** | LSP4IJ may call `start()` concurrently for multiple `.x` files, briefly spawning extra LSP server processes | Harmless -- extras are killed within milliseconds. We guard notifications with `AtomicBoolean` to avoid duplicates | [lsp4ij#888](https://github.com/redhat-developer/lsp4ij/issues/888) |
| **"Show Logs" link in error popups** | When the LSP server returns an error (e.g., internal exception), the error notification shows "Show Logs" / "Disable error reporting". The "Show Logs" link opens `idea.log`, **not** the LSP server log file, and may be unclickable | Tail the actual LSP log directly: `tail -f ~/.xtc/logs/lsp-server.log`. Also check the LSP Console: **View -> Tool Windows -> Language Servers -> Logs tab** | LSP4IJ limitation |
| **Error notification popup not actionable** | The `textDocument/semanticTokens Internal error` popup's links ("Show Logs", "Disable error reporting", "More") may not respond to clicks in some IntelliJ versions | The popup auto-dismisses. Check the LSP Console Logs tab for the actual server-side stack trace | LSP4IJ UI limitation |

### IntelliJ Platform Issues

| Issue | Impact | Workaround | Reference |
|-------|--------|------------|-----------|
| **`intellijIdea()` downloads Ultimate (IU)** | JetBrains deprecated `intellijIdeaCommunity()` in Platform Gradle Plugin 2.11 for 2025.3+. The replacement `intellijIdea()` downloads IntelliJ Ultimate which bundles 50+ plugins requiring `com.intellij.modules.ultimate`. These all fail to load with WARN messages | We disable `com.intellij.modules.ultimate` and Kubernetes plugins in `disabled_plugins.txt` via the `configureDisabledPlugins` task. Remaining warnings are cosmetic | JetBrains 2025.3 unified distribution |
| **EDT "slow operations" / "prohibited" warnings** | IntelliJ reports plugins that perform I/O or heavy work on the Event Dispatch Thread. Our `JreProvisioner.findSystemJava()` called `ProjectJdkTable.getInstance()` in the connection provider's `init {}` block (EDT) | Fixed: moved JRE resolution to `start()` which runs off EDT. If new EDT warnings appear, check that no IntelliJ platform API calls are in `init {}` blocks or constructors | IntelliJ 2025.3 strict EDT enforcement |
| **CDS warning in tests** | `[warning][cds] Archived non-system classes are disabled because the java.system.class.loader property is specified` appears in test output | Harmless -- IntelliJ sets `-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader` for plugin classloading. Suppressed with `-Xlog:cds=off` in test config | Standard for all IntelliJ plugin tests |

### Debugging Tips

**Where to find logs:**

| Log | Location | Contents |
|-----|----------|----------|
| LSP server log | `~/.xtc/logs/lsp-server.log` | All `[Server]`, `[TreeSitter]`, `[Parser]`, `[QueryEngine]` messages |
| IntelliJ `idea.log` | Sandbox `log/idea.log` (path shown at `runIde` startup) | Platform errors, plugin loading, EDT violations |
| LSP Console (IDE) | **View -> Tool Windows -> Language Servers -> Logs** | JSON-RPC traces, server stderr |
| Gradle console | Terminal running `runIde` | Build output + tailed LSP log (real-time) |

**When "Show Logs" doesn't work:**

The LSP4IJ error popup's "Show Logs" link points to `idea.log`, which typically does not
contain the actual LSP server error. Instead:

1. Open the **Language Servers** tool window (bottom panel, next to Terminal)
2. Select **XTC Language Server** -> **Logs** tab
3. Look for `SEVERE:` or stack traces in the log output
4. Or tail the server log directly: `tail -f ~/.xtc/logs/lsp-server.log`

**When IntelliJ complains about "slow operations":**

IntelliJ 2025.3 strictly enforces EDT rules. If you see "plugin to blame: XTC Language Support"
in slow operation reports:

1. Check `idea.log` for the exact stack trace (search for "SlowOperations")
2. The stack trace shows which XTC code ran on EDT
3. Fix: move the offending call to a background thread or `ApplicationManager.executeOnPooledThread()`

## Documentation

- [Tree-sitter Feature Matrix](../tree-sitter/doc/functionality.md) - What Tree-sitter can/cannot do
- [Tree-sitter Integration Plan](../doc/plans/PLAN_TREE_SITTER.md) - Full implementation details
