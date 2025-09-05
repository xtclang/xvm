# XTC Language Development Container

This devcontainer provides a complete development environment for the XTC (Ecstasy) programming language using the official XVM Docker image.

## Quick Start

1. Install the [Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers) for VS Code
2. Open this folder in VS Code
3. When prompted, click "Reopen in Container" or use Command Palette: "Dev Containers: Reopen in Container"
4. The container will automatically pull `ghcr.io/xtclang/xvm:latest` and set up the environment

## What's Included

- **XVM Runtime**: Complete XDK with `xtc` (compiler) and `xec` (executor)
- **Java Runtime**: Optimized JRE for running XTC programs
- **Git**: Version control tools
- **GitHub CLI**: For interacting with GitHub repositories

## Available Tools

- `xtc` - XTC/Ecstasy compiler
- `xec` - XTC/Ecstasy program executor  
- `git` - Version control
- `gh` - GitHub CLI

## Example Usage

```bash
# Compile an XTC program
xtc hello.x

# Run the compiled program
xec hello

# Check versions
xtc --version
xec --version
```

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