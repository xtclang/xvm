# Language Support for XTC (Ecstasy)

## Executive Summary

This document outlines a comprehensive plan to create IDE-independent language support for XTC (Ecstasy), leveraging modern protocols like LSP, DAP, and TextMate grammars to provide syntax highlighting, code intelligence, debugging, and more across multiple IDEs.

## Table of Contents

1. [Understanding XTC/Ecstasy](#understanding-xtcecstasy)
2. [Current State of Tooling](#current-state-of-tooling)
3. [IDE-Independent Language Support Technologies](#ide-independent-language-support-technologies)
4. [Why DSL Representation is Beneficial](#why-dsl-representation-is-beneficial)
5. [Comprehensive Implementation Plan](#comprehensive-implementation-plan)
6. [Architecture Overview](#architecture-overview)
7. [Phased Rollout Strategy](#phased-rollout-strategy)

---

## Understanding XTC/Ecstasy

### What is Ecstasy?

**Ecstasy** is a modern application programming language designed for:
- Modular development
- Long-term sustainability of secure "serverless cloud" applications
- Reactive, event-driven, service- and fiber-based execution
- Container-based architecture

**Current Version**: 0.4.4-SNAPSHOT (pre-1.0, not production-ready)

**Website**: https://xtclang.org/

### File Types

- **`.x`** - Ecstasy source code files (human-readable)
- **`.xtc`** - Compiled Ecstasy modules (binary format)

### Example HelloWorld.x

```ecstasy
module HelloWorld {
    void run() {
        @Inject Console console;
        console.print("Hello World!");
    }
}
```

### Key Language Features

- First-class modules with versioning and conditionality
- First-class functions with currying and partial application
- Type-safe object orientation with auto-narrowing types
- Type inference
- Deeply-immutable types
- Asynchronous services (async/await, @Future promises)
- Software containers with resource injection
- Transitively-closed, immutable type systems

---

## Current State of Tooling

### What EXISTS Today

#### 1. Compiler Infrastructure ✅

**Location**: `javatools/src/main/java/org/xvm/compiler/`

- **Lexer** (`org.xvm.compiler.Lexer`) - Tokenizes source into Tokens
- **Parser** (`org.xvm.compiler.Parser`) - Recursive descent parser building AST
- **Compiler** (`org.xvm.tool.Compiler`) - Converts `.x` to `.xtc` modules
- **FileStructure** (`org.xvm.asm.FileStructure`) - Binary format serialization

#### 2. Grammar Definition ✅

**Location**: `doc/bnf.x` (34KB, ~200 rules)

Formal BNF grammar covering:
- Module, package, class, interface, service, const, enum, mixin declarations
- Type system with generics and constraints
- Control flow (if/else, loops, switch/case)
- Exception handling
- Annotations and modifiers
- Full expression syntax

**Language Specification**: `doc/x.md` (209KB) - Comprehensive draft specification

#### 3. Gradle Plugin ✅

**Location**: `plugin/`
**Plugin ID**: `org.xtclang.xtc`

**Available Tasks**:
- `XtcCompileTask` - Compiles `.x` files to `.xtc` modules
- `XtcRunTask` - Executes Ecstasy modules
- `XtcVersionTask` - Reports version info
- `XtcExtractXdkTask` - Extracts XDK dependencies
- `XtcLauncherTask` - Creates executables

**Published**: Gradle Plugin Portal

#### 4. Runtime ⚠️

**Status**: Proof-of-Concept interpreter (intentionally slow)
**Location**: `javatools/src/main/java/org/xvm/runtime/`

#### 5. Console Debugger ✅

**Status**: Fully functional command-line debugger
**Location**: `javatools/src/main/java/org/xvm/runtime/DebugConsole.java`

**Key Features**:
- **Breakpoints**: Line breakpoints, conditional breakpoints, exception breakpoints
- **Stepping**: Step over, step into, step out, step to line
- **Variable Inspection**: View local variables, instance fields, watches
- **Expression Evaluation**: Evaluate expressions at runtime using `EvalCompiler`
- **Call Stack Navigation**: Navigate frame stack, view all frames
- **Watch System**: Add watches on objects, properties, and array elements
- **Service/Fiber Visualization**: View all containers, services, and fiber states
- **Interactive Console**: JLine-based console with command history

**Architecture**:
```java
// Interface implemented by DebugConsole
public interface Debugger {
    int activate(Frame ctx, int iPC);
    int checkBreakPoint(Frame frame, int iPC);
    void onReturn(Frame frame);
    int checkBreakPoint(Frame frame, ExceptionHandle hEx);
}
```

**Commands Available**:
- `B`, `B+`, `B-`, `BT` - Breakpoint management
- `BC` - Conditional breakpoints
- `BE+`, `BE-` - Exception breakpoints
- `S`, `I`, `O`, `SL`, `R` - Stepping/running
- `E`, `EM` - Expression evaluation
- `WO`, `WR`, `W-` - Watch management
- `F` - Frame navigation
- `VD`, `VC`, `VF` - View modes (debugger, console, services/fibers)
- `D`, `DS`, `DI` - Variable display options

**Integration with Runtime**:
- Hooks into `Frame.checkBreakPoint()` during execution
- Uses `EvalCompiler` for runtime expression evaluation
- Freezes time in containers during debugging
- Supports debugging across service boundaries

**Limitation**: Console-only (not IDE-integrated)

#### 6. JIT Compiler (Java Bytecode) 🚧

**Status**: Under active development (experimental)
**Location**:
- `javatools/src/main/java/org/xvm/javajit/` - JIT compiler
- `javatools_jitbridge/` - Bridge between Ecstasy and Java runtime

**Purpose**: First JIT prototype that compiles XTC bytecode to Java bytecode

**Architecture**:
```
┌──────────────┐     ┌─────────────┐     ┌──────────────┐
│   .xtc       │────►│  JitConnector│────►│ Java Bytecode│
│  (XVM        │     │  TypeSystem  │     │   Classes    │
│  bytecode)   │     │  Linker      │     │              │
└──────────────┘     └─────────────┘     └──────────────┘
```

**Key Components**:
- **`Builder`**: Base class for JIT class builders, generates Java bytecode
- **`TypeSystem`**: Maps Ecstasy types to Java types/classes
- **`JitConnector`**: Entry point, loads modules and creates containers
- **`ModuleLoader`**: Custom ClassLoader for generated bytecode
- **`Linker`**: Links modules together into a coherent type system
- **`Ctx`**: Execution context (via ScopedValue for thread safety)

**Generated Output**:
- Pure Java classes from Ecstasy classes
- Uses Java 21+ ClassFile API for bytecode generation
- Bridge classes in `javatools_jitbridge` provide native implementations

**Current Capabilities**:
- Compiles Ecstasy modules to executable Java bytecode
- Supports basic method invocation
- Handles simple data types (Int64, String, Boolean, etc.)
- Container and service management

**Future Uncertainty**:
**IMPORTANT**: This is a **prototype** JIT implementation. The final JIT target is **not decided**:
- Could remain Java bytecode
- Could switch to LLVM IR
- Could switch to another backend (GraalVM, custom VM, etc.)

**Implications for IDE Support**:
- Debug information must be flexible (not assume Java bytecode)
- DAP implementation should work with interpreter AND JIT
- Source mapping is critical (bytecode ↔ source lines)

### What DOES NOT Exist Yet

- ❌ Language Server Protocol (LSP) implementation
- ❌ Debug Adapter Protocol (DAP) implementation
  - *Note: Console debugger exists, but not IDE-integrated via DAP*
- ❌ IDE plugins (IntelliJ, VSCode, Eclipse, etc.)
- ❌ Syntax highlighting definitions
- ❌ Code completion/intelligence
- ❌ Go-to-definition, find references
- ❌ Refactoring tools
- ❌ Type hierarchy visualization

**From README**: "We will also work on IDE Language support as soon as we have enough cycles to do so"

---

## IDE-Independent Language Support Technologies

### 1. Language Server Protocol (LSP)

#### Overview

**LSP** defines a JSON-RPC protocol between an editor/IDE and a language server that provides language features.

**Version**: 3.17 (latest as of 2025)
**Created by**: Microsoft
**Specification**: https://microsoft.github.io/language-server-protocol/

#### Key Benefits

**Write Once, Support Everywhere**:
Instead of:
- Python plugin for VSCode
- Python plugin for Sublime
- Python plugin for Vim
- Python plugin for IntelliJ

You create:
- One Python Language Server

#### Capabilities Provided

1. **Code Intelligence**:
   - Autocomplete (textDocument/completion)
   - Hover tooltips (textDocument/hover)
   - Signature help (textDocument/signatureHelp)

2. **Navigation**:
   - Go to definition (textDocument/definition)
   - Find references (textDocument/references)
   - Go to implementation (textDocument/implementation)
   - Go to type definition (textDocument/typeDefinition)

3. **Code Actions**:
   - Refactoring (textDocument/codeAction)
   - Quick fixes
   - Organize imports

4. **Diagnostics**:
   - Real-time error/warning reporting
   - Semantic validation

5. **Formatting**:
   - Document formatting (textDocument/formatting)
   - Range formatting (textDocument/rangeFormatting)

6. **Symbols**:
   - Document symbols (textDocument/documentSymbol)
   - Workspace symbols (workspace/symbol)

#### Supported IDEs/Editors

- Visual Studio Code (native support)
- IntelliJ IDEA (2023.2+, including PyCharm 2025.1+)
- Neovim (via nvim-lspconfig)
- Emacs (via lsp-mode)
- Sublime Text (via LSP package)
- Eclipse (via lsp4e)
- Atom
- Vim (via various plugins)

#### Architecture

```
┌──────────────┐                    ┌──────────────────┐
│              │   JSON-RPC over    │                  │
│   IDE/Editor │◄──────────────────►│ Language Server  │
│              │   stdio/socket/etc │                  │
└──────────────┘                    └──────────────────┘
                                             │
                                             ▼
                                    ┌──────────────────┐
                                    │                  │
                                    │   Compiler API   │
                                    │   Parser/Lexer   │
                                    │   Type System    │
                                    │                  │
                                    └──────────────────┘
```

#### Implementation Approaches

**Option 1: Native Language Implementation**
- Write LSP server in language of choice (Java for Ecstasy makes sense)
- Full access to existing compiler infrastructure
- Better performance

**Option 2: Use LSP Framework**
- Eclipse LSP4J (Java-based LSP implementation)
- Microsoft's LSP SDK (Node.js/TypeScript)
- Other language-specific frameworks

#### Communication Protocol

**Transport**: JSON-RPC 2.0 over:
- Standard input/output (stdio)
- Sockets
- Named pipes

**Message Format**:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "textDocument/completion",
  "params": {
    "textDocument": {
      "uri": "file:///path/to/file.x"
    },
    "position": {
      "line": 5,
      "character": 10
    }
  }
}
```

---

### 2. TextMate Grammars (Syntax Highlighting)

#### Overview

**TextMate grammars** define syntax highlighting rules using regular expressions. Originally created for TextMate editor, now widely adopted.

**Format**: JSON or plist
**Engine**: Oniguruma regular expressions

#### Key Benefits

- **Declarative**: Define patterns, not imperative code
- **Widely Supported**: Works in VSCode, IntelliJ, Sublime, Atom, etc.
- **Fast**: Regex-based tokenization is efficient
- **Portable**: Same grammar file works across editors

#### Supported IDEs/Editors

**Native Support**:
- Visual Studio Code (primary tokenization engine)
- Sublime Text
- Atom
- TextMate (original)

**Import/Bundle Support**:
- IntelliJ IDEA (basic syntax highlighting only)
- Eclipse (via TextMate plugin)

#### Grammar Structure

**File**: `.tmLanguage.json` or `.tmLanguage`

**Basic Structure**:
```json
{
  "scopeName": "source.ecstasy",
  "name": "Ecstasy",
  "fileTypes": ["x"],
  "patterns": [
    {
      "name": "keyword.control.ecstasy",
      "match": "\\b(if|else|while|for|switch|case)\\b"
    },
    {
      "name": "storage.type.ecstasy",
      "match": "\\b(module|class|interface|service|const|enum|mixin)\\b"
    },
    {
      "name": "string.quoted.double.ecstasy",
      "begin": "\"",
      "end": "\"",
      "patterns": [
        {
          "name": "constant.character.escape.ecstasy",
          "match": "\\\\."
        }
      ]
    }
  ]
}
```

#### Scope Naming Convention

Standard naming follows TextMate convention:
- `keyword.control` - Control flow keywords
- `storage.type` - Type declarations
- `entity.name.function` - Function names
- `variable.parameter` - Parameters
- `comment.line` - Line comments
- `string.quoted` - String literals
- `constant.numeric` - Numbers

#### Limitations

**TextMate Alone Provides**:
- ✅ Syntax highlighting
- ✅ Basic bracket matching

**TextMate Does NOT Provide**:
- ❌ Code completion
- ❌ Semantic analysis
- ❌ Type checking
- ❌ Refactoring
- ❌ Go-to-definition

**Conclusion**: TextMate is necessary for syntax highlighting but insufficient for full language support. Must be combined with LSP.

---

### 3. Debug Adapter Protocol (DAP)

#### Overview

**DAP** defines the abstract protocol between a development tool and a debugger/runtime.

**Version**: 1.70.0 (latest)
**Created by**: Microsoft
**Specification**: https://microsoft.github.io/debug-adapter-protocol/

#### Key Benefits

**Write Once, Debug Everywhere**:
- One debug adapter implementation
- Works in VSCode, IntelliJ, Vim, Emacs, etc.
- Consistent debugging experience

#### Capabilities Provided

1. **Breakpoints**:
   - Line breakpoints
   - Conditional breakpoints
   - Function breakpoints
   - Data breakpoints

2. **Execution Control**:
   - Start/stop/pause
   - Step over/into/out
   - Continue execution

3. **Stack Inspection**:
   - Stack traces
   - Stack frame variables
   - Scoped variables

4. **Expression Evaluation**:
   - Watch expressions
   - REPL/console evaluation
   - Hover evaluation

5. **Advanced Features**:
   - Exception breakpoints
   - Logpoints (breakpoints that log)
   - Multi-threaded debugging

#### Architecture

```
┌──────────────┐                    ┌──────────────────┐
│              │   JSON-RPC over    │                  │
│   IDE/Editor │◄──────────────────►│ Debug Adapter    │
│              │   stdio/socket/etc │                  │
└──────────────┘                    └──────────────────┘
                                             │
                                             ▼
                                    ┌──────────────────┐
                                    │                  │
                                    │   Debugger/      │
                                    │   Runtime        │
                                    │                  │
                                    └──────────────────┘
```

#### Supported IDEs/Editors

- Visual Studio Code (native support)
- IntelliJ IDEA (via plugins)
- Neovim (via nvim-dap)
- Emacs (via dap-mode)
- CodeLite
- Eclipse

#### Implementation Approaches

**For Ecstasy**:
1. Instrument the Ecstasy runtime/interpreter
2. Add debugging hooks (breakpoint support, step control, etc.)
3. Create DAP adapter that communicates with instrumented runtime
4. Handle DAP protocol translation

**Communication Protocol**:
Similar to LSP, uses JSON-RPC 2.0

**Example Message**:
```json
{
  "type": "request",
  "seq": 1,
  "command": "setBreakpoints",
  "arguments": {
    "source": {
      "path": "/path/to/HelloWorld.x"
    },
    "breakpoints": [
      {"line": 5, "condition": "x > 10"}
    ]
  }
}
```

#### Recent Developments (2024-2025)

- **GDB DAP Support**: GNU Debugger now supports DAP
- **GraalVM**: Built-in DAP implementation
- **Wider adoption**: More tools supporting DAP

---

### 4. Comparison Summary

| Feature | LSP | TextMate | DAP |
|---------|-----|----------|-----|
| **Purpose** | Code intelligence | Syntax highlighting | Debugging |
| **Complexity** | High | Low | High |
| **IDE Support** | Excellent | Excellent | Good |
| **Implementation Effort** | Large | Small | Large |
| **Required for MVP** | No* | Yes | No |
| **Full IDE Experience** | Yes | No | Yes |

*MVP = Minimum Viable Product (basic syntax highlighting)

---

## Why DSL Representation is Beneficial

### 1. Leveraging Existing Compiler Infrastructure

Ecstasy already has:
- ✅ Robust lexer (`org.xvm.compiler.Lexer`)
- ✅ Recursive descent parser (`org.xvm.compiler.Parser`)
- ✅ Complete AST representation (`org.xvm.compiler.ast.*`)
- ✅ Type system implementation
- ✅ Semantic analysis
- ✅ Error reporting

**Benefit**: LSP server can directly use these components without reimplementation.

### 2. Single Source of Truth

**BNF Grammar** (`doc/bnf.x`):
- 34KB, ~200 rules
- Formal specification of language syntax
- Already maintained by language developers

**Benefit**:
- TextMate grammar generated from BNF (or written to match)
- LSP server uses same parser as compiler
- Consistency between compilation and IDE features
- Changes to language automatically reflected in tooling

### 3. Rich Semantic Information

DSL representation via AST provides:
- **Type information**: Inferred and explicit types
- **Symbol resolution**: Variables, functions, classes
- **Scope information**: Nested scopes, imports
- **Reference tracking**: Where symbols are defined/used
- **Documentation**: Comments, annotations

**Benefit**: Enables advanced IDE features:
- Accurate code completion
- Type-aware refactoring
- Semantic highlighting (different colors for different symbol types)
- Intelligent navigation

### 4. Incremental Compilation

Modern IDE features require **fast** response times (<100ms).

**DSL Approach**:
- Parse only changed files
- Maintain AST cache
- Incremental symbol resolution
- Partial type checking

**Benefit**: Real-time feedback without full recompilation

### 5. Error Recovery

Users expect IDE to work with **broken code**.

**DSL Parser Features**:
- Error recovery strategies
- Partial AST construction
- Heuristic error correction

**Benefit**: Code completion and navigation work even with syntax errors

### 6. Platform Independence

**DSL Representation**: Abstract syntax tree (AST)
- Not tied to Java, not tied to JVM
- Can be serialized/deserialized
- Can be consumed by tools in any language

**Benefit**: Future flexibility for:
- Browser-based IDEs
- Native language server implementations
- Third-party tool integration

### 7. Testing and Validation

**DSL Grammar**:
- Can be tested independently
- Fuzzing for correctness
- Comparison with reference implementation

**Benefit**: Higher quality tooling, fewer bugs

---

## Existing Infrastructure: Key Advantages

### 1. Console Debugger - Significant Head Start

**What This Means**: 60-70% of debugging functionality already exists!

**Already Implemented**:
- ✅ Breakpoint system (line, conditional, exception)
- ✅ Stepping logic (over, into, out, to line)
- ✅ Variable inspection and watches
- ✅ Expression evaluation using `EvalCompiler`
- ✅ Stack frame navigation
- ✅ Service and fiber visualization
- ✅ Ecstasy-specific features (async, services, containers)

**What's Missing**: Only the IDE integration layer (DAP protocol adapter)

**Impact on Timeline**:
- **Original estimate**: 8-12 weeks for Phase 5 (full debugger from scratch)
- **Revised estimate**: 4-6 weeks for Phase 5 (DAP adapter only)
- **Savings**: ~6 weeks of development time

### 2. JIT Compiler - Performance Future

**What This Means**: Path to production-quality performance, but still experimental.

**Current State**:
- 🚧 Prototype JIT compiling XTC bytecode → Java bytecode
- 🚧 Uses Java 21+ ClassFile API for bytecode generation
- 🚧 Basic functionality working (simple types, method calls, containers)

**Design Implications for IDE Support**:

**CRITICAL**: The final JIT backend is undecided (Java bytecode, LLVM IR, or other). IDE tooling must be **backend-agnostic**.

**Requirements**:
1. **Source Mapping**: Must maintain `.x` source ↔ runtime location mapping regardless of backend
2. **Debug Information**: Generate appropriate debug info for each backend:
   - Java bytecode → Line Number Tables, Local Variable Tables
   - LLVM IR → DWARF debug information
   - Other backends → Backend-specific formats

3. **Unified Debug Interface**: DAP adapter should use abstract debug API:
   ```
   Interface: DebugBackend
   - setBreakpoint(sourceFile, line)
   - step(mode: Over|Into|Out)
   - evaluateExpression(expr, frame)
   - getStackTrace()
   - getVariables(frame, scope)
   ```

4. **Runtime Mode Detection**: IDE should detect and adapt to runtime mode:
   - Interpreter mode: Use existing `DebugConsole` infrastructure
   - JIT mode: Use backend-specific debug hooks (JVMTI, LLDB, etc.)

**Benefits**:
- Future-proof design
- Can support multiple backends simultaneously
- Debugging works regardless of JIT choice

### 3. Compiler Infrastructure - Solid Foundation

**What This Means**: LSP server can reuse production compiler components.

**Reusable Components**:
- ✅ `Lexer` - Tokenization
- ✅ `Parser` - AST construction
- ✅ `EvalCompiler` - Expression evaluation (for code completion/hover)
- ✅ Type system - Type inference and checking
- ✅ `ConstantPool` - Symbol resolution
- ✅ Error reporting - Diagnostic messages

**Impact**:
- No need to reimplement parsing logic
- Consistency between compiler and IDE
- Single source of truth for language semantics

---

## Comprehensive Implementation Plan

### Phase 1: Foundation (Syntax Highlighting) - 2-4 weeks

#### Objective
Basic syntax highlighting in VSCode and IntelliJ IDEA.

#### Deliverables

**1.1 Create TextMate Grammar**
- File: `syntaxes/ecstasy.tmLanguage.json`
- Based on `doc/bnf.x` grammar
- Cover:
  - Keywords (module, class, interface, if, else, etc.)
  - Types (built-in and user-defined)
  - Strings, numbers, booleans
  - Comments (line and block)
  - Annotations (@Inject, @Future, etc.)
  - Operators

**1.2 VSCode Extension (Basic)**
- Extension manifest: `package.json`
- Register language: `source.ecstasy`
- File associations: `.x` files
- TextMate grammar reference
- Basic icon for `.x` files

**1.3 IntelliJ TextMate Bundle**
- Create TextMate bundle
- Import into IntelliJ
- Test basic highlighting

#### Success Criteria
- ✅ `.x` files display with syntax highlighting in VSCode
- ✅ `.x` files display with syntax highlighting in IntelliJ
- ✅ All major language constructs highlighted correctly
- ✅ No noticeable performance issues

#### Example: VSCode package.json

```json
{
  "name": "ecstasy-language-support",
  "displayName": "Ecstasy Language Support",
  "description": "Syntax highlighting for Ecstasy (.x) files",
  "version": "0.1.0",
  "publisher": "xtclang",
  "engines": {
    "vscode": "^1.80.0"
  },
  "categories": ["Programming Languages"],
  "contributes": {
    "languages": [{
      "id": "ecstasy",
      "aliases": ["Ecstasy", "ecstasy", "xtc"],
      "extensions": [".x"],
      "configuration": "./language-configuration.json"
    }],
    "grammars": [{
      "language": "ecstasy",
      "scopeName": "source.ecstasy",
      "path": "./syntaxes/ecstasy.tmLanguage.json"
    }]
  }
}
```

---

### Phase 2: Language Server Foundation - 6-10 weeks

#### Objective
Implement core LSP server with basic code intelligence.

#### Deliverables

**2.1 LSP Server Scaffolding**
- New module: `lsp-server/` (or `javatools_lsp/`)
- Use **Eclipse LSP4J** framework (Java-based)
- Basic server initialization
- Handle `initialize`, `initialized`, `shutdown` requests

**2.2 Document Management**
- Track open documents (`textDocument/didOpen`)
- Handle edits (`textDocument/didChange`)
- Close documents (`textDocument/didClose`)
- Maintain in-memory document state

**2.3 Parsing Integration**
- Integrate existing `org.xvm.compiler.Lexer`
- Integrate existing `org.xvm.compiler.Parser`
- Build AST for each document
- Cache AST for performance

**2.4 Diagnostics (Errors/Warnings)**
- Implement `textDocument/publishDiagnostics`
- Map compiler errors to LSP diagnostics
- Real-time error reporting as user types
- Error recovery for partial code

**2.5 Document Symbols**
- Implement `textDocument/documentSymbol`
- Extract symbols from AST:
  - Modules
  - Classes/interfaces/services
  - Methods/functions
  - Properties
- Enable "Outline" view in IDE

**2.6 Basic Completion**
- Implement `textDocument/completion`
- Keyword completion
- Snippet completion (for common patterns)
- No semantic completion yet (Phase 3)

#### Architecture

```
┌─────────────────────────────────────────────────┐
│           Ecstasy Language Server               │
│                                                 │
│  ┌───────────────┐      ┌─────────────────┐   │
│  │   LSP4J       │      │  Compiler API   │   │
│  │   Framework   │◄────►│                 │   │
│  │               │      │  - Lexer        │   │
│  └───────┬───────┘      │  - Parser       │   │
│          │              │  - AST          │   │
│          │              │  - ErrorLog     │   │
│          ▼              └─────────────────┘   │
│  ┌───────────────┐                            │
│  │  Document     │                            │
│  │  Manager      │                            │
│  └───────────────┘                            │
│                                                │
└────────────────┬────────────────────────────────┘
                 │
                 │ JSON-RPC (stdio)
                 │
                 ▼
        ┌─────────────────┐
        │   IDE/Editor    │
        └─────────────────┘
```

#### Success Criteria
- ✅ LSP server starts and responds to initialization
- ✅ Real-time syntax errors appear in IDE
- ✅ Document outline shows symbols
- ✅ Basic keyword completion works
- ✅ No crashes or hangs during normal editing

---

### Phase 3: Advanced Code Intelligence - 8-12 weeks

#### Objective
Full semantic understanding with type-aware features.

#### Deliverables

**3.1 Symbol Table / Index**
- Build symbol table from AST
- Track:
  - All types (classes, interfaces, etc.)
  - All methods/functions
  - All properties
  - All local variables
- Scope tracking (module, package, class, method, block)
- Import resolution

**3.2 Type Resolution**
- Integrate existing type system from compiler
- Resolve all type references
- Compute inferred types
- Track generic type parameters

**3.3 Go to Definition**
- Implement `textDocument/definition`
- Find symbol at cursor position
- Jump to declaration

**3.4 Find References**
- Implement `textDocument/references`
- Find all usages of symbol
- Search across workspace

**3.5 Hover Information**
- Implement `textDocument/hover`
- Show type information
- Show documentation (from comments)
- Show signature

**3.6 Smart Completion**
- Context-aware completion
- Member access completion (after `.`)
- Type-aware suggestions
- Import auto-completion

**3.7 Signature Help**
- Implement `textDocument/signatureHelp`
- Show function parameters while typing
- Highlight current parameter

**3.8 Rename Refactoring**
- Implement `textDocument/rename`
- Rename symbol across workspace
- Update all references

**3.9 Code Actions**
- Implement `textDocument/codeAction`
- Quick fixes for common errors
- Import suggestions
- Extract method/variable

#### Success Criteria
- ✅ Go to definition works for all symbol types
- ✅ Find references finds all usages accurately
- ✅ Hover shows correct type and documentation
- ✅ Completion suggests relevant symbols only
- ✅ Rename updates all references correctly
- ✅ Performance: <100ms response time for most operations

---

### Phase 4: IDE Extensions - 4-6 weeks

#### Objective
Polished IDE extensions leveraging LSP server.

#### Deliverables

**4.1 VSCode Extension (Full)**
- Updated `package.json` with LSP client
- Start LSP server automatically
- Configure server settings
- Add commands:
  - "Ecstasy: Restart Language Server"
  - "Ecstasy: Compile Module"
  - "Ecstasy: Run Module"
- Integrate with Gradle tasks

**4.2 IntelliJ IDEA Plugin**
- Use IntelliJ LSP support (2023.2+)
- Configure LSP server connection
- Add tool window for XTC output
- Gradle integration
- Run configurations for `.x` files

**4.3 Configuration Options**
- Settings for LSP server
- Path to XDK
- Compilation options
- Formatting preferences

#### Example: VSCode Extension with LSP Client

```typescript
import * as path from 'path';
import * as vscode from 'vscode';
import {
  LanguageClient,
  LanguageClientOptions,
  ServerOptions,
  TransportKind
} from 'vscode-languageclient/node';

let client: LanguageClient;

export function activate(context: vscode.ExtensionContext) {
  // Path to language server JAR
  const serverPath = context.asAbsolutePath(
    path.join('server', 'ecstasy-lsp-server.jar')
  );

  // Server options: launch Java process
  const serverOptions: ServerOptions = {
    run: {
      command: 'java',
      args: ['-jar', serverPath],
      transport: TransportKind.stdio
    },
    debug: {
      command: 'java',
      args: ['-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005', '-jar', serverPath],
      transport: TransportKind.stdio
    }
  };

  // Client options
  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ scheme: 'file', language: 'ecstasy' }],
    synchronize: {
      fileEvents: vscode.workspace.createFileSystemWatcher('**/*.x')
    }
  };

  // Create and start client
  client = new LanguageClient(
    'ecstasyLanguageServer',
    'Ecstasy Language Server',
    serverOptions,
    clientOptions
  );

  client.start();
}

export function deactivate(): Thenable<void> | undefined {
  if (!client) {
    return undefined;
  }
  return client.stop();
}
```

#### Success Criteria
- ✅ One-click installation from VSCode marketplace
- ✅ One-click installation from JetBrains marketplace
- ✅ LSP features work seamlessly in both IDEs
- ✅ Good user documentation

---

### Phase 5: Debugging Support - 4-6 weeks

#### Objective
IDE-integrated debugging via DAP, leveraging existing console debugger infrastructure.

#### Status
**MAJOR ADVANTAGE**: Ecstasy already has a fully functional console debugger (`DebugConsole.java`) with all core debugging features implemented. This significantly reduces the work required.

#### Deliverables

**5.1 DAP Adapter Implementation** ✨ *Primary Work*
- New module: `debug-adapter/`
- Implement DAP protocol (JSON-RPC)
- **Reuse existing**: `Debugger` interface and `DebugConsole` implementation
- Translate DAP requests to `DebugConsole` commands
- Handle launch/attach configurations
- Map source locations to runtime state

**5.2 Adapt Existing Debugger for IDE Use** ✨ *Leverage Existing*
- **Already exists**: Breakpoint support (line, conditional, exception)
- **Already exists**: Step control (over/into/out, step to line)
- **Already exists**: Variable inspection (locals, fields, watches)
- **Already exists**: Expression evaluation via `EvalCompiler`
- **Already exists**: Stack inspection and frame navigation
- **New work**: Expose programmatic API for DAP adapter (currently console-driven)
- **New work**: Event-driven notifications (breakpoint hit, step complete, etc.)

**5.3 Runtime Backend Flexibility** 🔧 *Critical for JIT*
- **Challenge**: Must support BOTH interpreter AND JIT backends
- **Interpreter**: Direct integration with existing `DebugConsole`
- **JIT (Java Bytecode)**: Requires source mapping (`.x` source ↔ Java bytecode)
  - Use JVMTI (JVM Tool Interface) for JIT debugging
  - OR: Maintain interpreter debug hooks even when JIT is active
- **Future JIT (LLVM IR)**: Must be backend-agnostic
  - Design DAP adapter to use abstract debug interface
  - Runtime backend handles specifics (DWARF, source maps, etc.)

**5.4 Breakpoint Management**
- **Already exists**: Line breakpoints, conditional breakpoints, exception breakpoints
- **New work**: DAP protocol translation:
  - `setBreakpoints` → DebugConsole `B+`, `BC` commands
  - `setExceptionBreakpoints` → DebugConsole `BE+` commands
  - Breakpoint hit events → DAP `stopped` events

**5.5 Execution Control**
- **Already exists**: Step over (`S`), step into (`I`), step out (`O`), continue (`R`)
- **New work**: DAP protocol translation:
  - `continue`, `next`, `stepIn`, `stepOut` requests
  - `stopped` event emission on breakpoint/step completion

**5.6 Variable Inspection**
- **Already exists**: Local variables, instance variables, watches, expression evaluation
- **New work**: DAP protocol translation:
  - `scopes` → Frame variables
  - `variables` → Nested variable expansion
  - `evaluate` → `E` and `EM` commands (eval expressions)

**5.7 Stack Traces**
- **Already exists**: Call stack display, frame switching, service/fiber visualization
- **New work**: DAP protocol translation:
  - `stackTrace` → Frame list
  - `scopes` → Variable scopes per frame

**5.8 IDE Integration**
- VSCode debug extension configuration
- IntelliJ debug configuration (via DAP support)
- Launch configurations for common scenarios
- Support both interpreter and JIT modes

#### Example: VSCode launch.json

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "ecstasy",
      "request": "launch",
      "name": "Launch Ecstasy Program",
      "program": "${workspaceFolder}/src/HelloWorld.x",
      "stopOnEntry": false,
      "cwd": "${workspaceFolder}",
      "env": {}
    }
  ]
}
```

#### Architecture

**Interpreter Mode** (Existing Infrastructure):
```
┌─────────────┐         ┌──────────────┐         ┌─────────────────┐
│             │  DAP    │              │ Adapt   │  DebugConsole   │
│  IDE Debug  │◄───────►│    Debug     │ Existing│  (Interpreter)  │
│  UI         │  JSON   │    Adapter   │ Commands│  ✅ Fully       │
│             │  -RPC   │              │         │  Functional     │
└─────────────┘         └──────────────┘         └────────┬────────┘
                                                          │
                                                          ▼
                                                 ┌─────────────────┐
                                                 │  Debugger API   │
                                                 │  Frame          │
                                                 │  checkBreakPoint│
                                                 └─────────────────┘
```

**JIT Mode** (Future, Backend-Agnostic):
```
┌─────────────┐         ┌──────────────┐         ┌─────────────────┐
│             │  DAP    │              │         │  Debug Backend  │
│  IDE Debug  │◄───────►│    Debug     │◄────────┤  Abstraction    │
│  UI         │  JSON   │    Adapter   │         │                 │
│             │  -RPC   │              │         └────────┬────────┘
└─────────────┘         └──────────────┘                  │
                                                          ▼
                                    ┌──────────────────────────────────┐
                                    │       Runtime Backend            │
                                    │  ┌────────────┐  ┌────────────┐ │
                                    │  │ Interpreter│  │  JIT       │ │
                                    │  │ (Existing) │  │ (Java BC   │ │
                                    │  │            │  │  or LLVM)  │ │
                                    │  └────────────┘  └────────────┘ │
                                    └──────────────────────────────────┘
```

**Key Design Principle**: DAP adapter must NOT assume specific runtime backend.

#### Success Criteria
- ✅ Set breakpoints in `.x` files
- ✅ Hit breakpoints during execution (interpreter mode)
- ✅ Step through code line by line
- ✅ Inspect variables and expressions
- ✅ View call stack
- ✅ Handle Ecstasy-specific features (services, async/fibers)
- ✅ Work with both interpreter and JIT runtimes
- ✅ Backend-agnostic design (future-proof for LLVM/other JITs)

---

### Phase 6: Polish & Advanced Features - Ongoing

#### Deliverables

**6.1 Semantic Highlighting**
- Implement `textDocument/semanticTokens`
- Different colors for:
  - Types vs values
  - Mutable vs immutable
  - Local vs member variables
  - Parameters

**6.2 Code Formatting**
- Implement `textDocument/formatting`
- Define Ecstasy style guide
- Configurable options

**6.3 Code Lenses**
- Show references inline
- "Run" lens for main methods
- "Debug" lens

**6.4 Call Hierarchy**
- Implement `textDocument/prepareCallHierarchy`
- Show callers/callees

**6.5 Type Hierarchy**
- Show class/interface hierarchy
- Mixin composition visualization

**6.6 Workspace Symbols**
- Implement `workspace/symbol`
- Fast symbol search across project

**6.7 Inlay Hints**
- Show inferred types
- Show parameter names

**6.8 Performance Optimization**
- Incremental parsing
- Background indexing
- Caching strategies

---

## Architecture Overview

### Overall System Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                          IDE Layer                             │
│                                                                │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────┐          │
│  │   VSCode    │  │  IntelliJ   │  │    Vim/      │          │
│  │  Extension  │  │    Plugin   │  │    Emacs     │  ...     │
│  └──────┬──────┘  └──────┬──────┘  └──────┬───────┘          │
│         │                │                 │                  │
└─────────┼────────────────┼─────────────────┼──────────────────┘
          │                │                 │
          │  LSP/DAP       │  LSP/DAP        │  LSP/DAP
          │  (JSON-RPC)    │  (JSON-RPC)     │  (JSON-RPC)
          │                │                 │
          ▼                ▼                 ▼
┌────────────────────────────────────────────────────────────────┐
│                     Protocol Layer                             │
│                                                                │
│  ┌──────────────────────────┐  ┌─────────────────────────┐   │
│  │                          │  │                         │   │
│  │  Ecstasy Language Server │  │  Ecstasy Debug Adapter  │   │
│  │        (LSP)             │  │        (DAP)            │   │
│  │                          │  │                         │   │
│  └────────────┬─────────────┘  └───────────┬─────────────┘   │
│               │                            │                 │
└───────────────┼────────────────────────────┼─────────────────┘
                │                            │
                │                            │
                ▼                            ▼
┌────────────────────────────────────────────────────────────────┐
│                    Compiler/Runtime Layer                      │
│                                                                │
│  ┌─────────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │   Lexer     │  │  Parser  │  │   AST    │  │  Type    │  │
│  │             │─►│          │─►│          │─►│  System  │  │
│  └─────────────┘  └──────────┘  └──────────┘  └──────────┘  │
│                                                                │
│  ┌─────────────┐  ┌──────────┐                                │
│  │  Symbol     │  │ Ecstasy  │                                │
│  │  Table      │  │ Runtime  │  (with debug hooks)            │
│  └─────────────┘  └──────────┘                                │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

#### Language Server (LSP)
- Document lifecycle management
- Parsing and AST construction
- Symbol resolution and indexing
- Type checking
- Diagnostic reporting
- Code completion
- Navigation (definition, references)
- Refactoring

#### Debug Adapter (DAP)
- Launch/attach to runtime
- Breakpoint management
- Execution control
- Stack inspection
- Variable inspection
- Expression evaluation

#### Compiler/Runtime
- Lexical analysis
- Parsing
- Type system
- Code generation
- Execution (with debug support)

---

## Phased Rollout Strategy

### Priority Matrix

| Phase | Priority | Effort | User Value | Dependencies | Existing Infrastructure |
|-------|----------|--------|------------|--------------|-------------------------|
| Phase 1 | High | Low | Medium | None | ✅ BNF grammar exists |
| Phase 2 | High | Medium | High | Phase 1 | ✅ Compiler API reusable |
| Phase 3 | High | High | Very High | Phase 2 | ✅ Type system reusable |
| Phase 4 | Medium | Medium | High | Phase 2 | None |
| Phase 5 | Medium | **Low→Medium** | High | Phase 2, 4 | ✅ **DebugConsole exists!** |
| Phase 6 | Low | Medium | Medium | Phase 3, 5 | ✅ EvalCompiler reusable |

**Key Change**: Phase 5 effort reduced from "High" to "Low→Medium" due to existing debugger infrastructure.

### Minimal Viable Product (MVP)

**Target**: 3-4 months
**Includes**:
- Phase 1: Syntax highlighting ✅
- Phase 2: Basic LSP (diagnostics, symbols, basic completion) ✅
- Phase 4: VSCode extension ✅

**User Value**:
- Syntax-highlighted `.x` files
- Real-time error detection
- Basic navigation (outline, go to definition)

### Full IDE Experience

**Target**: 6-10 months (Revised from 8-12 months)
**Includes**:
- Phase 3: Advanced code intelligence ✅
- Phase 4: IntelliJ plugin ✅
- Phase 5: Debugging support ✅ (4-6 weeks instead of 8-12 weeks!)

**User Value**:
- Production-quality IDE experience
- Full refactoring capabilities
- Integrated debugging (leveraging existing console debugger)

**Timeline Improvement**: Existing `DebugConsole` saves ~6 weeks of development time.

### Long-term Vision

**Target**: 10+ months
**Includes**:
- Phase 6: Polish and advanced features ✅
- Performance optimization
- Additional IDE support (Vim, Emacs, etc.)
- Cloud/web-based IDE support
- JIT-aware debugging (when backend is finalized)

---

## Implementation Recommendations

### 1. Start with Phase 1

**Why**: Quick win, immediate user value, low complexity.

**Action Items**:
1. Study `doc/bnf.x` grammar
2. Write TextMate grammar in `ecstasy.tmLanguage.json`
3. Create minimal VSCode extension
4. Test with example `.x` files
5. Iterate on highlighting accuracy

**Timeline**: 2-4 weeks for one developer

### 2. Leverage Existing Infrastructure

**Critical**: Don't rewrite the compiler!

**Reuse**:
- `org.xvm.compiler.Lexer` - Tokenization
- `org.xvm.compiler.Parser` - AST construction
- `org.xvm.compiler.ast.*` - AST representation
- Type system implementation
- Error reporting

**Integration Approach**:
- Create thin LSP wrapper around compiler APIs
- Adapt error messages to LSP diagnostic format
- Expose symbol information from AST

### 3. Use Eclipse LSP4J for LSP Server

**Why Java**:
- Existing compiler is Java-based
- Direct access to compiler classes
- No marshaling/unmarshaling overhead
- Team expertise

**Why LSP4J**:
- Mature framework (used by many language servers)
- Handles protocol details
- Focus on language semantics, not protocol

**Alternative**: Could use Node.js/TypeScript, but would need:
- JNI bridge to compiler, OR
- Rewrite parser/compiler (not recommended), OR
- Run compiler as separate process (performance overhead)

### 4. Plan for Incremental Compilation

**Challenge**: Full recompilation is too slow for IDE feedback.

**Solution**:
- Parse only changed files
- Maintain AST cache
- Invalidate dependent files
- Lazy type resolution

**Implementation**:
- Track file dependencies
- Use virtual file system
- Implement dirty tracking

### 5. Test with Real Projects

**Critical**: Test with actual Ecstasy codebases, not just examples.

**Test Projects**:
- `xvm/lib_ecstasy/` - Core library
- `xvm/manualTests/` - Test cases
- `platform/` - Real-world usage

**Test Scenarios**:
- Large files (performance)
- Complex type hierarchies
- Heavy use of generics
- Syntax errors (error recovery)
- Incomplete code (during typing)

### 6. Iterate Based on User Feedback

**Process**:
1. Release early (Phase 1 MVP)
2. Gather feedback from community
3. Prioritize features based on real usage
4. Iterate quickly

**Channels**:
- GitHub issues
- xtclang community forums
- Direct user testing

### 7. Documentation

**Essential Documentation**:
- Installation guide (for each IDE)
- Feature showcase with GIFs/videos
- Troubleshooting guide
- Developer guide (for contributors)
- LSP server API documentation

### 8. Performance Benchmarks

**Metrics to Track**:
- Parse time (should be <50ms for typical file)
- Completion response time (<100ms)
- Diagnostics latency (<200ms after edit)
- Memory usage (should handle large workspaces)

**Tools**:
- LSP server logging
- VSCode/IntelliJ profiling
- Load testing with large projects

---

## Answers to Specific Questions

### Q: How can we use DSL to represent the xtc language?

**Answer**: Ecstasy already has a DSL representation through its:

1. **Formal BNF Grammar** (`doc/bnf.x`):
   - Defines the abstract syntax
   - Can be used to generate parsers (though current parser is hand-written)
   - Serves as single source of truth

2. **Abstract Syntax Tree** (`org.xvm.compiler.ast.*`):
   - In-memory DSL representation
   - Rich semantic information (types, scopes, symbols)
   - Already used by compiler

3. **Type System**:
   - First-class representation of types
   - Generics, constraints, inference
   - Core to language semantics

**For IDE Tooling**:
- LSP server works with AST representation
- TextMate grammar is derived from BNF (or written to match)
- Symbol table built from AST traversal

### Q: Why is DSL representation good?

**Answer**:

1. **Separation of Concerns**:
   - Syntax (BNF) separate from semantics (AST + type system)
   - Parsing separate from analysis
   - Analysis separate from code generation

2. **Reusability**:
   - Same parser for compiler and IDE
   - Same type checker for compilation and code completion
   - Same error reporting for command-line and real-time diagnostics

3. **Correctness**:
   - Single source of truth (BNF grammar)
   - Formal definition reduces ambiguity
   - Testable independently

4. **Maintainability**:
   - Language changes propagate automatically
   - No need to update multiple implementations
   - Easier to add new features

5. **Tooling Ecosystem**:
   - Other tools can consume DSL representation
   - Static analysis tools
   - Code generators
   - Documentation generators
   - Formatters/linters

6. **Performance**:
   - AST caching enables incremental compilation
   - Symbol table indexing enables fast lookup
   - Lazy evaluation for large codebases

7. **Debugging**:
   - AST can be inspected/visualized
   - Clear separation of compilation stages
   - Easier to diagnose issues

### Q: Is it possible to introduce IDE-independent language support for a new language like this?

**Answer**: **Yes, absolutely!** This is exactly what LSP and DAP were designed for.

**Evidence**:

1. **LSP Success Stories**:
   - Rust (rust-analyzer): Single LSP server, works in VSCode, IntelliJ, Vim, Emacs
   - TypeScript: Microsoft's LSP server, universal support
   - Python (Pylance/Jedi): LSP-based, works everywhere
   - C/C++ (clangd): LSP server by LLVM project

2. **DAP Success Stories**:
   - GDB: Now supports DAP (2024)
   - LLDB: DAP adapters available
   - Node.js: DAP for JavaScript/TypeScript debugging

3. **Ecstasy Advantages**:
   - ✅ Already has robust compiler infrastructure
   - ✅ Well-defined grammar (BNF)
   - ✅ Clear language specification
   - ✅ Active development community

**Requirements for Success**:

1. **Commitment**: Ongoing maintenance of LSP/DAP servers
2. **Testing**: Comprehensive testing with real projects
3. **Documentation**: Good docs for users and contributors
4. **Community**: Engage with IDE tool developers
5. **Performance**: Optimize for large codebases

**Challenges**:

1. **Initial Effort**: Significant development time (6-12 months for full experience)
2. **Complexity**: LSP/DAP protocols have many features
3. **Edge Cases**: Handling incomplete/invalid code
4. **Performance**: Real-time response requirements
5. **Ecstasy-Specific Features**: Services, async, immutability need special handling

**Recommendation**: **Yes, proceed with LSP/DAP approach!**

This is the modern standard for language tooling and will provide the best experience for Ecstasy developers across all IDEs.

---

## Next Steps

### Immediate Actions (Week 1-2)

1. ✅ Review and approve this plan
2. ⬜ Assign team/developers to project
3. ⬜ Set up project structure:
   - `ide-support/` (root directory)
   - `ide-support/textmate/` (TextMate grammar)
   - `ide-support/vscode/` (VSCode extension)
   - `ide-support/intellij/` (IntelliJ plugin)
   - `ide-support/lsp-server/` (Language server)
   - `ide-support/debug-adapter/` (Debug adapter)
4. ⬜ Create tracking issues in GitHub
5. ⬜ Begin Phase 1 implementation

### Short-term Goals (Month 1-3)

1. ⬜ Complete Phase 1 (syntax highlighting)
2. ⬜ Release VSCode extension v0.1 (syntax only)
3. ⬜ Begin Phase 2 (LSP server foundation)
4. ⬜ Gather community feedback

### Medium-term Goals (Month 4-8)

1. ⬜ Complete Phase 2 (basic LSP)
2. ⬜ Complete Phase 3 (advanced LSP)
3. ⬜ Release VSCode extension v1.0 (full LSP)
4. ⬜ Begin Phase 4 (IDE extensions)
5. ⬜ Begin Phase 5 (debugging)

### Long-term Goals (Month 9-12)

1. ⬜ Complete Phase 4 (IntelliJ plugin)
2. ⬜ Complete Phase 5 (debugging support)
3. ⬜ Release v2.0 with debugging
4. ⬜ Begin Phase 6 (polish)
5. ⬜ Expand to other IDEs (Vim, Emacs)

---

## Conclusion

IDE-independent language support for Ecstasy is **achievable**, **highly valuable**, and **significantly accelerated by existing infrastructure**. By leveraging:

1. **Existing compiler infrastructure** (lexer, parser, AST, type system) ✅
2. **Existing console debugger** (DebugConsole with full debugging features) ✅ **NEW**
3. **Modern protocols** (LSP for code intelligence, DAP for debugging)
4. **TextMate grammars** (for syntax highlighting)
5. **DSL representation** (BNF grammar + AST)

We can provide a **world-class development experience** for Ecstasy developers across **all major IDEs** (VSCode, IntelliJ, Vim, Emacs, etc.) with a **single implementation**.

**The DSL approach is beneficial** because it:
- Enables reuse of compiler infrastructure
- Provides rich semantic information
- Maintains single source of truth
- Enables incremental compilation
- Supports advanced IDE features

**Major Discovery**: The existence of `DebugConsole.java` **significantly reduces** the complexity of Phase 5:
- 60-70% of debugging functionality already implemented
- Only need DAP protocol adapter (4-6 weeks instead of 8-12 weeks)
- Saves approximately **6 weeks** of development time

**JIT Compiler Considerations**:
- Prototype JIT (XTC → Java bytecode) exists but final backend is **undecided**
- Could be Java bytecode, LLVM IR, or other backend
- **Critical**: DAP adapter must be **backend-agnostic**
- Design for flexibility: support interpreter AND JIT modes
- Source mapping is essential regardless of backend choice

**Revised Timeline**:
- **MVP**: 3-4 months (unchanged)
- **Full experience**: 6-10 months (improved from 8-12 months)
- **Debugging support**: 4-6 weeks (improved from 8-12 weeks)

**Recommendation**: **Begin with Phase 1 immediately** to provide value to users while building toward comprehensive LSP/DAP implementation. The existing debugger infrastructure de-risks Phase 5 significantly.

---

## References

- **Ecstasy**: https://xtclang.org/
- **Language Server Protocol**: https://microsoft.github.io/language-server-protocol/
- **Debug Adapter Protocol**: https://microsoft.github.io/debug-adapter-protocol/
- **Eclipse LSP4J**: https://github.com/eclipse-lsp4j/lsp4j
- **TextMate Grammars**: https://macromates.com/manual/en/language_grammars
- **VSCode Extension API**: https://code.visualstudio.com/api
- **IntelliJ Platform SDK**: https://plugins.jetbrains.com/docs/intellij/
