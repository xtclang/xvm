# Ecstasy Language Support for VS Code

Visual Studio Code extension for the [Ecstasy](https://xtclang.org) (XTC) programming language.

## Features

- **Syntax highlighting** for `.x` files via TextMate grammar, with semantic token layering for richer colours (types, methods, properties, annotations, modifiers)
- **Language Server Protocol (LSP)** — hover, code completion, go-to-definition, find references, document outline, and inlay hints
- **Debugger (DAP)** — launch and step-debug XTC modules via the Debug Adapter Protocol
- **Tasks** — built-in Build, Test, Clean, and Run tasks for Gradle-based XTC projects
- **Scaffolding** — "XTC: Create New Project" command to initialise new projects
- **Code snippets** for common XTC constructs
- **Automatic Java discovery** — finds or downloads a suitable Java 25+ JRE with no manual setup

## Requirements

| Requirement | Version |
|---|---|
| VS Code | 1.96.0 or later |
| Java | 25+ (auto-downloaded from Adoptium if not found) |
| XDK / `xtc` CLI | Required only for the **Create Project** command |

## Installation

### From VSIX

1. Download `xtc-language-<version>.vsix` from the [releases page](https://github.com/xtclang/xvm/releases) or build it from source (see below).
2. In VS Code: open the Extensions view → `···` menu → **Install from VSIX…**
3. Select the downloaded file and reload when prompted.

### Building from Source

> **Note:** All `./gradlew :lang:*` tasks require `-PincludeBuildLang=true -PincludeBuildAttachLang=true` when run from the repository root.

```bash
# Build the extension and produce the .vsix
./gradlew :lang:vscode-extension:build \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true
```

The packaged extension is written to `lang/vscode-extension/xtc-language-<version>.vsix`.

## Getting Started

### 1. Open a `.x` File

Syntax highlighting activates automatically as soon as you open any file with the `.x` extension. No extra configuration is needed.

### 2. Language Server

The LSP server starts automatically in the background. Watch the status bar — bottom-right of the VS Code window:

| Icon | Meaning |
|---|---|
| `⟳ XTC` (spinning) | Server is starting |
| `✓ XTC` | Server is ready |
| `⚠ XTC` | Server encountered an error — click to restart |
| `✗ XTC` (red) | Server stopped — click to restart |

Click any status bar item to open the **XTC Language Server** output panel for logs.

If the `lsp-server.jar` is missing (i.e. you haven't built the extension yet), VS Code will show an error notification with a button to display the required build command.

### 3. Java Discovery

The extension locates a Java 25+ runtime in the following order:

1. `xtc.java.home` VS Code setting (explicit override)
2. System discovery via `jdk-utils` (checks `JAVA_HOME`, `PATH`, SDKMAN, mise, asdf, jEnv, Homebrew, Gradle cache, etc.)
3. A previously downloaded JRE cached in the extension's global storage
4. Automatic download from [Eclipse Adoptium](https://adoptium.net) into global storage

For option 4 a progress notification appears during the download. The JRE is cached after the first download, so subsequent starts are instant.

## Usage

### Syntax Highlighting

Opens automatically for any `.x` file. Semantic tokens overlay the TextMate grammar once the language server is running, providing richer colour for types, methods, properties, annotations, and modifiers such as `static`, `@RO`, and `abstract`.

### LSP Features

Once the server status bar item shows `✓ XTC`, all LSP features are active:

| Feature | How to trigger |
|---|---|
| **Hover** | Move the cursor over any symbol |
| **Completion** | Type normally, or press `Ctrl+Space` to force suggestions |
| **Go to Definition** | `F12` or `Ctrl+Click` |
| **Find References** | `Shift+F12` |
| **Document Outline** | **View → Outline** |
| **Inlay Hints** | Shown inline automatically (toggle with `xtc.inlayHints.enabled`) |

### Debugging

1. Open a `.x` file containing an XTC module.
2. Press `F5` (or go to **Run → Start Debugging**).
3. VS Code detects the module name automatically and launches the DAP debug session.

To customise the debug launch, add a configuration to `.vscode/launch.json`:

```jsonc
{
    "type": "xtc",
    "request": "launch",
    "name": "Debug MyApp",
    "module": "MyApp",
    "method": "run",          // optional, defaults to "run"
    "args": "arg1,arg2",      // optional, comma-separated
    "cwd": "${workspaceFolder}"
}
```

The DAP server requires `dap-server.jar` (produced by the build). If it is missing, VS Code shows an error notification.

### Tasks

The extension provides an **xtc** task type. For any workspace folder that contains a `build.gradle.kts`, the following tasks appear automatically in **Terminal → Run Task…**:

| Task | Description |
|---|---|
| **Build** | Runs `./gradlew build` |
| **Test** | Runs `./gradlew test` |
| **Clean** | Runs `./gradlew clean` |

You can also define custom run tasks in `.vscode/tasks.json`:

```jsonc
{
    "type": "xtc",
    "label": "Run MyApp",
    "moduleName": "MyApp",
    "methodName": "run",        // optional
    "moduleArguments": "a,b",   // optional, comma-separated
    "useGradle": true,          // true = ./gradlew runXtc, false = xtc run
    "quietMode": true           // adds -q to the Gradle invocation
}
```

### Commands

Open the Command Palette (`Cmd+Shift+P` / `Ctrl+Shift+P`) and search for **XTC**:

| Command | Description |
|---|---|
| `XTC: Create New Project` | Scaffold a new XTC project in a chosen directory |
| `XTC: Run Module` | Run a named XTC module via Gradle |
| `XTC: Restart Language Server` | Stop and restart the LSP server |
| `XTC: Show Language Server Output` | Open the LSP output panel |

#### Creating a New Project

1. Run **XTC: Create New Project** from the Command Palette.
2. Enter a project name (must start with a letter or underscore; only letters, digits, and underscores are allowed).
3. Select a project type: **Application**, **Library**, or **Service**.
4. Choose a parent directory in the file picker.

The command opens an integrated terminal and runs:
```
xtc init "<name>" --type <type> --dir "<parent>"
```

### Snippets

Type a prefix in any `.x` file to insert a snippet:

| Prefix | Inserts |
|---|---|
| `mod` | `module` declaration |
| `cls` | `class` declaration |
| `iface` | `interface` declaration |
| `svc` | `service` declaration |
| `mix` | `mixin` declaration |
| `enu` | `enum` declaration |
| `con` | `const` declaration |
| `pkg` | `package` import |
| `meth` | Method declaration |
| `run` | `void run()` with `@Inject Console` |
| `runa` | `void run(String[] args)` with `@Inject Console` |
| `prop` | Property declaration |
| `roprop` | Read-only computed property |
| `construct` | Constructor |
| `if` | `if` statement |
| `ife` | `if-else` statement |

## Settings

All settings are under the `xtc` namespace in **Settings** (`Cmd+,` / `Ctrl+,`):

| Setting | Type | Default | Description |
|---|---|---|---|
| `xtc.java.home` | string | `""` | Path to a Java 25+ installation directory. Overrides all automatic discovery. |
| `xtc.trace.server` | enum | `"off"` | LSP wire-level tracing: `off`, `messages`, or `verbose`. |
| `xtc.inlayHints.enabled` | boolean | `true` | Show inlay hints from the language server. |
| `xtc.sourceRoots` | string[] | `[]` | Extra directories the language server indexes for `.x` files (e.g. XDK library sources). Accepts absolute paths. |
| `xtc.formatting.indentSize` | integer | `4` | Spaces per indentation level. |
| `xtc.formatting.continuationIndentSize` | integer | `8` | Spaces for continuation lines. |
| `xtc.formatting.tabSize` | integer | `4` | Tab width in spaces. |
| `xtc.formatting.insertSpaces` | boolean | `true` | Insert spaces instead of tabs. |
| `xtc.formatting.maxLineWidth` | integer | `120` | Right-margin column. |

### Changing Java Home

Set `xtc.java.home` to the root of a Java 25+ installation (the directory that contains `bin/java`):

```jsonc
// .vscode/settings.json
{
    "xtc.java.home": "/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home"
}
```

After changing this setting VS Code will prompt you to restart the language server.

### Adding XDK Library Sources

To get completions and hover information for the standard library, point `xtc.sourceRoots` at the `lib_ecstasy/src` directory of your XDK checkout:

```jsonc
{
    "xtc.sourceRoots": [
        "/path/to/xvm/lib_ecstasy/src/main/x"
    ]
}
```

After saving, VS Code will prompt you to restart the language server.

## Troubleshooting

| Symptom | Action |
|---|---|
| Status bar shows `⚠ XTC` or `✗ XTC` | Click the item to restart; check the **XTC Language Server** output panel for errors |
| "JAR not found" error on startup | Build the extension: `./gradlew :lang:vscode-extension:assemble -PincludeBuildLang=true -PincludeBuildAttachLang=true` |
| No completions or hover | Confirm the status bar shows `✓ XTC`; enable `xtc.trace.server: "verbose"` and check the output panel |
| Wrong Java version | Set `xtc.java.home` to a Java 25+ installation |
| `xtc init` not found on Create Project | Install the XDK and ensure `xtc` is on your `PATH` |

## Development

### Project Structure

```text
vscode-extension/
├── package.json                    # Extension manifest and configuration schema
├── tsconfig.json                   # TypeScript configuration
├── src/
│   ├── extension.ts                # Activation / deactivation entry point
│   ├── commands.ts                 # Command registrations (createProject, runModule, …)
│   ├── debug-adapter.ts            # DAP descriptor factory and launch config provider
│   ├── java.ts                     # Java 25+ discovery and JVM argument construction
│   ├── lsp-client.ts               # LSP client lifecycle, crash recovery, and wiring
│   ├── status-bar.ts               # Status bar state management
│   └── task-provider.ts            # Build / Test / Clean / Run task provider
├── syntaxes/
│   └── xtc.tmLanguage.json         # TextMate grammar (copied from tree-sitter build)
├── language-configuration.json     # Bracket matching, comment toggles, etc.
├── snippets/
│   └── xtc.json                    # Code snippets
├── server/
│   ├── lsp-server.jar              # LSP server (copied from lang:lsp-server build)
│   └── dap-server.jar              # DAP server (copied from lang:dap-server build)
└── icons/
    └── xtc.svg                     # File icon
```

### Building

```bash
# Build the VS Code extension only
./gradlew :lang:vscode-extension:build \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true

# Build all lang projects
./gradlew :lang:build \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true
```

### LSP Features

The language server starts automatically when you open a `.x` file:

- **Hover**: Move cursor over symbols for type information
- **Completion**: Type to see suggestions (Ctrl+Space to trigger)
- **Go to Definition**: Ctrl+Click or F12
- **Find References**: Shift+F12
- **Outline**: View → Outline

## Development

### Project Structure

```text
vscode-extension/
├── package.json           # Extension manifest
├── tsconfig.json          # TypeScript configuration
├── src/
│   ├── extension.ts       # Extension entry point (activation/deactivation)
│   ├── commands.ts        # Command registrations
│   ├── debug-adapter.ts   # DAP adapter + debug config provider
│   ├── java.ts            # Java 25+ discovery and JVM args
│   ├── lsp-client.ts      # LSP client lifecycle and wiring
│   ├── status-bar.ts      # Status bar state management
│   ├── task-provider.ts   # Task provider (build/test/clean/run)
│   └── test/              # Unit and integration tests
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

#### Option 1: Using Gradle (recommended)

```bash
# From the repository root
./gradlew :lang:vscode-extension:runIde
```

This builds the extension and launches VS Code with it loaded - similar to IntelliJ's `runIde`.

#### Option 2: Using VS Code directly

1. Open the `lang/vscode-extension` folder in VS Code
2. Press F5 to launch Extension Development Host
3. Open a folder containing `.x` files

### Using the Extension

Once VS Code launches with the extension:

#### 1. Syntax Highlighting (automatic)

- Open any `.x` file - highlighting works immediately
- You should see the XTC icon in the file tab

#### 2. Create a New Project

- Press `Cmd+Shift+P` (or `Ctrl+Shift+P`)
- Type "XTC: Create New Project"
- Follow the prompts (name, type, folder)
- This runs `xtc init` in a terminal

#### 3. LSP Features

The LSP server supports multiple adapters. See [LSP Server README](../lsp-server/README.md) for details.

```bash
# Build with default adapter (tree-sitter)
./gradlew :lang:vscode-extension:build

# Build with mock adapter (regex-based, no native dependencies)
./gradlew :lang:vscode-extension:build -Plsp.adapter=mock
```

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

**Troubleshooting**: The LSP server needs Java 25+ in your PATH or `JAVA_HOME` set. Check the Output panel (View →
Output → select "Ecstasy Language Server") if LSP isn't working.

## License

Apache License 2.0
