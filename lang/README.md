# XTC Language Server

A Language Server Protocol (LSP) implementation for the Ecstasy/XTC programming language.

NOTE: This is quite experimental and it is more of a case study / analysis document combined with
code in a state that is more or less mock-up for a while.

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
xtc-lsp/
├── build.gradle.kts              # Build configuration
├── settings.gradle.kts           # Project settings
├── README.md                     # This file
└── src/
    ├── main/
    │   ├── java/org/xvm/lsp/
    │   │   ├── adapter/          # Compiler adapter interface and mocks
    │   │   │   ├── XtcCompilerAdapter.java
    │   │   │   └── MockXtcCompilerAdapter.java
    │   │   ├── model/            # Immutable data model
    │   │   │   ├── Location.java
    │   │   │   ├── Diagnostic.java
    │   │   │   ├── SymbolInfo.java
    │   │   │   └── CompilationResult.java
    │   │   └── server/           # LSP server implementation
    │   │       ├── XtcLanguageServer.java
    │   │       └── XtcLanguageServerLauncher.java
    │   └── resources/
    │       └── logback.xml       # Logging configuration
    └── test/
        ├── java/org/xvm/lsp/     # Unit and integration tests
        └── resources/
            └── sample.x          # Sample XTC file for testing
```
