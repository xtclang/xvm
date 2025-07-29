# Docker Build and Usage Instructions

This document describes how to build and use Docker images for the XVM project. The XDK Docker Container provides native multi-platform support with architecture-specific native launchers.

## Available Gradle Tasks

### Individual Platform Tasks
- `dockerBuildAmd64` - Build Docker image for linux/amd64 platform only
- `dockerBuildArm64` - Build Docker image for linux/arm64 platform only
- `dockerPushAmd64` - Push AMD64 Docker image to GitHub Container Registry
- `dockerPushArm64` - Push ARM64 Docker image to GitHub Container Registry

### Combined Tasks
- `dockerBuild` - Build Docker images for both platforms (amd64 and arm64)
- `dockerBuildAndPush` - Build and push Docker images for both platforms
- `dockerPushAll` - Push all platform-specific Docker images to GitHub Container Registry
- `dockerBuildMultiPlatform` - Build multi-platform manifest and push directly to registry

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
# Build and push both platforms
./gradlew dockerBuildAndPush

# Build multi-platform manifest (builds and pushes directly)
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
- `VERSION=local` - Builds from current source checkout
- `JAVA_VERSION=21` - Uses Java 21 JDK/JRE
- Platform matches host architecture (linux/amd64 on x86, linux/arm64 on ARM)
- Compiles native launchers from C source for target platform
- Creates architecture-specific xcc/xec executables
- Automatic dependency on `installDist` task to ensure XDK is built first

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
- `local` (default) - Uses local source
- `latest` - Downloads latest master branch
- `v1.0.0` (or any version tag) - Uses GitHub release
- `abc123` (commit hash) - Downloads specific commit

## Direct Docker Commands (Alternative to Gradle)

If you prefer using Docker commands directly instead of Gradle tasks:

### Basic Builds
```bash
# Native platform build
docker buildx build -t xvm:latest .

# With VERSION override
docker buildx build --build-arg VERSION=local -t xvm:latest .
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

## Pulling and Using Images

### Authentication
```bash
# Login to GitHub Container Registry
docker login ghcr.io -u YOUR_GITHUB_USERNAME
```

### Pull Images
```bash
# Pull latest (Docker auto-selects platform)
docker pull ghcr.io/xtclang/xvm:latest

# Pull platform-specific versions
docker pull ghcr.io/xtclang/xvm:latest-amd64
docker pull ghcr.io/xtclang/xvm:latest-arm64

# Pull specific version
docker pull ghcr.io/xtclang/xvm:VERSION-amd64
```

### Run Images
```bash
# Interactive shell
docker run -it --rm ghcr.io/xtclang/xvm:latest /bin/bash

# Run xec directly (default command)
docker run --rm ghcr.io/xtclang/xvm:latest

# Run xcc compiler
docker run --rm ghcr.io/xtclang/xvm:latest xcc

# Mount local files for compilation
docker run -v $(pwd):/workspace -w /workspace --rm ghcr.io/xtclang/xvm:latest xcc myfile.x

# Force specific platform
docker run --platform linux/amd64 --rm ghcr.io/xtclang/xvm:latest-amd64
docker run --platform linux/arm64 --rm ghcr.io/xtclang/xvm:latest-arm64
```

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

## Automated Builds (GitHub Actions)

Docker images are automatically built and pushed to GitHub Container Registry when:
- Code is pushed to the `master` branch
- Manual workflow dispatch is triggered

The CI workflow (`ci.yml`) will:
1. Build the XDK using Gradle
2. Build AMD64 Docker image using `dockerPushAmd64` task  
3. Push to `ghcr.io/xtclang/xvm:latest-amd64` and `ghcr.io/xtclang/xvm:VERSION-amd64`
4. Images automatically appear in the repository's Packages section

No manual authentication needed - GitHub Actions uses `GITHUB_TOKEN` automatically.

## Notes

- Multi-platform builds (`dockerBuildMultiPlatform`) cannot use `--load` and will push directly to the registry
- Individual platform builds can be loaded locally for testing
- All builds compile native launchers specific to the target platform
- The `VERSION=local` build argument ensures builds use your current source checkout