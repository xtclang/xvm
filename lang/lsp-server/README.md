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

## Architecture

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
│ CompilerAdapter│   │ Adapter       │   │ RealCompiler- │
│               │   │               │   │ Adapter       │
│ - Regex-based │   │ - Tree-sitter │   │ - Full XTC    │
│ - For testing │   │ - Syntax only │   │   compiler    │
└───────────────┘   └───────────────┘   └───────────────┘
```

## Selecting the Backend

The adapter is selected at **build time** via a Gradle property. The launcher reads this from
the embedded `lsp-version.properties` file.

### Build Commands

**Build with Mock adapter (default):**
```bash
./gradlew :lang:lsp-server:build
```

**Build with Tree-sitter adapter:**
```bash
./gradlew :lang:lsp-server:build -Plsp.adapter=treesitter
```

**Set default in gradle.properties:**
```properties
# In lang/lsp-server/gradle.properties (create if needed)
lsp.adapter=mock
# or
lsp.adapter=treesitter
```

### Verifying the Active Backend

The server logs the active backend prominently at startup:

```
========================================
XTC Language Server v1.0.0
Backend: Tree-sitter (syntax-aware)
Built: 2026-01-21T15:30:00Z
========================================
```

In IntelliJ: View → Tool Windows → Language Servers (LSP4IJ) to see server logs.

### Backend Comparison

| Feature | Mock | Tree-sitter |
|---------|------|-------------|
| Symbol detection | Regex (basic) | AST-based (accurate) |
| Nested symbols | ❌ Limited | ✅ Full hierarchy |
| Syntax errors | ❌ Basic patterns | ✅ Precise location |
| Error recovery | ❌ None | ✅ Continues parsing |
| Performance | Fast | Fast (incremental) |

### Enabling Tree-sitter

To use the Tree-sitter backend:

1. Build the native library: `./gradlew :lang:dsl:buildTreeSitterLibrary`
2. Copy to resources: `lang/lsp-server/src/main/resources/native/<platform>/`
3. Build with tree-sitter: `./gradlew :lang:lsp-server:build -Plsp.adapter=treesitter`

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
- [Adapter Layer Design](../doc/3-lsp-solution/adapter-layer-design.md) - How the adapter pattern works
- [Incremental Migration](../doc/3-lsp-solution/incremental-migration.md) - Path to restartable compiler
