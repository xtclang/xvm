# Docker Build and Usage Instructions

This document describes how to build and use Docker images for the XVM project. The XDK Docker Container provides native multi-platform support with architecture-specific native launchers.

## Quick Start Guide

**Just want to run XVM tools? Skip to [Using Pre-built Images](#using-pre-built-images) section.**

For developers building the project, see the [Building Images](#building-images) section.

## Available Gradle Tasks

All Docker tasks are now organized in the `docker/` subproject. Run from project root:

### Build Tasks
- `docker:buildAmd64` - Build Docker image for AMD64 platform
- `docker:buildArm64` - Build Docker image for ARM64 platform  
- `docker:buildMultiPlatform` - Build multi-platform Docker images (recommended for local builds)

### Push Tasks
- `docker:pushAmd64` - Push AMD64 Docker image to GitHub Container Registry
- `docker:pushArm64` - Push ARM64 Docker image to GitHub Container Registry
- `docker:pushMultiPlatform` - Build and push multi-platform Docker images (recommended for publishing)

### Management Tasks
- `docker:createManifest` - Create multi-platform manifest
- `docker:testDockerImageFunctionality` - Test Docker image functionality
- `docker:listImages` - List Docker images in registry
- `docker:cleanImages` - Clean up old Docker package versions with improved verification (default: keep 10 most recent, protect master images)

## Building Images

### Build Both Platforms
```bash
# Multi-platform build (recommended for local development)
./gradlew docker:buildMultiPlatform

# Individual platform builds (useful for local testing)
./gradlew docker:buildAmd64 docker:buildArm64
```

### Build Specific Platform
```bash
# AMD64 only
./gradlew docker:buildAmd64

# ARM64 only  
./gradlew docker:buildArm64
```

### Build and Push
```bash
# Build and push multi-platform manifest (recommended)
./gradlew docker:pushMultiPlatform

# Push individual platforms only
./gradlew docker:pushAmd64 docker:pushArm64

# Create manifest after pushing individual platforms
./gradlew docker:createManifest
```

### Working with Docker Directory

You can also run tasks directly from the `docker/` subdirectory:

```bash
cd docker/
../gradlew buildMultiPlatform

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
- Manual workflow dispatch is triggered with `always_build_docker_image: true`
- Manual workflow dispatch is triggered with `always_clean_docker: true` (runs cleanup only)

#### Workflow Input Options

The CI workflow accepts these input parameters:

- `always_build_docker_image`: Always build Docker images regardless of branch
- `always_clean_docker`: Always run Docker cleanup regardless of branch  
- `always_publish_snapshot`: Always publish snapshot packages regardless of branch
- `single_platform`: Run only single platform (ubuntu-latest, windows-latest, or full matrix)
- `run_manual_tests`: Run manual tests (default: true)
- `run_manual_tests_parallel`: Run manual tests in parallel mode (default: true)
- `extra_gradle_options`: Extra Gradle options to pass to the build

#### Triggering Workflows Manually

```bash
# Build Docker images on any branch
gh workflow run ci.yml --ref your-branch-name --raw-field always_build_docker_image=true

# Test cleanup functionality only
gh workflow run ci.yml --ref your-branch-name --raw-field always_clean_docker=true --raw-field single_platform=ubuntu-latest

# Full build with single platform for faster testing
gh workflow run ci.yml --ref your-branch-name --raw-field always_build_docker_image=true --raw-field single_platform=ubuntu-latest
```

### CI Process

The GitHub Actions workflow (`.github/workflows/ci.yml`) performs:

1. **Build Verification**: Runs on Ubuntu + Windows matrix
   - Uses `setup-xvm-build` action for consistent environment setup
   - Builds XDK with `./gradlew installDist`
   - Runs all tests including manual tests
   - Uploads build artifacts for reuse

2. **Compute Docker Tags**: Calculates metadata once for consistency
   - Determines version, branch, and commit information
   - Generates tag lists for all subsequent Docker operations

3. **Docker Build & Push**: Only after ALL tests pass
   - Downloads verified build artifacts (no rebuild)  
   - Builds multi-platform images (AMD64 + ARM64) in parallel on native runners
   - Uses pre-built XDK artifacts with fresh source checkout
   - Pushes to GitHub Container Registry with multiple tags

4. **Docker Testing**: Validates functionality
   - Tests both `xec` and `xcc` commands
   - Validates compilation and execution of test programs
   - Ensures native launcher functionality works correctly

5. **Package Cleanup**: Automated maintenance with enhanced verification
   - Removes old Docker package versions (keeps 10 most recent + 1 master image)
   - Uses retry verification with 3 attempts and 5-second delays
   - Fails CI build if deletions don't complete (prevents silent failures)

#### Image Tags Created

After successful builds, these tags are published:
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

## Bootstrap Guide: Setting Up CI/CD and Containerization

### Initial Project Setup

If you're setting up Docker containerization and CI/CD for the first time, here's the complete bootstrap process:

#### 1. GitHub CLI Authentication

First, authenticate with comprehensive scopes for full CI/CD operations:

```bash
# Authenticate with all necessary scopes for containerization and CI
gh auth refresh --hostname github.com --scopes repo,read:packages,write:packages,delete:packages,workflow

# Verify authentication and scopes
gh auth status --show-token
```

**Required Scopes Explained**:
- `repo` - Access repository contents, create releases, manage artifacts
- `read:packages` - List and pull Docker images/packages 
- `write:packages` - Push Docker images to GitHub Container Registry
- `delete:packages` - Clean up old Docker image versions (for maintenance)
- `workflow` - Trigger and manage GitHub Actions workflows

**Security Note**: These scopes are standard for containerization workflows. The GitHub CLI stores tokens securely in your system keychain (macOS Keychain, Windows Credential Manager, Linux keyring).

#### 2. Docker Registry Authentication

```bash
# Login to GitHub Container Registry (one-time setup)
gh auth token | docker login ghcr.io -u $(gh api user --jq .login) --password-stdin

# Verify Docker registry access
docker pull ghcr.io/xtclang/xvm:latest --quiet
```

#### 3. Local Build Environment Setup

```bash
# Verify Docker buildx multi-platform support
docker buildx version

# Create buildx builder if needed (for multi-platform builds)
docker buildx create --name multiplatform --use
docker buildx inspect --bootstrap

# Test local Docker build
./gradlew docker:buildArm64  # or buildAmd64 depending on your platform
```

#### 4. CI/CD Secrets Configuration

For GitHub Actions to work properly, ensure these secrets are configured in your repository:

**Repository Settings → Secrets and variables → Actions**:

```bash
# Required secrets (these should already be configured)
GITHUB_TOKEN                    # Automatically provided by GitHub Actions
ORG_XTCLANG_GPG_SIGNING_KEY    # For package signing (if enabled)
ORG_XTCLANG_GPG_SIGNING_PASSWORD # For package signing (if enabled)

# Optional secrets for enhanced publishing
ORG_XTCLANG_GRADLE_PLUGIN_PORTAL_PUBLISH_KEY    # For Gradle plugin portal
ORG_XTCLANG_GRADLE_PLUGIN_PORTAL_PUBLISH_SECRET # For Gradle plugin portal
PACKAGE_DELETE_TOKEN            # For automated cleanup (custom token with delete:packages)
```

The `GITHUB_TOKEN` is automatically provided and has sufficient permissions for Docker operations.

#### 5. Testing the Complete Pipeline

```bash
# Test local builds first
./gradlew docker:buildArm64
./gradlew docker:buildArm64  # Second run should be faster (caching test)

# Test manual workflow dispatch
gh workflow run "XVM Verification and Package Updates" \
  --ref your-branch-name \
  --field always_build_docker_image=true \
  --field single_platform=full

# Monitor the workflow
gh run list --limit 3
gh run view --web  # Opens in browser
```

#### 6. Package Management Setup

```bash
# Test package operations
./gradlew docker:listImages      # List all Docker packages
./gradlew docker:cleanupVersions # Clean up old versions (with confirmation)

# View packages in GitHub web interface
open "https://github.com/orgs/xtclang/packages"
```

### Troubleshooting Common Bootstrap Issues

#### Authentication Problems
```bash
# Clear and re-authenticate if having issues
gh auth logout --hostname github.com
gh auth login --hostname github.com --scopes repo,read:packages,write:packages,delete:packages,workflow
```

#### Docker Registry Access Denied
```bash
# Re-authenticate with Docker registry
gh auth token | docker login ghcr.io -u $(gh api user --jq .login) --password-stdin

# Test with explicit package name
docker pull ghcr.io/xtclang/xvm:latest
```

#### Local Docker Build Issues
```bash
# Check Docker buildx setup
docker buildx ls

# Recreate builder if needed
docker buildx rm multiplatform
docker buildx create --name multiplatform --driver docker-container --use
```

#### CI/CD Workflow Failures
```bash
# Check recent workflow runs
gh run list --limit 5

# View detailed logs
gh run view WORKFLOW_ID --log

# Common issue: missing authentication
# Solution: Verify GITHUB_TOKEN has sufficient permissions in repo settings
```

### Alternative: Fine-Grained Personal Access Tokens

For enhanced security, you can use fine-grained tokens scoped to specific repositories:

1. **GitHub.com → Settings → Developer settings → Personal access tokens → Fine-grained tokens**
2. **Select specific repositories** (e.g., just `xtclang/xvm`)
3. **Choose minimal permissions**:
   - Contents: Read (for repository access)
   - Packages: Read + Write + Delete (for Docker operations)  
   - Actions: Write (for workflow dispatch)
   - Metadata: Read (always required)

4. **Use the token**:
```bash
gh auth login --with-token < token.txt
```

**Benefits**: More secure than classic tokens, repository-specific, granular permissions.

### Maintenance Checklist

Once bootstrapped, maintain your setup with these periodic tasks:

- **Monthly**: Review and clean up old Docker image versions
- **Quarterly**: Rotate authentication tokens for security  
- **After major changes**: Test the complete CI/CD pipeline end-to-end
- **Before releases**: Verify all package permissions and visibility settings

This bootstrap process ensures your containerization and CI/CD pipeline is properly configured with appropriate security and permissions.

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

#### Automated Cleanup (Current Implementation)

The XVM project uses a built-in Gradle task for automated cleanup with enhanced safety features:

```bash
# Manual cleanup (with confirmation prompts)
./gradlew docker:cleanImages

# Automated cleanup (for CI - no prompts)
./gradlew docker:cleanImages -Pforce=true

# Dry-run to see what would be deleted
./gradlew docker:cleanImages -PdryRun=true

# Custom retention count (default: 10)
./gradlew docker:cleanImages -PkeepCount=15
```

**Enhanced Safety Features**:
- Always protects at least one master/release image
- Keeps configurable number of most recent versions (default: 10)
- Uses retry verification with 3 attempts and 5-second delays
- Fails in CI if deletions don't complete (prevents silent failures)
- Detailed logging shows exactly what was deleted vs kept

#### Alternative: External Retention Policy

You can also use external tools for more complex retention policies:

```yaml
- name: Clean up old container versions  
  uses: snok/container-retention-policy@v3
  with:
    image-names: xvm
    cut-off: 4 weeks ago UTC
    keep-n-most-recent: 10
    account-type: org
    org-name: xtclang
    token: ${{ secrets.PACKAGE_DELETE_TOKEN }}
```

#### Manual Version Cleanup
```bash
# List all versions with details
./gradlew docker:listImages

# Delete specific version by ID (use with caution)
gh api -X DELETE 'orgs/xtclang/packages/container/xvm/versions/VERSION_ID'
```

**Note**: The built-in cleanup task is safer and more reliable than manual deletion scripts, with comprehensive verification to prevent accidental data loss.