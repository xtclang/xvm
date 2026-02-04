# XTC Language Tooling

Language tooling for the Ecstasy/XTC programming language, including LSP server, IDE plugins, 
and editor support.

## Status: Alpha

> **Note:** This code is currently in **alpha status** and is **unsupported**. It is under 
> active development with the goal of reaching beta quality. You are welcome to evaluate 
> and test it, but please be aware that:
>
> - APIs and functionality may change without notice
> - Some features may be incomplete or unstable
> - Bug reports and feedback are appreciated but support is limited
>
> We are actively working to improve stability and move toward a supported beta release.

## Enabling the Lang Build

By default, the lang build is **disabled** in `gradle.properties`. To include it in the XVM composite build,
you need to set two properties:

| Property | Purpose |
|----------|---------|
| `includeBuildLang` | Include lang as a build (for IDE visibility and `./gradlew lang:*` tasks) |
| `includeBuildAttachLang` | Attach lang lifecycle tasks to root build (so `./gradlew build` includes lang) |

### Option 1: Command Line (Temporary)

```bash
./gradlew build -PincludeBuildLang=true -PincludeBuildAttachLang=true
```

### Option 2: Environment Variables (Session/Shell)

```bash
export ORG_GRADLE_PROJECT_includeBuildLang=true
export ORG_GRADLE_PROJECT_includeBuildAttachLang=true
./gradlew build
```

### Option 3: Edit gradle.properties (Persistent)

In the root `gradle.properties`, change:

```properties
includeBuildLang=true
includeBuildAttachLang=true
```

> **Note:** Do not commit `true/true` to the repository while lang is still in alpha.
> The CI workflow automatically enables lang for builds.

## Testing the Tree-sitter Parser

The tree-sitter adapter is tested against the entire XDK corpus (675+ `.x` files from `lib_*` directories).
This ensures the grammar can parse real-world Ecstasy code.

```bash
# Run the full corpus test (requires lang build enabled)
./gradlew :lang:tree-sitter:testTreeSitterParse -PincludeBuildLang=true

# Filter to specific files (comma-separated patterns)
./gradlew :lang:tree-sitter:testTreeSitterParse -PtestFiles=ecstasy/numbers

# Disable timing output
./gradlew :lang:tree-sitter:testTreeSitterParse -PshowTiming=false
```

The test outputs parse timing sorted by slowest files, helping identify grammar bottlenecks.

## Subprojects

| Project | Description |
|---------|-------------|
| [dsl](./dsl/) | Language model DSL and editor support generators |
| [tree-sitter](./tree-sitter/) | Tree-sitter grammar for incremental parsing |
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

## Native Library Build (Tree-sitter)

The LSP server uses tree-sitter for fast, incremental parsing. This requires a native shared library
compiled from the generated grammar. We use **Zig** for cross-compilation, enabling builds for all
platforms from any development machine.

### Quick Commands

```bash
# Build native library for current platform (auto-downloads Zig)
./gradlew :lang:tree-sitter:ensureNativeLibraryUpToDate

# Build for ALL platforms
./gradlew :lang:tree-sitter:buildAllNativeLibraries

# Run IDE with tree-sitter adapter
./gradlew :lang:intellij-plugin:runIde -Plsp.adapter=treesitter
```

### Supported Platforms

| Platform | Output | Architecture |
|----------|--------|--------------|
| darwin-arm64 | `libtree-sitter-xtc.dylib` | Mach-O arm64 |
| darwin-x64 | `libtree-sitter-xtc.dylib` | Mach-O x86_64 |
| linux-x64 | `libtree-sitter-xtc.so` | ELF x86-64 |
| linux-arm64 | `libtree-sitter-xtc.so` | ELF aarch64 |
| windows-x64 | `libtree-sitter-xtc.dll` | PE32+ x86-64 |

For detailed documentation on native library builds, Zig cross-compilation, and LSP adapter integration,
see **[tree-sitter/README.md → Native Library Build](./tree-sitter/README.md#native-library-build)**.

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
│  │               XtcCompilerAdapter (interface)            │    │
│  │  ┌──────────────┬──────────────┬──────────────┐         │    │
│  │  │ Mock         │ TreeSitter   │ Compiler     │         │    │
│  │  │ (regex)      │ (syntax)     │ (stub)       │         │    │
│  │  └──────────────┴──────────────┴──────────────┘         │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

### LSP Adapter Selection

```bash
# Tree-sitter (default) - syntax-aware, ~70% LSP features
./gradlew :lang:lsp-server:fatJar -Plsp.adapter=treesitter

# Mock - regex-based, for testing without native libraries
./gradlew :lang:lsp-server:fatJar -Plsp.adapter=mock

# Compiler stub - all methods logged, placeholder for future compiler
./gradlew :lang:lsp-server:fatJar -Plsp.adapter=compiler
```

## Tree-sitter Feature Matrix

For detailed documentation on what Tree-sitter can and cannot provide for LSP features, including
exact implementation locations in the codebase, see:

**[tree-sitter/doc/functionality.md](./tree-sitter/doc/functionality.md)**

This covers:
- LSP feature implementation matrix (which adapter handles what)
- What Tree-sitter can do (syntax highlighting, document symbols, basic navigation)
- What Tree-sitter cannot do (type inference, cross-file references, semantic diagnostics)
- File-by-file reference for all LSP implementation code

## Dependency Versions & Compatibility

The lang tooling uses several interdependent libraries. This section documents version constraints and compatibility.

### LSP Stack

| Library | Version | Purpose | Notes |
|---------|---------|---------|-------|
| **lsp4j** | 0.21.1 | LSP protocol types & JSON-RPC | Eclipse's Java LSP implementation |
| **lsp4ij** | 0.19.1 | IntelliJ LSP client plugin | Red Hat's plugin, uses lsp4j internally |

**How they work together:**
- **lsp4j** provides the LSP protocol implementation (types, JSON-RPC, message handling)
- **lsp4ij** is an IntelliJ plugin that provides LSP client support for any language
- Our `XtcLanguageServerFactory` creates an in-process LSP server using lsp4j
- lsp4ij connects to it via piped streams

### Tree-sitter Stack

| Library | Version | Purpose | Notes |
|---------|---------|---------|-------|
| **jtreesitter** | 0.24.1 | Java bindings for tree-sitter | JVM FFI to native tree-sitter |
| **tree-sitter-cli** | 0.24.3 | Parser generator CLI | Must match jtreesitter major.minor |

**Version Constraint (Java 21):**
```
⚠️  jtreesitter 0.25+ requires Java 22
⚠️  jtreesitter 0.26+ requires Java 23
✅  jtreesitter 0.24.x works with Java 21
```

IntelliJ 2025.1 ships with JBR 21 (JetBrains Runtime = Java 21), so we must use jtreesitter 0.24.x
until IntelliJ ships with JBR 22+ (expected: IntelliJ 2026.x).

Track JBR releases: https://github.com/JetBrains/JetBrainsRuntime/releases

### IntelliJ Platform

| Library | Version | Purpose |
|---------|---------|---------|
| **intellij-ide** | 2025.1 | Target IDE version |
| **intellij-jdk** | 21 | Plugin JDK requirement |
| **intellij-platform-gradle-plugin** | 2.10.5 | Build plugin for IntelliJ plugins |

### Compatibility Matrix

| Component | Requires | Provides |
|-----------|----------|----------|
| IntelliJ 2025.1 | JBR 21 | Plugin runtime |
| lsp4ij 0.19.1 | IntelliJ 2023.2+ | LSP client |
| lsp4j 0.21.1 | Java 11+ | LSP protocol |
| jtreesitter 0.24.1 | Java 21 | Native parsing |
| tree-sitter-cli 0.24.3 | - | Parser generation |

All versions are defined in `/gradle/libs.versions.toml`.

## Documentation

- [Language Support Overview](./doc/LANGUAGE_SUPPORT.md) - Comprehensive guide to implementing LSP and DAP support for Ecstasy
- [LSP Implementation Survey](./doc/LSP_IMPLEMENTATIONS_SURVEY.md) - Survey of how other languages implement language server support
- [Implementation Plans](./doc/plans/) - Detailed implementation plans for IDE integration and Tree-sitter grammar

Architecture analysis and research documentation: *Internal documentation*

## TODOs

- [ ] **Language naming consistency**: The formal language name is "Ecstasy", but the codebase currently uses "xtc" for technical identifiers (scope names, language IDs, file names). A future PR should audit and unify the naming to use "Ecstasy" where appropriate for user-facing elements while keeping "xtc" for technical identifiers where consistency is important (e.g., `source.xtc` scope name, `xtc.tmLanguage.json` file names).
