# LSP Implementation Survey: Real-World Language Support

This document surveys how major programming languages have implemented IDE-independent language support using LSP, DAP, and TextMate grammars. These examples inform the XTC/Ecstasy language support strategy.

---

## Contents

1. [Rust - rust-analyzer](#1-rust---rust-analyzer)
2. [TypeScript - typescript-language-server](#2-typescript---typescript-language-server)
3. [Python - Pylance / Jedi](#3-python---pylance--jedi)
4. [Go - gopls](#4-go---gopls)
5. [C/C++ - clangd](#5-cc---clangd)
6. [Kotlin - kotlin-language-server](#6-kotlin---kotlin-language-server)
7. [Java - Eclipse JDT.LS](#7-java---eclipse-jdtls)
8. [Comparison Matrix](#comparison-matrix)

---

## 1. Rust - rust-analyzer

**Status**: ⭐ Gold standard for modern LSP implementation

### Components

**LSP Server**: `rust-analyzer`
- **Repository**: https://github.com/rust-lang/rust-analyzer
- **Language**: Rust (self-hosted)
- **Architecture**: Standalone LSP server
- **Lines of Code**: ~300K LOC

**TextMate Grammar**:
- **Repository**: https://github.com/dustypomerleau/rust-syntax
- **Used by**: VSCode, Sublime Text, Atom, etc.

**Debug Adapter**: Multiple DAP implementations
- `lldb-vscode` (LLDB-based, for native debugging)
- `codelldb` (Popular VSCode extension)
- `rust-gdb` (GDB wrapper)

### IDE Support

| IDE                | LSP Support | DAP Support | Implementation                             |
|--------------------|-------------|-------------|--------------------------------------------|
| **VSCode**         | ✅ Yes      | ✅ Yes      | `rust-analyzer` extension + `codelldb`     |
| **IntelliJ IDEA**  | ✅ Yes      | ✅ Yes      | Built-in Rust plugin + rust-analyzer mode  |
| **Vim/Neovim**     | ✅ Yes      | ✅ Yes      | Via `nvim-lspconfig` + `nvim-dap`          |
| **Emacs**          | ✅ Yes      | ✅ Yes      | Via `lsp-mode` + `dap-mode`                |
| **Sublime Text**   | ✅ Yes      | ❌ Limited  | Via LSP package                            |

### Key Features Implemented

- ✅ Real-time diagnostics (compile errors/warnings)
- ✅ Code completion with type inference
- ✅ Go to definition/implementation
- ✅ Find references
- ✅ Rename refactoring
- ✅ Inline hints (type annotations, parameter names)
- ✅ Macro expansion visualization
- ✅ Cargo integration (build system)

### Debugging

- Uses LLVM's LLDB debugger (native code)
- DAP adapters: `lldb-vscode`, `codelldb`
- Supports breakpoints, stepping, variable inspection
- Works with both debug and release builds

### Lessons Learned

✅ **Successes**:
- Self-hosted in Rust ensures dogfooding
- Incremental compilation (salsa library) for fast responses
- Works across all major IDEs
- Active community contributions

⚠️ **Challenges**:
- Initial development took 2+ years to mature
- Macro system complexity required special handling
- Memory usage can be high for large projects

---

## 2. TypeScript - typescript-language-server

**Status**: ⭐ Reference implementation by Microsoft

### Components

**LSP Server**: `typescript-language-server`
- **Repository**: https://github.com/typescript-language-server/typescript-language-server
- **Language**: TypeScript (self-hosted)
- **Architecture**: Wraps TypeScript compiler API
- **Maintained by**: Microsoft + community

**TextMate Grammar**:
- **Repository**: Embedded in VSCode
- **Scope**: `source.ts`, `source.tsx`

**Debug Adapter**: `vscode-js-debug`
- **Repository**: https://github.com/microsoft/vscode-js-debug
- **Supports**: Node.js, Chrome, Edge debugging
- **Protocol**: DAP

### IDE Support

| IDE                | LSP Support | DAP Support | Implementation                    |
|--------------------|-------------|-------------|-----------------------------------|
| **VSCode**         | ✅ Yes      | ✅ Yes      | Native (built-in)                 |
| **IntelliJ IDEA**  | ✅ Yes      | ✅ Yes      | Built-in TypeScript support       |
| **Vim/Neovim**     | ✅ Yes      | ✅ Yes      | Via `nvim-lspconfig` + `nvim-dap` |
| **Emacs**          | ✅ Yes      | ✅ Yes      | Via `lsp-mode` + `dap-mode`       |
| **Sublime Text**   | ✅ Yes      | ❌ Limited  | Via LSP package                   |

### Key Features Implemented

- ✅ IntelliSense (auto-completion)
- ✅ Real-time type checking
- ✅ Go to definition/references
- ✅ Rename refactoring
- ✅ Organize imports
- ✅ Quick fixes (auto-import, etc.)
- ✅ Signature help
- ✅ Semantic highlighting

### Debugging

- **Node.js**: Debug Adapter connects to V8 inspector protocol
- **Browser**: Remote debugging via Chrome DevTools Protocol
- Source maps for TypeScript → JavaScript mapping
- Supports breakpoints, watches, call stack, step debugging

### Lessons Learned

✅ **Successes**:
- Reuses existing TypeScript compiler (no duplication)
- Fast incremental compilation
- Excellent source map support
- Works with JavaScript too

⚠️ **Challenges**:
- TypeScript compiler wasn't originally designed for IDE use
- Had to add `LanguageService` API for incremental updates
- Large projects can be slow (tsserver memory usage)

---

## 3. Python - Pylance / Jedi

**Status**: ⭐ Multiple competing LSP implementations

### Components

**LSP Servers** (multiple options):

**Option 1: Pylance** (Microsoft, closed-source core)
- **Language**: Python + TypeScript (wrapper)
- **Engine**: Pyright (type checker)
- **Speed**: Fast (written in TypeScript/Node.js)
- **Best for**: VSCode users

**Option 2: Jedi** (Open-source)
- **Repository**: https://github.com/davidhalter/jedi
- **Language**: Pure Python
- **Speed**: Moderate
- **Best for**: Vim, Emacs, Sublime

**Option 3: python-lsp-server** (formerly python-language-server)
- **Repository**: https://github.com/python-lsp/python-lsp-server
- **Language**: Pure Python
- **Uses**: Jedi, Rope, pyflakes, etc.
- **Best for**: Generic LSP clients

**TextMate Grammar**:
- **Repository**: https://github.com/MagicStack/MagicPython
- **Scope**: `source.python`

**Debug Adapter**: `debugpy`
- **Repository**: https://github.com/microsoft/debugpy
- **Based on**: Python's `pdb` + DAP wrapper
- **Supports**: CPython debugging

### IDE Support

| IDE                  | LSP Support | DAP Support | Implementation                       |
|----------------------|-------------|-------------|--------------------------------------|
| **VSCode**           | ✅ Yes      | ✅ Yes      | Pylance + Python extension + debugpy |
| **IntelliJ/PyCharm** | ✅ Yes      | ✅ Yes      | Built-in (proprietary, not LSP)      |
| **Vim/Neovim**       | ✅ Yes      | ✅ Yes      | Jedi/python-lsp-server + nvim-dap    |
| **Emacs**            | ✅ Yes      | ✅ Yes      | python-lsp-server + dap-mode         |
| **Sublime Text**     | ✅ Yes      | ❌ Limited  | Via LSP package                      |

### Key Features Implemented

- ✅ Code completion (with type hints)
- ✅ Type checking (optional, via mypy/pyright)
- ✅ Go to definition/references
- ✅ Rename refactoring
- ✅ Auto-i
- ✅ Docstring on hover
- ✅ Pytest integration

### Debugging (debugpy)

- Injects debug hooks into Python interpreter
- Supports breakpoints, conditional breakpoints
- Variable inspection (locals, globals, closures)
- Expression evaluation in debug context
- Multi-threaded debugging
- Remote debugging (attach to running process)

### Lessons Learned

✅ **Successes**:
- Multiple LSP implementations give users choice
- Debugpy is excellent (production-quality)
- Works well with dynamic typing

⚠️ **Challenges**:
- Dynamic typing makes static analysis hard
- Import resolution is complex (sys.path, virtualenvs)
- Pylance being partially closed-source is controversial
- Performance varies widely between implementations

---

## 4. Go - gopls

**Status**: ⭐ Official LSP server by Go team

### Components

**LSP Server**: `gopls`
- **Repository**: https://github.com/golang/tools/tree/master/gopls
- **Language**: Go (self-hosted)
- **Architecture**: Uses Go's official `go/ast` and `go/types` packages
- **Maintained by**: Go team at Google

**TextMate Grammar**:
- **Repository**: https://github.com/jeff-hykin/better-go-syntax
- **Scope**: `source.go`

**Debug Adapter**: `delve` (via `vscode-go`)
- **Repository**: https://github.com/go-delve/delve
- **Debugger**: Delve (native Go debugger)
- **DAP Support**: Via adapter layer

### IDE Support

| IDE                 | LSP Support | DAP Support | Implementation                          |
|---------------------|-------------|-------------|-----------------------------------------|
| **VSCode**          | ✅ Yes      | ✅ Yes      | Go extension + gopls + delve            |
| **IntelliJ/GoLand** | ✅ Yes      | ✅ Yes      | Built-in (proprietary + gopls fallback) |
| **Vim/Neovim**      | ✅ Yes      | ✅ Yes      | gopls + nvim-dap-go                     |
| **Emacs**           | ✅ Yes      | ✅ Yes      | gopls + dap-mode                        |
| **Sublime Text**    | ✅ Yes      | ❌ Limited  | Via LSP package                         |

### Key Features Implemented

- ✅ Code completion
- ✅ Go to definition/implementation/references
- ✅ Rename refactoring
- ✅ Code actions (extract function, etc.)
- ✅ Inline documentation
- ✅ Import organization
- ✅ Error detection (via `go vet`, `staticcheck`)
- ✅ Go modules support

### Debugging (Delve)

- Native Go debugger (understands goroutines)
- Breakpoints (including function breakpoints)
- Goroutine visualization
- Variable inspection (including channels, maps)
- Expression evaluation
- Core dump analysis

### Lessons Learned

✅ **Successes**:
- Reuses Go's standard library (go/ast, go/types)
- Very fast (compiled language, efficient implementation)
- Official support from Go team
- Excellent goroutine debugging

⚠️ **Challenges**:
- Initial gopls performance was poor (improved over time)
- Go modules added complexity
- Delve has some limitations with optimized builds

---

## 5. C/C++ - clangd

**Status**: ⭐ LLVM-based LSP server

### Components

**LSP Server**: `clangd`
- **Repository**: https://github.com/llvm/llvm-project/tree/main/clang-tools-extra/clangd
- **Language**: C++
- **Architecture**: Built on Clang compiler frontend
- **Maintained by**: LLVM project

**TextMate Grammar**:
- **Repository**: Built into most editors (legacy TextMate grammars)
- **Scope**: `source.c`, `source.cpp`

**Debug Adapters**: Multiple options
- `lldb-vscode` (LLDB-based)
- `cppdbg` (Microsoft, uses GDB/LLDB)
- `codelldb` (Popular VSCode extension)

### IDE Support

| IDE                | LSP Support | DAP Support | Implementation                    |
|--------------------|-------------|-------------|-----------------------------------|
| **VSCode**         | ✅ Yes      | ✅ Yes      | clangd extension + cppdbg         |
| **IntelliJ/CLion** | ✅ Yes      | ✅ Yes      | Built-in (proprietary + clangd)   |
| **Vim/Neovim**     | ✅ Yes      | ✅ Yes      | clangd + nvim-dap                 |
| **Emacs**          | ✅ Yes      | ✅ Yes      | clangd + dap-mode                 |
| **Qt Creator**     | ✅ Yes      | ✅ Yes      | Built-in clangd support           |

### Key Features Implemented

- ✅ Code completion (context-aware)
- ✅ Go to definition/declaration/references
- ✅ Rename refactoring
- ✅ Code actions (fix includes, etc.)
- ✅ Hover documentation
- ✅ Compile error diagnostics
- ✅ Include path resolution
- ✅ Cross-compilation support

### Debugging (LLDB)

- Native debugger (part of LLVM)
- Breakpoints (line, conditional, watchpoints)
- Variable inspection (complex types)
- Expression evaluation (C++ expressions)
- Assembly-level debugging
- Core dump analysis
- Remote debugging

### Lessons Learned

✅ **Successes**:
- Built on production compiler (Clang)
- Very accurate (same parser as compiler)
- Fast incremental compilation
- Excellent cross-platform support

⚠️ **Challenges**:
- C++ complexity (templates, macros)
- Build system integration (compile_commands.json required)
- Memory usage for large projects
- Header dependencies require careful indexing

---

## 6. Kotlin - kotlin-language-server

**Status**: ⭐ Community-driven LSP implementation

> **Note**: This is particularly relevant for XTC as both are JVM-based languages.

### Components

**LSP Server**: `kotlin-language-server`
- **Repository**: https://github.com/fwcd/kotlin-language-server
- **Language**: Kotlin (self-hosted)
- **Architecture**: Uses Kotlin compiler API
- **Maintained by**: Community (fwcd)

**TextMate Grammar**:
- **Repository**: https://github.com/nishtahir/language-kotlin
- **Scope**: `source.kotlin`

**Debug Adapter**: Uses Java debugging
- Via `java-debug` (Microsoft)
- Kotlin compiles to JVM bytecode
- Uses JDWP (Java Debug Wire Protocol)

### IDE Support

| IDE               | LSP Support | DAP Support  | Implementation                             |
|-------------------|-------------|--------------|--------------------------------------------|
| **VSCode**        | ✅ Yes      | ✅ Yes       | Kotlin extension + kotlin-language-server  |
| **IntelliJ IDEA** | ✅ Yes      | ✅ Yes       | Built-in (JetBrains, not LSP)              |
| **Vim/Neovim**    | ✅ Yes      | ⚠️ Via Java | kotlin-language-server + nvim-jdtls        |
| **Emacs**         | ✅ Yes      | ⚠️ Via Java | kotlin-language-server + dap-mode          |

### Key Features Implemented

- ✅ Code completion
- ✅ Go to definition/references
- ✅ Hover information
- ✅ Diagnostics (compile errors)
- ⚠️ Limited refactoring (rename only)
- ⚠️ No code actions yet

### Debugging

- Debugs Kotlin via JVM bytecode
- Uses Java debuggers (JDWP)
- Source mapping: Kotlin → JVM bytecode
- Breakpoints work at Kotlin source level
- Variable names preserved (with debug info)

### Lessons Learned

✅ **Successes**:
- Community project shows LSP is accessible
- Reuses Kotlin compiler
- Good enough for basic usage

⚠️ **Challenges**:
- IntelliJ IDEA's built-in support is much better
- Kotlin compiler wasn't designed for incremental IDE use
- Limited resources (community-driven)
- JVM debugging adds complexity

---

## 7. Java - Eclipse JDT.LS

**Status**: ⭐ Eclipse-based LSP server

> **Note**: Highly relevant for XTC as both target the JVM.

### Components

**LSP Server**: `eclipse.jdt.ls`
- **Repository**: https://github.com/eclipse-jdt/eclipse.jdt.ls
- **Language**: Java
- **Architecture**: Built on Eclipse JDT (Java Development Tools)
- **Maintained by**: Eclipse Foundation + Microsoft

**TextMate Grammar**:
- **Repository**: Built into most editors
- **Scope**: `source.java`

**Debug Adapter**: `java-debug`
- **Repository**: https://github.com/microsoft/java-debug
- **Protocol**: DAP over JDWP
- **Supports**: JVM debugging

### IDE Support

| IDE               | LSP Support | DAP Support | Implementation                             |
|-------------------|-------------|-------------|--------------------------------------------|
| **VSCode**        | ✅ Yes      | ✅ Yes      | Java extension pack + jdt.ls + java-debug  |
| **IntelliJ IDEA** | ✅ Yes      | ✅ Yes      | Built-in (JetBrains, not LSP)              |
| **Vim/Neovim**    | ✅ Yes      | ✅ Yes      | nvim-jdtls + nvim-dap                      |
| **Emacs**         | ✅ Yes      | ✅ Yes      | lsp-java + dap-mode                        |

### Key Features Implemented

- ✅ Code completion (context-aware)
- ✅ Go to definition/implementation/references
- ✅ Rename refactoring
- ✅ Extract method/variable
- ✅ Organize imports
- ✅ Quick fixes (auto-import, etc.)
- ✅ Formatter
- ✅ Maven/Gradle integration

### Debugging (java-debug)

- DAP adapter over JDWP
- Breakpoints (line, conditional, exception)
- Variable inspection (all Java types)
- Expression evaluation (Java expressions)
- Hot code replacement (limited)
- Remote debugging
- Multi-threaded debugging

### Lessons Learned

✅ **Successes**:
- Reuses mature Eclipse JDT compiler
- Very feature-rich
- Good incremental compilation
- Excellent Maven/Gradle integration

⚠️ **Challenges**:
- Eclipse JDT is complex and heavyweight
- Startup time can be slow
- Memory usage is high
- Java version compatibility

---

## Comparison Matrix

| Language       | LSP Server                       | Written In | Reuses Compiler?     | DAP Adapter           | Debugger Backend | IDE Coverage | Maturity |
|----------------|----------------------------------|------------|----------------------|-----------------------|------------------|--------------|----------|
| **Rust**       | rust-analyzer                    | Rust       | ✅ Yes (rustc API)   | lldb-vscode, codelldb | LLDB (native)    | Excellent    | Mature   |
| **TypeScript** | typescript-language-server       | TypeScript | ✅ Yes (TSC API)     | vscode-js-debug       | V8 Inspector     | Excellent    | Mature   |
| **Python**     | Pylance, Jedi, python-lsp-server | Python/TS  | ⚠️ Partial           | debugpy               | pdb + hooks      | Excellent    | Mature   |
| **Go**         | gopls                            | Go         | ✅ Yes (go/ast)      | delve                 | Delve (native)   | Excellent    | Mature   |
| **C/C++**      | clangd                           | C++        | ✅ Yes (Clang)       | lldb-vscode, cppdbg   | LLDB/GDB         | Excellent    | Mature   |
| **Kotlin**     | kotlin-language-server           | Kotlin     | ✅ Yes (kotlinc)     | java-debug            | JDWP             | Good         | Growing  |
| **Java**       | eclipse.jdt.ls                   | Java       | ✅ Yes (Eclipse JDT) | java-debug            | JDWP             | Excellent    | Mature   |

---

## Key Takeaways for XTC/Ecstasy

Based on this survey, the most relevant patterns for XTC are:

1. **Reuse Compiler Infrastructure** - All successful LSP servers leverage their language's compiler
2. **JVM-based patterns** - Kotlin and Java examples show how to debug JVM bytecode languages
3. **Community-driven is viable** - kotlin-language-server shows a community project can succeed
4. **Incremental compilation matters** - Performance depends on avoiding full recompilation
5. **DAP is simpler than expected** - Wrapping existing debuggers with DAP is straightforward
