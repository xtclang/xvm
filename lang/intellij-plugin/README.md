# XTC IntelliJ Plugin

IntelliJ IDEA plugin for XTC (Ecstasy) language support.

## Features

- **New Project Wizard** - Create XTC projects directly from IntelliJ (File → New → Project → XTC)
- **Run Configurations** - Run XTC applications via Gradle or `xtc run`
- **Syntax Highlighting** - Full syntax highlighting for `.x` files (via TextMate grammar)
- **Language Features via LSP** (currently mocked - real compiler integration coming):
  - Hover information
  - Code completion
  - Go to definition
  - Find references
  - Document outline
  - Diagnostics

> **Note**: The LSP server currently uses a mock adapter with basic regex-based parsing.
> Full semantic features (accurate go-to-definition, type-aware completion, etc.) will
> be available once the real XTC compiler is integrated.

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

- IntelliJ IDEA 2025.1 or later
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

> **Note**: The LSP currently uses mock responses based on regex pattern matching.
> Features like go-to-definition will show plausible results but are not semantically accurate.

1. Open a `.x` file in an XTC project
2. Test hover: Move cursor over a symbol - you should see type information (mocked)
3. Test completion: Type and trigger completion (Ctrl+Space) - shows keyword/symbol suggestions
4. Test go-to-definition: Ctrl+Click on a symbol - navigates based on pattern matching
5. If LSP isn't working:
   - Check **Help → Show Log in Finder** for `XtcStreamConnectionProvider` messages
   - Look for "XTC LSP server started with PID:" in the log
   - Verify `xtc-lsp.jar` exists in the plugin's lib folder

#### Viewing Plugin Logs

The sandbox IDE writes logs to:
```
lang/intellij-plugin/build/idea-sandbox/IC-2025.1/log/idea.log
```

To view logs in real-time:
```bash
tail -f lang/intellij-plugin/build/idea-sandbox/IC-2025.1/log/idea.log | grep -i "xtc\|lsp"
```

#### Clearing Sandbox State

If you need a fresh start, clean the sandbox:
```bash
./gradlew :lang:intellij-plugin:clean
# Or manually:
rm -rf lang/intellij-plugin/build/idea-sandbox/
```

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
├── settings.gradle.kts           # Gradle settings
├── src/main/
│   ├── kotlin/org/xtclang/idea/
│   │   ├── XtcFileType.kt        # File type + language + icons
│   │   ├── lsp/
│   │   │   └── XtcLspServerSupportProvider.kt  # LSP server integration
│   │   ├── project/
│   │   │   ├── XtcProjectGenerator.kt   # New Project wizard
│   │   │   └── XtcProjectSettings.kt    # Project settings
│   │   └── run/
│   │       ├── XtcRunConfiguration.kt   # Run configuration
│   │       └── XtcRunConfigurationType.kt
│   └── resources/
│       ├── META-INF/plugin.xml   # Plugin manifest
│       └── icons/xtc.svg         # Plugin icon
└── README.md
```

## Architecture: How LSP Communication Works

```
┌─────────────────────────────────────────────────────────────────────┐
│                        IntelliJ IDEA                                 │
│  ┌──────────────────┐    ┌────────────────────┐                     │
│  │ XTC Plugin       │    │ LSP4IJ Plugin      │                     │
│  │ (this plugin)    │───▶│ (dependency)       │                     │
│  │                  │    │                    │                     │
│  │ - Project wizard │    │ - LSP client       │                     │
│  │ - Run configs    │    │ - Protocol handler │                     │
│  │ - File type      │    │ - JSON-RPC         │                     │
│  └──────────────────┘    └─────────┬──────────┘                     │
│                                    │ stdio                          │
└────────────────────────────────────┼────────────────────────────────┘
                                     │
                          ┌──────────▼──────────┐
                          │ XTC LSP Server      │
                          │ (separate process)  │
                          │                     │
                          │ java -jar           │
                          │   xtc-lsp-all.jar   │
                          │                     │
                          │ Located at:         │
                          │ $XDK_HOME/lib/      │
                          └─────────────────────┘
```

**Key points:**
- The IntelliJ plugin does NOT contain the LSP server
- The LSP server runs as a separate Java process
- Communication is via stdio (stdin/stdout) using JSON-RPC
- LSP4IJ handles the protocol details
- The plugin just tells LSP4IJ how to start the server

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
