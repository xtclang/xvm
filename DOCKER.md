# Docker Build and Usage Instructions

This document describes how to build and use Docker images for the XVM project. The XDK Docker Container provides native multi-platform support with architecture-specific native launchers.

## Quick Start Guide

**Just want to run XVM tools? Skip to [Using Pre-built Images](#using-pre-built-images) section.**

For developers building the project, see the [Building Images](#building-images) section.

## Available Gradle Tasks

### Individual Platform Tasks
- `dockerBuildAmd64` - Build Docker image for linux/amd64 platform only
- `dockerBuildArm64` - Build Docker image for linux/arm64 platform only
- `dockerPushAmd64` - Push AMD64 Docker image to GitHub Container Registry
- `dockerPushArm64` - Push ARM64 Docker image to GitHub Container Registry

### Combined Tasks
- `dockerBuild` - Build Docker images locally for both platforms (amd64 and arm64)
- `dockerPushAll` - Push all platform-specific Docker images to GitHub Container Registry
- `dockerBuildAndPush` - Build and push individual platform images (depends on dockerPushAll)

### Multi-Platform Tasks
- `dockerBuildMultiPlatform` - Build multi-platform images locally (may not work on all setups)
- `dockerPushMultiPlatform` - Build and push multi-platform manifest to registry
- `dockerBuildAndPushMultiPlatform` - Alias for dockerPushMultiPlatform

## Building Images

### Build Both Platforms
```bash
./gradlew dockerBuild
```

### Build Specific Platform
```bash
# AMD64 only
./gradlew dockerBuildAmd64

# ARM64 only
./gradlew dockerBuildArm64
```

### Build and Push
```bash
# Build and push individual platform images
./gradlew dockerBuildAndPush

# Build and push multi-platform manifest
./gradlew dockerPushMultiPlatform

# Just build multi-platform locally (may not work on all Docker setups)
./gradlew dockerBuildMultiPlatform
```

## Image Tags

All builds create platform-specific tags:
- `ghcr.io/xtclang/xvm:latest-amd64` - Latest AMD64 build
- `ghcr.io/xtclang/xvm:latest-arm64` - Latest ARM64 build
- `ghcr.io/xtclang/xvm:VERSION-amd64` - Versioned AMD64 build
- `ghcr.io/xtclang/xvm:VERSION-arm64` - Versioned ARM64 build

Generic tags (when using multi-platform builds):
- `ghcr.io/xtclang/xvm:latest` - Multi-platform manifest
- `ghcr.io/xtclang/xvm:VERSION` - Versioned multi-platform manifest

## Build Configuration

### Default Settings
- Downloads latest master from GitHub (no local source used)
- `JAVA_VERSION=21` - Uses Java 21 JRE (distroless)
- Platform matches host architecture (linux/amd64 on x86, linux/arm64 on ARM)
- Compiles native launchers from C source for target platform
- Creates architecture-specific xcc/xec executables
- Self-contained images (~101MB) with minimal attack surface

### Build Arguments
You can override default settings using build arguments:

```bash
# Use different Java version
docker buildx build --build-arg JAVA_VERSION=17 -t xvm:latest .

# Build from specific commit
docker buildx build --build-arg VERSION=abc123 -t xvm:latest .

# Build from GitHub release
docker buildx build --build-arg VERSION=v1.0.0 -t xvm:latest .
```

### VERSION Options
- Empty/unset (default) - Downloads latest master branch
- `abc123` (commit hash) - Downloads specific commit
- `v1.0.0` (or any git ref) - Downloads specific tag/branch

## Direct Docker Commands (Alternative to Gradle)

If you prefer using Docker commands directly instead of Gradle tasks:

### Basic Builds
```bash
# Native platform build
docker buildx build -t xvm:latest .

# With specific commit
docker buildx build --build-arg VERSION=abc123 -t xvm:latest .
```

### Cross-Platform Builds
```bash
# Force Linux AMD64
docker buildx build --platform linux/amd64 -t xvm:amd64 --load .

# Force Linux ARM64
docker buildx build --platform linux/arm64 -t xvm:arm64 --load .

# Multi-platform manifest (creates separate images for each arch)
docker buildx build --platform linux/amd64,linux/arm64 -t xvm:latest --push .
```

### How Multi-Platform Works
- Docker builds SEPARATE images for each architecture specified
- Each build compiles its own native launcher (amd64 gets x86_64, arm64 gets aarch64)
- Results in a manifest with architecture-specific images
- Docker automatically pulls the correct image for the runtime platform

### Advanced Options
```bash
# With GitHub Actions caching
docker buildx build --cache-from type=gha --cache-to type=gha,mode=max -t xvm:latest .

# Push to custom registry
docker buildx build --platform linux/amd64,linux/arm64 -t myregistry/xvm:latest --push .

# Extract architecture-specific launcher to host
docker buildx build --target launcher-export --platform linux/arm64 -o . .
```

## Using Pre-built Images

Pre-built Docker images are automatically published to GitHub Container Registry from the latest master branch.

### Quick Start (No Authentication Required)

The images are public and can be used immediately:

```bash
# Check if XVM tools work
docker run --rm ghcr.io/xtclang/xvm:latest xec --version
docker run --rm ghcr.io/xtclang/xvm:latest xcc --version

# Compile and run an Ecstasy program
echo 'module HelloWorld { void run() { @Inject Console console; console.print("Hello, World"); } }' > hello.x
docker run -v $(pwd):/workspace -w /workspace --rm ghcr.io/xtclang/xvm:latest xcc hello.x
docker run -v $(pwd):/workspace -w /workspace --rm ghcr.io/xtclang/xvm:latest xec hello
```

### Available Images

All images are multi-platform and work on AMD64 and ARM64:

```bash
# Latest build from master branch
ghcr.io/xtclang/xvm:latest

# Specific version tags
ghcr.io/xtclang/xvm:0.4.4-SNAPSHOT

# Commit-specific builds  
ghcr.io/xtclang/xvm:abc1234     # Short commit hash
ghcr.io/xtclang/xvm:abc1234...  # Full commit hash

# Platform-specific (if needed)
ghcr.io/xtclang/xvm:latest-amd64
ghcr.io/xtclang/xvm:latest-arm64
```

### Common Usage Patterns

```bash
# Run xec directly (default command)
docker run --rm ghcr.io/xtclang/xvm:latest

# Run xcc compiler
docker run --rm ghcr.io/xtclang/xvm:latest xcc --help

# Compile local files
docker run -v $(pwd):/workspace -w /workspace --rm ghcr.io/xtclang/xvm:latest xcc myfile.x

# Run compiled program
docker run -v $(pwd):/workspace -w /workspace --rm ghcr.io/xtclang/xvm:latest xec MyProgram

# Check build information
docker run --rm ghcr.io/xtclang/xvm:latest cat /opt/xdk/xvm.json
```

### Notes

- **No shell access**: Images use distroless base (no bash/sh for security)
- **Small size**: ~101MB total (includes Java runtime + XDK)
- **Native performance**: Each platform uses native launchers (no emulation)
- **Public access**: No `docker login` required for pulling images

### Docker Compose Example
```yaml
version: '3.8'
services:
  xvm-compiler:
    image: ghcr.io/xtclang/xvm:latest
    platform: linux/amd64  # Optional: force platform
    volumes:
      - ./src:/workspace
    working_dir: /workspace
    command: xcc main.x
    
  xvm-runtime:
    image: ghcr.io/xtclang/xvm:latest
    volumes:
      - ./compiled:/workspace
    working_dir: /workspace
    command: xec app.xtc
```

## Prerequisites

- Docker with buildx support
- Authentication to GitHub Container Registry (`docker login ghcr.io`)
- For multi-platform builds: Docker buildx builder with multi-platform support

## Continuous Integration & Automated Builds

### GitHub Actions Workflow

Docker images are automatically built and published when:
- Code is pushed to the `master` branch
- Manual workflow dispatch is triggered with `always_publish_snapshot: true`

### CI Process

The GitHub Actions workflow (`.github/workflows/ci.yml`) performs:

1. **Build Verification**: Runs on Ubuntu + Windows matrix
   - Builds XDK with `./gradlew installDist`
   - Runs all tests including manual tests
   - Uploads build artifacts for reuse

2. **Docker Build & Push**: Only after ALL tests pass
   - Downloads verified build artifacts (no rebuild)
   - Builds multi-platform images (AMD64 + ARM64)
   - Uses latest master source from GitHub
   - Pushes to GitHub Container Registry with multiple tags

3. **Image Tags Created**:
   ```bash
   ghcr.io/xtclang/xvm:latest
   ghcr.io/xtclang/xvm:latest-amd64
   ghcr.io/xtclang/xvm:latest-arm64
   ghcr.io/xtclang/xvm:0.4.4-SNAPSHOT
   ghcr.io/xtclang/xvm:abc1234       # Short commit hash
   ghcr.io/xtclang/xvm:abc1234...    # Full commit hash
   ```

### Key Features

- **Test-First**: Docker builds ONLY run if all tests pass
- **Multi-Platform**: Single workflow builds both AMD64 and ARM64
- **Fast Builds**: Reuses verified artifacts, includes layer caching
- **Commit Tracking**: Each image tagged with git commit for traceability
- **Public Access**: Images are publicly accessible without authentication

### Build Info

Each image includes build metadata at `/opt/xdk/xvm.json`:
```json
{
  "buildDate": "2025-07-30T14:42:57Z",
  "gitCommit": "d5c6b7c3f8236665037f2c33731419d004195f8a", 
  "version": "master-d5c6b7c3f",
  "platform": "linux/amd64"
}
```

## Technical Notes

### Docker Build Behavior

- **Source Code**: All builds download fresh source from GitHub (no local files used)
- **Multi-platform**: Uses Docker buildx to create separate images per architecture
- **Native Launchers**: Each platform gets optimized native binaries (no Java wrapper scripts)
- **Caching**: Local builds use filesystem cache, CI uses GitHub Actions cache
- **Base Image**: Uses Google's distroless Java for minimal attack surface

### Performance

- **Image Size**: ~101MB (vs ~300MB+ with full JRE base images) 
- **Build Time**: ~3-5 minutes for multi-platform (with caching)
- **Startup**: Native launchers provide fast startup vs pure Java
- **Security**: Distroless base has no shell, package managers, or extra tools

### Limitations

- **No shell access**: Images are distroless (use docker exec with external tools if debugging needed)
- **Multi-platform pushes**: Cannot use `--load` (pushes directly to registry)
- **Platform emulation**: Cross-platform builds may be slower on some systems