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

The server is used by both the [IntelliJ plugin](../intellij-plugin/) and [VS Code extension](../vscode-extension/).

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
              │    XtcCompilerAdapter     │  ← Interface
              └─────────────┬─────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
┌───────┴───────┐   ┌───────┴───────┐   ┌───────┴───────┐
│ MockXtc-      │   │ TreeSitter-   │   │ (Future)      │
│ Compiler-     │   │ Adapter       │   │ Compiler-     │
│ Adapter       │   │               │   │ Adapter       │
│               │   │               │   │               │
│ - Regex-based │   │ - Tree-sitter │   │ - Full XTC    │
│ - Default     │   │ - Syntax AST  │   │   compiler    │
└───────────────┘   └───────────────┘   └───────────────┘
```

## Adapter Selection

The adapter is selected at **build time** via the `lsp.adapter` Gradle property.
The selection is embedded in `lsp-version.properties` inside the JAR.

### Available Adapters

| Adapter | Value | Description |
|---------|-------|-------------|
| **Mock** (default) | `mock` | Regex-based parsing. No native dependencies. Good for testing and basic features. |
| **Tree-sitter** | `treesitter` | AST-based parsing using tree-sitter. Requires native library. Accurate syntax analysis. |

### Build Commands

```bash
# Build with Mock adapter (default)
./gradlew :lang:lsp-server:build

# Build with Tree-sitter adapter
./gradlew :lang:lsp-server:build -Plsp.adapter=treesitter

# Run IntelliJ with specific adapter
./gradlew :lang:intellij-plugin:runIde -Plsp.adapter=treesitter

# Run VS Code with specific adapter
./gradlew :lang:vscode-extension:runIde -Plsp.adapter=treesitter
```

### Setting a Default Adapter

Create or edit `gradle.properties` in the project root or `lang/` directory:

```properties
lsp.adapter=mock
# or
lsp.adapter=treesitter
```

### Verifying the Active Backend

The server logs the active backend at startup:

```
========================================
XTC Language Server v1.0.0
Backend: MockXtcCompilerAdapter
Built: 2026-01-31T15:30:00Z
========================================
```

In IntelliJ: **View → Tool Windows → Language Servers** (LSP4IJ) to see server logs.

### Backend Comparison

| Feature | Mock | Tree-sitter |
|---------|------|-------------|
| Symbol detection | Regex (basic) | AST-based (accurate) |
| Nested symbols | ❌ Limited | ✅ Full hierarchy |
| Syntax errors | ❌ Basic patterns | ✅ Precise location |
| Error recovery | ❌ None | ✅ Continues parsing |
| Native library | Not needed | Required |
| Performance | Fast | Fast (incremental) |

### Tree-sitter Native Library

The tree-sitter adapter requires a native library (`libtree-sitter-xtc`). Pre-built libraries
for all platforms are committed to source control. See [tree-sitter/README.md](../tree-sitter/README.md)
for details on building and managing native libraries.

> **Note**: The `lsp.adapter` property only affects which adapter is used at runtime.
> The tree-sitter native library build is **always** verified regardless of adapter selection,
> to ensure pre-built libraries stay up-to-date with grammar changes.

## Key Components

| Component | Description |
|-----------|-------------|
| `XtcLanguageServer` | LSP protocol handler |
| `XtcCompilerAdapter` | Interface for extracting data from the XTC compiler |
| `MockXtcCompilerAdapter` | Regex-based implementation for testing and fallback |
| `TreeSitterAdapter` | Tree-sitter based implementation for syntax intelligence |
| `CompilationResult` | Immutable snapshot of compilation output |

## Building

```bash
# Build the project
./gradlew :lsp-server:build

# Run tests
./gradlew :lsp-server:test

# Create JAR
./gradlew :lsp-server:jar
```

## Usage

The LSP server is used by:
- **intellij-plugin**: Runs in-process for IntelliJ IDEA
- **vscode-extension**: Runs as subprocess for VS Code

## Supported LSP Features

| Feature | Status | Notes |
|---------|--------|-------|
| Diagnostics | ✅ | Errors and warnings |
| Hover | ✅ | Type information |
| Completion | ✅ | Keywords and symbols |
| Go to Definition | ✅ | Jump to declaration |
| Find References | ✅ | Find all usages |
| Document Symbols | ✅ | Outline view |
| Formatting | ❌ | Not yet |
| Rename | ❌ | Not yet |
| Code Actions | ❌ | Not yet |

## Documentation

- [Tree-sitter Integration Plan](../doc/plans/PLAN_TREE_SITTER.md) - Full details on tree-sitter backend

The LSP server uses an adapter pattern to abstract the compiler backend, enabling future migration to a restartable/incremental compiler without changing the LSP protocol layer.
