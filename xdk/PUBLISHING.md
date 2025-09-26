# XDK Publishing Guide

This guide covers how to publish XDK artifacts and the XTC Gradle Plugin to various repositories including GitHub Packages, Maven Central, and Gradle Plugin Portal.

## Table of Contents
- [Overview](#overview)
- [Projects](#projects)
- [Credentials Setup](#credentials-setup)
- [Publishing Destinations](#publishing-destinations)
- [Publishing Workflows](#publishing-workflows)
- [Troubleshooting](#troubleshooting)

## Overview

The XVM project produces two main publishable artifacts:
1. **XDK Distribution** (`xdk`) - The complete XTC Language SDK as a ZIP archive
2. **XTC Gradle Plugin** (`plugin`) - Gradle plugin for building XTC projects

Both artifacts support:
- **Snapshot versions** (e.g., `0.4.4-SNAPSHOT`) - Unstable development builds
- **Release versions** (e.g., `0.4.3`) - Stable, immutable releases

## Projects

### XDK Distribution (`xdk`)
- **Artifact**: `org.xtclang:xdk`
- **Type**: ZIP archive containing the complete SDK
- **Includes**: Compiler, runtime, standard libraries, tools
- **Published to**: GitHub Packages, Maven Central

### XTC Gradle Plugin (`plugin`)
- **Artifact**: `org.xtclang:xtc-plugin`
- **Type**: JAR (Gradle plugin)
- **Purpose**: Enables Gradle builds for XTC projects
- **Published to**: GitHub Packages, Maven Central, Gradle Plugin Portal

## Credentials Setup

### GitHub Packages (Default Repository)

**Required for**: Publishing to GitHub Packages repository

#### Option 1: Local Properties
Add to `~/.gradle/gradle.properties`:
```properties
GitHubUsername=your-github-username
GitHubPassword=your-personal-access-token
```

#### Option 2: Environment Variables (CI/CD)
```bash
export GITHUB_ACTOR=your-username
export GITHUB_TOKEN=your-token
```

**Get credentials**: Create a Personal Access Token at https://github.com/settings/tokens with `write:packages` scope

### Maven Central

**Required for**: Publishing to Maven Central (Sonatype)

Add to `~/.gradle/gradle.properties` (keep private!):
```properties
# Maven Central credentials
mavenCentralUsername=your-sonatype-username
mavenCentralPassword=your-sonatype-token

# GPG Signing (required for Maven Central)
# Option 1: File-based signing
signing.keyId=96080E1D
signing.password=your-key-password  # Leave empty if no password
signing.secretKeyRingFile=/Users/you/.gnupg/secring.gpg

# Option 2: In-memory signing (base64-encoded)
# signingInMemoryKey=LS0tLS1CRUdJTi...
```

**Get credentials**:
1. Register at https://central.sonatype.com
2. Create user token (not regular password)
3. Generate GPG key for signing (see GPG section below)

### Gradle Plugin Portal

**Required for**: Publishing plugin to https://plugins.gradle.org

Add to `~/.gradle/gradle.properties`:
```properties
gradle.publish.key=your-api-key
gradle.publish.secret=your-api-secret
```

**Get credentials**: https://plugins.gradle.org → "API Keys" → Generate

### GPG Key Setup (Maven Central Only)

Maven Central requires all artifacts to be signed with GPG.

#### Generate a new key:
```bash
# Generate key without passphrase (for automation)
gpg --batch --generate-key << 'EOF'
Key-Type: RSA
Key-Length: 4096
Name-Real: Your Name
Name-Email: your.email@example.com
Expire-Date: 2y
%no-protection
EOF

# List your keys
gpg --list-secret-keys --keyid-format SHORT

# Export for file-based signing
gpg --export-secret-keys KEYID > ~/.gnupg/secring.gpg

# Or export for in-memory signing (base64)
gpg --export-secret-keys KEYID | base64 > key.txt
```

#### Publish public key (REQUIRED):
```bash
gpg --keyserver keyserver.ubuntu.com --send-keys KEYID
gpg --keyserver keys.openpgp.org --send-keys KEYID
```

## Publishing Destinations

### Configuration Properties

Control where artifacts are published using these properties:

```properties
# In gradle.properties or via -P flag
org.xtclang.publish.GitHub=true          # Default: true
org.xtclang.publish.mavenCentral=false   # Default: false
org.xtclang.publish.gradlePluginPortal=false  # Default: false
```

### Repository Details

| Repository | Snapshots | Releases | Authentication | Notes |
|------------|-----------|----------|----------------|-------|
| **GitHub Packages** | ✅ | ✅ | GitHub Token | Default repository |
| **Maven Central** | ✅ (OSSRH) | ✅ | Sonatype + GPG | Requires signing |
| **Gradle Plugin Portal** | ❌ | ✅ | API Key | Plugin only, no snapshots |

## Publishing Workflows

### Quick Commands

```bash
# Validate credentials before publishing
./gradlew validateCredentials \
  -Porg.xtclang.publish.mavenCentral=true

# Publish SNAPSHOT to GitHub (default)
./gradlew publishRemote

# Publish RELEASE to Maven Central (staged)
./gradlew publishRemote \
  -Porg.xtclang.publish.mavenCentral=true \
  -Porg.xtclang.publish.GitHub=false \
  -Pversion=0.4.3

# Publish and immediately release to Maven Central
./gradlew publishAndReleaseToMavenCentral \
  -Porg.xtclang.publish.mavenCentral=true \
  -Pversion=0.4.3

# Drop/cancel staged deployment
./gradlew xdk:dropMavenCentralDeployment \
  --deployment-id=<deployment-id>

# Publish to local Maven repository (testing)
./gradlew publishLocal
```

### Publishing Snapshots

Snapshots are development builds that can be updated/overwritten.

#### To GitHub Packages (Default)
```bash
# Current version in version.properties will be used (e.g., 0.4.4-SNAPSHOT)
./gradlew publishRemote
```

#### To Maven Central Snapshot Repository
```bash
./gradlew publishRemote \
  -Porg.xtclang.publish.mavenCentral=true \
  -Porg.xtclang.publish.GitHub=false
```

### Publishing Releases

Releases are immutable - once published, they cannot be changed!

#### Step 1: Prepare Release Version
```bash
# Override snapshot version with release version
./gradlew build -Pversion=0.4.3
```

#### Step 2: Publish to Staging

##### To GitHub Packages
```bash
./gradlew publishRemote \
  -Pversion=0.4.3
```

##### To Maven Central (Staged for Review)
```bash
./gradlew publishToMavenCentral \
  -Porg.xtclang.publish.mavenCentral=true \
  -Pversion=0.4.3
```

This uploads to staging area for review before release.

#### Step 3: Release from Staging (Maven Central)

##### Option A: Manual Release (Recommended)
1. Log into https://central.sonatype.com
2. Find deployment in "Deployments" section
3. Review artifacts
4. Click "Publish" to release

##### Option B: Automatic Release
```bash
# Bypasses staging - publishes immediately!
./gradlew publishAndReleaseToMavenCentral \
  -Porg.xtclang.publish.mavenCentral=true \
  -Pversion=0.4.3
```

⚠️ **Warning**: Automatic release is irreversible!

##### Option C: Drop/Cancel Staged Deployment
If you need to cancel a staged deployment (before release):
```bash
# First, get the deployment ID from https://central.sonatype.com
# Then drop it using the --deployment-id flag (NOT -P):
./gradlew xdk:dropMavenCentralDeployment \
  --deployment-id=<deployment-id-from-portal>

# Or for the plugin:
./gradlew plugin:dropMavenCentralDeployment \
  --deployment-id=<deployment-id-from-portal>
```

⚠️ **Important**: Use `--deployment-id=xxx` (task option), not `-PdeploymentId=xxx` (property)

This removes the staged artifacts without releasing them.

#### Step 4: Publish Plugin to Gradle Portal
```bash
./gradlew plugin:publishPlugins \
  -Porg.xtclang.publish.gradlePluginPortal=true \
  -Pversion=0.4.3
```

### Managing Staged Deployments (Maven Central)

When you publish to Maven Central with staging (default behavior), you have several options:

#### Viewing Staged Deployments
1. Log into https://central.sonatype.com
2. Navigate to "Deployments" section
3. You'll see list of staged deployments with:
   - Deployment ID
   - Upload date
   - Artifact details
   - Status

#### Releasing Staged Deployment
```bash
# Option 1: Through web portal (recommended)
# Review artifacts at https://central.sonatype.com, then click "Publish"

# Option 2: Skip staging entirely (use with caution!)
./gradlew publishAndReleaseToMavenCentral \
  -Porg.xtclang.publish.mavenCentral=true \
  -Pversion=0.4.3
```

#### Dropping (Canceling) Staged Deployment
If you discover issues after staging but before release:

```bash
# Get deployment ID from https://central.sonatype.com
# Then drop the deployment using --deployment-id flag:
./gradlew xdk:dropMavenCentralDeployment \
  --deployment-id=abc123def456

# Or drop plugin deployment:
./gradlew plugin:dropMavenCentralDeployment \
  --deployment-id=abc123def456
```

⚠️ **Important**: The Vanniktech plugin uses `--deployment-id` as a task option, NOT `-PdeploymentId`

**Common reasons to drop a deployment:**
- Found bugs after staging
- Wrong version number
- Missing or incorrect artifacts
- Failed validation checks
- Need to update documentation

**Note**: You can re-publish with the same version after dropping (unlike after releasing, which makes the version permanent).

### Complete Release Example

Publishing version 0.4.3 to all repositories:

```bash
# 1. Validate all credentials
./gradlew validateCredentials \
  -Porg.xtclang.publish.mavenCentral=true \
  -Porg.xtclang.publish.gradlePluginPortal=true

# 2. Build everything with release version
./gradlew clean build -Pversion=0.4.3

# 3. Publish to GitHub Packages
./gradlew publishRemote -Pversion=0.4.3

# 4. Publish to Maven Central (staged)
./gradlew publishToMavenCentral \
  -Porg.xtclang.publish.mavenCentral=true \
  -Porg.xtclang.publish.GitHub=false \
  -Pversion=0.4.3

# 5. Manually release from https://central.sonatype.com

# 6. Publish plugin to Gradle Portal
./gradlew plugin:publishPlugins \
  -Porg.xtclang.publish.gradlePluginPortal=true \
  -Pversion=0.4.3
```

## Task Reference

### Global Tasks

| Task | Description |
|------|-------------|
| `publishRemote` | Publishes all artifacts to enabled remote repositories |
| `publishLocal` | Publishes to local Maven repository (~/.m2/repository) |
| `validateCredentials` | Validates credentials for enabled repositories |

### XDK-Specific Tasks

| Task | Description |
|------|-------------|
| `xdk:publishToMavenCentral` | Publishes XDK to Maven Central staging (safe, requires manual release) |
| `xdk:publishAndReleaseToMavenCentral` | Publishes XDK and auto-releases (skips staging, immediate release!) |
| `xdk:publishAllPublicationsToGitHubRepository` | Publishes XDK to GitHub |
| `xdk:dropMavenCentralDeployment` | Cancels/removes staged deployment (requires --deployment-id=xxx) |

### Plugin-Specific Tasks

| Task | Description |
|------|-------------|
| `plugin:publishToMavenCentral` | Publishes plugin to Maven Central staging (safe, requires manual release) |
| `plugin:publishAndReleaseToMavenCentral` | Publishes plugin and auto-releases (skips staging, immediate release!) |
| `plugin:publishPlugins` | Publishes to Gradle Plugin Portal (releases only, no snapshots) |
| `plugin:publishAllPublicationsToGitHubRepository` | Publishes plugin to GitHub |
| `plugin:dropMavenCentralDeployment` | Cancels/removes staged deployment (requires --deployment-id=xxx) |

## Version Management

### Version Sources (in priority order)
1. Command line: `-Pversion=0.4.3`
2. Environment: `ORG_GRADLE_PROJECT_version=0.4.3`
3. Default: `version.properties` file

### Version Formats
- **Snapshot**: `0.4.4-SNAPSHOT` (development, mutable)
- **Release**: `0.4.3` (stable, immutable)
- **Suffix**: `0.4.3+suffix` (custom builds)

## Testing Publications

### Local Testing
```bash
# Publish to local Maven repository
./gradlew publishLocal -Pversion=0.4.3-test

# Verify artifacts
ls ~/.m2/repository/org/xtclang/xdk/0.4.3-test/
ls ~/.m2/repository/org/xtclang/xtc-plugin/0.4.3-test/
```

### Staging Testing (Maven Central)
```bash
# Publish to staging
./gradlew publishToMavenCentral \
  -Porg.xtclang.publish.mavenCentral=true \
  -Pversion=0.4.3-rc1

# Test from staging repository before release
# Then either release or drop from web portal
```

## Troubleshooting

### Common Issues

#### "GitHub credentials not available"
- Check `GitHubUsername` and `GitHubPassword` in `~/.gradle/gradle.properties`
- For CI: Ensure `GITHUB_ACTOR` and `GITHUB_TOKEN` environment variables are set

#### "Could not read PGP secret key"
- Check GPG key format in `signing.secretKeyRingFile` or `signingInMemoryKey`
- Ensure key was exported correctly
- Verify no passphrase if using automation

#### "Received status code 401 from server: Unauthorized"
- Maven Central: Check `mavenCentralUsername` and `mavenCentralPassword`
- GitHub: Token may be expired or lack `write:packages` permission
- Gradle Portal: Verify API key and secret

#### "Cannot publish to Gradle Plugin Portal snapshot repository"
- The Gradle Plugin Portal doesn't accept SNAPSHOT versions
- Only publish release versions to Plugin Portal

#### "Version already exists"
- Release versions are immutable on Maven Central and Plugin Portal
- Use a new version number or publish to GitHub (allows overwrites)

### Validation Commands

```bash
# Check current configuration
./gradlew properties | grep publish

# Validate credentials for specific repository
./gradlew validateCredentials \
  -Porg.xtclang.publish.mavenCentral=true

# Dry run to see what would be published
./gradlew publishToMavenCentral --dry-run \
  -Porg.xtclang.publish.mavenCentral=true \
  -Pversion=0.4.3

# List all publishing tasks
./gradlew tasks --group=publishing
```

## Security Best Practices

1. **Never commit credentials** to the repository
2. Keep `~/.gradle/gradle.properties` with restrictive permissions (`600`)
3. Use environment variables for CI/CD
4. Rotate tokens periodically
5. Use GPG keys without passphrase only for automation
6. Always test with staging/snapshot before releasing
7. Review staged artifacts before releasing to Maven Central

## Additional Resources

- [GitHub Packages Documentation](https://docs.github.com/en/packages)
- [Maven Central Guide](https://central.sonatype.org/publish/publish-guide/)
- [Gradle Plugin Portal](https://plugins.gradle.org/docs/publish-plugin)
- [Vanniktech Publish Plugin](https://github.com/vanniktech/gradle-maven-publish-plugin)
- [GPG Signing Guide](https://central.sonatype.org/publish/requirements/gpg/)