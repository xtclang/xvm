# XTC IDE Integration Plan

## Executive Summary

This plan outlines how to create a unified IDE integration layer for XTC, similar to how Java developers use `gradle init` or `maven archetype:generate` to scaffold projects and then work seamlessly in IntelliJ, VS Code, or Eclipse.

**Core Principle**: The `xtc` CLI is the source of truth. IDE plugins are thin wrappers that shell out to CLI commands.

## Current State Analysis

### What Exists

| Component | Location | Status |
|-----------|----------|--------|
| Gradle Plugin | `/plugin/` | Production - provides XtcCompileTask, XtcRunTask, etc. |
| LSP Server | `/lang/` | Implemented - hover, completion, diagnostics, go-to-definition |
| Language Formalization | `/lib_ecstasy/.../lang/src/` | Complete - Lexer.x, Parser.x, AST |
| Initializer | `/javatools/.../tool/Initializer.java` | NEW - `xtc init` command |

### What's Planned

| Component | Status |
|-----------|--------|
| IntelliJ Plugin | In progress - project wizard + run configs |
| VS Code Extension | Planned - commands + LSP client |
| TextMate Grammar | Planned - syntax highlighting |
| Zed/Eclipse | Planned - thin integrations |

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         IDE Plugins (Thin Wrappers)                     │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐  ┌─────────────┐   │
│  │   IntelliJ   │  │    VS Code   │  │     Zed     │  │   Eclipse   │   │
│  └──────┬───────┘  └──────┬───────┘  └──────┬──────┘  └──────┬──────┘   │
│         │                 │                 │                 │         │
│         └─────────────────┴─────────────────┴─────────────────┘         │
│                                   │                                     │
│                          (shell out to CLI)                             │
├───────────────────────────────────┼─────────────────────────────────────┤
│                           ┌───────▼───────┐                             │
│                           │   xtc CLI     │                             │
│                           │  init | run   │                             │
│                           │  build | test │                             │
│                           └───────┬───────┘                             │
├───────────────────────────────────┼─────────────────────────────────────┤
│         ┌─────────────────────────┼─────────────────────────┐           │
│         │                         │                         │           │
│  ┌──────▼──────┐          ┌───────▼───────┐         ┌───────▼───────┐   │
│  │ Initializer │          │   Compiler    │         │    Runner     │   │
│  │  (templates)│          │   (xcc)       │         │    (xec)      │   │
│  └─────────────┘          └───────────────┘         └───────────────┘   │
├─────────────────────────────────────────────────────────────────────────┤
│                           ┌───────────────┐                             │
│                           │  LSP Server   │ ◄── IDE language features   │
│                           │ (hover, etc.) │                             │
│                           └───────────────┘                             │
└─────────────────────────────────────────────────────────────────────────┘
```

## The `xtc init` Command

### Implementation: Initializer.java

Located at `/javatools/src/main/java/org/xvm/tool/Initializer.java`

**Usage:**
```bash
xtc init myapp                        # Create application project
xtc init mylib --type=library         # Create library project
xtc init myproj --multi-module        # Create multi-module project
xtc init myapp --type=service         # Create service project
```

**Options:**
- `<project-name>` - Required positional argument
- `--type`, `-t` - Project type: application (default), library, service
- `--multi-module`, `-m` - Create multi-module structure

### Generated Project Structure

**Single Module (Application):**
```
myapp/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── .gitignore
└── src/
    └── main/
        └── x/
            └── myapp.x
```

**Multi-Module:**
```
myproject/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── .gitignore
├── app/
│   ├── build.gradle.kts
│   └── src/main/x/app.x
└── lib/
    ├── build.gradle.kts
    └── src/main/x/lib.x
```

## IntelliJ Plugin

### Components

1. **XtcProjectGenerator** - New Project wizard integration
   - Calls `xtc init` with user-specified options
   - Imports as Gradle project after creation

2. **XtcRunConfigurationType** - Run configuration
   - Calls `xtc run` or delegates to Gradle `xtcRun` task

3. **XtcLspServerSupportProvider** - Language features via LSP
   - Launches LSP server from `/lang/`
   - Provides hover, completion, diagnostics, go-to-definition

### Project Wizard Flow

```
User: File → New → Project → XTC

┌────────────────────────────────────────────┐
│  New XTC Project                           │
├────────────────────────────────────────────┤
│  Project name: [myapp____________]         │
│                                            │
│  Project type: ○ Application (default)    │
│                ○ Library                   │
│                ○ Service                   │
│                                            │
│  □ Multi-module project                    │
│                                            │
│  Location: [~/projects/myapp____] [...]    │
│                                            │
│           [Cancel]              [Create]   │
└────────────────────────────────────────────┘

→ Executes: xtc init myapp --type=application
→ Imports as Gradle project
→ Opens in IDE with full support
```

## VS Code Extension

### Components

1. **Commands**
   - `xtc.newProject` - Runs `xtc init` via input prompts
   - `xtc.run` - Runs `xtc run` in terminal
   - `xtc.build` - Runs `xtc build`

2. **LSP Client**
   - Connects to LSP server for language features
   - Auto-starts when `.x` file opened

3. **TextMate Grammar**
   - Syntax highlighting for `.x` files
   - Based on language formalization in Lexer.x

### Extension Structure

```
vscode-extension/
├── package.json           # Extension manifest
├── src/
│   └── extension.ts       # Commands + LSP client
├── syntaxes/
│   └── xtc.tmLanguage.json
└── language-configuration.json
```

## LSP Server Integration

The LSP server at `/lang/` provides:

| Feature | LSP Method | Status |
|---------|------------|--------|
| Hover | `textDocument/hover` | ✅ Implemented |
| Completion | `textDocument/completion` | ✅ Implemented |
| Go to Definition | `textDocument/definition` | ✅ Implemented |
| Find References | `textDocument/references` | ✅ Implemented |
| Document Symbols | `textDocument/documentSymbol` | ✅ Implemented |
| Diagnostics | `textDocument/publishDiagnostics` | ✅ Implemented |
| Semantic Tokens | `textDocument/semanticTokens` | ❌ Not yet |
| Formatting | `textDocument/formatting` | ❌ Not yet |
| Rename | `textDocument/rename` | ❌ Not yet |

### Distribution

- Fat JAR: `xtc-lsp-server-all.jar`
- Bundled with XDK
- IDE plugins find via `XDK_HOME` or download on demand

## Implementation Phases

### Phase 1: Core CLI (DONE)
- [x] `Initializer.java` - project scaffolding
- [x] Register `init` command in `Launcher.java`
- [x] Project templates (application, library, service, multi-module)

### Phase 2: IntelliJ Plugin (IN PROGRESS)
- [ ] Project wizard (`XtcProjectGenerator`)
- [ ] Run configuration (`XtcRunConfigurationType`)
- [ ] LSP integration (`XtcLspServerSupportProvider`)
- [ ] Plugin manifest and build

### Phase 3: VS Code Extension
- [ ] Commands (new project, run, build)
- [ ] LSP client integration
- [ ] TextMate grammar
- [ ] Extension packaging

### Phase 4: Polish
- [ ] Gradle wrapper bundling in templates
- [ ] Better error messages
- [ ] Documentation
- [ ] Marketplace publishing

## User Experience Goal

```bash
# Install XDK (includes xtc CLI)
brew install xtc
# or
sdk install xtc

# Create new project
xtc init myapp --type=application
cd myapp

# Open in any IDE - it just works
idea .   # IntelliJ: recognizes Gradle, provides XTC support
code .   # VS Code: activates extension, starts LSP

# Develop with full IDE support
# - Syntax highlighting
# - Hover, completion, diagnostics (LSP)
# - Run/Debug configurations

# Build and run
./gradlew build
./gradlew run
# or
xtc build
xtc run
```

## Files Created/Modified

### New Files (this branch)

| File | Purpose |
|------|---------|
| `javatools/.../tool/Initializer.java` | `xtc init` command |
| `init/intellij-plugin/` | IntelliJ plugin |
| `init/vscode-extension/` | VS Code extension |
| `init/PLAN_IDE_INTEGRATION.md` | This document |

### Modified Files

| File | Changes |
|------|---------|
| `javatools/.../tool/Launcher.java` | Added `init` command case |

## Syntax Highlighting Strategy

### Current: TextMate Grammar

Currently using TextMate grammar for syntax highlighting:
- IDE-independent format (works in VS Code, IntelliJ, Sublime, etc.)
- Generated from DSL in `lang/build.gradle.kts`
- Located at `lang/build/textmate/`
- Provides basic keyword/string/comment highlighting

**Limitation**: TextMate is regex-based and cannot understand semantic context.

### Future: LSP Semantic Tokens

**Goal**: Migrate syntax highlighting to LSP semantic tokens for IDE-independent, semantically-accurate highlighting.

**Benefits**:
- Highlighting logic lives in LSP server (single implementation)
- Works with ANY LSP-compatible editor
- Semantic accuracy (knows if `Console` is a type vs variable)
- Can highlight based on actual XTC parser/compiler knowledge

**Required Changes**:

1. **LSP Server** (`lang/lsp-server/`):
   - Implement `textDocument/semanticTokens/full`
   - Implement `textDocument/semanticTokens/range` (optional, for performance)
   - Return token types: `keyword`, `type`, `function`, `variable`, `string`, `comment`, etc.
   - Use XTC lexer/parser for accurate tokenization

2. **Capability Registration**:
   ```java
   // In XtcLanguageServer.initialize()
   ServerCapabilities capabilities = new ServerCapabilities();
   SemanticTokensOptions semanticTokens = new SemanticTokensOptions();
   semanticTokens.setFull(true);
   semanticTokens.setLegend(new SemanticTokensLegend(
       Arrays.asList("keyword", "type", "function", "variable", "string", "comment", "number", "operator"),
       Arrays.asList("declaration", "definition", "readonly", "static")
   ));
   capabilities.setSemanticTokensProvider(semanticTokens);
   ```

3. **Token Provider Implementation**:
   ```java
   @Override
   public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
       // Use XTC Lexer to tokenize the document
       // Map XTC token types to LSP semantic token types
       // Return encoded token data
   }
   ```

4. **IDE Plugins**:
   - LSP4IJ (IntelliJ) automatically supports semantic tokens
   - VS Code LSP client automatically supports semantic tokens
   - No plugin changes needed once server implements it

**Migration Path**:
1. Keep TextMate grammar as fallback (for editors without LSP)
2. Implement semantic tokens in LSP server
3. LSP clients will prefer semantic tokens when available
4. Eventually deprecate TextMate for LSP-enabled editors

**Priority**: Medium - TextMate works for basic highlighting, semantic tokens adds accuracy.

## Design Principles

1. **CLI is source of truth** - All logic lives in the xtc CLI
2. **Editors only shell out** - Never duplicate logic per editor
3. **Maintain UX parity** - Same experience across all IDEs
4. **Leverage LSP** - Write language features once, use everywhere
5. **Gradle for build** - Use existing Gradle plugin infrastructure
6. **Prefer LSP over IDE-specific** - Semantic tokens over TextMate when possible
