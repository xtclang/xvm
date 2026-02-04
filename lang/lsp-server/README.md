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
| Native library | Not needed | Required | Not needed |
| All LSP calls logged | ✅ | ✅ | ✅ |

## Supported LSP Features

All LSP methods are wired up in `XtcLanguageServer` and delegate to the adapter.
Unimplemented methods use default interface implementations that log warnings.

| Feature | Mock | TreeSitter | Compiler | LSP Method |
|---------|:----:|:----------:|:--------:|------------|
| **Core (Implemented)** |
| Diagnostics | ⚠️ | ✅ | ❌ | `textDocument/publishDiagnostics` |
| Hover | ⚠️ | ✅ | ❌ | `textDocument/hover` |
| Completion | ⚠️ | ✅ | ❌ | `textDocument/completion` |
| Go to Definition | ⚠️ | ⚠️ | ❌ | `textDocument/definition` |
| Find References | ❌ | ⚠️ | ❌ | `textDocument/references` |
| Document Symbols | ⚠️ | ✅ | ❌ | `textDocument/documentSymbol` |
| **Tree-sitter Capable (Stubs)** |
| Document Highlights | ❌ | 🔧 | ❌ | `textDocument/documentHighlight` |
| Selection Ranges | ❌ | 🔧 | ❌ | `textDocument/selectionRange` |
| Folding Ranges | ❌ | 🔧 | ❌ | `textDocument/foldingRange` |
| Document Links | ❌ | 🔧 | ❌ | `textDocument/documentLink` |
| **Requires Compiler (Stubs)** |
| Signature Help | ❌ | ❌ | 🔮 | `textDocument/signatureHelp` |
| Rename | ❌ | ❌ | 🔮 | `textDocument/rename` |
| Prepare Rename | ❌ | ❌ | 🔮 | `textDocument/prepareRename` |
| Code Actions | ❌ | ❌ | 🔮 | `textDocument/codeAction` |
| Semantic Tokens | ❌ | ❌ | 🔮 | `textDocument/semanticTokens/full` |
| Inlay Hints | ❌ | ❌ | 🔮 | `textDocument/inlayHint` |
| Formatting | ❌ | ❌ | 🔮 | `textDocument/formatting` |
| Range Formatting | ❌ | ❌ | 🔮 | `textDocument/rangeFormatting` |
| Workspace Symbols | ❌ | ❌ | 🔮 | `workspace/symbol` |

Legend: ✅ = Implemented, ⚠️ = Partial, ❌ = Not implemented, 🔧 = Tree-sitter TODO, 🔮 = Future compiler

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

## Logging

The LSP server logs to both stderr (for IntelliJ's Language Servers panel) and a file.

### Log File Location

```bash
~/.xtc/logs/lsp-server.log
```

### Changing Log Level

Set the log level via `-Dxtc.logLevel`:

```bash
# Run IntelliJ with DEBUG logging
./gradlew :lang:intellij-plugin:runIde -PincludeBuildLang=true -Dxtc.logLevel=DEBUG

# Valid levels: TRACE, DEBUG, INFO (default), WARN, ERROR
```

### Tailing Logs

```bash
tail -f ~/.xtc/logs/lsp-server.log
```

## Documentation

- [Tree-sitter Feature Matrix](../tree-sitter/doc/functionality.md) - What Tree-sitter can/cannot do
- [Tree-sitter Integration Plan](../doc/plans/PLAN_TREE_SITTER.md) - Full implementation details
