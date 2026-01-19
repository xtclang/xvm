# XTC Language Tooling

Language tooling for the Ecstasy/XTC programming language, including LSP server, IDE plugins, and editor support.

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

## Documentation

Architecture analysis and research documentation is in a separate repository:
[xtc-language-support-research](https://github.com/xtclang/xtc-language-support-research)

Implementation plans are in [doc/plans/](./doc/plans/).
