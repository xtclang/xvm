# XTC IntelliJ Plugin

IntelliJ IDEA plugin for XTC (Ecstasy) language support.

## Features

- **New Project Wizard** - Create XTC projects directly from IntelliJ (File → New → Project → XTC)
- **Run Configurations** - Run XTC applications via Gradle or `xtc run`
- **Syntax Highlighting** - Full syntax highlighting for `.x` files (via TextMate grammar)
- **Language Features via LSP** - hover, completion, go-to-definition, find references, outline
  (see [LSP Server README](../lsp-server/README.md) for adapter details)

## Installation

### From JetBrains Marketplace (Recommended)

1. Open IntelliJ IDEA
2. Go to **Settings/Preferences → Plugins → Marketplace**
3. Search for "XTC Language Support"
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

- IntelliJ IDEA 2025.3 or later
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

### Installing the Built Plugin

After running `buildPlugin`, you can install the ZIP in any IntelliJ IDEA 2025.1+ instance:

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
[runIde]   IntelliJ IDEA: 2025.3.2 (sinceBuild=253)
[runIde]   LSP4IJ:        0.19.1
[runIde]   XTC plugin:    0.4.4-SNAPSHOT
[runIde] ─── Sandbox ───
[runIde]   Path:      .../build/idea-sandbox/IC-2025.3.2
[runIde]   Status:    reused (existing sandbox with IDE caches/indices)
[runIde]   Plugins:   [intellij-plugin, lsp4ij]
[runIde]   IDE log:   .../build/idea-sandbox/IC-2025.3.2/log/idea.log
[runIde]              tail -f .../build/idea-sandbox/IC-2025.3.2/log/idea.log
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
[lsp-server] 10:23:45 INFO  XtcLanguageServer - XTC Language Server v0.4.4
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
tail -f lang/intellij-plugin/build/idea-sandbox/IC-2025.3.2/log/idea.log
# Or filter to XTC-related entries:
tail -f lang/intellij-plugin/build/idea-sandbox/IC-2025.3.2/log/idea.log | grep -i "xtc\|lsp"
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
rm -rf ~/.gradle/caches/modules-2/files-2.1/idea/ideaIC/2025.3.2
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
   - **Plugin name**: XTC Language Support
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
4. Search for "XTC Language Support"
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

      - name: Set up JDK 24
        uses: actions/setup-java@v4
        with:
          java-version: '24'
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
│   │   ├── XtcTextMateBundleProvider.kt  # TextMate grammar integration
│   │   ├── dap/
│   │   │   └── XtcDebugAdapterFactory.kt # DAP server integration
│   │   ├── lsp/
│   │   │   ├── XtcLspServerSupportProvider.kt  # LSP server factory + connection provider
│   │   │   └── jre/
│   │   │       └── JreProvisioner.kt     # Foojay JRE download/caching
│   │   ├── project/
│   │   │   ├── XtcNewProjectWizard.kt    # New Project wizard entry
│   │   │   └── XtcNewProjectWizardStep.kt # Wizard step implementation
│   │   └── run/
│   │       ├── XtcRunConfiguration.kt         # Run configuration
│   │       ├── XtcRunConfigurationProducer.kt # Auto-detect runnable files
│   │       └── XtcRunConfigurationType.kt     # Run config type registration
│   └── resources/
│       ├── META-INF/plugin.xml   # Plugin manifest
│       └── icons/xtc.svg         # Plugin icon
└── README.md
```

## Architecture: How LSP Communication Works

```
┌──────────────────────────────────────────────────────────────────┐
│                    IntelliJ IDEA (JBR 21)                        │
│  ┌──────────────────┐    ┌────────────────────┐                  │
│  │ XTC Plugin       │    │ LSP4IJ Plugin      │                  │
│  │ (this plugin)    │───▶│ (Red Hat)          │                  │
│  │                  │    │                    │                  │
│  │ - Project wizard │    │ - LSP client       │                  │
│  │ - Run configs    │    │ - Protocol handler │                  │
│  │ - TextMate       │    │ - JSON-RPC         │                  │
│  │ - JRE provision  │    │ - stderr capture   │                  │
│  └──────────────────┘    └─────────┬──────────┘                  │
│                                    │ stdio (JSON-RPC)            │
└────────────────────────────────────┼─────────────────────────────┘
                                     │
                          ┌──────────▼──────────┐
                          │ XTC LSP Server      │
                          │ (separate process)  │
                          │ Java 25 (Temurin)   │
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
- The LSP server runs as a **separate out-of-process** Java process
- It requires Java 25+ (for tree-sitter's FFM API), while IntelliJ uses JBR 21
- The plugin provisions a JRE automatically via Foojay Disco API (cached in `~/.xtc/jre/`)
- The server JAR lives in `bin/` (not `lib/`) to avoid classloader conflicts with LSP4IJ
- Communication is via stdio (stdin/stdout) using JSON-RPC; logging goes to stderr
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
