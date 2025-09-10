# XVM GitHub Workflows and Actions

> **📌 Looking for the main project documentation?**  
# **👉 [GO TO MAIN PROJECT README](../README.md)** 

> **⚠️ This file contains GitHub Actions documentation only**  
> **👉 [Main Ecstasy Project README](../README.md)** - Language overview, quickstart, and installation  
> **👉 [Docker Documentation](../docker/README.md)** - Container development and usage  
> 
> This page contains GitHub Actions and CI/CD documentation only.

This directory contains GitHub-specific configuration files, workflows, and automation for the XVM project.

## CI/CD Pipeline

### Overview

The XVM project uses a comprehensive CI/CD pipeline defined in [`workflows/ci.yml`](workflows/ci.yml) that handles building, testing, packaging, and publishing across multiple platforms. The pipeline is designed to ensure code quality, platform compatibility, and automated deployment.

### Workflow Structure

The CI/CD pipeline consists of 8 main jobs that run in sequence and parallel where appropriate:

1. **build-verify** - Core build and test execution (matrix: Ubuntu + Windows)
2. **publish-maven-artifact-snapshot** - Publishes Maven artifact snapshots to GitHub Packages
3. **compute-docker-tags** - Calculates Docker image tags and metadata
4. **docker-build** - Builds multi-platform Docker images
5. **docker-manifest** - Creates and publishes Docker manifests
6. **docker-test** - Tests the built Docker images
7. **docker-cleanup** - Cleans up old Docker packages and images
8. **update-homebrew-tap** - Updates the Homebrew tap with new releases
 c
### Trigger Conditions

The pipeline runs on:
- **Push to any branch** - Full pipeline execution
- **Manual workflow dispatch** - With extensive configuration options

### Manual Workflow Optionsn a m

The workflow provides comprehensive manual control through workflow dispatch inputs:

```yaml
# Publishing Control
snapshot-maven: true/false    # Force Maven artifact snapshot publishing
docker-image: true/false  # Force Docker image builds
homebrew: true/false # Force Homebrew tap updates
master-semantics: true/false                   # Run branch as if it were master

# Platform Control
platforms: all|ubuntu-latest|windows-latest  # Control build matrix

# Testing Control
test: true/false           # Enable/disable manual tests
parallel-test: true/false # Run manual tests in parallel

# Build Control
extra-gradle-options: "string"        # Additional Gradle options
```

### Build Matrix

The **build-verify** job runs on a configurable matrix:
- **Default**: Ubuntu Latest + Windows Latest
- **Configurable**: Single platform or full matrix via `single_platform` input
- **Java Version**: Dynamically determined from `xdk.properties`
- **Gradle Version**: 8.14.2 (defined in environment)

### Environment Configuration

Key environment variables and settings:

```yaml
# Build Configuration
gradle_options: "-Dorg.gradle.caching.debug=false -Dorg.gradle.vfs.verbose=false -Dorg.gradle.jvmargs='-Xmx4g -XX:+UseG1GC' --stacktrace --warning-mode=all --console=plain"
java_distribution: temurin
gradle_version: 8.14.2

# Project Configuration  
ORG_GRADLE_PROJECT_includeBuildManualTests: true
ORG_GRADLE_PROJECT_includeBuildAttachManualTests: true
ORG_GRADLE_PROJECT_xtcPluginOverrideVerboseLogging: true
```

### Custom Actions

The pipeline uses several custom actions located in `.github/actions/`:

- **setup-xvm-build** - Sets up Java, Gradle, and caching for XVM builds
- **get-java-version** - Extracts Java version from XDK properties
- **get-xdk-version** - Determines XDK version for releases
- **compute-docker-tags** - Calculates Docker image tags and metadata
- **create-snapshot-release** - Creates GitHub releases for snapshots
- **update-homebrew-tap** - Updates the Homebrew formula

### Dependencies and Caching

The pipeline employs aggressive caching strategies:
- **Gradle Build Cache** - Shared across jobs and runs
- **Gradle Wrapper Cache** - Cached per Gradle version
- **Java Installation Cache** - Cached per Java version and distribution
- **Docker Layer Cache** - For faster Docker builds

### Secret Management

Required secrets for full functionality:

```yaml
# Publishing
GRADLE_PUBLISH_KEY: Gradle Plugin Portal publish key
GRADLE_PUBLISH_SECRET: Gradle Plugin Portal publish secret
GPG_SIGNING_KEY: GPG key for artifact signing
GPG_SIGNING_PASSWORD: GPG key password
MAVEN_CENTRAL_USERNAME: Maven Central/Sonatype username
MAVEN_CENTRAL_PASSWORD: Maven Central/Sonatype password

# GitHub (automatically provided)
GITHUB_TOKEN: GitHub Actions token for package publishing
```

### Conditional Execution Logic

Jobs run conditionally based on:
- **Branch name** (master branch gets full publishing)
- **Manual dispatch inputs** (override defaults)
- **Success of dependent jobs** (fail-fast where appropriate)
- **File changes** (future enhancement opportunity)

### Manual Testing

The pipeline includes comprehensive manual test execution:
- **Default**: Manual tests run on all builds
- **Parallel Mode**: Optional parallel execution for faster feedback
- **Configurable**: Can be disabled via workflow dispatch

### Docker Multi-Platform Builds

Docker builds support multiple architectures:
- **Platforms**: linux/amd64, linux/arm64
- **Base Images**: Configurable via build arguments
- **Registry**: GitHub Container Registry (ghcr.io/xtclang/xvm)
- **Cleanup**: Automated cleanup of old images and packages

### Using the CI/CD Pipeline

#### Running a Standard Build
```bash
# Push to trigger automatic build
git push origin your-branch

# Manual trigger with defaults
gh workflow run ci.yml
```

#### Parameter Syntax Options

GitHub CLI provides two ways to pass workflow inputs:

- **`-f`** or **`--raw-field`**: Simple key=value format (recommended for most cases)
- **`-F`** or **`--field`**: Advanced format that respects @ syntax for file input/JSON processing

**For workflow dispatch inputs, both work identically:**
```bash
# These are equivalent:
gh workflow run ci.yml -f platforms=ubuntu-latest
gh workflow run ci.yml --raw-field platforms=ubuntu-latest  
gh workflow run ci.yml -F platforms=ubuntu-latest
gh workflow run ci.yml --field platforms=ubuntu-latest
```

**Use `-f` for simplicity** unless you need advanced features like reading values from files.

#### Manual Control Examples
```bash
# Test only on Ubuntu (faster iteration)
gh workflow run ci.yml -f platforms=ubuntu-latest

# Force publish Maven artifact snapshot from feature branch
gh workflow run ci.yml -f snapshot-maven=true

# Run as if master branch (enables all publishing)
gh workflow run ci.yml -f master-semantics=true

# Build with extra Gradle options
gh workflow run ci.yml -f extra-gradle-options="--debug --scan"

# Disable manual tests for faster builds
gh workflow run ci.yml -f test=false
```

#### Monitoring Builds
```bash
# List recent workflow runs
gh run list --workflow=ci.yml

# Watch a specific run
gh run watch <run-id>

# View logs for failed jobs
gh run view <run-id> --log-failed
```

### Future Improvements and Simplifications

#### Immediate Improvements Needed
1. **Gradle Clean Issues** - The composite build structure creates task interference requiring careful clean separation
2. **Cache Efficiency** - Gradle cache hit rates could be improved with better cache key strategies
3. **Windows Build Reliability** - Windows builds occasionally fail due to file locking issues
4. **Docker Build Speed** - Multi-platform builds are slow; layer caching needs optimization

#### Simplification Opportunities
1. **Reduce Manual Dispatch Options** - Too many manual options create complexity; consolidate common patterns
2. **Job Consolidation** - Some jobs could be merged to reduce orchestration complexity
3. **Environment Variable Cleanup** - Many environment variables could be defaults or computed
4. **Custom Actions** - Some custom actions could be replaced with standard marketplace actions

#### Architecture Improvements
1. **Matrix Strategy Enhancement** - Dynamic matrix generation based on changed files
2. **Conditional Job Execution** - Skip unnecessary jobs based on file changes (paths filtering)
3. **Parallel Test Execution** - Better parallel test strategies to reduce total build time
4. **Artifact Management** - Improved artifact retention and cleanup policies

#### Developer Experience Improvements
1. **Build Status Dashboard** - Better visibility into build health and trends  
2. **Local Development Parity** - Ensure local builds match CI exactly
3. **Faster Feedback Loops** - Fail-fast strategies for common issues
4. **Documentation Integration** - Auto-generated documentation from successful builds

#### Technical Debt
1. **Secret Rotation** - Automated secret rotation for publishing credentials
2. **Workflow Versioning** - Better versioning strategy for workflow changes
3. **Monitoring and Alerting** - Proactive monitoring of build infrastructure health
4. **Cost Optimization** - Analysis and optimization of GitHub Actions usage costs

## Dependabot

### Overview

Dependabot automatically monitors and updates dependencies across multiple package ecosystems in this project. It creates pull requests with dependency updates to help maintain security and keep dependencies current.

### Configuration

The Dependabot configuration is defined in [`dependabot.yml`](dependabot.yml) and monitors:

- **Gradle dependencies** - Java/Kotlin dependencies in build.gradle.kts files
- **GitHub Actions** - Workflow and action version updates
- **Docker images** - Base image updates in Dockerfiles

### Schedule

All dependency checks run weekly on **Saturdays at 06:00 UTC** to minimize disruption during the work week.

### Team Assignments

Pull requests are automatically assigned to the `xtclang/maintainers` team:
- **lagergren** - Project maintainer
- **cpurdy** - Project maintainer  
- **ggleyzer** - Project maintainer

Team members receive GitHub notifications when Dependabot creates PRs.

### How It Works

1. **Weekly Scans**: Dependabot scans for dependency updates every Saturday
2. **PR Creation**: Creates pull requests for available updates
3. **Review Assignment**: Automatically assigns PRs to the maintainers team
4. **Commit Formatting**: Uses standardized commit message prefixes:
   - `deps:` for Gradle dependencies
   - `ci:` for GitHub Actions
   - `docker:` for Docker image updates

### Temporarily Disabling Checks

To temporarily disable a specific ecosystem:

1. Comment out the entire block in `dependabot.yml`:
```yaml
# Temporarily disabled - Docker updates
# - package-ecosystem: "docker"
#   directory: "/docker"
#   schedule:
#     interval: "weekly"
```

2. Or set `open-pull-requests-limit: 0` to prevent new PRs

### Manual Dependency Checks

Since Dependabot runs only on GitHub, you can run equivalent checks locally to preview what Dependabot would find:

#### Gradle Dependencies (Manual Equivalents)
```bash
# Check for all dependency updates (equivalent to Dependabot's Gradle scan)
./gradlew dependencyUpdates

# Check for security vulnerabilities in dependencies
./gradlew dependencyCheckAnalyze  # Requires OWASP dependency-check plugin

# Analyze specific dependencies for versions and conflicts
./gradlew dependencyInsight --dependency <dependency-name>

# View complete dependency tree
./gradlew dependencies

# Check for dependency conflicts
./gradlew dependencies --configuration runtimeClasspath

# List outdated plugins
./gradlew dependencyUpdates --revision=release
```

#### GitHub Actions (Manual Equivalents)
```bash
# Check for action updates in workflows
find .github/workflows -name "*.yml" -exec grep -H "uses:" {} \;

# Verify action versions against latest releases
gh api repos/actions/checkout/releases/latest --jq '.tag_name'
gh api repos/actions/setup-java/releases/latest --jq '.tag_name'
gh api repos/gradle/actions/releases/latest --jq '.tag_name'

# Check all action versions in workflows
grep -r "uses:" .github/workflows/ | sed 's/.*uses: //' | sort -u
```

#### Docker Images (Manual Equivalents)
```bash
# Check for newer base image versions
docker pull ubuntu:latest  # For images using ubuntu
docker images --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}"

# Check specific image in Dockerfile
grep "^FROM" docker/Dockerfile

# Check for security vulnerabilities in images
docker scout cves docker/Dockerfile  # If Docker Scout is available

# Check image layer information
docker history <image-name>
```

#### Security Vulnerability Scanning
```bash
# Check for known vulnerabilities via GitHub API
gh api repos/xtclang/xvm/vulnerability-alerts --jq '.[] | {package: .security_vulnerability.package.name, severity: .security_advisory.severity}'

# Check dependency graph for the repository
gh api repos/xtclang/xvm/dependency-graph/snapshots --jq '.snapshots[0].manifests'

# Compare dependencies between commits (what would be flagged)
gh api repos/xtclang/xvm/dependency-graph/compare/HEAD~10...HEAD --jq '.[] | select(.vulnerabilities != []) | {name: .package.name, vulnerabilities: .vulnerabilities}'

# Use GitHub's advisory database directly
gh api graphql -f query='
query($owner: String!, $name: String!) {
  repository(owner: $owner, name: $name) {
    vulnerabilityAlerts(first: 100) {
      nodes {
        securityAdvisory {
          summary
          severity
        }
        securityVulnerability {
          package {
            name
            ecosystem
          }
        }
      }
    }
  }
}' -f owner=xtclang -f name=xvm
```

#### Alternative Tools for Local Scanning
```bash
# Use Renovate for comprehensive dependency scanning
npm install -g renovate
renovate --dry-run --print-config

# Use community Dependabot CLI (limited functionality)
npm install -g @dependabot/cli
dependabot update --dry-run --debug

# Use Snyk for security scanning
npm install -g snyk
snyk test  # For security vulnerabilities
snyk monitor  # For ongoing monitoring
```

#### Triggering Dependabot Checks on GitHub
```bash
# Trigger Dependabot security updates manually
gh api repos/xtclang/xvm/dependabot/alerts -X POST

# Check Dependabot configuration status
gh api repos/xtclang/xvm/dependabot/secrets

# View current Dependabot alerts
gh api repos/xtclang/xvm/dependabot/alerts --jq '.[] | {number: .number, package: .security_vulnerability.package.name, severity: .security_advisory.severity}'

# Check if Dependabot is enabled for the repository
gh api repos/xtclang/xvm --jq '.security_and_analysis.dependabot_security_updates.status'

# Force refresh dependency graph (triggers Dependabot analysis)
gh api repos/xtclang/xvm/dependency-graph/snapshots -X POST -f "ref=refs/heads/$(git branch --show-current)"

# Manually trigger Dependabot version updates (if workflow exists)
gh workflow run dependabot-auto-merge.yml  # If you have auto-merge workflow

# Check Dependabot pull requests specifically  
gh pr list --author "app/dependabot" --json number,title,headRefName
```

#### Complete Manual Audit Workflow
```bash
# 1. Check Gradle dependencies
./gradlew dependencyUpdates

# 2. Check GitHub Actions
grep -r "uses:" .github/workflows/ | sed 's/.*uses: //' | sort -u

# 3. Check Docker images  
grep "^FROM" docker/Dockerfile

# 4. Check for security issues
gh api repos/xtclang/xvm/vulnerability-alerts

# 5. Trigger Dependabot check on GitHub
gh api repos/xtclang/xvm/dependency-graph/snapshots -X POST -f "ref=refs/heads/$(git branch --show-current)"

# 6. Generate dependency report
./gradlew dependencies > dependency-report.txt
```

### Pull Request Limits

- **Gradle**: Maximum 5 open PRs
- **GitHub Actions**: Maximum 5 open PRs  
- **Docker**: Maximum 5 open PRs

This prevents overwhelming the review queue while ensuring important updates aren't missed.

### Repository Settings

Dependabot can also be managed via repository settings:
- Settings → Code security and analysis → Dependabot version updates
- Toggle entire ecosystems on/off
- View security advisories and alerts

### Workflow Integration

Dependabot works alongside the existing CI pipeline defined in [`workflows/ci.yml`](workflows/ci.yml):
- All Dependabot PRs trigger the full CI suite
- Tests must pass before merging dependency updates
- Gradle build validation ensures compatibility

### Best Practices

1. **Review PRs promptly** - Security updates should be prioritized
2. **Test thoroughly** - Dependency updates can introduce breaking changes
3. **Group related updates** - Consider bundling minor updates when practical
4. **Monitor for conflicts** - Watch for dependency version conflicts
5. **Keep configuration updated** - Adjust limits and schedules as needed