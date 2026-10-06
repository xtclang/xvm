# Ecstasy IntelliJ Plugin

L50 adds compiler-proven property/accessor-family rename, explicit alias rename, simple member-file
type moves and public-type auto-import repairs. See the
[scope and proof boundaries](../../docs/errs-integration-plan.md#broader-refactoring-checkpoint-l50).

Live workspace/source navigation (L47–L49): unsaved headers and workspace-folder changes refresh the
compiler graph; detached graph queries are reused, and healthy modules remain navigable beside a
broken neighbor. Matching bundled XDK declarations open read-only source files with artifact-backed declaration monikers. LSP4IJ virtual content/refresh support remains UP25. Complete reference
and refactoring proofs still fail closed. This adds no AST state or compiler listener changes.
See [scope, ownership and validation](../../docs/errs-integration-plan.md#live-workspace-and-source-navigation-checkpoint-l47l49).


IntelliJ IDEA plugin for Ecstasy language support.

## Features

- **New Project Wizard** - Create XTC projects directly from IntelliJ (File → New → Project → XTC)
- **Run Configurations** - Run XTC applications via Gradle or `xtc run`
- **Syntax Highlighting** - Full syntax highlighting for `.x` files (via TextMate grammar)
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

1. Open the **Run/Debug Configurations** dialog
2. Click **+** → **XTC Application**
3. Configure:
   - **Module name** - The XTC module to run
   - **Program arguments** - Arguments to pass to your application
   - **Use Gradle** - Recommended; uses `./gradlew runXtc`
4. Click **Run** or **Debug**

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

| Task | Output | Use Case |
|------|--------|----------|
| `buildPlugin` | `build/distributions/*.zip` | Install in any IntelliJ instance |
| `runIde` | Launches sandbox IDE | Quick testing during development |
| `verifyPlugin` | Verification report | Check IDE compatibility |

### Compiler playbook in IntelliJ

```bash
./gradlew :lang:intellij-plugin:testCompilerPlaybook \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler
```

This opt-in task uses JetBrains Starter/Driver to launch the packaged plugin and LSP4IJ 0.21.0
in IntelliJ IDEA 2026.2.3. Starter explicitly disables Ultimate features and enables license
checks, suppresses the paid module and prevents automatic trials. The suite asserts after every
case that Ultimate remains unloaded. The unified IDEA download
uses JetBrains' `IdeaUltimate` artifact name, but the tested features require only the free
Community feature set. No personal settings or license are copied into the test profile.
See [JetBrains' unified distribution explanation](https://www.jetbrains.com/help/idea/intellij-idea-single-distribution.html).

The suite reads all 264 scenario definitions from [shared data](../test-fixtures/compiler-playbook/scenarios.json)
and source fixtures from the [manual playbook](../doc/manual-test-plan.md#xdkadapter-playbook).
The earlier protocol selection, `run-15914309414363009017`, passes START and X136/X137/X140/X141
with zero IDE errors: settings, restart, UTF-16 hover/rename ranges and runtime server tracing.
The [coverage map](../doc/manual-test-plan.md#protocol-and-lifecycle-coverage-map)
separates automated editor cases, controlled backend races and pending manual checks. This is not
a full-catalog checkpoint. The preceding settings selection passes X118/X132/X135–X139.

The earlier watcher selection `run-17174738471798629344` passes START and 18 of 19 cases with zero
IDE errors. Automatic external watch creation/repair and settings replacement pass without fixture
VFS refresh; the selected lazy-action bridge passes import/member generation and Undo/Redo. X130's
native batch Move/Undo/Redo now passes in `run-1746762976235942700`, including START and zero
IDE errors. The manifest uses the platform `refactoring.moveHandler` extension point; file-only
Move uses project-level Undo. This is not a full 140-case checkpoint.
See the [current acceptance record](../../docs/errs-integration-plan.md#watcher-move-and-log-view-acceptance-follow-up-2026-09-30).
The following receipts describe the preceding catalog.
Startup and the preceding 113 scenarios have a complete passing checkpoint recorded below. The
[current demo record](../../docs/errs-integration-plan.md#native-intellij-demo-continuation-2026-09-29)
tracks the 128-case selection and its resumed/focused runs separately: all 128 cases have passing
receipts. The final X60/X77/X78/X105/X122/X123 recheck passes in `run-5742519770640114134` with
zero IDE errors after correcting repeated diagnostic pulls, source/resource invalidation and
shared completion expectations. VS Code also passes all 128 cases in one run, `run-b59XBq`.
The IntelliJ coverage is not one uninterrupted full-suite checkpoint. Coverage includes native
Structure/folding/selection, diagnostics and Problems navigation/clearing, definitions/references/
highlights, dependency overlays, completion lists and exact accepted edits, method/constructor
Parameter Info, argument-value fitting and declaration recovery through X108. Native checks include the X34 multi-target chooser, X99/X100
live discovery and partial hierarchy, X101 library edit guards, and X102–X105 refactoring/quick fixes.
Native validation uses selected cases; see the execution receipts below. The disposable IDE
profile disables sole-candidate auto-insertion and automatic completion popups so tests can inspect
every requested completion list first. Autosave is disabled to preserve unsaved-overlay checks;
shipped plugin defaults are unchanged.

`CompilerPlaybookTest.startupEditing` separately exercises five editor-edit phases during cold
open/restart, including immediate repair, shortening and close/reopen. It does not wait for server
readiness before editing. `run-1799467324333192176` passes with no IDE failures; its
`startup-editing.json` records versions, PIDs and diagnostic/fold evidence. X103's reverse file
rename exposed a real transport read-lock/VFS write-lock deadlock; the hook now uses immutable
document text without an IDE read action. Both rename directions pass in `run-8812860837009261601`.
Select only startup with `--tests '*CompilerPlaybookTest.startupEditing'`, or only feature cases
with `--tests '*CompilerPlaybookTest.compilerPlaybook'` plus `-PintellijPlaybookCases=X103`.

Each report's `server-trace/` directory contains child-server JSON lines with queue size and
ordered jobs, javatools durations and LSP request-to-reply timings. See the
[trace guide](../lsp-server/README.md#compiler-queue-and-api-timing). The normal startup information
balloon uses an eight-second smart fadeout timer, paused during interaction; notification history
is retained.

Editor diagnostics are copied inside the disposable IDE by a small test-only probe. This avoids
the driver's object descriptions evaluating cancelled lazy quick fixes when reading annotations.
The probe reads installed severity/message/offset values; it does not issue substitute LSP queries.
It is built from integration-test classes and is absent from the shipping plugin.

Parameter Info checks inspect the native request result, every visible parameter row and the
exact bold argument in enabled overloads. Inactive overloads are intentionally dimmed according
to `activeSignature`; with no selected overload, every row remains enabled.
Invalid calls must clear an earlier hint. X20 now requires the full signature label without an
invented bold argument; an Ecstasy-specific Parameter Info handler preserves this label when
LSP4IJ would display `<no parameters>`. X81/X82 inspect Property-kind metadata from the native
completion request. X20/X81/X82 pass natively. Problems-row clicking and visual
layout remain manual.

Every report lists all 264 scenario IDs and distinguishes failed/unselected cases from passing
ones. The [L60 checklist](../../docs/errs-integration-plan.md#intellij-parity-backlog-l60) keeps each
new case open until it has a pass receipt and records client limitations found during validation.
The new protocol checks use the installed language-client connection. Synthetic nonexistent-file
and deleted-file diagnostics use its verbose trace: LSP4IJ cannot display these without a virtual
file. X57 reproduced stale edit application in LSP4IJ 0.21.0 and now passes through Ecstasy's
guarded native Rename handler. Its immutable request snapshot is checked inside the write command
before applying edits; typing, file lifecycle changes and server restart retire the response.
This does not guard other LSP4IJ workspace-edit entry points or null-version closed-file races.
The missing-case subset has 50/50 passing receipts; X29 now passes with current signature metadata.
The resumed 61-case batch passed every scenario assertion but failed the IDE-error gate on
X41's unguarded PSI read. That harness call now uses a read action; X41/X108 pass together with
no IDE errors. A subsequent full checkpoint was interrupted by desktop focus loss at X108.
The complete 113-case checkpoint now passes in `run-6034631232732848040`, with zero IDE errors
and zero JUnit failures/errors/skips. Gradle took 6 minutes 40 seconds. Earlier runs exposed the
session timeout, focus handling and X30's missing document-readiness check after restart; those
corrections are included. The harness allows 30 minutes overall, with bounded operation waits.
See the [L60 execution record](../../docs/errs-integration-plan.md#intellij-parity-backlog-l60).

X33–X35 and X99–X108 have passing native receipts
across the checkpoint and focused X105 rerun. The receipts and the failures fixed during validation
are recorded in the [active validation record](../../docs/errs-integration-plan.md#header-slots-and-native-editor-parity-c28l53l54).
The full checkpoint above includes these assertions. Coverage metadata continues to record
implemented assertions independently of runtime receipts. Separate startup and focus-recovery
tests pass; startup acceptance also checks that the untouched information balloon disappears.

The isolated IDE's title and status bar show completed/selected cases, the remaining count and
the current case. Focus restoration is indicated there too. The harness restores focus through
`AppIcon`, without Driver's title-bar mouse click. If a popup was interrupted, it reopens only
the unapplied inspection and only while the document's modification stamp is unchanged. Accepted
completion edits, quick fixes, renames and file moves are not replayed. Editing a fixture during
recovery fails explicitly. Automatic activation can redirect your typing into the test IDE;
an isolated desktop is preferable when working concurrently. Other native controls may still use
the mouse. Failed checks close the disposable IDE during cleanup.

Readiness checks poll every 100 ms instead of the Driver default of one second, with the same
failure deadlines. Test-editor scrolling disables animation for the operation and avoids the
Driver helper's fixed 200 ms sleep. Native UI assertions are unchanged. The dedicated
`--tests '*CompilerPlaybookTest.focusRecovery'` check creates an unowned test window to interrupt
completion and Parameter Info, and verifies that completed completion/rename edits cannot replay.

The later L55/L61/L62 checkpoint passes START and X4/X102/X103/X104 with zero IDE errors.
X4 adds an installed-client declaration request check beside native navigation. X103 verifies
forward/reverse native rename of a member file and its nested companion directory. The
[batch receipts](../../docs/errs-integration-plan.md#teaching-workspace-declarations-and-resource-moves-l55l61l62)
distinguish these selected assertions from the earlier full playbook run.

Live case results are appended to `build/reports/compiler-playbook/run-*/progress.jsonl`.
The final report is `results.json` in the same directory; `ide-paths.txt` points to
the isolated IDE's profile/log directory. Starter caches its IDE download below the same report
root's `out/ide-tests/cache`. Ordinary unit tests do not launch an IDE. Starter/Driver follows the
selected IDE build; Kodein, patched coroutines and the standalone launcher's Kotlin standard
library are integration-test dependencies and are not bundled in the plugin.

### Compiler source-module configuration

Compiler mode bundles the full matching XDK library set as read-only dependencies and discovers
source modules/import edges under workspace folders at startup and on watched-file changes.
The configuration below overrides that discovered graph. Set `sourceModules` to `null` to
restore discovery; `[]` disables it. Unsaved import edges, close and dynamic folder changes now refresh discovered graphs.

With a compiler build, open **Settings → Languages & Frameworks → Language Servers**, select
**XTC Language Server**, and edit its **Configuration** JSON:

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
3. Click **+** → **XTC Application**
4. Configure:
   - **Module name**: The XTC module containing your app
   - **Program arguments**: Any arguments for your app
   - **Use Gradle**: Recommended for integrated builds
5. Click **Apply**, then **Run**
6. Verify:
   - The run configuration appears in the toolbar
   - Running invokes Gradle's `runXtc` task
   - Output appears in the Run tool window

#### Testing LSP Features (Language Server)

The LSP server supports multiple adapters. See [LSP Server README](../lsp-server/README.md) for details.

```bash
# Run with default adapter (tree-sitter - AST-based)
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
[runIde]   IDE log:   .../lang/.intellijPlatform/sandbox/intellij-plugin/IU-2026.2.3/log/idea.log
[runIde]              tail -f .../lang/.intellijPlatform/sandbox/intellij-plugin/IU-2026.2.3/log/idea.log
[runIde] ─── mavenLocal XTC Artifacts ───
[runIde]   ~/.m2/repository/org/xtclang
[runIde]   xdk: 0.4.4-SNAPSHOT
[runIde]   xtc-plugin: 0.4.4-SNAPSHOT
[runIde] ─── Reset Commands ───
[runIde]   Nuke sandbox (keeps IDE download):  ./gradlew :lang:intellij-plugin:clean
[runIde]   Nuke cached IDE + metadata:         rm -rf lang/.intellijPlatform/localPlatformArtifacts
[runIde] LSP log:  ~/.xtc/logs/lsp-server.log (tailing to console)
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
tail -f lang/.intellijPlatform/sandbox/intellij-plugin/IU-2026.2.3/log/idea.log
# Or filter to XTC-related entries:
tail -f lang/.intellijPlatform/sandbox/intellij-plugin/IU-2026.2.3/log/idea.log | grep -i "xtc\|lsp"
```

**LSP server file log** (always available, even outside `runIde`):

```bash
tail -f ~/.xtc/logs/lsp-server.log
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

This creates: `build/distributions/xtc-intellij-plugin-<version>.zip`

### Step 5: Publish (First Time - Manual Upload)

For the **first release**, you must upload manually:

1. Go to https://plugins.jetbrains.com/plugin/add
2. Fill in the form:
   - **Plugin name**: Ecstasy Language Support
   - **Category**: Languages
   - **License**: Apache 2.0
   - **Plugin ZIP**: Upload `build/distributions/xtc-intellij-plugin-<version>.zip`
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
│   │   │   └── XtcDebugAdapterFactory.kt # DAP server integration
│   │   ├── lsp/
│   │   │   ├── XtcLanguageClient.kt            # Custom LSP client (forwards Code Style → server)
│   │   │   └── XtcLspServerSupportProvider.kt  # LSP server factory + connection provider
│   │   ├── project/
│   │   │   ├── XtcNewProjectWizard.kt    # New Project wizard entry
│   │   │   └── XtcNewProjectWizardStep.kt # Wizard step implementation
│   │   ├── run/
│   │   │   ├── XtcRunConfiguration.kt         # Run configuration
│   │   │   ├── XtcRunConfigurationProducer.kt # Auto-detect runnable files
│   │   │   └── XtcRunConfigurationType.kt     # Run config type registration
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
- The `runIde` task also tails `~/.xtc/logs/lsp-server.log` to the Gradle console

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
`// TODO LSP4IJ:` comments with their removal conditions and stable UP identifiers in the
[upstream issue register](../../docs/errs-upstream-issues.md).

Lazy action selection now uses a registered client command to resolve and apply the edit in a
version-checked undo command; selected native X105/X122/X127 acceptance passes. X131 adds protocol assertions
for code-lens/link/inlay/symbol resolution through the installed client connection.

L72 adds shared X132 for multiple-range formatting and negotiated save hooks; selected native
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
share LSP4IJ storage and preserve compiler graph/Undo ownership. Changing Full/Incremental text
transport restarts the service and restores unsaved buffers. Inlay changes and Code Style settings
apply live. The read-only effective view includes PID, runtime, capabilities, bundled read-only XDK
libraries and compiler queue names/count. Server save edits are unavailable in LSP4IJ 0.21.0; use
native **Actions on Save → Reformat code**. Compiler mode wraps expressions/lists at safe token
boundaries using the Code Style right margin; literal splitting and comment reflow are not supported.

The follow-up catalog now contains 148 scenarios. New X142 verifies constant-initializer semantic
navigation and rename/undo; X143 compares partial workspace-symbol batches with the ordinary
response. X124/X131/X134/X142/X143 pass in both editors across selected runs and a focused
IntelliJ X142 correction; this does not establish a full 148-case checkpoint. See the
[follow-up receipt](../../docs/errs-integration-plan.md#follow-up-validation-receipt-2026-09-30) for
run IDs, failed attempts and the remaining L80/L81 acceptance limits.

L81 selected acceptance passes X145/X146/X147/X259 plus startup with zero IDE errors. X259
checks displayed consumer inlays while applying the real Language Service settings page,
changing a dependency and restarting; the consumer text/version must remain unchanged.
This complements the existing two-project lifetime check. See the
[L81 receipt](../../docs/errs-integration-plan.md#l81-native-acceptance-closure-2026-10-05).


### Ordered libraries and attached sources

**Settings → Ecstasy Compiler → Libraries and sources** edits ordered external binary paths
and per-module source-directory attachments. Use **Inherit binary libraries from Gradle** to keep
build-model ownership, or clear the list to remove external libraries. The bundled XDK is always
present and read-only. Apply persists the draft; Reset/Cancel discards pending changes. **Reload
applied libraries** rereads saved paths without a restart. The source-module tab also offers
**Order resource directories…** with native path selection and automatic-resource inheritance.

Attachments supply navigation-only snapshots; they do not add compiler source modules. IntelliJ
opens protected fallback files until LSP4IJ supports our virtual library editor (UP25). Use matching
sources; available debug text is checked but is not a complete binary/source equivalence proof.
Project settings survive server restart; invalid replacements retain accepted compiler inputs.
Shared X266–X268 and their [batch receipt](../../docs/errs-integration-plan.md#ordered-libraries-and-attached-sources-batch-ui3ui4ui6-2026-10-06)
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
logged diagnostics; it does not collect source files. Export requires a running server. Shared
X269/X270 check restart boundaries, invalid settings, retention/status and installed export.
Native OS save-dialog layout, fresh-IDE persistence and rollover stress remain manual checks.
See the [contract and receipt](../../docs/errs-integration-plan.md#machine-local-jvm-settings-and-log-support-ui5ui6-2026-10-06).
