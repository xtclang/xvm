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

In IntelliJ: **View → Tool Windows → Language Servers** (LSP4IJ) to see server logs.

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
| **Future (Requires Compiler)** |
| Semantic Tokens | ❌ | ❌ | 🔮 | `textDocument/semanticTokens/full` |
| Inlay Hints | ❌ | ❌ | 🔮 | `textDocument/inlayHint` |
| Workspace Symbols | ❌ | ❌ | 🔮 | `workspace/symbol` |

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
Properties can be set via Gradle `-P` flags, `gradle.properties`, environment variables, or
system properties depending on the property.

### Gradle Properties (`-P` flags or `gradle.properties`)

| Property | Default | Description |
|----------|---------|-------------|
| `log` | `INFO` | Log level for XTC LSP/DAP servers. Valid: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` |
| `lsp.adapter` | `treesitter` | Parsing backend. Valid: `treesitter`, `mock`, `compiler` |
| `lsp.semanticTokens` | `false` | Enable semantic token highlighting (opt-in) |
| `includeBuildLang` | `false` | Include `lang` as a composite build (IDE visibility, task addressability) |
| `includeBuildAttachLang` | `false` | Wire lang lifecycle tasks to root build (requires `includeBuildLang=true`) |
| `lsp.buildSearchableOptions` | `false` | Build IntelliJ searchable options index |

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `XTC_LOG_LEVEL` | `INFO` | Log level override. Same valid values as `-Plog`. Useful for CI or shell profiles. |

### Precedence for Log Level

The log level is resolved in this order (first match wins):

1. `-Plog=<level>` Gradle property
2. `-Dxtc.logLevel=<level>` JVM system property
3. `XTC_LOG_LEVEL=<level>` environment variable
4. Default: `INFO`

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
| `[Server]` | `XtcLanguageServer` — LSP protocol handler |
| `[Launcher]` | `XtcLanguageServerLauncher` — server startup |
| `[TreeSitter]` | `TreeSitterAdapter` — syntax-level intelligence |
| `[Mock]` | `MockXtcCompilerAdapter` — regex-based adapter |
| `[Parser]` | `XtcParser` — tree-sitter native parser |
| `[QueryEngine]` | `XtcQueryEngine` — tree-sitter query execution |
| `[WorkspaceIndexer]` | `WorkspaceIndexer` — background file scanner |
| `[WorkspaceIndex]` | `WorkspaceIndex` — symbol index |

### Tailing Logs

```bash
tail -f ~/.xtc/logs/lsp-server.log
```

## Documentation

- [Tree-sitter Feature Matrix](../tree-sitter/doc/functionality.md) - What Tree-sitter can/cannot do
- [Tree-sitter Integration Plan](../doc/plans/PLAN_TREE_SITTER.md) - Full implementation details
