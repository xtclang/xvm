# XTC IntelliJ Plugin

IntelliJ IDEA plugin for XTC (Ecstasy) language support.

## Features

- **New Project Wizard** - Create XTC projects directly from IntelliJ (File → New → Project → XTC)
- **Run Configurations** - Run XTC applications via Gradle or `xtc run`
- **File Type Support** - Syntax recognition for `.x` files

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
cd init/intellij-plugin
./gradlew buildPlugin
```

The plugin ZIP will be in `build/distributions/`.

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
   - **Use Gradle** - Recommended; uses `./gradlew xtcRun`
4. Click **Run** or **Debug**

## Development

### Building the Plugin

```bash
# Build the plugin
./gradlew buildPlugin

# Run in a sandbox IDE for testing
./gradlew runIde

# Run plugin verification
./gradlew verifyPlugin
```

### Publishing to JetBrains Marketplace

Publishing is **disabled by default** to prevent accidental releases.

#### Setup

1. **Get a Marketplace Token**:
   - Go to [JetBrains Marketplace](https://plugins.jetbrains.com/)
   - Log in with your JetBrains account
   - Go to your profile → **My Tokens**
   - Generate a new token with **Plugin Upload** scope

2. **Set Environment Variables**:
   ```bash
   export JETBRAINS_TOKEN="your-token-here"
   ```

3. **Publish** (must explicitly enable):
   ```bash
   ./gradlew publishPlugin -PenablePublish=true
   ```

#### Release Channels

The channel is automatically selected based on version:
- `0.1.0-alpha` → publishes to **alpha** channel
- `0.1.0-beta` → publishes to **beta** channel
- `0.1.0` → publishes to **default** (stable) channel

#### Optional: Signed Releases

For production releases, you can sign the plugin:

```bash
export JETBRAINS_CERTIFICATE_CHAIN="base64-encoded-chain"
export JETBRAINS_PRIVATE_KEY="base64-encoded-key"
export JETBRAINS_PRIVATE_KEY_PASSWORD="your-password"

./gradlew signPlugin
./gradlew publishPlugin -PenablePublish=true
```

See [JetBrains Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html) for details.

#### CI/CD Publishing

For GitHub Actions or other CI systems:

```yaml
- name: Publish Plugin
  env:
    JETBRAINS_TOKEN: ${{ secrets.JETBRAINS_TOKEN }}
  run: ./gradlew publishPlugin -PenablePublish=true
```

## Project Structure

```
intellij-plugin/
├── build.gradle.kts              # Plugin build configuration
├── settings.gradle.kts           # Gradle settings
├── src/main/
│   ├── kotlin/org/xtclang/idea/
│   │   ├── XtcFileType.kt        # File type + language + icons
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
