# Ecstasy (XTC) Language Support for VS Code

Official Visual Studio Code extension for the **[Ecstasy programming language](https://xtclang.org)** (file extension `.x`, runtime XVM, compiler `xtc`).

Ecstasy is a modular, object-oriented language designed for secure, multi-tenant cloud computing. The language is statically typed, uses a verifiable bytecode format, and ships with first-class support for services, fibers, and immutability. This extension brings rich IDE tooling for Ecstasy directly into VS Code: syntax highlighting, semantic tokens, hover, completion, navigation, debugging, project scaffolding, and Gradle task integration.

![Ecstasy](icons/xtc.png)

## Features

- **Syntax highlighting** for `.x` files via a TextMate grammar generated from the official tree-sitter parser
- **Semantic tokens** — types, methods, properties, annotations, and modifiers (`static`, `@RO`, `abstract`, etc.) get richer colour from the language server
- **Language Server Protocol (LSP)** — hover, completions, go-to-definition, find references, document outline, inlay hints, and diagnostics
- **Debug Adapter Protocol (DAP)** — launch and step-debug Ecstasy modules with breakpoints, variables, and call stack inspection
- **Tasks** — auto-discovered Gradle Build / Test / Clean / Run tasks for any workspace that contains a `build.gradle.kts`
- **Project scaffolding** — `Ecstasy: Create New Project` command to bootstrap a new Ecstasy application, library, or service
- **Snippets** for common Ecstasy constructs (`module`, `class`, `service`, `mixin`, `const`, etc.)
- **Automatic Java discovery** — finds or downloads a suitable Java 25+ JRE; no manual setup needed

## Requirements

| Requirement   | Version / Notes                                                    |
|---------------|--------------------------------------------------------------------|
| VS Code       | `1.96.0` or later                                                  |
| Java runtime  | `25+` (auto-discovered, or auto-downloaded from Adoptium)          |
| XDK / `xtc`   | Required only for the **Create Project** command                   |

## Installation

### From the Marketplace

Search for **Ecstasy Language Support** in the Extensions view (`Cmd+Shift+X` / `Ctrl+Shift+X`) and click **Install**.

### From a VSIX

1. Download `xtc-language-<version>.vsix` from the [releases page](https://github.com/xtclang/xvm/releases), or build it locally (see [Building](#building-from-source)).
2. In VS Code: open the Extensions view → `···` menu → **Install from VSIX…** → select the file.
3. Reload VS Code when prompted.

### Building from Source

> **Note:** All `./gradlew :lang:*` tasks require `-PincludeBuildLang=true -PincludeBuildAttachLang=true` when run from the repository root.

```bash
./gradlew :lang:vscode-extension:build \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true
```

The resulting `xtc-language-<version>.vsix` is written to `lang/vscode-extension/`.

## Quick Start

1. Open any folder containing `.x` files (or run `Ecstasy: Create New Project`).
2. Open a `.x` source file — syntax highlighting activates immediately.
3. Watch the bottom-right status bar for the LSP server state — once it shows `✓ XTC`, all language features are live.

## Language Server

The LSP server starts automatically on activation. Status is shown in the status bar:

| Indicator         | Meaning                                                |
|-------------------|--------------------------------------------------------|
| `⟳ XTC` (spinning)| Server is starting                                     |
| `✓ XTC`           | Server is ready                                        |
| `⚠ XTC`           | Server encountered an error — click to restart        |
| `✗ XTC` (red)     | Server stopped — click to restart                      |

Click the status bar item to open the **XTC Language Server** output channel for logs.

### Java Discovery

The extension locates a Java 25+ runtime in this order:

1. `xtc.java.home` setting (explicit override)
2. System discovery via `jdk-utils` (`JAVA_HOME`, `PATH`, SDKMAN, mise, asdf, jEnv, Homebrew, Gradle cache)
3. A previously downloaded JRE cached in the extension's global storage
4. Automatic download from [Eclipse Adoptium](https://adoptium.net)

A progress notification appears during the first download. The JRE is then cached, so subsequent starts are instant.

## Using the Extension

### LSP Features

Once the status bar shows `✓ XTC`:

| Feature              | Trigger                       |
|----------------------|-------------------------------|
| **Hover**            | Move cursor over a symbol     |
| **Completion**       | Type or press `Ctrl+Space`    |
| **Go to Definition** | `F12` or `Ctrl+Click`         |
| **Find References**  | `Shift+F12`                   |
| **Outline**          | **View → Outline**            |
| **Inlay Hints**      | Shown automatically           |

### Debugging

1. Open a `.x` file containing an Ecstasy module.
2. Press `F5` (or **Run → Start Debugging**) — the module name is auto-detected.

To customise the launch, add a configuration to `.vscode/launch.json`:

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

### Tasks

For any workspace folder containing a `build.gradle.kts`, the following tasks appear under **Terminal → Run Task…**:

| Task     | Command            |
|----------|--------------------|
| Build    | `./gradlew build`  |
| Test     | `./gradlew test`   |
| Clean    | `./gradlew clean`  |

Custom run tasks can be added to `.vscode/tasks.json`:

```jsonc
{
    "type": "xtc",
    "label": "Run MyApp",
    "moduleName": "MyApp",
    "methodName": "run",        // optional
    "moduleArguments": "a,b",   // optional, comma-separated
    "useGradle": true,          // true: ./gradlew runXtc; false: xtc run
    "quietMode": true           // adds -q to the Gradle invocation
}
```

### Commands

Open the Command Palette (`Cmd+Shift+P` / `Ctrl+Shift+P`) and search for **Ecstasy**:

| Command                                  | Description                                            |
|------------------------------------------|--------------------------------------------------------|
| `Ecstasy: Create New Project`            | Scaffold a new Ecstasy project (`xtc init`)            |
| `Ecstasy: Run Module`                    | Run a named Ecstasy module via Gradle                  |
| `Ecstasy: Restart Language Server`       | Stop and restart the LSP server                        |
| `Ecstasy: Show Language Server Output`   | Open the LSP output channel                            |

### Snippets

Type a prefix in any `.x` file to insert a snippet:

| Prefix      | Inserts                                          |
|-------------|--------------------------------------------------|
| `mod`       | `module` declaration                             |
| `cls`       | `class` declaration                              |
| `iface`     | `interface` declaration                          |
| `svc`       | `service` declaration                            |
| `mix`       | `mixin` declaration                              |
| `enu`       | `enum` declaration                               |
| `con`       | `const` declaration                              |
| `pkg`       | `package` import                                 |
| `meth`      | Method declaration                               |
| `run`       | `void run()` with `@Inject Console`              |
| `runa`      | `void run(String[] args)` with `@Inject Console` |
| `prop`      | Property declaration                             |
| `roprop`    | Read-only computed property                      |
| `construct` | Constructor                                      |
| `if`        | `if` statement                                   |
| `ife`       | `if`-`else` statement                            |

## Extension Settings

All settings live under the `xtc` namespace (`Cmd+,` / `Ctrl+,`):

| Setting                                | Type      | Default | Description                                                                                              |
|----------------------------------------|-----------|---------|----------------------------------------------------------------------------------------------------------|
| `xtc.java.home`                        | string    | `""`    | Java 25+ install path. Overrides automatic discovery.                                                    |
| `xtc.trace.server`                     | enum      | `"off"` | LSP wire tracing: `off`, `messages`, `verbose`.                                                          |
| `xtc.inlayHints.enabled`               | boolean   | `true`  | Show inlay hints from the language server.                                                               |
| `xtc.sourceRoots`                      | string[]  | `[]`    | Extra absolute directories indexed by the language server (e.g. XDK standard-library sources).           |
| `xtc.formatting.indentSize`            | integer   | `4`     | Spaces per indentation level.                                                                            |
| `xtc.formatting.continuationIndentSize`| integer   | `8`     | Spaces for continuation lines.                                                                           |
| `xtc.formatting.tabSize`               | integer   | `4`     | Tab width in spaces.                                                                                     |
| `xtc.formatting.insertSpaces`          | boolean   | `true`  | Insert spaces instead of tabs.                                                                           |
| `xtc.formatting.maxLineWidth`          | integer   | `120`   | Right-margin column.                                                                                     |

### Pointing at a specific Java

```jsonc
// .vscode/settings.json
{
    "xtc.java.home": "/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home"
}
```

VS Code will prompt to restart the language server after the change.

### Indexing the XDK standard library

```jsonc
{
    "xtc.sourceRoots": [
        "/path/to/xvm/lib_ecstasy/src/main/x"
    ]
}
```

## Contributed Language Configuration

This extension contributes the following defaults for the `xtc` language:

- `editor.tabSize`: `4`
- `editor.insertSpaces`: `true`
- `editor.formatOnType`: `true`
- `editor.autoIndent`: `"full"`
- `editor.semanticHighlighting.enabled`: `true`
- `editor.bracketPairColorization.enabled`: `true`
- `editor.guides.bracketPairs`: `"active"`
- `files.associations`: `*.x` → `xtc`

## Troubleshooting

| Symptom                                                | Action                                                                                                  |
|--------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| Status bar shows `⚠ XTC` or `✗ XTC`                    | Click to restart; check the **XTC Language Server** output channel for errors.                          |
| "JAR not found" error on activation                    | Rebuild: `./gradlew :lang:vscode-extension:assemble -PincludeBuildLang=true -PincludeBuildAttachLang=true`. |
| No completions or hover                                | Confirm `✓ XTC` in the status bar; enable `xtc.trace.server: "verbose"` and inspect the output channel. |
| Wrong Java version detected                            | Set `xtc.java.home` to a Java 25+ installation directory.                                               |
| `xtc init` not found when running **Create Project**   | Install the XDK and put `xtc` on your `PATH`.                                                           |
| `.x` files not highlighted                             | Run **Developer: Reload Window**; verify the file's language mode (bottom-right) is `Ecstasy`.          |

For deeper diagnostics, open **View → Output** and select **XTC Language Server**.

## Development

### Project Structure

```text
vscode-extension/
├── package.json                    # Extension manifest and contribution points
├── tsconfig.json                   # TypeScript configuration
├── src/
│   ├── extension.ts                # Activation / deactivation entry point
│   ├── commands.ts                 # Ecstasy: ... command registrations
│   ├── debug-adapter.ts            # DAP descriptor factory and config provider
│   ├── java.ts                     # Java 25+ discovery and JVM argument construction
│   ├── lsp-client.ts               # LSP client lifecycle and crash recovery
│   ├── status-bar.ts               # Status bar state management
│   └── task-provider.ts            # Build / Test / Clean / Run task provider
├── syntaxes/
│   └── xtc.tmLanguage.json         # TextMate grammar (copied from tree-sitter build)
├── snippets/
│   └── xtc.json                    # Code snippets
├── language-configuration.json     # Brackets, comments, surrounding pairs
├── server/
│   ├── lsp-server.jar              # LSP server (copied from :lang:lsp-server)
│   └── dap-server.jar              # DAP server (copied from :lang:dap-server)
└── icons/
    ├── xtc.png                     # Marketplace / extension icon (256×256)
    ├── xtc-file.png                # File icon for .x files
    └── xtc.avif                    # Source artwork
```

### Build commands

```bash
# Build the VS Code extension and produce the .vsix
./gradlew :lang:vscode-extension:build \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true

# Prepare resources without packaging (useful while iterating)
./gradlew :lang:vscode-extension:assemble \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true

# Launch a clean VS Code instance with the extension loaded
./gradlew :lang:vscode-extension:runCode \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true
```

### Running from VS Code (Extension Development Host)

1. Open the `lang/vscode-extension/` folder in VS Code.
2. Press `F5` to launch the Extension Development Host.
3. Open a folder with `.x` files (or create one with `xtc init`).

### LSP adapter selection

The language server supports multiple parser adapters:

```bash
# Default (tree-sitter)
./gradlew :lang:vscode-extension:build

# Mock adapter (regex-based, no native dependencies)
./gradlew :lang:vscode-extension:build -Plsp.adapter=mock
```

See the [LSP server README](../lsp-server/README.md) for details.

## Links

- Ecstasy language site: [xtclang.org](https://xtclang.org)
- Source repository: [github.com/xtclang/xvm](https://github.com/xtclang/xvm)
- Issues: [github.com/xtclang/xvm/issues](https://github.com/xtclang/xvm/issues)

## License

Apache License 2.0 — see [LICENSE.md](LICENSE.md).
