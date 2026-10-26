# Ecstasy IntelliJ Plugin

The bundled language server uses the compiler adapter by default. Tree-sitter remains a selectable
syntax-only alternative. Compiler features include diagnostics, navigation, references, completion,
theme-based semantic highlighting and guarded refactorings over configured source and library inputs.
See the [manual playbook](../doc/manual-test-plan.md) for exact feature boundaries and native tests.

## Features

- **New Project Wizard** - Create XTC projects directly from IntelliJ (File → New → Project → XTC)
- **Build and Run** - Build/Rebuild Project and Ecstasy run configurations use the language
  server's embedded compiler or Gradle, chosen under Settings | Build, Execution, Deployment |
  Ecstasy ([details](#running-your-application)); `xtc run` remains available per configuration
- **Debug** - With the embedded compiler, Debug of an Ecstasy run configuration goes through
  LSP4IJ's DAP client to a debug session in the language server: breakpoints, stepping, variables,
  watches and Evaluate Expression ([details](#running-your-application))
- **Syntax Highlighting** - TextMate grammar for `.x` files, explicitly connected to Ecstasy’s
  native file type through TextMate’s syntax factory and editor highlighter.
- **Semantic Highlighting** - Enabled by default; compiler-resolved types, functions, enum members
  annotations and bundled-library identities refine the editor's theme colors. String escapes,
  interpolation, keywords and numeric literals retain their lexical scopes. See the
  [highlighting playbook](../doc/manual-test-plan.md#19-semantic-tokens) for examples and opt-out settings.
- **Language Features via LSP** - hover, completion, go-to-definition, find references, outline,
  auto-indent on type (see [LSP Server README](../lsp-server/README.md) for details)
- **Code Style Settings** - Configurable indentation defaults under
  Settings > Editor > Code Style > Ecstasy (indent size, continuation indent, tabs vs spaces).
  Settings flow to the LSP server via `workspace/configuration` and are used for on-type
  formatting when no project-level `xtc-format.toml` is present.

## Installation

### From JetBrains Marketplace (Recommended)

1. Open IntelliJ IDEA
2. Go to **Settings/Preferences → Plugins → Marketplace**
3. Search for "Ecstasy Language Support"
4. Click **Install**
5. Restart IntelliJ IDEA

### From Disk (Development/Alpha Builds)

1. Download the plugin ZIP from [Releases](https://github.com/xtclang/xvm/releases)
2. Open IntelliJ IDEA
3. Go to **Settings/Preferences → Plugins**
4. Click the gear icon → **Install Plugin from Disk...**
5. Select the downloaded ZIP file
6. Restart IntelliJ IDEA

### Building from Source

> **Note:** All `./gradlew :lang:*` commands require `-PincludeBuildLang=true -PincludeBuildAttachLang=true` when run from the project root.

```bash
# From the repository root
./gradlew :lang:intellij-plugin:buildPlugin
```

The plugin ZIP will be created at:
```
lang/intellij-plugin/build/distributions/intellij-plugin-<version>.zip
```

Then install manually:
1. Open IntelliJ IDEA
2. **Settings/Preferences → Plugins**
3. Click the gear icon (⚙️) → **Install Plugin from Disk...**
4. Navigate to and select the ZIP file
5. Restart IntelliJ IDEA

## Prerequisites

- IntelliJ IDEA 2026.2 or later (build 262+); development and acceptance tests target 2026.2.3
- The free feature set is sufficient; no Ultimate subscription is required
- XDK installed and `xtc` command available in PATH
- Gradle plugin for IntelliJ (bundled with most editions)

## Usage

### Creating a New Project

1. **File → New → Project**
2. Select **XTC** from the left panel
3. Configure:
   - **Project name** - Name of your project/module
   - **Project type** - Application, Library, or Service
   - **Multi-module** - Check for multi-module project structure
4. Click **Create**

The plugin invokes `xtc init` to scaffold the project, then imports it as a Gradle project.

### Running Your Application

**Settings | Build, Execution, Deployment | Ecstasy → Build and run using** chooses how the project
builds and runs, like IntelliJ's own setting for Gradle projects. It is stored with the project
(`.idea/ecstasy-build.xml`):

| Choice | Build / Rebuild Project | Ecstasy run configurations set to *Project default* |
|--------|-------------------------|------------------------------------------------------|
| Embedded compiler (default) | The language server's compiler builds every source module in dependency order into `build/xtc/embedded/`; results and linked diagnostics appear in the **Build** tool window | The server builds the module, then runs it in its execution worker JVM |
| Automatic | Gradle when the project root has a Gradle build, embedded otherwise | Same choice |
| Gradle | Left to Gradle or the IDE's builder | `./gradlew runXtc` |

The embedded compiler is the default also in a project with a Gradle build. It builds with the
language server's compiler and bundled XDK, so the Gradle build's own settings, such as `runXtc`
arguments, JVM options or its XDK version, do not apply; embedded builds and runs in such a project
say so in the Build tool window and Run console, with a link to the settings page. Choose **Gradle**
to use Gradle. Modules without Ecstasy sources keep building with Gradle or the IDE's builder.

Ways to run a module:

- The **▶ Run** lens above the `module` declaration. It appears only on modules that declare a
  `run` method the runner can call: exactly one method of that name, with at most one required
  parameter. The lens reuses the module's run configuration or creates a temporary one.
- **Run Context Configuration** (`Ctrl+Shift+R` on macOS, `Ctrl+Shift+F10` elsewhere) or the
  editor context menu in an Ecstasy file.
- A saved **Ecstasy Application** configuration (**Run → Edit Configurations… → + → Ecstasy
  Application**), from the toolbar or `Shift+F10`.

Build Project is `Cmd+F9` / `Ctrl+F9`; Rebuild Project is in the **Build** menu. In embedded mode
both build every Ecstasy module, even from *Build Module*, from the editor's text, saved or not: the
plugin saves no files. IntelliJ itself saves all files before a run, not before a build.

An Ecstasy Application configuration has:

- **Module name** — the module to run.
- **Module source** — optional source file URI. Embedded runs use it ahead of the name, because it
  identifies the module exactly; the Run lens and Run Context Configuration fill it in.
- **Method name** — the entry method; empty means `run`.
- **Module arguments** — comma-separated. Embedded runs pass them as a `String[]` when the method
  requires one, or when arguments are given and it accepts a parameter.
- **Run with** — *Project default*, *Embedded compiler*, *Gradle* or *xtc command line*.
  Configurations saved with the older *Use Gradle* checkbox become *Project default* (or *xtc
  command line* when it was cleared).
- **Quiet mode** — adds `-q` to Gradle runs.
- **Stop at the program's first line** — under Debug, stops the program before its module's own
  code runs.
- **Let the program run while Evaluate Expression runs code** — under Debug, the rest of the
  program runs too while Evaluate Expression runs code.

Embedded runs use a normal Run console with **Stop** and **Rerun**. Each run gets a fresh
application container. Each running program has an execution worker JVM of its own, so up to four
programs run at once; one idle worker is kept for the next run, and after a worker dies the next run
gets a new one. Programs never run inside the language server. A failed build prints its
diagnostics in the console, linked to the sources, and runs nothing. A program that reads console
input reads the lines typed into its Run console, including lines typed while it still builds;
**Send EOF** ends the input. A module with a Run lens also has a Run icon in the gutter of that
line, which runs it the same way.
Embedded mode needs the compiler language service.

With the embedded compiler, a configuration can also be debugged: **Debug** starts a debug session
in the language server, which builds and runs the module as Run does, in the same execution workers,
with LSP4IJ's DAP client as the debugger. Line breakpoints can be set in `.x` files; they are
verified when the program starts, and a breakpoint or `assert:debug` stops the program, with its
line, call stack and the other fibers that wait shown in the Debug tool window. The running program
keeps the code it was built from, so once the file is edited a breakpoint is placed on the line it
was built with, and one on a line changed since is marked invalid, saying why. Its Variables view
shows the frame's locals and expands objects and arrays, a large array to its first 100 elements and
how many more there are, since LSP4IJ does not read a variable's children a page at a time (UP42 in
`docs/remaining.md`); LSP4IJ shows strings and numbers without their type. An object's computed properties and its `toString()` follow its fields as lazy
variables, `(...)`, whose values run the program's code, for at most a second; expand one to show
its value, since LSP4IJ does not show a Show value link for them yet (UP35, LSP4IJ pull request
[#1768](https://github.com/redhat-developer/lsp4ij/pull/1768)). A mutable object's value ends with its
identity code, such as `#1023456`, which tells two objects with the same fields apart. Integers in
hexadecimal wait on LSP4IJ, whose Variables view has no Show Values as Hexadecimal yet (UP36, LSP4IJ
pull request [#1769](https://github.com/redhat-developer/lsp4ij/pull/1769)). **Set Value** on a local, a
field or an array element gives it the value of an expression such as `5`, `n * 2` or `"text"`,
computed without running the program's code; one of another type, or a field of an immutable object,
is refused with the reason. In **Evaluate Expression**, **Basic Completion** completes the name
typed: after a dot, with the fields, properties and methods of the value before it, otherwise with
the frame's variables and the members of `this`; nothing of the program runs to find them. LSP4IJ
0.21.0 reports an IDE error, `Document/PSI mismatch`, as the expression there is edited (UP37, fixed
by LSP4IJ pull request [#1763](https://github.com/redhat-developer/lsp4ij/pull/1763)). **Step Over**, **Step Into** and **Step Out** move the stopped fiber line by line,
**Resume Program** runs it on and **Stop** ends it where it is. A breakpoint's condition, an
expression such as `done` or `i % 2 == 0 && i > 0`, decides whether it stops. Its pass count and
**Evaluate and log**, and a breakpoint whose **Suspend** is unchecked, wait on LSP4IJ, which sends only the condition (UP32 in
`docs/remaining.md`, LSP4IJ pull request [#1759](https://github.com/redhat-developer/lsp4ij/pull/1759)).
An uncaught exception stops the program where it is thrown, by default, and the exception is the
frame's first variable; exception filter conditions and the exception's stack in the hint wait on
LSP4IJ too (UP34, UP33). The
Watches view shows expressions such as `point.x + 1` or `ints[i] > 0` in the selected frame, without
running the program's code, and what is not evaluated shows why; LSP4IJ's debugger hover evaluates
the name under the mouse. **Evaluate Expression** evaluates them so too, and runs what that refuses,
such as a call, as code on the stopped fiber, for at most two seconds, and what the code changed
stays changed; the Variables view shows it the next time it reads the variables, since LSP4IJ does
not handle the `invalidated` event yet (UP40). Only that code, and the services it calls, run
meanwhile; the configuration's **Let the program run while Evaluate Expression runs code** lets the
rest of the program run too, without stopping at breakpoints, for code that waits on work the
program's other fibers do. **Pause
Program** does nothing yet: LSP4IJ's DAP client does not pause (UP30 in `docs/remaining.md`, fixed in
LSP4IJ pull request [#1757](https://github.com/redhat-developer/lsp4ij/pull/1757)). The configuration's **Stop at the
program's first line** stops it there, before the module's own code runs, as `stopOnEntry` does in VS Code. Debug is
not offered for Gradle or command-line runs.

## Development

### Building the Plugin

```bash
# From the repository root

# Build distributable ZIP (for manual installation or sharing)
./gradlew :lang:intellij-plugin:buildPlugin
# Output: lang/intellij-plugin/build/distributions/intellij-plugin-<version>.zip

# Run in a sandbox IDE for quick testing during development
./gradlew :lang:intellij-plugin:runIde

# Run plugin verification (checks compatibility with target IDE versions)
./gradlew :lang:intellij-plugin:verifyPlugin
```

### Build Artifacts

`verifyPlugin` also runs `verifyPluginInternalApis`. Binary incompatibilities, invalid plugins
and missing dependencies fail verification. Internal API usages are checked against
[`verifier-lsp4ij-apis.txt`](verifier-lsp4ij-apis.txt), which records the exact LSP4IJ 0.21.0
signatures used for live-connection ownership, semantic-cache invalidation and coalesced editor
refresh. These remain unsupported upstream APIs; the list documents a compatibility risk, not a
public-API guarantee. Other internal APIs, changed signatures and LSP4IJ version changes fail the
check. Complete verifier reports are retained under `build/reports/pluginVerifier` and uploaded
by CI. Revisit the exceptions when upgrading LSP4IJ, and remove them as public replacements are
adopted. Publishing requires both checks.

| Task | Output | Use Case |
|------|--------|----------|
| `buildPlugin` | `build/distributions/*.zip` | Install in any IntelliJ instance |
| `runIde` | Launches sandbox IDE | Quick testing during development |
| `verifyPlugin` | Verification report | Check IDE compatibility |

### Compiler playbook in IntelliJ

```bash
./gradlew :lang:intellij-plugin:testCompilerPlaybook \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true
```

This opt-in task launches the packaged plugin with released LSP4IJ in an isolated IntelliJ profile.
Ultimate features and automatic trials are disabled. The 324-case catalog is shared with VS Code;
see the [manual playbook](../doc/manual-test-plan.md) and [testing reference](testing.md).

The task also runs startup editing, focus recovery, settings persistence and project-lifecycle tests.
Select a method with `--tests '*CompilerPlaybookTest.compilerPlaybook'`, or select feature cases with
`-PintellijPlaybookCases=X145,X146,X147,X259`. Omit the case property for the full catalog.
The large-file probe is separately enabled with `-PintellijLargeFileProbe=true`.

The status bar displays the current case and completed/remaining counts. The harness may restore
desktop focus; avoid typing into its fixtures. Failed operations stop that run, and timed-out
mutations are not replayed. Unexpected IDE errors fail acceptance. X254/X257 retain explicit partial
coverage for the [client compatibility limits](../doc/manual-test-plan.md#client-compatibility-limits).

Results, live `progress.jsonl`, screenshots and server traces are written under
`build/reports/compiler-playbook/run-*`. Inspect `results.json` and `ideFailures`, plus JUnit XML
under `build/test-results/testCompilerPlaybook/`. `ide-paths.txt` identifies the disposable IDE logs.
Tests use the signed native IDE distribution and JBR from the Starter cache; test-only probes are
absent from the shipped plugin. Report cleanup retains five completed payloads by default.

### Compiler source-module configuration

Compiler mode bundles the full matching XDK library set as read-only dependencies and discovers
source modules/import edges under workspace folders at startup and on watched-file changes.
The configuration below overrides that discovered graph. Set `sourceModules` to `null` to
restore discovery; `[]` disables it. Unsaved import edges, close and dynamic folder changes now refresh discovered graphs.

With a compiler build, open **Settings → Languages & Frameworks → Language Servers**, select
**Ecstasy Language Server**, and edit its **Configuration** JSON:

```json
{
  "xtc": {
    "compiler": {
      "sourceModules": [
        { "name": "Library", "uri": "Library.x" },
        { "name": "Consumer", "uri": "Consumer.x", "dependencies": ["Library"] }
      ]
    }
  }
}
```

LSP4IJ stores these server settings at IDE scope. Relative paths resolve against the current
single workspace folder; use absolute file URIs when needed. Applying settings notifies the
running server. Setting `sourceModules` to `[]` clears the graph; omitting it preserves the
current graph. This uses LSP4IJ's existing JSON editor, not a dedicated XTC project-settings UI.
In **Languages & Frameworks > Ecstasy Compiler**, **Add consumers from folders** adds the modules under
the chosen folders that import the running graph as table rows (an empty table first takes the effective
modules); they apply with **Apply**. Rename covers only modules in the graph.
`XtcLanguageClient` delegates section lookup and configuration notifications to LSP4IJ; only
`xtc.formatting` reads Ecstasy Code Style settings.

### Release-Grade Build And Publish

Use these commands from the repository root when preparing an installable alpha ZIP or publishing to JetBrains Marketplace:

```bash
./gradlew \
  -PincludeBuildLang=true \
  -PincludeBuildAttachLang=true \
  -Plsp.buildSearchableOptions=true \
  :lang:intellij-plugin:buildPlugin \
  :lang:intellij-plugin:verifyPlugin
```

To inspect what searchable-options generation actually produced:

```bash
./gradlew \
  -PincludeBuildLang=true \
  -PincludeBuildAttachLang=true \
  :lang:intellij-plugin:summarizeSearchableOptions
```

```bash
./gradlew \
  -PincludeBuildLang=true \
  -PincludeBuildAttachLang=true \
  -PenablePublish=true \
  -Plsp.buildSearchableOptions=true \
  :lang:intellij-plugin:publishPlugin
```

For snapshot Marketplace publishes, the build derives a unique timestamped publish version automatically so JetBrains Marketplace accepts repeated alpha uploads.

### Expected `buildSearchableOptions` Noise

When `-Plsp.buildSearchableOptions=true` is enabled, Gradle launches a headless IntelliJ instance to traverse settings pages and generate searchable options metadata. That IDE process currently emits a lot of warnings that look alarming but are not plugin verification failures.

The most common expected warnings are:

- `InstanceNotOverridableException` from internal IntelliJ services
- `Daemon rejected discovery request` against the local `.intellijPlatform/ides` directory
- `Job was cancelled` stack traces from background Marketplace/UI requests during panel disposal
- bundled JetBrains theme warnings such as `Theme Islands Darcula ... deprecated`
- headless environment warnings such as `JCEF is manually disabled`

What matters is the Gradle task outcome:

- `BUILD SUCCESSFUL` means searchable options generation completed
- Plugin compatibility is determined by `:lang:intellij-plugin:verifyPlugin`, not by those headless IDE warnings
- JetBrains Marketplace upload success/failure is determined by the `publishPlugin` task response, not by searchable-options log noise

### Installing the Built Plugin

After running `buildPlugin`, you can install the ZIP in an IntelliJ IDEA 2026.2+ instance:

1. Locate the ZIP: `lang/intellij-plugin/build/distributions/intellij-plugin-<version>.zip`
2. Open IntelliJ IDEA → **Settings/Preferences → Plugins**
3. Click ⚙️ → **Install Plugin from Disk...**
4. Select the ZIP file
5. Restart IntelliJ IDEA

This is useful for:
- Testing in your main IDE (not a sandbox)
- Sharing with team members before publishing
- Testing on different IDE versions
- Verifying the plugin works outside the development environment

### Testing During Development

When running `runIde`, a sandboxed IntelliJ IDEA instance opens with the plugin installed. Here's how to test each
feature:

#### Testing Syntax Highlighting

1. Create or open a project containing `.x` files
2. Open a `.x` file - you should see:
   - XTC file icon (X logo) in the file tree
   - Syntax highlighting (keywords, strings, comments colored)
   - The file type shows as "XTC Source" in the status bar
3. If syntax highlighting doesn't work, check:
   - **Help → Show Log in Finder** and look for TextMate bundle errors
   - Ensure the `textmate/` directory exists in the plugin's lib folder

#### Testing the Project Creation Wizard

1. **File → New → Project...**
2. In the left panel, select **XTC**
3. Configure:
   - **Project name**: Enter a name (e.g., "MyXtcApp")
   - **Project type**: Choose Application, Library, or Service
   - **Multi-module**: Check for multi-module project structure
4. Click **Create**
5. Verify:
   - A Gradle project is created with XTC structure
   - The `build.gradle.kts` contains XTC plugin configuration
   - Sample `.x` files are generated

#### Testing Run Configurations

1. Open a project with XTC modules
2. **Run → Edit Configurations...**
3. Click **+** → **Ecstasy Application**
4. Configure:
   - **Module name**: The XTC module containing your app
   - **Module arguments**: Any arguments for your app
   - **Run with**: *Project default*, or a specific mode
5. Click **Apply**, then **Run**
6. Verify:
   - The run configuration appears in the toolbar
   - In Gradle mode, running invokes Gradle's `runXtc` task
   - In embedded mode, the console shows the build, the program's output and its exit code, and
     **Stop** ends a running program
   - Output appears in the Run tool window
7. With **Build and run using** set to *Embedded compiler*, **Build → Build Project** reports each
   module and any diagnostics in the **Build** tool window

#### Testing LSP Features (Language Server)

Use **Tools → Switch Ecstasy Language Adapter**, Find Action, or **Ctrl+Alt+X, then A**
(**Control+Option+X, then A** on macOS) to select **Ecstasy Compiler**, **Tree-sitter** or
**Bundled default**. The same choice is available in **Ecstasy Language Service** settings.
Compiler provides semantic analysis; Tree-sitter provides syntax-based features without compiler
type checking. Switching restarts the server and reopens unsaved buffers. Both adapters ship in
the plugin, so switching does not require rebuilding or changing LSP4IJ.

To restart from a terminal, use the bundled `bin/xtc-lsp` script (Python 3 required), or run it
from this repository:

```bash
python3 lang/scripts/xtc-lsp list
python3 lang/scripts/xtc-lsp restart --project ~/src/platform --ide intellij
```

The project must already be open in the updated plugin, with an Ecstasy file opened at least once.
The CLI asks IntelliJ to restart its server and resynchronize unsaved buffers; it does not save files
or send process signals. Omit `--ide` when only one editor has that exact project root open. If multiple
windows match, use `--session` with an ID from `list`. A failed startup is reported to the CLI; the
editor control endpoint remains available for another attempt. A timeout does not automatically retry.

Discovery records live in `~/.xtc/editor-control`. The endpoint binds only to IPv4 loopback and requires
a per-session token. Unix records are owner-only; Windows records inherit the user-profile directory's
permissions. Records are removed on project close; stale records left by an IDE crash are ignored.
For remote IDE sessions, run the CLI on the machine hosting the IDE backend.

The default setting honors the server build's choice, normally Compiler. Development builds can
still change that default. See [LSP Server README](../lsp-server/README.md) for details.

```bash
# Run with the default compiler adapter and bundled XDK
./gradlew :lang:intellij-plugin:runIde

# Run with mock adapter (regex-based, no native dependencies)
./gradlew :lang:intellij-plugin:runIde -Plsp.adapter=mock
```

1. Open a `.x` file in an XTC project
2. Test hover: Move cursor over a symbol
3. Test completion: Type and trigger completion (Ctrl+Space)
4. Test go-to-definition: Ctrl+Click on a symbol
5. If LSP isn't working:
   - Check **Help → Show Log in Finder** for LSP messages
   - Look for "XTC LSP Server started" in the log

#### Sandbox Console Output

When you run `./gradlew :lang:intellij-plugin:runIde`, the task logs detailed environment
information before launching the IDE. This is useful for debugging version mismatches,
stale sandbox state, or missing artifacts:

```
[runIde] ─── Version Matrix (gradle/libs.versions.toml) ───
[runIde]   IntelliJ IDEA: 2026.2.3 (sinceBuild=262)
[runIde]   LSP4IJ:        0.21.0
[runIde]   XTC plugin:    0.4.4-SNAPSHOT
[runIde] ─── Sandbox ───
[runIde]   Path:      .../lang/.intellijPlatform/sandbox/intellij-plugin/IU-2026.2.3
[runIde]   Status:    reused (existing sandbox with IDE caches/indices)
[runIde]   Plugins:   [intellij-plugin, lsp4ij]
[runIde]   IDE log:   .../lang/.intellijPlatform/sandbox/intellij-plugin/IU-2026.2.3/log_runIde/idea.log
[runIde]              tail -f .../lang/.intellijPlatform/sandbox/intellij-plugin/IU-2026.2.3/log_runIde/idea.log
[runIde] ─── mavenLocal XTC Artifacts ───
[runIde]   ~/.m2/repository/org/xtclang
[runIde]   xdk: 0.4.4-SNAPSHOT
[runIde]   xtc-plugin: 0.4.4-SNAPSHOT
[runIde] ─── Reset Commands ───
[runIde]   Nuke sandbox (keeps IDE download):  ./gradlew :lang:intellij-plugin:clean
[runIde]   Nuke cached IDE + metadata:         rm -rf lang/.intellijPlatform/localPlatformArtifacts
[runIde] LSP log:  ~/.xtc/logs/lsp/server-*/server.log (tailing to console)
```

Once the IDE is running and you open a `.x` file, LSP server logs are streamed
to the Gradle console in real time:

```
[lsp-server] 10:23:45 INFO  XtcLanguageServer - ========================================
[lsp-server] 10:23:45 INFO  XtcLanguageServer - Ecstasy Language Server v0.4.4
[lsp-server] 10:23:45 INFO  XtcLanguageServer - Backend: Tree-sitter
[lsp-server] 10:23:45 INFO  XtcLanguageServer - ========================================
[lsp-server] 10:23:46 INFO  XtcLanguageServer - textDocument/didOpen: file:///path/to/Hello.x
[lsp-server] 10:23:46 INFO  TreeSitterAdapter - parsed in 13.2ms, 0 errors, 42 symbols (query: 1.5ms)
```

All versions are pinned in `gradle/libs.versions.toml`. Changing a version there
automatically triggers a re-download or rebuild on the next `runIde`.

#### IDE Cache Layers

The IntelliJ Platform Gradle Plugin manages three separate cache layers:

| Layer | Location | Size | Survives `clean`? |
|-------|----------|------|-------------------|
| **Download** | `~/.gradle/caches/modules-2/files-2.1/idea/ideaIC/<version>/` | ~1 GB | Yes |
| **Extracted** | `~/.gradle/caches/<gradle-ver>/transforms/...` | ~3 GB | Yes |
| **Sandbox** | `lang/intellij-plugin/build/idea-sandbox/IC-<version>/` | ~200 MB | No |

The sandbox contains IDE config, plugin JARs, indices, and logs. It is rebuilt
from the cached download by `prepareSandbox` whenever it is missing.

#### Viewing Plugin Logs

**LSP server logs** are automatically tailed to the Gradle console (see above).
These appear with a `[lsp-server]` prefix whenever the LSP server is active.

**IDE logs** (`idea.log`) are NOT tailed automatically because they are very
noisy (indexing, VFS, GC, etc.). To view them in a separate terminal:

```bash
tail -f lang/.intellijPlatform/sandbox/intellij-plugin/IU-2026.2.3/log_runIde/idea.log
# Or filter to XTC-related entries:
tail -f lang/.intellijPlatform/sandbox/intellij-plugin/IU-2026.2.3/log_runIde/idea.log | grep -i "xtc\|lsp"
```

**LSP server file log** (always available, even outside `runIde`):

```bash
tail -f ~/.xtc/logs/lsp/server-*/server.log
```

#### Clearing Sandbox State

```bash
# Nuke sandbox only (keeps IDE download - fast recovery)
./gradlew :lang:intellij-plugin:clean

# Nuke everything including the downloaded IDE (re-downloads ~1.5 GB)
rm -rf lang/.intellijPlatform/ides/IU-2026.2.3
rm -rf lang/.intellijPlatform/localPlatformArtifacts
```

After nuking, running `runIde` again downloads (if needed) and rebuilds a fresh sandbox.

---

## Publishing to JetBrains Marketplace (Step-by-Step)

### Step 1: Create a JetBrains Account

1. Go to [JetBrains Marketplace](https://plugins.jetbrains.com/)
2. Click **Sign In** → Create an account or sign in with existing JetBrains account
3. Verify your email address

### Step 2: Create a Plugin Upload Token

1. Once signed in, click your profile icon → **My Tokens**
   - Direct link: https://plugins.jetbrains.com/author/me/tokens
2. Click **Generate Token**
3. Give it a name (e.g., "XTC Plugin Upload")
4. Select scope: **Plugin Upload**
5. Click **Generate**
6. **Copy the token immediately** - you won't see it again!

### Step 3: Set Up Your Environment

```bash
# Set the token as an environment variable
export JETBRAINS_TOKEN="perm:your-token-here"

# Optional: Add to your shell profile for persistence
echo 'export JETBRAINS_TOKEN="perm:your-token-here"' >> ~/.zshrc
```

### Step 4: Build the Plugin

```bash
cd init/intellij-plugin
gradle buildPlugin
```

This creates: `build/distributions/intellij-plugin-<version>.zip`

### Step 5: Publish (First Time - Manual Upload)

For the **first release**, you must upload manually:

1. Go to https://plugins.jetbrains.com/plugin/add
2. Fill in the form:
   - **Plugin name**: Ecstasy Language Support
   - **Category**: Languages
   - **License**: Apache 2.0
   - **Plugin ZIP**: Upload `build/distributions/intellij-plugin-<version>.zip`
3. Click **Upload**
4. Wait for JetBrains to review (usually 1-2 business days for first submission)

### Step 6: Subsequent Releases (Automated)

After the first approval, you can publish updates via Gradle:

```bash
# Publishing is disabled by default - must explicitly enable
gradle publishPlugin -PenablePublish=true
```

### Release Channels

The plugin automatically publishes to channels based on version string:

| Version Pattern | Channel | Users See |
|-----------------|---------|-----------|
| `0.4.4-SNAPSHOT` | alpha | Early adopters only |
| `0.4.4-alpha` | alpha | Early adopters only |
| `0.4.4-beta` | beta | Beta testers |
| `0.4.4` | default | All users |

To change the channel, edit `version.properties`:
```properties
xdk.intellij.release.channel=alpha
```

### Step 7: Install the Alpha Plugin

Users can install alpha versions:

1. Open IntelliJ IDEA
2. **Settings → Plugins → ⚙️ → Manage Plugin Repositories**
3. Add: `https://plugins.jetbrains.com/plugins/alpha/list`
4. Search for "Ecstasy Language Support"
5. Install

Or install from disk:
1. **Settings → Plugins → ⚙️ → Install Plugin from Disk...**
2. Select the `.zip` file from `build/distributions/`

---

## Optional: Plugin Signing

For production releases, JetBrains recommends signing plugins:

### Generate a Certificate

```bash
# Generate a private key
openssl genpkey -algorithm RSA -out private.pem -pkeyopt rsa_keygen_bits:4096

# Generate a certificate signing request
openssl req -new -key private.pem -out request.csr

# Self-sign (or get signed by JetBrains)
openssl x509 -req -days 365 -in request.csr -signkey private.pem -out certificate.crt

# Base64 encode for environment variables
base64 -i certificate.crt -o certificate.b64
base64 -i private.pem -o private.b64
```

### Set Signing Environment Variables

```bash
export JETBRAINS_CERTIFICATE_CHAIN=$(cat certificate.b64)
export JETBRAINS_PRIVATE_KEY=$(cat private.b64)
export JETBRAINS_PRIVATE_KEY_PASSWORD=""  # If key is unencrypted
```

### Sign and Publish

```bash
gradle signPlugin
gradle publishPlugin -PenablePublish=true
```

---

## CI/CD Publishing (GitHub Actions)

Add to `.github/workflows/publish-plugin.yml`:

```yaml
name: Publish IntelliJ Plugin

on:
  push:
    tags:
      - 'v*'

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'

      - name: Build Plugin
        working-directory: init/intellij-plugin
        run: ./gradlew buildPlugin

      - name: Publish Plugin
        working-directory: init/intellij-plugin
        env:
          JETBRAINS_TOKEN: ${{ secrets.JETBRAINS_TOKEN }}
        run: ./gradlew publishPlugin -PenablePublish=true
```

Add `JETBRAINS_TOKEN` to GitHub repository secrets.

## Project Structure

```
intellij-plugin/
├── build.gradle.kts              # Plugin build configuration
├── src/main/
│   ├── kotlin/org/xtclang/idea/
│   │   ├── PluginPaths.kt               # Plugin directory/JAR path resolution
│   │   ├── XtcIconProvider.kt            # Icon provider for .x files
│   │   ├── XtcIntelliJLanguage.kt        # IntelliJ Language singleton (for Code Style, etc.)
│   │   ├── XtcTextMateBundleProvider.kt  # TextMate grammar integration
│   │   ├── dap/
│   │   │   ├── XtcDebugAdapterFactory.kt # Debug of Ecstasy Application configurations through LSP4IJ's DAP client
│   │   │   └── LanguageServerDebugProcessHandler.kt # Carries a debug session over the language server connection
│   │   ├── lsp/
│   │   │   ├── XtcLanguageClient.kt            # Custom LSP client (forwards Code Style → server)
│   │   │   └── XtcLspServerSupportProvider.kt  # LSP server factory + connection provider
│   │   ├── project/
│   │   │   ├── XtcNewProjectWizard.kt    # New Project wizard entry
│   │   │   └── XtcNewProjectWizardStep.kt # Wizard step implementation
│   │   ├── run/
│   │   │   ├── EmbeddedExecution.kt           # Embedded build/run server access, run routing, reports
│   │   │   ├── EmbeddedRunProcessHandler.kt   # An embedded run as a process (console, Stop, Rerun)
│   │   │   ├── RunGutterIcons.kt              # Gutter Run icons on the Run lens lines
│   │   │   ├── XtcBuildConfigurable.kt        # Build, Execution, Deployment | Ecstasy page
│   │   │   ├── XtcBuildSettings.kt            # Project build mode (automatic, embedded, Gradle)
│   │   │   ├── XtcProjectTaskRunner.kt        # Embedded Build/Rebuild Project
│   │   │   ├── XtcRunConfiguration.kt         # Run configuration
│   │   │   ├── XtcRunConfigurationProducer.kt # Auto-detect runnable files
│   │   │   ├── XtcRunConfigurationType.kt     # Run config type registration
│   │   │   └── XtcRunModuleAction.kt          # The Run lens's xtc.runModule command
│   │   └── style/
│   │       ├── XtcCodeStyleSettings.kt              # XTC-specific code style options
│   │       └── XtcLanguageCodeStyleSettingsProvider.kt # Code Style settings page
│   └── resources/
│       ├── META-INF/plugin.xml   # Plugin manifest
│       └── icons/xtc.svg         # Plugin icon
└── README.md
```

## Architecture: How LSP Communication Works

```
┌──────────────────────────────────────────────────────────────────┐
│                    IntelliJ IDEA (JBR 25)                        │
│  ┌──────────────────┐    ┌────────────────────┐                  │
│  │ XTC Plugin       │    │ LSP4IJ Plugin      │                  │
│  │ (this plugin)    │───▶│ (Red Hat)          │                  │
│  │                  │    │                    │                  │
│  │ - Project wizard │    │ - XtcLanguageClient│                  │
│  │ - Run configs    │    │ - Protocol handler │                  │
│  │ - TextMate       │    │ - JSON-RPC         │                  │
│  │ - Code Style     │    │ - stderr capture   │                  │
│  └──────────────────┘    └─────────┬──────────┘                  │
│                                    │ stdio (JSON-RPC)            │
└────────────────────────────────────┼─────────────────────────────┘
                                     │
                          ┌──────────▼──────────┐
                          │ XTC LSP Server      │
                          │ (separate process)  │
                          │ JBR 25              │
                          │                     │
                          │ java -jar           │
                          │  xtc-lsp-server.jar │
                          │                     │
                          │ plugins/            │
                          │  intellij-plugin/   │
                          │   bin/ (off classpath)│
                          └─────────────────────┘
```

**Key points:**
- The LSP server runs as a **separate out-of-process** Java process using IntelliJ's JBR 25
- Out-of-process execution provides classloader isolation (avoids lsp4j conflicts with LSP4IJ) and crash/memory isolation
- The server JAR lives in `bin/` (not `lib/`) to avoid classloader conflicts with LSP4IJ
- The server command line is built using LSP4IJ's `JavaProcessCommandBuilder` which resolves the JBR java binary automatically
- Communication is via stdio (stdin/stdout) using JSON-RPC; logging goes to stderr
- `XtcLanguageClient` bridges IntelliJ Code Style settings to the LSP server via `workspace/configuration`
- LSP4IJ captures stderr and shows it in the Language Servers panel
- The `runIde` task also tails `~/.xtc/logs/lsp/server-*/server.log` to the Gradle console

## Troubleshooting

### "xtc: command not found"

The plugin requires the `xtc` CLI to be in your PATH. Either:
- Install the XDK and add it to PATH
- Set the `XDK_HOME` environment variable

### Project wizard not appearing

Ensure you have the Gradle plugin enabled in IntelliJ (bundled by default).

### Build errors after project creation

1. Ensure Gradle wrapper was created (check for `gradlew` in project)
2. Try **File → Invalidate Caches and Restart**
3. Re-import the Gradle project

## License

Apache License 2.0 - See [LICENSE](../../LICENSE) for details.

The native file-tree Move action now requests compiler proof before moving selected source files
or module containers. Shared X130 passes native batch Move and project Undo/Redo. Labels
use Ecstasy; implementation names retain Xtc. Upstream compatibility bridges carry searchable
`// TODO LSP4IJ:` comments with their removal conditions and their affected behavior in the
[client compatibility limits](../doc/manual-test-plan.md#client-compatibility-limits).

Lazy action selection now uses a registered client command to resolve and apply the edit in a
version-checked undo command; selected native X105/X122/X127 acceptance passes. X131 adds protocol assertions
for code-lens/link/inlay/symbol resolution through the installed client connection.

Shared X132 covers for multiple-range formatting and negotiated save hooks; selected native
acceptance and the backend/packaged transport regressions pass.
The server defaults to full synchronization and no save-time edits; custom LSP hosts can set
`initializationOptions.xtcDocumentSync` with independent `incremental` and `formatOnSave` booleans.
No additional compiler process or compilation is needed for save formatting.

### Quick server log access

Press **Ctrl+Alt+X, then L** (**Control+Option+X, then L** on macOS) to show/hide the server log.
The view retains its history while hidden. Change the shortcut in the editor's native keymap.
See the [manual playbook](../doc/manual-test-plan.md#server-log-shortcut-acceptance) for acceptance
steps and the distinction between server logs and protocol tracing.

Language-service preferences are under **Settings → Languages & Frameworks → Ecstasy Language
Service**; **Ecstasy Language Service Defaults** supplies application defaults. Project overrides
share LSP4IJ storage and preserve compiler graph/Undo ownership. Changing the language adapter or
Full/Incremental text transport restarts the service and restores unsaved buffers. Inlay changes and Code Style settings
apply live. The read-only effective view includes PID, runtime, capabilities, bundled read-only XDK
libraries and compiler queue names/count. Server save edits are unavailable in LSP4IJ 0.21.0; use
native **Actions on Save → Reformat code**. Compiler mode wraps expressions/lists at safe token
boundaries using the Code Style right margin; literal splitting and comment reflow are not supported.

X142 verifies constant-initializer navigation and rename/Undo. X143 compares partial
workspace-symbol batches with the ordinary response. These checks complement the protocol
and lifecycle cases; see the [manual playbook](../doc/manual-test-plan.md).

X145–X147 exercise cancellation, refresh and report ownership. X259 checks displayed consumer inlays while applying the real Language Service settings page,
changing a dependency and restarting; the consumer text/version must remain unchanged.
This complements the existing two-project lifetime check. See the
[acceptance reports](../doc/manual-test-plan.md#acceptance-reports).

### Ordered libraries and attached sources

**Settings → Ecstasy Compiler → Libraries and sources** edits ordered external binary paths
and per-module source-directory attachments. Use **Inherit binary libraries from Gradle** to keep
build-model ownership, or clear the list to remove external libraries. The bundled XDK is always
present and read-only. Apply persists the draft; Reset/Cancel discards pending changes. **Reload
applied libraries** rereads saved paths without a restart. The source-module tab also offers
**Order resource directories…** with native path selection and automatic-resource inheritance.

Attachments supply navigation-only snapshots; they do not add compiler source modules. IntelliJ
opens protected fallback files until LSP4IJ supports our virtual library editor. Use matching
sources; available debug text is checked but is not a complete binary/source equivalence proof.
Project settings survive server restart; invalid replacements retain accepted compiler inputs.
Shared X266–X268 and their [acceptance reports](../doc/manual-test-plan.md#acceptance-reports)
record automated coverage and the remaining acceptance boundaries.

### Machine-local JVM tuning and log support

Open **Settings → Ecstasy Server Runtime and Logs**. Enter one supported JVM option per line
(e.g. `-Xmx2G`). Apply saves locally, outside project files and Settings Sync; the explicit Restart
button restarts running Ecstasy servers in this IDE using the saved settings.

Retention defaults: 7 days, 10 MB/file, 50 MB archived per stream and five stopped-server sessions.
Logs live under `~/.xtc/logs/lsp/server-<PID>-<start-time>/`; active files are additional to archive
caps, active processes are protected and stopped sessions are pruned on the next server start.
Legacy shared logs and the IDE's own protocol console are outside this policy.

**Tools → Export Ecstasy Server Logs** saves a ZIP of recent log tails and current status from
the connected server (at most eight 512 KiB tails, 4 MiB log input). It may contain local paths and
logged diagnostics; it does not collect source files. When stopped, export uses only this project’s
last recorded launch, including up to 64 KiB of launcher output. Its manifest identifies offline
mode and truncation and lists included files; another project’s logs are never substituted. Shared
X269–X272 cover settings, restart, live/offline export and recovery from an intentional failed JVM
launch. Full editor exit/reopen has a separate two-process persistence test. Native OS save-dialog
layout and rollover stress remain manual checks. See the [acceptance report format](../doc/manual-test-plan.md#acceptance-reports) for
how these checks are recorded.

The **Ecstasy Compiler** page separates **Source modules**, **Libraries and sources** and
**Build import**. Import buttons stay with their help; the detailed paths/report are expandable.
Build import uses native Kotlin UI DSL groups, ordinary-width buttons, smaller help text and
collapsed details, and a bordered vertical page list. Build actions are stacked at their normal
width; the standalone window fits the selected page. Switching pages preserves unapplied edits.
The Language Service page distinguishes saved machine runtime settings from the running server;
stale replies cannot replace a newer report.

### Compiler documentation and reference counts

The compiler adapter offers **Generate documentation comment** on written declaration headers.
It preserves indentation and line endings, derives method parameter/return entries from the
compiler signature, and leaves existing comments intact. The edit participates in normal Undo/Redo.

Reference CodeLens shows source type, method and property usage counts across the complete configured
source graph, including unopened consumers. Counts exclude declarations and are omitted while the
graph cannot be resolved. Clicking a count opens the editor's native references view. External
consumers outside the configured graph are not counted. Run lenses remain independent.

Use **Ecstasy Language Service → Show Ecstasy reference counts** to toggle counts live. The
project can inherit the application preference. IntelliJ Code Vision settings still control rendering.
