# IDE and Build Tool Integration

## Overview

The XTC tooling ecosystem has three layers:

```
┌─────────────────────────────────────────────────────────────────────┐
│                      IDE Plugins                                    │
│   ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐ │
│   │  VSCode Plugin  │  │ IntelliJ Plugin │  │  Other IDE Plugins  │ │
│   └────────┬────────┘  └────────┬────────┘  └──────────┬──────────┘ │
│            └────────────────────┼──────────────────────┘            │
│                                 │ (LSP protocol)                    │
├─────────────────────────────────┼───────────────────────────────────┤
│                      ┌──────────▼──────────┐                        │
│                      │    LSP Server       │                        │
│                      │  (shared by all)    │                        │
│                      └──────────┬──────────┘                        │
├─────────────────────────────────┼───────────────────────────────────┤
│                      ┌──────────▼──────────┐                        │
│                      │   Adapter Layer     │◄────┐                  │
│                      │  (clean data model) │     │                  │
│                      └──────────┬──────────┘     │                  │
├─────────────────────────────────┼────────────────┼──────────────────┤
│                      ┌──────────▼──────────┐     │                  │
│                      │    XTC Compiler     │     │                  │
│                      └─────────────────────┘     │                  │
├──────────────────────────────────────────────────┼──────────────────┤
│                      ┌───────────────────────────┴─┐                │
│                      │    Gradle/Maven Plugin      │                │
│                      └─────────────────────────────┘                │
└─────────────────────────────────────────────────────────────────────┘
```

**Key insight**: The LSP server and build plugins share the same adapter layer. Build once, use everywhere.

## Component Responsibilities

### LSP Server

The LSP server (see [implementation](./implementation.md)) handles:
- IDE communication via JSON-RPC
- Document synchronization
- Hover, completion, diagnostics
- Go-to-definition, find references
- Semantic highlighting

**One LSP server serves all IDEs.** This is the beauty of LSP - write it once, every IDE can use it.

### IDE Plugins

IDE plugins are thin wrappers that:
1. Launch the LSP server as a subprocess
2. Connect to it via stdin/stdout or socket
3. Handle IDE-specific UI integration

**VSCode Plugin** (`xvm.xtc-vscode`):
```
xvm.xtc-vscode/
├── package.json          # Extension manifest
├── src/
│   └── extension.ts      # ~100 lines of glue code
├── syntaxes/
│   └── xtc.tmLanguage    # TextMate grammar for syntax highlighting
└── language-configuration.json
```

**IntelliJ Plugin** (`xvm.xtc-intellij`):
```
xvm.xtc-intellij/
├── build.gradle.kts
├── src/main/
│   ├── kotlin/
│   │   └── org/xvm/intellij/
│   │       ├── XtcLanguage.kt
│   │       └── XtcLspServerSupportProvider.kt  # ~50 lines
│   └── resources/
│       └── META-INF/plugin.xml
```

### Gradle Plugin (Already Exists)

The XTC Gradle plugin (`org.xtclang.xtc`) **already exists** and is used to build the XTC platform, XDK, and all XTC projects. It lives in `/plugin/` and provides:

**Existing Tasks:**
- `XtcCompileTask` - compiles `.x` files to `.xtc` modules
- `XtcRunTask` - runs XTC modules
- `XtcExtractXdkTask` - extracts the XDK
- `XtcVersionTask` - version information

**Existing Features:**
- Source set integration (`src/main/x`, `src/test/x`)
- Dependency resolution for XTC modules
- Configuration for compiler and runtime options
- Integration with Java plugin for mixed projects

**What the Adapter Layer Would Add:**

The existing plugin invokes the compiler as a black box. An adapter layer would enable:
- **Better error reporting** - structured diagnostics instead of text output
- **Incremental compilation** - understanding what changed
- **IDE integration** - Gradle builds surfacing errors in IDE
- **Build cache support** - proper input/output tracking

### Other Tool Integration

The adapter layer enables XTC support in analysis tools:

| Tool | What It Needs | Adapter Provides |
|------|---------------|------------------|
| **ErrorProne** | AST access for bug patterns | Extracted AST model |
| **PMD** | Source analysis | Token stream, AST |
| **SpotBugs** | Bytecode/semantic analysis | Type information |
| **Checkstyle** | Style checking | Token positions, AST |
| **SonarQube** | Quality metrics | All of the above |
| **Javadoc-like** | Documentation extraction | Symbol info, comments |

**Example: ErrorProne-style checker for XTC**
```java
public final class XtcBugChecker implements XtcAnalyzer {
    @Override
    public void analyze(final @NonNull LspSnapshot snapshot) {
        // Access clean, immutable AST
        for (final var symbol : snapshot.symbols().allSymbols()) {
            if (symbol.kind() == METHOD && symbol.name().startsWith("get")
                    && returnsVoid(symbol)) {
                report(symbol.location(), "Getter should not return void");
            }
        }
    }
}
```

The point is: **any tool that needs to understand XTC code can use the adapter layer** instead of fighting the compiler internals directly.

## LSP Reuse in IDE Plugins

### What IDE Plugins Reuse (100%)

| Feature | LSP Protocol | Plugin Work |
|---------|--------------|-------------|
| Hover information | `textDocument/hover` | None - automatic |
| Go-to-definition | `textDocument/definition` | None - automatic |
| Find references | `textDocument/references` | None - automatic |
| Auto-completion | `textDocument/completion` | None - automatic |
| Diagnostics | `textDocument/publishDiagnostics` | None - automatic |
| Document symbols | `textDocument/documentSymbol` | None - automatic |
| Semantic tokens | `textDocument/semanticTokens` | None - automatic |
| Formatting | `textDocument/formatting` | None - automatic |
| Rename | `textDocument/rename` | None - automatic |

### What IDE Plugins Must Implement

| Feature | Why Not LSP | Plugin Work |
|---------|-------------|-------------|
| Basic syntax highlighting | Performance (TextMate grammar) | ~200 lines |
| File icons | IDE-specific API | ~20 lines |
| Project structure view | IDE-specific API | ~100 lines |
| Run configurations | IDE-specific API | ~200 lines |
| Debug integration | Debug Adapter Protocol (DAP) | Separate server |

### VSCode Plugin Implementation

```typescript
// src/extension.ts - This is almost the entire plugin
import * as vscode from 'vscode';
import { LanguageClient, LanguageClientOptions, ServerOptions } from 'vscode-languageclient/node';

let client: LanguageClient;

export function activate(context: vscode.ExtensionContext) {
    // Find the LSP server executable
    const serverPath = findXtcLspServer();

    const serverOptions: ServerOptions = {
        command: serverPath,
        args: ['--stdio']
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'xtc' }],
        synchronize: {
            fileEvents: vscode.workspace.createFileSystemWatcher('**/*.xtc')
        }
    };

    client = new LanguageClient('xtc', 'XTC Language Server', serverOptions, clientOptions);
    client.start();
}

export function deactivate(): Thenable<void> | undefined {
    return client?.stop();
}
```

**That's it.** The LSP server does all the heavy lifting.

### IntelliJ Plugin Implementation

```kotlin
// XtcLspServerSupportProvider.kt
class XtcLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LspServerStarter
    ) {
        if (file.extension == "xtc") {
            serverStarter.ensureServerStarted(XtcLspServerDescriptor(project))
        }
    }
}

class XtcLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "XTC") {
    override fun createCommandLine(): GeneralCommandLine {
        return GeneralCommandLine(findXtcLspServer(), "--stdio")
    }

    override fun isSupportedFile(file: VirtualFile): Boolean {
        return file.extension == "xtc"
    }
}
```

**Also minimal.** IntelliJ's LSP support handles everything else.

## Gradle Plugin Architecture

The Gradle plugin has different needs than LSP:

```kotlin
// build.gradle.kts (user's project)
plugins {
    id("org.xvm.xtc") version "1.0"
}

xtc {
    mainModule = "myapp"
    sourceDir = "src/main/x"
}
```

### What Gradle Plugin Needs from Adapter Layer

```kotlin
// XtcCompileTask.kt
abstract class XtcCompileTask : DefaultTask() {
    @get:InputFiles
    abstract val sourceFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun compile() {
        // Use the same adapter layer as LSP
        val adapter = XtcCompilerAdapter()

        for (sourceFile in sourceFiles) {
            val result = adapter.compile(sourceFile.path, sourceFile.readText())

            // Extract diagnostics (same model as LSP)
            for (diagnostic in result.diagnostics) {
                when (diagnostic.severity) {
                    ERROR -> logger.error("${diagnostic.location}: ${diagnostic.message}")
                    WARNING -> logger.warn("${diagnostic.location}: ${diagnostic.message}")
                }
            }

            // Write compiled output
            if (result.success) {
                writeModule(result.module, outputDir)
            }
        }
    }
}
```

### Shared vs Gradle-Specific Code

| Component | Shared with LSP | Gradle-Specific |
|-----------|-----------------|-----------------|
| Adapter layer | ✅ Yes | - |
| Diagnostic extraction | ✅ Yes | - |
| Symbol extraction | ✅ Yes | - |
| Module structure | ✅ Yes | - |
| Incremental compilation | - | ✅ Yes |
| Dependency resolution | - | ✅ Yes |
| Build cache integration | - | ✅ Yes |
| Task configuration | - | ✅ Yes |

## Timeline Estimates

| Component | Effort | Prerequisites |
|-----------|--------|---------------|
| Adapter layer | 3-4 weeks | - |
| LSP Server | 4-6 weeks | Adapter layer |
| VSCode Plugin | 1-2 days | LSP Server, TextMate grammar |
| IntelliJ Plugin | 2-3 days | LSP Server |
| Gradle Plugin enhancement | 2-3 weeks | Adapter layer |
| Analysis tool integration | 1-2 weeks each | Adapter layer |

**Key insights:**
- IDE plugins are trivial once LSP exists
- Gradle plugin already exists - enhancement is easier than greenfield
- Analysis tools (ErrorProne, PMD, etc.) all share the adapter layer

## Development Strategy

### Phase 1: Foundation (4-6 weeks)
1. Build adapter layer
2. Build LSP server
3. Test with VSCode (fastest to iterate)

### Phase 2: IDE Plugins (1 week)
1. VSCode plugin (2 days including TextMate grammar)
2. IntelliJ plugin (3 days including testing)
3. Publish to marketplaces

### Phase 3: Gradle Plugin Enhancement (2-3 weeks)
The plugin already exists. Enhancements using the adapter layer:
1. Structured error reporting (1 week)
2. IDE-friendly diagnostic output (few days)
3. Incremental compilation hooks (1-2 weeks)

### Phase 4: Analysis Tools (As Needed)
Each tool that wants XTC support:
1. ErrorProne-style bug checkers
2. PMD rules for XTC
3. Checkstyle rules for XTC
4. Documentation generators

### Phase 5: Advanced Features
1. Debug Adapter Protocol (DAP) server
2. Test runner integration
3. Profiler integration

## File Structure

Current and proposed project structure:

```
xvm/
├── javatools/                    # Existing compiler
├── xdk/                          # Existing XDK
├── plugin/                       # EXISTING Gradle plugin
│   └── src/main/java/
│       └── org/xtclang/plugin/
│           ├── XtcPlugin.java
│           ├── XtcProjectDelegate.java
│           └── tasks/
│               ├── XtcCompileTask.java
│               ├── XtcRunTask.java
│               └── ...
│
├── tooling/                      # NEW - LSP and adapter code
│   ├── adapter/                  # Shared adapter layer
│   │   └── src/main/java/
│   │       └── org/xvm/tooling/adapter/
│   ├── lsp-server/               # LSP implementation
│   │   └── src/main/java/
│   │       └── org/xvm/tooling/lsp/
│   ├── vscode-plugin/            # VSCode extension
│   │   ├── package.json
│   │   └── src/
│   └── intellij-plugin/          # IntelliJ plugin
│       ├── build.gradle.kts
│       └── src/
```

## Summary

1. **LSP server is the core** - implements all language intelligence
2. **IDE plugins are thin** - just launch LSP and handle IDE-specific UI
3. **Gradle plugin already exists** - can be enhanced to use adapter layer
4. **Adapter layer is shared** - same clean data model for all consumers
5. **Build once, use everywhere** - one LSP server serves VSCode, IntelliJ, Vim, Emacs, etc.
6. **Analysis tools benefit too** - ErrorProne, PMD, etc. can all use the adapter

The existing Gradle plugin at `/plugin/` is mature and handles compilation, running, and dependency resolution. The adapter layer would enhance it with structured diagnostics and incremental compilation support.
