# XTC Language Support for VS Code

Visual Studio Code extension for XTC (Ecstasy) language support.

## Features

- **Syntax highlighting** for `.x` files (via TextMate grammar)
- **Language features via LSP** (currently mocked - real compiler integration coming):
  - Hover information
  - Code completion
  - Go to definition
  - Find references
  - Document outline
  - Diagnostics

> **Note**: The LSP server currently uses a mock adapter with basic regex-based parsing.
> Full semantic features (accurate go-to-definition, type-aware completion, etc.) will
> be available once the real XTC compiler is integrated.
- **Create Project** command to scaffold new XTC projects

## Requirements

- VS Code 1.75.0 or later
- Java 21+ (for the LSP server)
- XDK installed with `xtc` command in PATH (for project creation)

## Installation

### From VSIX (Development/Alpha Builds)

1. Download the `.vsix` file from releases
2. In VS Code: Extensions → `...` menu → "Install from VSIX..."
3. Select the downloaded file

### Building from Source

```bash
# From the repository root
./gradlew :lang:vscode-extension:build
```

The extension will be packaged as `lang/vscode-extension/xtc-language-<version>.vsix`

## Usage

### Syntax Highlighting

Open any `.x` file - syntax highlighting is automatic.

### Creating a New Project

1. Open Command Palette (Cmd+Shift+P / Ctrl+Shift+P)
2. Run "XTC: Create New Project"
3. Enter project name (letters, digits, underscores only)
4. Select project type (Application, Library, Service)
5. Choose parent folder

### LSP Features

The language server starts automatically when you open a `.x` file:
- **Hover**: Move cursor over symbols for type information
- **Completion**: Type to see suggestions (Ctrl+Space to trigger)
- **Go to Definition**: Ctrl+Click or F12
- **Find References**: Shift+F12
- **Outline**: View → Outline

## Development

### Project Structure

```
vscode-extension/
├── package.json           # Extension manifest
├── tsconfig.json          # TypeScript configuration
├── src/
│   └── extension.ts       # Extension entry point (LSP client)
├── syntaxes/
│   └── xtc.tmLanguage.json  # TextMate grammar (copied from build)
├── language-configuration.json  # Language config (copied from build)
├── server/
│   └── lsp-server.jar     # LSP server (copied from build)
└── icons/
    └── xtc.svg            # File icon
```

### Building

```bash
# Build just the VS Code extension
./gradlew :lang:vscode-extension:build

# Or build all lang projects
./gradlew :lang:build
```

### Testing Locally

**Option 1: Using Gradle (recommended)**

```bash
# From the repository root
./gradlew :lang:vscode-extension:runCode
```

This builds the extension and launches VS Code with it loaded - similar to IntelliJ's `runIde`.

**Option 2: Using VS Code directly**

1. Open the `lang/vscode-extension` folder in VS Code
2. Press F5 to launch Extension Development Host
3. Open a folder containing `.x` files

### Using the Extension

Once VS Code launches with the extension:

**1. Syntax Highlighting (automatic)**
- Open any `.x` file - highlighting works immediately
- You should see the XTC icon in the file tab

**2. Create a New Project**
- Press `Cmd+Shift+P` (or `Ctrl+Shift+P`)
- Type "XTC: Create New Project"
- Follow the prompts (name, type, folder)
- This runs `xtc init` in a terminal

**3. LSP Features** (mocked, but functional)
- **Hover**: Move cursor over symbols for type info
- **Completion**: `Ctrl+Space` to trigger suggestions
- **Go to Definition**: `Cmd+Click` or `F12`
- **Find References**: `Shift+F12`
- **Outline**: View → Outline panel

**To test, you need an XTC project:**

```bash
# Create a test project first
xtc init testapp --type app --dir /tmp
```

Then in VS Code:
1. File → Open Folder → select `/tmp/testapp`
2. Open `src/main/x/testapp.x`
3. You should see syntax highlighting and LSP features

**Troubleshooting**: The LSP server needs Java 21+ in your PATH or `JAVA_HOME` set. Check the Output panel (View → Output → select "XTC Language Server") if LSP isn't working.

## License

Apache License 2.0
