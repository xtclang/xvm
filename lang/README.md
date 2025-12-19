# XTC Language Support

Language tooling for the Ecstasy/XTC programming language, including:
- **Language Server Protocol (LSP)** implementation for IDE features
- **Syntax highlighting** for multiple editors (VS Code, Vim, Emacs, Sublime, bat, etc.)
- **Kotlin DSL** for defining the language model (single source of truth)

## Current Status

### Working Features

| Component | Status | Notes |
|-----------|--------|-------|
| **Kotlin DSL** | ✅ Working | Single source of truth for language definition |
| **TextMate Grammar** | ✅ Generated | `./gradlew generateTextMate` → VS Code, Sublime, etc. |
| **Sublime Syntax** | ✅ Working | For `bat` command-line viewer |
| **Vim Syntax** | ✅ Generated | `./gradlew generateVim` |
| **Emacs Mode** | ✅ Generated | `./gradlew generateEmacs` |
| **Tree-sitter** | ✅ Generated | `./gradlew generateTreeSitter` → Helix, Zed, GitHub |
| **VS Code Config** | ✅ Generated | Bracket matching, auto-close pairs |
| **LSP Server** | 🔶 Skeleton | Mock adapter - needs real compiler integration |
| **DAP (Debugging)** | ❌ Not started | Interface designed in `XtcCompilerAdapterFull.java` |

### Quick Start - Syntax Highlighting

```bash
# Generate all editor support files
./gradlew generateAllEditorSupport

# Files are written to build/generated/
ls build/generated/
# xtc.tmLanguage.json  - TextMate (VS Code, Sublime)
# xtc.vim              - Vim/Neovim
# xtc-mode.el          - Emacs
# grammar.js           - Tree-sitter
# highlights.scm       - Tree-sitter queries
# language-configuration.json - VS Code config
```

### Testing Syntax Highlighting

**With bat (command-line):**
```bash
# Install sublime-syntax (one-time setup)
cp ~/.config/bat/syntaxes/Ecstasy.sublime-syntax  # Already installed if you ran setup
bat cache --build
bat /path/to/file.x
```

**With VS Code:**
```bash
cd vscode-xtc
code .
# Press F5 to launch Extension Development Host
# Open any .x file to see highlighting
```

**With IntelliJ:**
1. Settings → Editor → TextMate Bundles
2. Add directory: `lang/vscode-xtc`
3. Open any `.x` file

### Language Model Statistics

```bash
./gradlew languageStats
```

Shows keyword counts, operator precedence levels, AST concept hierarchy, etc.

## Building

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Create fat JAR for distribution
./gradlew fatJar
```

The fat JAR will be created at `build/libs/xtc-lsp-0.1.0-SNAPSHOT-all.jar`.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     IDE / Editor                                │
│            (VSCode, IntelliJ, Vim, Emacs, etc.)                 │
└─────────────────────────┬───────────────────────────────────────┘
                          │ JSON-RPC over stdio
┌─────────────────────────▼───────────────────────────────────────┐
│                   XtcLanguageServer                             │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐    │
│  │ TextDocService  │ │ WorkspaceService│ │  Capabilities   │    │
│  └────────┬────────┘ └─────────────────┘ └─────────────────┘    │
│           │                                                     │
│  ┌────────▼────────────────────────────────────────────────┐    │
│  │               XtcCompilerAdapter                         │   │
│  │  (Interface - swap mock for real implementation)         │   │
│  └────────┬────────────────────────────────────────────────┘   │
│           │                                                     │
│  ┌────────▼────────┐  ┌────────────────────┐                   │
│  │  MockAdapter    │  │  RealAdapter       │                   │
│  │  (for testing)  │  │  (uses XTC compiler)│                  │
│  └─────────────────┘  └────────────────────┘                   │
└─────────────────────────────────────────────────────────────────┘
```

## Testing the LSP Server

### 1. Manual Testing with VS Code

#### Step 1: Build the fat JAR

```bash
./gradlew fatJar
```

#### Step 2: Create a VS Code extension

Create a directory `vscode-xtc/` with these files:

**package.json:**
```json
{
  "name": "xtc-language",
  "displayName": "XTC Language",
  "version": "0.1.0",
  "engines": { "vscode": "^1.75.0" },
  "categories": ["Programming Languages"],
  "activationEvents": ["onLanguage:xtc"],
  "main": "./out/extension.js",
  "contributes": {
    "languages": [{
      "id": "xtc",
      "aliases": ["Ecstasy", "XTC"],
      "extensions": [".x", ".xtc"],
      "configuration": "./language-configuration.json"
    }]
  },
  "dependencies": {
    "vscode-languageclient": "^9.0.1"
  }
}
```

**src/extension.ts:**
```typescript
import * as path from 'path';
import { workspace, ExtensionContext } from 'vscode';
import { LanguageClient, LanguageClientOptions, ServerOptions } from 'vscode-languageclient/node';

let client: LanguageClient;

export function activate(context: ExtensionContext) {
    // Path to the LSP server JAR
    const serverJar = '/path/to/xtc-lsp-0.1.0-SNAPSHOT-all.jar';

    const serverOptions: ServerOptions = {
        command: 'java',
        args: ['-jar', serverJar]
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'xtc' }],
        synchronize: {
            fileEvents: workspace.createFileSystemWatcher('**/*.x')
        }
    };

    client = new LanguageClient('xtc', 'XTC Language Server', serverOptions, clientOptions);
    client.start();
}

export function deactivate(): Thenable<void> | undefined {
    return client?.stop();
}
```

#### Step 3: Install and test

```bash
cd vscode-xtc
npm install
npm run compile
# Press F5 in VS Code to launch Extension Development Host
# Open a .x file and test hover, completion, etc.
```

### 2. Manual Testing with Command Line

You can test the LSP server directly using JSON-RPC messages:

```bash
# Start the server
java -jar build/libs/xtc-lsp-0.1.0-SNAPSHOT-all.jar

# Send initialize request (paste this, then press Enter twice)
Content-Length: 123

{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"capabilities":{}}}
```

### 3. Testing with lsp-test (Recommended)

Create an integration test that simulates a real LSP client:

```java
@Test
void integrationTest() throws Exception {
    // Start the server in a subprocess
    ProcessBuilder pb = new ProcessBuilder(
        "java", "-jar", "build/libs/xtc-lsp-0.1.0-SNAPSHOT-all.jar");
    Process process = pb.start();

    // Send initialize
    sendMessage(process, """
        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"capabilities":{}}}
        """);

    // Read response
    String response = readMessage(process);
    assertThat(response).contains("hoverProvider");

    // Open a document
    sendMessage(process, """
        {"jsonrpc":"2.0","method":"textDocument/didOpen","params":{
            "textDocument":{"uri":"file:///test.x","languageId":"xtc","version":1,
            "text":"module test { class Foo {} }"}
        }}
        """);

    // Request hover
    sendMessage(process, """
        {"jsonrpc":"2.0","id":2,"method":"textDocument/hover","params":{
            "textDocument":{"uri":"file:///test.x"},
            "position":{"line":0,"character":20}
        }}
        """);

    response = readMessage(process);
    assertThat(response).contains("class Foo");

    process.destroy();
}
```

### 4. Testing with Neovim

Add to your `init.lua`:

```lua
vim.api.nvim_create_autocmd("FileType", {
    pattern = "xtc",
    callback = function()
        vim.lsp.start({
            name = "xtc-lsp",
            cmd = { "java", "-jar", "/path/to/xtc-lsp-0.1.0-SNAPSHOT-all.jar" },
            root_dir = vim.fs.dirname(vim.fs.find({ "build.gradle.kts", ".git" }, { upward = true })[1]),
        })
    end,
})
```

## Supported Features

| Feature | Status | Notes |
|---------|--------|-------|
| Diagnostics | ✅ | Errors and warnings published on document open/change |
| Hover | ✅ | Shows type information and documentation |
| Completion | ✅ | Keywords, types, and document symbols |
| Go to Definition | ✅ | Jump to symbol definition |
| Find References | ✅ | Find all references to a symbol |
| Document Symbols | ✅ | Outline view of classes, methods, etc. |
| Formatting | ❌ | Not yet implemented |
| Rename | ❌ | Not yet implemented |
| Code Actions | ❌ | Not yet implemented |

## Logging

Logs are written to `~/.xtc-lsp/lsp.log`. Set log level in `logback.xml`:

```xml
<logger name="org.xvm.lsp" level="DEBUG"/>
```

## Switching from Mock to Real Compiler

Currently, the LSP uses `MockXtcCompilerAdapter` for testing. To use the real XTC compiler:

1. Add dependency on `javatools` in `build.gradle.kts`
2. Implement `RealXtcCompilerAdapter` that:
   - Creates a `Source` from the document content
   - Invokes `Lexer` → `Parser` → compilation
   - Extracts diagnostics from `ErrorListener`
   - Extracts symbols from the AST
3. Update `XtcLanguageServerLauncher` to use the real adapter

See `doc/3-lsp-solution/adapter-layer-design.md` for the full adapter pattern.

## Project Structure

```
lang/
├── build.gradle.kts              # Build configuration with generator tasks
├── settings.gradle.kts           # Project settings
├── README.md                     # This file
├── dsl/                          # Kotlin DSL - SINGLE SOURCE OF TRUTH
│   ├── XtcLanguage.kt            # Complete language definition (~1600 lines)
│   └── org/xtclang/tooling/
│       ├── LanguageModelCli.kt   # CLI for generators
│       ├── model/
│       │   └── LanguageModel.kt  # DSL framework and data classes
│       └── generators/           # Output generators
│           ├── TextMateGenerator.kt
│           ├── VimGenerator.kt
│           ├── EmacsGenerator.kt
│           ├── TreeSitterGenerator.kt
│           └── VSCodeConfigGenerator.kt
├── src/
│   └── main/java/org/xvm/lsp/
│       ├── adapter/              # Compiler adapter interface
│       │   ├── XtcCompilerAdapter.java
│       │   ├── XtcCompilerAdapterFull.java  # Full API including DAP
│       │   └── MockXtcCompilerAdapter.java
│       ├── model/                # Immutable data model
│       │   ├── Location.java
│       │   ├── Diagnostic.java
│       │   ├── SymbolInfo.java
│       │   └── CompilationResult.java
│       └── server/               # LSP server implementation
│           ├── XtcLanguageServer.java
│           └── XtcLanguageServerLauncher.java
├── vscode-xtc/                   # VS Code extension (syntax highlighting only)
│   ├── package.json
│   ├── syntaxes/xtc.tmLanguage.json
│   └── language-configuration.json
└── build/generated/              # Generated output (not in git)
    ├── xtc.tmLanguage.json
    ├── xtc.vim
    ├── xtc-mode.el
    ├── grammar.js
    ├── highlights.scm
    └── language-configuration.json
```

## Language Model DSL

The language is defined once in `dsl/XtcLanguage.kt` using a Kotlin DSL:

```kotlin
val xtcLanguage = language(
    name = "Ecstasy",
    fileExtensions = listOf("x", "xtc"),
    scopeName = "source.xtc"
) {
    // Keywords with categories
    keywords(KeywordCategory.CONTROL, "if", "else", "for", "while", "return")
    keywords(KeywordCategory.DECLARATION, "class", "interface", "module")
    keywords(KeywordCategory.MODIFIER, "public", "private", "static")

    // Operators with precedence and associativity
    operator("+", precedence = 12, LEFT, ARITHMETIC)
    operator("==", precedence = 5, NONE, COMPARISON)

    // Scope mappings for each editor
    scope("keyword") {
        textMate = "keyword.control.xtc"
        intellij = "KEYWORD"
        vim = "Keyword"
        emacs = "font-lock-keyword-face"
        treeSitter = "@keyword"
    }

    // Built-in types
    builtinTypes("Int", "String", "Boolean", "Array", "Map")
}
```

This single definition generates syntax files for all supported editors.

## Gradle Tasks

```bash
# Language model
./gradlew languageStats          # Show statistics
./gradlew dumpLanguageModel      # Export as JSON
./gradlew validateSources        # Validate against lib_ecstasy

# Editor support generation
./gradlew generateTextMate       # VS Code, Sublime, etc.
./gradlew generateVim            # Vim/Neovim
./gradlew generateEmacs          # Emacs
./gradlew generateTreeSitter     # Helix, Zed, GitHub
./gradlew generateVSCodeConfig   # VS Code language config
./gradlew generateAllEditorSupport  # All of the above

# LSP server
./gradlew build                  # Build everything
./gradlew fatJar                 # Create distributable JAR
./gradlew test                   # Run tests
```

## Next Steps

1. **Add SublimeSyntaxGenerator** - Generate `.sublime-syntax` for bat/Sublime
2. **Real Compiler Adapter** - Connect LSP to actual XTC compiler
3. **DAP Implementation** - Debug Adapter Protocol for debugging
4. **Semantic Tokens** - Rich highlighting beyond TextMate patterns
5. **IntelliJ Plugin** - Native plugin using the language model
