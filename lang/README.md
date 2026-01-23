# XTC Language Tooling

Language tooling for the Ecstasy/XTC programming language, including LSP server, IDE plugins, and editor support.

## Status: Alpha

> **Note:** This code is currently in **alpha status** and is **unsupported**. It is under active development with the goal of reaching beta quality. You are welcome to evaluate and test it, but please be aware that:
>
> - APIs and functionality may change without notice
> - Some features may be incomplete or unstable
> - Bug reports and feedback are appreciated but support is limited
>
> We are actively working to improve stability and move toward a supported beta release.

## Subprojects

| Project | Description |
|---------|-------------|
| [dsl](./dsl/) | Language model DSL and editor support generators |
| [lsp-server](./lsp-server/) | Language Server Protocol implementation |
| [intellij-plugin](./intellij-plugin/) | IntelliJ IDEA plugin |
| [vscode-extension](./vscode-extension/) | VS Code extension |

## Building

```bash
# Build everything
./gradlew build

# Build specific projects
./gradlew :dsl:build
./gradlew :lsp-server:build
./gradlew :intellij-plugin:buildPlugin
./gradlew :vscode-extension:build
```

## IntelliJ Plugin Development

### Running the Plugin in a Sandbox IDE

To test the plugin during development, run a sandboxed IntelliJ IDEA instance with the plugin loaded:

```bash
./gradlew :lang:intellij-plugin:runIde
```

This launches a separate IntelliJ IDEA with:
- The XTC plugin installed
- A fresh sandbox environment (settings, caches, etc.)
- Isolated from your main IDE installation

The sandbox IDE data is stored in `lang/intellij-plugin/build/idea-sandbox/`.

### Building a Distributable Plugin ZIP

To create a plugin ZIP that can be installed in any IntelliJ IDEA 2025.1+ instance:

```bash
./gradlew :lang:intellij-plugin:buildPlugin
```

The ZIP is created at:
```
lang/intellij-plugin/build/distributions/intellij-plugin-<version>.zip
```

### Installing the ZIP Manually

1. Open IntelliJ IDEA
2. **Settings/Preferences → Plugins**
3. Click the gear icon (⚙️) → **Install Plugin from Disk...**
4. Select the ZIP file
5. Restart IntelliJ IDEA

### Other Useful Tasks

| Task | Description |
|------|-------------|
| `./gradlew :lang:intellij-plugin:runIde` | Run plugin in sandbox IDE |
| `./gradlew :lang:intellij-plugin:buildPlugin` | Build distributable ZIP |
| `./gradlew :lang:intellij-plugin:verifyPlugin` | Check IDE compatibility |
| `./gradlew :lang:intellij-plugin:clean` | Clear sandbox and build artifacts |

### Publishing to JetBrains Marketplace

For detailed publication instructions (creating tokens, signing, CI/CD setup), see:
**[intellij-plugin/README.md → Publishing to JetBrains Marketplace](./intellij-plugin/README.md#publishing-to-jetbrains-marketplace-step-by-step)**

## Generating Editor Support

The DSL project generates syntax highlighting files for multiple editors:

```bash
# Generate all editor support files
./gradlew :dsl:generateEditorSupport

# Update the checked-in examples
./gradlew updateGeneratedExamples
```

Generated files:
- `xtc.tmLanguage.json` - TextMate grammar (VS Code, IntelliJ, Sublime)
- `language-configuration.json` - VS Code language config
- `xtc.vim` - Vim syntax file
- `xtc-mode.el` - Emacs major mode
- `grammar.js` - Tree-sitter grammar
- `highlights.scm` - Tree-sitter highlights

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     IDE / Editor                                │
│            (VSCode, IntelliJ, Vim, Emacs, etc.)                 │
└─────────────────────────┬───────────────────────────────────────┘
                          │ LSP (JSON-RPC)
┌─────────────────────────▼───────────────────────────────────────┐
│                   lsp-server                                    │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │               XtcCompilerAdapter                        │    │
│  │  (Interface - extracts data from XTC compiler)          │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

## Notes on how Far We Can Go With TreeSitter for LSP

**Tree-sitter operates below the LSP**, providing fast, incremental *syntax trees*.
It does **not** understand language meaning, only structure.

---

## 2. What Tree-sitter Can Do (No Compiler, No LSP Brain)

Tree-sitter alone can deliver a surprisingly polished *editor experience*, but only for syntax-level features.

### 2.1 Syntax Highlighting (Excellent)

Tree-sitter excels at:

- Context-aware highlighting
- Nested and recursive structures
- Incremental updates
- Graceful handling of incomplete or broken code

This already gets you to “modern IDE” baseline quality.

---

### 2.2 Structural Navigation

Pure syntax tree walking enables:

- Go to enclosing function / class
- Expand / shrink selection
- Code folding
- Breadcrumbs
- Outline / structure view

All of this is deterministic from syntax alone.

---

### 2.3 Basic Formatting

You can implement:

- Indentation rules
- Block-based formatting
- Simple reflow

**Limitation:** formatting cannot depend on types, symbol resolution, or semantic intent.

---

### 2.4 Syntax-Only Inspections

Tree-sitter supports inspections that do not require semantic understanding:

- Duplicate method names in the same file
- Missing braces / delimiters
- Invalid keyword placement
- Structural anti-patterns
- Trivially unused imports (syntactic only)

---

### 2.5 Simple Refactorings (Textual)

Possible but limited refactors:

- Rename *local* variables within a single subtree
- Extract block to function (mechanical)
- Move or reorder code blocks

**Important:** these are safe only when scope is obvious from syntax.

---

## 3. Hard Limits of Tree-sitter (Where It Completely Stops)

Anything below requires **semantic knowledge** and cannot be solved with Tree-sitter alone.

---

### 3.1 Name Resolution (Impossible)

Tree-sitter cannot answer:

- What does this identifier refer to?
- Where is this symbol defined?
- Which overload is selected?
- Is this method overridden?

Therefore you cannot implement:

- Go to definition
- Find usages
- Rename across files
- Call hierarchy

**Reason:** scope and binding are semantic, not syntactic.

---

### 3.2 Type System Features (Impossible)

Tree-sitter has no concept of types.

You cannot implement:

- Type inference
- Type mismatch diagnostics
- Generic instantiation
- Nullability analysis
- Flow-sensitive typing

---

### 3.3 Real Diagnostics

Without the compiler frontend, you cannot reliably detect:

- Semantic errors
- Dead code (semantic)
- Unreachable code
- Contract or effect violations
- Language-rule-based warnings

Any attempt here becomes heuristic and fragile.

---

### 3.4 Intelligent Code Completion

With Tree-sitter only, completion is limited to:

- Keywords
- Snippets
- Structural templates

You **cannot** provide:

- Member completion (`obj.` → methods)
- Overload-aware suggestions
- Generic-aware completion
- Completion based on expected type

This is one of the biggest UX gaps.

---

### 3.5 Cross-file / Project Intelligence

Tree-sitter is fundamentally file-local.

You cannot correctly handle:

- Imports and modules
- Dependency graphs
- Multi-module projects
- Project-wide symbol indices

---

### 3.6 Running and Debugging

Impossible without runtime integration:

- Breakpoints
- Step-through debugging
- Variable inspection
- Stack traces mapped to source

---

## 4. What Java/Kotlin-Level IDEs Actually Use

Modern IDEs for Java/Kotlin/Scala do **not** rely on Tree-sitter for intelligence.

They embed or tightly integrate:

- Compiler frontend
- Symbol resolver
- Type checker
- Build system model
- Partial evaluators and indexers

Tree-sitter (or equivalent) is used primarily for:

- Fast incremental parsing
- Highlighting
- Error recovery in broken code

The “smart” behavior comes from the language itself.

---

## 5. What the XTC LSP Must Interface With

To reach Java-level parity, the XTC language server must expose semantic services backed by the real language 
implementation.

### 5.1 Required Backend Components

At minimum:

1. **Authoritative Parser**
    - Produces the compiler’s AST (not Tree-sitter’s)

2. **Symbol Table**
    - Definitions
    - Scopes
    - Imports / modules

3. **Type Checker**
    - Expression typing
    - Constraint solving
    - Semantic error reporting

4. **Project Model**
    - Dependency graph
    - Build configuration
    - Module boundaries

5. **Runner / Evaluator**
    - Run / test / debug integration

---

### 5.2 LSP Features Unlocked Once Integrated

With the above in place, the IDE can support:

- Go to definition
- Find references
- Rename (project-wide)
- Semantic diagnostics
- Intelligent completion
- Inline type hints
- Call and type hierarchies
- Debugging support (via Debug Adapter Protocol)

---

## 6. Recommended Architecture for XTC

### Phase 1 – Tree-sitter First

Fast wins with minimal backend work:

- Syntax highlighting
- Structure view
- Folding
- Basic formatting
- Keyword and snippet completion

This already feels modern and usable.

---

### Phase 2 – Hybrid Tree-sitter + LSP

- Tree-sitter for:
    - Incremental parsing
    - Editor responsiveness
- Compiler frontend for:
    - Symbol resolution
    - Typing
    - Diagnostics

This is the model used by tools like Rust Analyzer.

---

### Phase 3 – Full IDE Parity

- Incremental type checking
- Persistent project index
- Debug adapter
- Build system integration

This is where Java/Kotlin-level polish lives — and where most complexity lies.

---

## 7. Key Takeaway

> **Tree-sitter can make an IDE feel fast and polished, but it can never make it feel smart.  
> Intelligence only comes from the language itself.**

Tree-sitter should be treated as a *performance and UX layer*, not as a replacement for the compiler frontend.

---

## 8. Practical Rule of Thumb

| Feature | Tree-sitter | LSP + Compiler |
|------|-------------|----------------|
| Highlighting | ✅ | ⚠️ (optional) |
| Folding / Outline | ✅ | ❌ |
| Formatting | ⚠️ | ✅ |
| Completion | ❌ | ✅ |
| Diagnostics | ❌ | ✅ |
| Refactoring | ⚠️ | ✅ |
| Debugging | ❌ | ✅ |

---

## Documentation

Architecture analysis and research documentation is in a separate repository:
[xtc-language-support-research](https://github.com/xtclang/xtc-language-support-research)

Implementation plans are in [doc/plans/](./doc/plans/).
