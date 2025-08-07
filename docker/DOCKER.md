# Docker Build and Usage Instructions

This document describes how to build and use Docker images for the XVM project. The XDK Docker Container provides native multi-platform support with architecture-specific native launchers.

## Quick Start Guide

**Just want to run XVM tools? Skip to [Using Pre-built Images](#using-pre-built-images) section.**

For developers building the project, see the [Building Images](#building-images) section.

## Available Gradle Tasks

All Docker tasks are now organized in the `docker/` subproject. Run from project root:

### Individual Platform Tasks (for debugging)
- `dockerBuildAmd64` - Build Docker image for linux/amd64 platform only
- `dockerBuildArm64` - Build Docker image for linux/arm64 platform only  
- `dockerPushAmd64` - Push AMD64 Docker image to GitHub Container Registry
- `dockerPushArm64` - Push ARM64 Docker image to GitHub Container Registry

### Main Tasks
- `dockerBuild` - Build Docker images for both platforms individually (amd64 + arm64)
- `dockerBuildMultiPlatform` - Build multi-platform images locally (recommended for local builds)
- `dockerPushMultiPlatform` - Build and push multi-platform manifest to registry (recommended for publishing)
- `dockerPushAll` - Push all platform-specific Docker images to GitHub Container Registry

### Workflow Tasks
- `dockerBuildPushAndManifest` - Complete CI-like workflow: build platforms + create manifests

## Building Images

### Build Both Platforms
```bash
# Individual platform builds (useful for local testing)
./gradlew dockerBuild

# Multi-platform build (recommended for local development)
./gradlew dockerBuildMultiPlatform
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
# Build and push multi-platform manifest (recommended)
./gradlew dockerPushMultiPlatform

# Build and push individual platform images then create manifests
./gradlew dockerBuildPushAndManifest

# Push individual platforms only
./gradlew dockerPushAll
```

### Working with Docker Directory

You can also run tasks directly from the `docker/` subdirectory:

```bash
cd docker/
../gradlew dockerBuildMultiPlatform

# Or use direct Docker commands
docker buildx build --platform linux/arm64 --tag test-xvm:latest .
```

## Image Tags

### Master Branch Images
All builds from the master branch create these tags:
- `ghcr.io/xtclang/xvm:latest-amd64` - Latest AMD64 build
- `ghcr.io/xtclang/xvm:latest-arm64` - Latest ARM64 build  
- `ghcr.io/xtclang/xvm:VERSION-amd64` - Versioned AMD64 build
- `ghcr.io/xtclang/xvm:VERSION-arm64` - Versioned ARM64 build
- `ghcr.io/xtclang/xvm:COMMIT_SHA-amd64` - Commit-specific AMD64 build
- `ghcr.io/xtclang/xvm:COMMIT_SHA-arm64` - Commit-specific ARM64 build

Multi-platform manifests:
- `ghcr.io/xtclang/xvm:latest` - Multi-platform manifest
- `ghcr.io/xtclang/xvm:VERSION` - Versioned multi-platform manifest
- `ghcr.io/xtclang/xvm:COMMIT_SHA` - Commit-specific multi-platform manifest

### Branch-Specific Images  
**Single Package Model**: All branches use the same package with branch-based tags.

Non-master branches create these tags:
- `ghcr.io/xtclang/xvm:BRANCH_NAME-amd64` - Branch-specific AMD64 build
- `ghcr.io/xtclang/xvm:BRANCH_NAME-arm64` - Branch-specific ARM64 build
- `ghcr.io/xtclang/xvm:COMMIT_SHA-amd64` - Commit-specific AMD64 build  
- `ghcr.io/xtclang/xvm:COMMIT_SHA-arm64` - Commit-specific ARM64 build

Multi-platform manifests:
- `ghcr.io/xtclang/xvm:BRANCH_NAME` - Branch multi-platform manifest
- `ghcr.io/xtclang/xvm:COMMIT_SHA` - Commit multi-platform manifest

Example for `lagergren/gradle-lifecycle-fixes`:
- `ghcr.io/xtclang/xvm:gradle-lifecycle-fixes-amd64`
- `ghcr.io/xtclang/xvm:gradle-lifecycle-fixes-arm64`  
- `ghcr.io/xtclang/xvm:gradle-lifecycle-fixes`

## Build Configuration

### Default Settings
- Downloads source from GitHub using branch/commit (no local source used)
- `GH_BRANCH=master` - Default branch to build from
- `GH_COMMIT` - Optional specific commit SHA (defaults to latest from branch)
- `JAVA_VERSION=21` - Uses Bellsoft Liberica OpenJDK 21 Alpine (consistent across build stages)
- Platform matches host architecture (linux/amd64 on x86, linux/arm64 on ARM)
- Compiles native launchers from C source for target platform
- Creates architecture-specific xcc/xec executables

### Build Scripts
The Docker build uses several helper scripts in `docker/scripts/`:

- **`clone-xdk.sh`** - Handles efficient git cloning with smart caching and commit/branch support
- **`build-launcher.sh`** - Compiles native C launchers for the target platform (amd64/arm64)
- **`build-xdk.sh`** - Builds the XDK using Gradle with cache optimization and debug output

### Build Arguments
You can override default settings using build arguments:

```bash
# Use different Java version
docker buildx build --build-arg JAVA_VERSION=17 -t xvm:latest .

# Build from specific commit
docker buildx build --build-arg GH_COMMIT=abc123 -t xvm:latest .

# Build from specific branch
docker buildx build --build-arg GH_BRANCH=feature-branch -t xvm:latest .

# Build from GitHub release tag
docker buildx build --build-arg GH_BRANCH=v1.0.0 -t xvm:latest .
```

### Build Argument Options
- `GH_BRANCH` (default: master) - Downloads specific branch or tag from GitHub
- `GH_COMMIT` (default: latest from branch) - Downloads specific commit SHA
- `JAVA_VERSION` (default: 21) - Java version to use for build and runtime

## Direct Docker Commands (Alternative to Gradle)

If you prefer using Docker commands directly instead of Gradle tasks:

### Basic Builds
```bash
# Native platform build
docker buildx build -t xvm:latest .

# With specific commit
docker buildx build --build-arg GH_COMMIT=abc123 -t xvm:latest .

# With specific branch
docker buildx build --build-arg GH_BRANCH=feature-branch -t xvm:latest .
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
ghcr.io/xtclang/xvm:abc1234567890abcdef1234567890abcdef12  # Full commit hash

# Branch-specific builds
ghcr.io/xtclang/xvm:master                              # Branch name
ghcr.io/xtclang/xvm:feature-branch                     # Feature branch

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

### Inspecting Multi-Platform Images

```bash
# View the multi-platform manifest list
docker manifest inspect ghcr.io/xtclang/xvm:latest

# Check which architecture was automatically pulled for your machine
docker image inspect ghcr.io/xtclang/xvm:latest --format '{{.Architecture}}'

# View complete image details (metadata, layers, config)
docker image inspect ghcr.io/xtclang/xvm:latest

# Get specific image information
docker image inspect ghcr.io/xtclang/xvm:latest --format '{{.Config.Env}}'     # Environment variables
docker image inspect ghcr.io/xtclang/xvm:latest --format '{{.Config.Cmd}}'     # Default command
docker image inspect ghcr.io/xtclang/xvm:latest --format '{{.Size}}'           # Image size in bytes
docker image inspect ghcr.io/xtclang/xvm:latest --format '{{.Created}}'        # Build timestamp
```

The manifest shows available platforms (`linux/amd64` and `linux/arm64`), and Docker automatically selects the correct architecture for your machine without requiring platform specification.

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
   ghcr.io/xtclang/xvm:abc1234567890abcdef1234567890abcdef12  # Full commit hash
   ghcr.io/xtclang/xvm:master                              # Branch name
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
  "commit": "d5c6b7c3f8236665037f2c33731419d004195f8a", 
  "branch": "master",
  "version": "master",
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

## GitHub Package Repository Management

### Authentication

To interact with GitHub Container Registry packages (listing, deleting, etc.), you need a Personal Access Token with appropriate scopes:

```bash
# Login to GitHub Container Registry using gh CLI
gh auth login
gh auth token | docker login ghcr.io -u $(gh api user --jq .login) --password-stdin
```

### Required Scopes

For different operations, you need these token scopes:
- **Pulling public images**: No authentication required
- **Pulling private images**: `read:packages`
- **Listing packages**: `read:packages`  
- **Deleting packages**: `read:packages` + `delete:packages`

To add scopes to your existing GitHub CLI authentication:
```bash
gh auth refresh --hostname github.com --scopes read:packages,delete:packages
```

### Managing Packages

#### List all container packages for the organization:
```bash
gh api 'orgs/xtclang/packages?package_type=container' --jq '.[] | {name: .name, visibility: .visibility, updated_at: .updated_at}'
```

#### Delete a package:
```bash
gh api -X DELETE 'orgs/xtclang/packages/container/PACKAGE_NAME'
```

#### Get package details:
```bash
gh api 'orgs/xtclang/packages/container/PACKAGE_NAME'
```

### Package Naming Strategy

- **Main images**: Use simple names like `xvm` for primary project artifacts
- **Branch-specific**: Use `xvm-BRANCH` format for development builds (e.g., `xvm-feature-branch`)
- **Avoid clutter**: Clean up temporary or test images regularly to keep the registry organized

### Visibility Settings

- **Public packages**: Accessible without authentication - preferred for open source
- **Internal packages**: Only accessible to organization members
- **Private packages**: Only accessible to specific users/teams

Change visibility in the GitHub web interface under package settings.

### Package Version Retention

**Important**: GitHub Container Registry keeps ALL package versions by default - there's no built-in retention policy.

#### Current Behavior Without Retention Policy
- Each `docker push` creates a new version stored permanently
- Tags like `latest` get reassigned but old image data remains under SHA digests
- Storage grows indefinitely with every CI build
- Manual cleanup required to prevent unbounded growth

#### Check Current Version Count
```bash
# Count total versions stored
gh api 'orgs/xtclang/packages/container/xvm/versions' --jq 'length'

# List recent versions with creation dates
gh api 'orgs/xtclang/packages/container/xvm/versions' --jq '.[] | {id: .id, created_at: .created_at}' | head -10
```

#### Automated Retention Policy (Recommended)

Add this to your GitHub Actions workflow to automatically manage versions:

```yaml
- name: Clean up old container versions
  uses: snok/container-retention-policy@v3
  with:
    image-names: xvm
    cut-off: 4 weeks ago UTC
    keep-n-most-recent: 5          # Maintains exactly 5 versions maximum
    account-type: org
    org-name: xtclang
    token: ${{ secrets.PACKAGE_DELETE_TOKEN }}  # Requires delete:packages scope
```

#### Manual Version Cleanup
```bash
# Delete specific version by ID
gh api -X DELETE 'orgs/xtclang/packages/container/xvm/versions/VERSION_ID'

# Bulk delete versions older than a date (requires scripting)
gh api 'orgs/xtclang/packages/container/xvm/versions' --jq '.[] | select(.created_at < "2025-07-01") | .id'
```

**Note**: Retention policies prevent storage growth by maintaining a rolling window (e.g., 5 most recent versions). Without a policy, versions accumulate indefinitely.