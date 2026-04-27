# Ecstasy Language Development Container

This devcontainer provides a complete development environment for the Ecstasy programming language using the official XVM Docker image.

## Quick Start

1. Install the [Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers) for VS Code
2. Open this folder in VS Code
3. When prompted, click "Reopen in Container" or use Command Palette: "Dev Containers: Reopen in Container"
4. The container will automatically pull `ghcr.io/xtclang/xvm:latest` and set up the environment

## What's Included

- **XDK**: Ecstasy distribution with the `xtc` unified CLI (and the lower-level `xcc` compiler / `xec` runner launchers)
- **Java Runtime**: Optimized JRE for running Ecstasy programs
- **Git**: Version control tools
- **GitHub CLI**: For interacting with GitHub repositories

## Available Tools

- `xtc` - Ecstasy unified CLI (`xtc init`, `xtc build`, `xtc run`, `xtc test`, `xtc disass`); see [doc/xtc-cli.md](../doc/xtc-cli.md) for the full reference
- `xcc` - Ecstasy compiler (equivalent to `xtc build`)
- `xec` - Ecstasy module runner (equivalent to `xtc run`)
- `git` - Version control
- `gh`  - GitHub CLI

## Example Usage

Starting from an empty workspace:

```bash
# Scaffold a new project (default type is `application`)
xtc init hello

# Compile an Ecstasy module
xtc build hello.x          # or: xcc hello.x

# Run the compiled module
xtc run hello              # or: xec hello

# Run tests in the module (uses xunit)
xtc test hello

# Check versions
xtc --version
```

See [doc/xtc-cli.md](../doc/xtc-cli.md) for the full set of `xtc` subcommands and options.

## Container Details

- **Base Image**: `ghcr.io/xtclang/xvm:latest`
- **Multi-platform**: Supports both AMD64 and ARM64
- **Size**: ~101MB (optimized runtime)
- **Updates**: Automatically pulls latest image builds from CI

## Customization

To customize the development environment:

1. Edit `.devcontainer/devcontainer.json`
2. Rebuild the container: Command Palette → "Dev Containers: Rebuild Container"

Common customizations:
- Add more VS Code extensions in `customizations.vscode.extensions`
- Install additional tools in `postCreateCommand`
- Set environment variables in `containerEnv`
- Forward ports for web services in `forwardPorts`

## Troubleshooting

### Container fails to start
- Check if Docker is running
- Verify you have internet access to pull `ghcr.io/xtclang/xvm:latest`
- Try "Dev Containers: Rebuild Container" from Command Palette

### XTC tools not found
- The container should automatically set `XDK_HOME=/opt/xdk` and add `/opt/xdk/bin` to PATH
- If issues persist, run: `source /etc/environment` or restart the terminal

### Performance issues
- The container uses an optimized runtime image
- For better performance, ensure your Docker desktop has adequate memory allocated (4GB+ recommended)
