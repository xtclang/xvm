# GitHub Workflows Documentation

This document describes the XVM project's CI/CD pipeline, workflow architecture, and how to use each workflow.

## Table of Contents

1. [Pipeline Overview](#pipeline-overview)
2. [Architecture: Build Artifacts vs Releases](#architecture-build-artifacts-vs-releases)
3. [Master Push Flow](#master-push-flow)
4. [Workflows Reference](#workflows-reference)
5. [Actions Reference](#actions-reference)
6. [Testing Publishing on Non-Master Branches](#testing-publishing-on-non-master-branches)
7. [Manual Testing](#manual-testing)
8. [Version Gating](#version-gating)
9. [Troubleshooting](#troubleshooting)

---

## Pipeline Overview

The XVM CI/CD pipeline follows a clear separation between internal build artifacts and external releases:

```
┌─────────────────────────────────────────────────────────────────┐
│                     PUSH TO MASTER                               │
│                  (version.properties = X.Y.Z-SNAPSHOT)           │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  commit.yml (VerifyCommit)                                          │
│  ├─ Build XDK                                                   │
│  ├─ Run tests (including manual tests)                          │
│  └─ Upload artifact: xdk-dist-{COMMIT}                          │
│     • Temporary (10 days)                                       │
│     • Contains: xdk-{VERSION}.zip                               │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                ┌───────────┴───────────┐
                │  workflow_run trigger │
                │  (waits for CI)       │
                └───────────┬───────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────────┐
│publish-docker│   │homebrew      │   │publish-snapshot  │
│.yml          │   │-update.yml   │   │.yml              │
├──────────────┤   ├──────────────┤   ├──────────────────┤
│Download      │   │Download      │   │Download          │
│artifact by   │   │artifact by   │   │artifact by       │
│commit        │   │commit        │   │commit            │
│              │   │              │   │                  │
│Build Docker  │   │Update brew   │   │Publish Maven     │
│images        │   │formula       │   │snapshots         │
│              │   │              │   │                  │
│Push to GHCR  │   │Push to tap   │   │Publish GitHub    │
│              │   │              │   │Release           │
└──────────────┘   └──────────────┘   └──────────────────┘
     multi-arch         xdk-latest         xdk-snapshots
     amd64/arm64        .rb formula        .zip release
```

### Flow Summary

1. **CI Build** (`commit.yml`) runs on every push to any branch
   - Builds, tests, uploads temporary build artifact
   - Artifact named: `xdk-dist-{40-char-commit-hash}`

2. **Automatic Triggers** (master only) wait for CI to complete:
   - `publish-docker.yml` - Builds multi-platform Docker images
   - `homebrew-update.yml` - Updates Homebrew tap formula
   - `publish-snapshot.yml` - Publishes Maven + GitHub snapshot release

3. **Manual Release** (`publish-release.yml`) triggered manually:
   - Only for non-SNAPSHOT versions
   - Creates draft GitHub release
   - Publishes to Maven Central staging
   - Optionally publishes to Gradle Plugin Portal

---

## Architecture: Build Artifacts vs Releases

### Build Artifacts (Internal Communication)

**Purpose**: Pass exact builds between workflows

**Storage**: GitHub Actions artifacts (10-day retention)

**Naming**: `xdk-dist-{COMMIT}`
- Artifact identifier includes full 40-character commit hash
- File inside artifact: `xdk-{VERSION}.zip` (from distZip task)

**Download**: Using `actions/download-artifact@v4` with `run-id`

**Example**:
```yaml
- name: Download XDK build artifact
  uses: actions/download-artifact@v4
  with:
      name: xdk-dist-abc123def456...  # Full commit hash
      path: ./artifacts
      repository: ${{ github.repository }}
      run-id: ${{ github.event.workflow_run.id }}
```

### GitHub Releases (External Publication)

**Purpose**: Permanent, public distribution

**Storage**: GitHub Releases (permanent)

**Naming**: `xdk-{VERSION}.zip` (version-based, no commit hash)

**Types**:
- **Snapshot**: Overwrites single file in `xdk-snapshots` prerelease
- **Release**: Creates new tagged release `v{VERSION}` as DRAFT

**Download**: Public HTTPS URL

**Example URLs**:
- Snapshot: `https://github.com/xtclang/xvm/releases/download/xdk-snapshots/xdk-0.4.4-SNAPSHOT.zip`
- Release: `https://github.com/xtclang/xvm/releases/download/v0.4.4/xdk-0.4.4.zip`

---

## Master Push Flow

### Automatic Execution (SNAPSHOT version)

When you push to master with `version.properties` containing a `-SNAPSHOT` version:

```bash
# Example: version.properties
xdk.version=0.4.4-SNAPSHOT
```

**Sequence**:

1. **commit.yml** starts immediately
   ```
   ├─ Checkout code
   ├─ Setup Java & Gradle
   ├─ Build XDK (clean, check, distZip)
   ├─ Run manual tests (if enabled)
   └─ Upload artifact: xdk-dist-{commit}
   ```

2. **After CI completes successfully**, these run in parallel:

   **publish-docker.yml**:
   ```
   ├─ Download artifact from CI run
   ├─ Build amd64 image → push to GHCR
   ├─ Build arm64 image → push to GHCR
   ├─ Create multi-platform manifests
   ├─ Test images
   └─ Clean up old images (keep 10)
   ```

   **homebrew-update.yml**:
   ```
   ├─ Download artifact from CI run
   ├─ Calculate SHA256 from artifact
   ├─ Generate xdk-latest.rb from template
   ├─ Add dynamic version with timestamp
   ├─ Clone homebrew-xvm tap
   ├─ Commit & push formula update
   └─ Summary
   ```

   **publish-snapshot.yml**:
   ```
   ├─ Validate version contains -SNAPSHOT ✓
   ├─ Download artifact from CI run
   ├─ Publish Maven snapshots to GitHub Packages
   ├─ Clean up old Maven packages (keep 50)
   ├─ Publish GitHub snapshot release (overwrites)
   └─ Summary
   ```

### Manual Execution (any branch)

All workflows support `workflow_dispatch` for manual testing from any branch.

---

## Workflows Reference

### commit.yml (VerifyCommit)

**Trigger**: Every push, every pull request, manual

**Purpose**: Build, test, create build artifact

**Platforms**: Ubuntu (default), Windows (optional via input)

**Key Steps**:
1. Setup XVM project (checkout, versions, Java, Gradle)
2. Build XDK (`clean`, `check`, `distZip`)
3. Run manual tests inline (configurable)
4. Upload artifact: `xdk-dist-{commit}` (Ubuntu only)
5. Generate summary

**Manual Trigger Inputs**:
- `test-publishing`: Trigger publishing workflows after build (default: false)
  - Set `true` to test full publishing pipeline on non-master branches
  - Publishing workflows: snapshot, docker, homebrew
- `platforms`: Run on specific platform(s) or all
  - Options: `all`, `ubuntu-latest`, `windows-latest`
  - Default: `all`
- `extra-gradle-options`: Extra Gradle CLI options
- `test`: Run manual tests (default: true)
- `parallel-test`: Run manual tests in parallel (default: false)

**Manual Test Configuration**:
- Set via workflow inputs when manually triggering
- Inline execution to keep cache hot
- Tasks: `runXtc`, `runOne`, `runTwoTestsInSequence`, `runAllTestTasks`/`runParallel`

**Example Manual Trigger**:
```bash
# Build and test only (no publishing)
gh workflow run commit.yml \
  --ref your-branch \
  -f platforms=ubuntu-latest \
  -f test=true \
  -f parallel-test=false

# Build, test, AND trigger publishing workflows
gh workflow run commit.yml \
  --ref your-branch \
  -f test-publishing=true \
  -f platforms=ubuntu-latest
```

**Artifact Output**:
- Name: `xdk-dist-{40-char-commit}`
- Contains: `xdk-{VERSION}.zip`
- Retention: 10 days
- Size: ~50-100 MB

---

### publish-snapshot.yml (Publish Snapshots)

**Trigger**:
- Automatic: After commit.yml completes on master
- Manual: Any branch via workflow_dispatch

**Purpose**: Publish snapshot artifacts to Maven and GitHub Releases

**Version Requirement**: **MUST** contain `-SNAPSHOT` (validated)

**Key Steps**:
1. Setup XVM project
2. **Validate version contains -SNAPSHOT** (fails if not)
3. Determine commit and run-id
4. Download build artifact from CI run
5. Publish Maven snapshots to GitHub Packages
6. Clean up old Maven packages:
   - `org.xtclang.xdk` (keep 50)
   - `org.xtclang.xtc-plugin` (keep 50)
   - `org.xtclang.xtc-plugin.org.xtclang.xtc-plugin.gradle.plugin` (keep 50)
7. Publish to GitHub snapshot release (overwrites)
8. Generate summary

**GitHub Release Behavior**:
- Release tag: `xdk-snapshots` (prerelease)
- Asset name: `xdk-{VERSION}.zip` (e.g., `xdk-0.4.4-SNAPSHOT.zip`)
- Overwrites previous snapshot (always latest)
- Includes commit SHA in release notes

**Manual Trigger**:
```bash
gh workflow run publish-snapshot.yml --ref master
```

**Note**: Manual triggers from branches cannot download CI artifacts (only builds from master get artifacts). The workflow will publish Maven snapshots but skip GitHub release.

---

### publish-docker.yml (Build Docker Images)

**Trigger**:
- Automatic: After commit.yml completes on master
- Manual: Any branch via workflow_dispatch

**Purpose**: Build and publish multi-platform Docker images

**Platforms**: linux/amd64, linux/arm64

**Key Steps**:
1. **Compute tags** (separate job):
   - Master: `latest`, `{VERSION}`, `{COMMIT}`
   - Branch: `{BRANCH}`, `{COMMIT}`
2. **Build per architecture** (matrix job):
   - Download build artifact from CI run
   - Copy to Docker context
   - Build image for platform
   - Push to GHCR with architecture-specific tags
3. **Create manifests** (combines architectures):
   - For each base tag, create multi-platform manifest
   - Links `{tag}-amd64` and `{tag}-arm64`
4. **Test images**:
   - Run `xec --version`, `xcc --version`, `xtc --version`
5. **Clean up** (optional):
   - Delete old Docker package versions (keep 10)

**Manual Trigger Inputs**:
- `skip-tests`: Skip Docker image tests (default: false)
- `cleanup`: Run cleanup after build (default: true)

**Example Manual Trigger**:
```bash
gh workflow run publish-docker.yml \
  --ref master \
  -f skip-tests=false \
  -f cleanup=true
```

**Published Images**:
- Registry: `ghcr.io/xtclang/xvm`
- Tags (master):
  - `ghcr.io/xtclang/xvm:latest`
  - `ghcr.io/xtclang/xvm:0.4.4-SNAPSHOT`
  - `ghcr.io/xtclang/xvm:abc123def...`
- Tags (branch):
  - `ghcr.io/xtclang/xvm:branch-name`
  - `ghcr.io/xtclang/xvm:abc123def...`

**Usage**:
```bash
docker pull ghcr.io/xtclang/xvm:latest
docker run --rm ghcr.io/xtclang/xvm:latest xec --version
```

---

### homebrew-update.yml (Update Homebrew)

**Trigger**:
- Automatic: After commit.yml completes on master
- Manual: Any branch via workflow_dispatch

**Purpose**: Update Homebrew tap with latest snapshot

**Formula**: `xdk-latest.rb` (class `XdkLatest`)

**Key Steps**:
1. Setup XVM project (metadata only, no build)
2. Determine XDK version (from version.properties or input)
3. Determine commit and run-id
4. Download build artifact from CI run
5. Calculate SHA256 from artifact
6. Build release URL pointing to GitHub snapshot release
7. Generate dynamic version with timestamp:
   - Format: `{VERSION}.{YYYYMMDDHHMMSS}`
   - Example: `0.4.4-SNAPSHOT.20250413120530`
   - Ensures `brew upgrade` works correctly
8. Clone homebrew-xvm tap repository
9. Copy template `.github/scripts/xdk-latest.rb.template`
10. Replace placeholders with `sed`:
    - `{{RELEASE_URL}}` → GitHub Release URL
    - `{{DYNAMIC_VERSION}}` → Timestamped version
    - `{{SHA256}}` → Calculated hash
    - `{{JAVA_VERSION}}` → Java version from version.properties
11. Commit and push to homebrew-xvm repo
12. Generate summary

**Manual Trigger Inputs**:
- `xdk-version`: Override version (default: use version.properties)

**Example Manual Trigger**:
```bash
gh workflow run homebrew-update.yml --ref master
```

**Homebrew Tap Repository**: `github.com/xtclang/homebrew-xvm`

**User Installation**:
```bash
brew tap xtclang/xvm
brew install xdk-latest
```

**Dependencies**: `openjdk@{version}` (from version.properties)

---

### publish-release.yml (Publish Release)

**Trigger**: Manual only (never automatic)

**Purpose**: Publish XDK releases to multiple repositories

**Version Requirement**: **MUST NOT** contain `-SNAPSHOT` (validated)

**Destinations**:
- GitHub Releases (DRAFT, requires manual publish)
- Maven Central (staging, requires manual release via Sonatype)
- Gradle Plugin Portal (optional, immediate publication)

**Key Steps**:

1. **Build and Test**:
   - Setup XVM project
   - Validate version does NOT contain `-SNAPSHOT`
   - Validate semantic versioning format
   - Build XDK with optional version override
   - Run tests (optional via input)
   - Upload artifact: `xdk-release-{version}`

2. **Publish GitHub Draft**:
   - Download artifact from build job
   - Create DRAFT GitHub release using `publish-github-release` action
   - Tag: `v{VERSION}` (e.g., `v0.4.4`)
   - Includes TODO template for release notes

3. **Publish Maven Staging**:
   - Setup XVM project with build cache
   - Publish to Maven Central staging repository
   - Requires manual steps in Sonatype Nexus:
     - Review staged artifacts
     - Close staging repository
     - Release to Maven Central (syncs in 10-30 min)

4. **Publish Gradle Plugin Portal** (optional):
   - Only if input enabled
   - Immediate publication (no staging available)
   - Cannot be undone

5. **Summary**:
   - GitHub: Created as DRAFT ✓
   - Maven: Published to STAGING ✓
   - Gradle: Published or Skipped
   - Manual steps required for completion

**Manual Trigger Inputs**:
- `version-override`: Override version from version.properties
  - Must NOT contain `-SNAPSHOT`
  - Must be semantic version (e.g., `0.4.4`)
- `skip-tests`: Skip test execution (default: false)
- `publish-to-gradle-plugin-portal`: Publish to Gradle Plugin Portal (default: false)
- `close-maven-staging`: Auto-close Maven staging repo (default: true)

**Example Manual Trigger**:
```bash
gh workflow run publish-release.yml \
  --ref release-branch \
  -f version-override=0.4.4 \
  -f skip-tests=false \
  -f publish-to-gradle-plugin-portal=false \
  -f close-maven-staging=true
```

**Post-Release Manual Steps**:

1. **Review Maven Central Staging**:
   - Log in to https://oss.sonatype.org/
   - Navigate to "Staging Repositories"
   - Find repository `orgxtclang-XXXX`
   - Review staged artifacts
   - Close repository (if not auto-closed)
   - Release to Maven Central

2. **Publish GitHub Release**:
   - Go to https://github.com/xtclang/xvm/releases
   - Find draft release `v{VERSION}`
   - Edit release notes (replace TODOs)
   - Click "Publish release"

3. **Update version.properties**:
   - Bump to next snapshot version
   - Example: `0.4.4` → `0.4.5-SNAPSHOT`
   - Commit and push to master

---

### dependency-updates.yml (Check Dependencies)

**Trigger**: Pull requests to master, manual

**Purpose**: Validate version updates and suggest dependency updates

**Key Steps**:
1. Checkout code
2. Run Gradle dependency checks
3. Comment on PR with suggestions

**Manual Trigger**:
```bash
gh workflow run dependency-updates.yml
```

---

## Actions Reference

### setup-xvm-project

**Purpose**: Complete XVM project setup (checkout, versions, build environment)

**Location**: `.github/actions/setup-xvm-project/action.yml`

**Inputs**:
- `setup-build`: Setup Java/Gradle (default: true)
  - Set `false` for metadata-only jobs
- `cache-read-only`: Gradle cache read-only mode (default: false)
  - Set `true` to reuse cache from CI build
- `checkout-depth`: Git checkout depth (default: 1)
- `enable-debug`: Enable debug logging (default: false)

**Outputs**:
- `java-version`: Java version from version.properties
- `xdk-version`: XDK version (e.g., `0.4.4-SNAPSHOT`)
- `xdk-version-release`: Without `-SNAPSHOT` (e.g., `0.4.4`)
- `xdk-version-next-snapshot`: Patch bumped (e.g., `0.4.5-SNAPSHOT`)
- `gradle-version`: Gradle version from gradle-wrapper.properties
- `java-distribution`: Java distribution (temurin)
- `gradle-options`: Standard Gradle CLI options
- `gradle-jvm-opts`: Standard Gradle JVM options (GRADLE_OPTS)

**Steps**:
1. Fetch sources
2. Extract versions from properties files
3. Compute release and next snapshot versions
4. Compute Kotlin toolchain Java version (main - 1)
5. Setup Java for Kotlin toolchain (if setup-build)
6. Setup Java (main) (if setup-build)
7. Setup Gradle (if setup-build)
8. Validate Gradle wrapper (if setup-build)

**Usage**:
```yaml
- name: Setup XVM Project
  id: versions
  uses: ./.github/actions/setup-xvm-project
  with:
      setup-build: true
      cache-read-only: false
      enable-debug: false
```

---

### publish-github-release

**Purpose**: Publish XDK build artifact to GitHub Release

**Location**: `.github/actions/publish-github-release/action.yml`

**Inputs**:
- `artifact-path`: Path to XDK zip file (build artifact)
- `xdk-version`: XDK version
- `commit`: Commit SHA for metadata
- `repo`: Repository in format `owner/repo`
- `github-token`: GitHub token with contents:write
- `release-type`: `snapshot` (overwrites) or `release` (tagged draft)
- `release-tag`: Override release tag (optional)
  - Defaults: `xdk-snapshots` for snapshot, `v{VERSION}` for release

**Outputs**:
- `release-url`: URL of published release
- `asset-name`: Name of published asset

**Behavior**:

**Snapshot Mode** (`release-type: snapshot`):
- Renames artifact to `xdk-{VERSION}.zip`
- Creates or updates `xdk-snapshots` prerelease
- Overwrites previous snapshot asset
- Includes commit SHA in release notes

**Release Mode** (`release-type: release`):
- Renames artifact to `xdk-{VERSION}.zip`
- Creates new tagged release `v{VERSION}`
- Sets as DRAFT (manual publish required)
- Includes TODO template for release notes
- Sets target commit

**Usage**:
```yaml
- name: Publish to GitHub Release
  uses: ./.github/actions/publish-github-release
  with:
      artifact-path: ./artifacts/xdk-0.4.4-SNAPSHOT.zip
      xdk-version: 0.4.4-SNAPSHOT
      commit: abc123def456...
      repo: ${{ github.repository }}
      github-token: ${{ secrets.GITHUB_TOKEN }}
      release-type: snapshot
```

---

## Testing Publishing on Non-Master Branches

### Overview

To test the complete publishing pipeline (snapshot, Docker, Homebrew) from a non-master branch **without** merging to master, manually dispatch the **VerifyCommit** workflow. When manually triggered, VerifyCommit automatically triggers all three publishing workflows after successful completion.

### Single Command to Test Publishing

```bash
gh workflow run commit.yml --ref your-branch-name -f test-publishing=true
```

This command will:
1. Build and test your branch
2. Upload build artifact
3. Automatically trigger publish-snapshot.yml
4. Automatically trigger publish-docker.yml
5. Automatically trigger homebrew-update.yml

**Without** `-f test-publishing=true`, only the build and test run (no publishing).

### How It Works

**Trigger Mechanism**:
- Publishing workflows listen for `workflow_run` events from "VerifyCommit"
- They check: `github.event.workflow_run.inputs.test-publishing == 'true'`
- When VerifyCommit is manually dispatched with `test-publishing=true`, this condition is TRUE
- Therefore, publishing workflows run even on non-master branches

**Automatic vs Manual Triggering**:

| Trigger Type | Branch | test-publishing | Publishing Runs? |
|--------------|--------|-----------------|------------------|
| Push (automatic) | master | N/A | ✅ YES (automatic on master) |
| Push (automatic) | feature-branch | N/A | ❌ NO (branch not master) |
| Manual dispatch | master | false | ❌ NO (flag not set) |
| Manual dispatch | master | true | ✅ YES (flag enabled) |
| Manual dispatch | feature-branch | false | ❌ NO (flag not set) |
| Manual dispatch | feature-branch | true | ✅ YES (flag enabled) |

### Step-by-Step Testing Guide

**1. Make changes to your branch**:
```bash
git checkout -b feature/update-publishing
vim .github/workflows/publish-snapshot.yml
git commit -am "Update snapshot publishing"
git push origin feature/update-publishing
```

**2. Trigger VerifyCommit (builds + triggers publishing)**:
```bash
gh workflow run commit.yml --ref feature/update-publishing -f test-publishing=true
```

**3. Monitor workflow runs**:
```bash
# List recent runs
gh run list --branch feature/update-publishing --limit 10

# Watch specific run
gh run watch
```

**4. Check triggered publishing workflows**:
- Go to Actions tab in GitHub
- Look for these workflows that started after VerifyCommit completed:
  - "Publish Snapshots"
  - "Build Docker Images"
  - "Update Homebrew"

### What Gets Published

When you manually trigger VerifyCommit from a non-master branch:

**✅ Maven Snapshots**: Published to GitHub Packages
- Requires version contains `-SNAPSHOT`
- Safe for testing (snapshots are ephemeral)

**✅ GitHub Release**: Updates `xdk-snapshots` prerelease
- Overwrites previous snapshot
- Safe for testing

**✅ Docker Images**: Published to GHCR
- Tagged with branch name (e.g., `ghcr.io/xtclang/xvm:feature-update-publishing`)
- Tagged with commit SHA
- Safe for testing (branch-specific tags)

**✅ Homebrew Formula**: Updates `homebrew-xvm` tap
- ⚠️  **Creates real commit in tap repo**
- ⚠️  **Affects users running `brew upgrade`**
- Consider if you need to test this

### Optional: Build Without Publishing

```bash
# Just build and test (no publishing)
gh workflow run commit.yml --ref your-branch

# With publishing enabled
gh workflow run commit.yml --ref your-branch -f test-publishing=true

# Skip manual tests (faster CI, no publishing)
gh workflow run commit.yml \
  --ref your-branch \
  -f test=false

# Run only Ubuntu with publishing
gh workflow run commit.yml \
  --ref your-branch \
  -f platforms=ubuntu-latest \
  -f test-publishing=true
```

### Full Example Workflow

```bash
# 1. Create and checkout feature branch
git checkout -b feature/docker-improvements
git push origin feature/docker-improvements

# 2. Make changes
vim .github/workflows/publish-docker.yml
git commit -am "Optimize Docker builds"
git push

# 3. Test the complete pipeline with ONE command (includes publishing)
gh workflow run commit.yml --ref feature/docker-improvements -f test-publishing=true

# 4. Monitor progress (watch until complete)
gh run watch

# 5. Verify all three publishing workflows succeeded
gh run list --branch feature/docker-improvements --limit 10

# 6. If issues found, fix and re-test
vim .github/workflows/publish-docker.yml
git commit -am "Fix Docker build issue"
git push
gh workflow run commit.yml --ref feature/docker-improvements -f test-publishing=true

# 7. Once working, merge to master
gh pr create --title "Optimize Docker builds"
```

### Monitoring Progress in GitHub UI

1. Go to: `https://github.com/xtclang/xvm/actions`
2. Click on the running "VerifyCommit" workflow
3. Wait for it to complete (shows green checkmark)
4. Look for triggered workflows below:
   - **Publish Snapshots** - Check it completed successfully
   - **Build Docker Images** - Verify both amd64 and arm64 built
   - **Update Homebrew** - Confirm formula was updated

### Multiple Test Runs

You can run multiple times on the same commit:
- Each run is isolated (separate artifact namespace via `run-id`)
- No conflicts between runs
- Each publishing workflow downloads from its triggering CI run
- Artifacts retained for 10 days

### Important Notes

**Snapshot Version Required**:
- `publish-snapshot.yml` validates version contains `-SNAPSHOT`
- If your `version.properties` has a non-SNAPSHOT version, snapshot publishing will fail
- Docker and Homebrew will still run successfully

**Real Publishing**:
- This triggers REAL publishing (not a dry-run)
- Maven snapshots → Real GitHub Packages
- Docker images → Real GHCR registry
- Homebrew formula → Real tap repository commit
- GitHub Release → Real `xdk-snapshots` release update

**When NOT to Use This**:
- If you only want to test the **build** (not publishing), just push and let CI run automatically
- If you want to test **release publishing** (non-SNAPSHOT), use `publish-release.yml` directly

### Troubleshooting

**Problem**: Publishing workflows don't trigger
**Solution**: Check VerifyCommit completed successfully. Publishing only triggers on success.

**Problem**: "Artifact not found" error in publishing workflows
**Solution**: VerifyCommit must complete fully and upload artifact. Check the VerifyCommit run succeeded.

**Problem**: Snapshot publishing fails with "not a SNAPSHOT version"
**Solution**: Your `version.properties` must contain `-SNAPSHOT`. Update it or skip snapshot testing.

---

## Manual Testing

### Running Manual Tests via CI Workflow

**Method 1: Via GitHub UI**
1. Go to Actions → VerifyCommit workflow
2. Click "Run workflow"
3. Select branch
4. Configure inputs:
   - `platforms`: Choose platform(s)
   - `test`: Enable manual tests (true)
   - `parallel-test`: Run in parallel (false for sequential)
5. Click "Run workflow"

**Method 2: Via GitHub CLI**
```bash
gh workflow run commit.yml \
  --ref your-branch \
  -f platforms=ubuntu-latest \
  -f test=true \
  -f parallel-test=false
```

**Manual Test Tasks**:
- `manualTests:runXtc` - Run XTC compiler
- `manualTests:runOne -PtestName=TestMisc` - Run single test
- `manualTests:runTwoTestsInSequence` - Run two tests
- `manualTests:runAllTestTasks` - Run all tests sequentially
- `manualTests:runParallel` - Run all tests in parallel

**Local Execution**:
```bash
# Run all tests sequentially
./gradlew manualTests:runAllTestTasks

# Run tests in parallel
./gradlew manualTests:runParallel

# Run single test
./gradlew manualTests:runOne -PtestName=TestMisc

# Run XTC compiler
./gradlew manualTests:runXtc
```

**Environment Configuration**:
```groovy
// In settings.gradle.kts or gradle.properties
org.gradle.project.includeBuildManualTests=true
org.gradle.project.includeBuildAttachManualTests=true
```

---

## Version Gating

### Snapshot Workflow Validation

**publish-snapshot.yml** validates that version contains `-SNAPSHOT`:

```yaml
- name: Validate snapshot version
  run: |
      VERSION="${{ steps.versions.outputs.xdk-version }}"
      if [[ "$VERSION" != *-SNAPSHOT ]]; then
          echo "❌ ERROR: Cannot publish snapshots for non-SNAPSHOT version"
          exit 1
      fi
```

**Result**: Only SNAPSHOT versions can be published as snapshots

### Release Workflow Validation

**publish-release.yml** validates that version does NOT contain `-SNAPSHOT`:

```yaml
- name: Validate and determine release version
  run: |
      if [[ "$RELEASE_VERSION" == *-SNAPSHOT ]]; then
          echo "❌ ERROR: Cannot publish release with -SNAPSHOT version"
          exit 1
      fi
      if ! [[ "$RELEASE_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
          echo "❌ ERROR: Invalid version format"
          exit 1
      fi
```

**Result**: Only non-SNAPSHOT semantic versions can be published as releases

### Version Override

Both workflows support version overrides:

**Snapshot** (automatic from version.properties):
- No override input (always uses version.properties)
- Must contain `-SNAPSHOT`

**Release** (manual input):
```yaml
workflow_dispatch:
    inputs:
        version-override:
            description: 'Release version override (e.g., 0.4.4)'
```

**Use case**: Test release workflow without updating version.properties

---

## Troubleshooting

### Build Artifact Not Found

**Symptom**: Workflow fails with "Artifact not found: xdk-dist-{commit}"

**Causes**:
1. CI workflow hasn't completed yet
2. CI workflow failed
3. Manual trigger from branch (artifacts only from master CI runs)
4. Artifact expired (10-day retention)

**Solution**:
- Check CI workflow status for that commit
- For manual testing, trigger from master or use version override
- Check artifact existence: `gh run view {run-id} --log`

### Wrong Commit Downloaded

**Symptom**: Docker/Homebrew workflow downloads wrong commit

**Cause**: Race condition or incorrect commit reference

**Prevention**:
- Workflows use `workflow_run.head_sha` for automatic triggers
- Full 40-character commit hashes prevent collisions

**Verification**:
```bash
# Check workflow_run event
gh run view {run-id} --json event --jq '.event.workflow_run.head_sha'
```

### Maven Package Cleanup Errors

**Symptom**: `actions/delete-package-versions` fails

**Causes**:
1. Insufficient permissions
2. Package doesn't exist yet
3. Fewer than min-versions-to-keep exist

**Solution**:
- Verify `packages: write` permission
- Ensure packages exist before cleanup
- Check package names are correct

### Homebrew Formula Invalid

**Symptom**: `brew install xdk-latest` fails

**Causes**:
1. Invalid SHA256
2. Release URL not accessible
3. Syntax error in template

**Debugging**:
```bash
# Check formula syntax
brew audit --strict --online xtclang/xvm/xdk-latest

# Verify download URL
curl -I https://github.com/xtclang/xvm/releases/download/xdk-snapshots/xdk-0.4.4-SNAPSHOT.zip

# Check SHA256
curl -sL {URL} | sha256sum
```

### GitHub Release Not Created

**Symptom**: `publish-github-release` action fails

**Causes**:
1. Release tag already exists
2. Insufficient permissions
3. Invalid artifact path

**Solution**:
- Check `contents: write` permission
- Verify artifact path is correct
- For releases, check if tag already exists: `git tag -l v{VERSION}`

### Docker Build Cache Issues

**Symptom**: Docker builds are slow or fail

**Causes**:
1. Cache miss
2. Platform-specific cache not found
3. Cache corruption

**Solution**:
```yaml
# Force cache rebuild
- name: Build Docker image
  with:
      cache-from: type=gha,scope=${{ matrix.arch }}
      cache-to: type=gha,mode=max,scope=${{ matrix.arch }}
      no-cache: true  # Add this to force rebuild
```

### Gradle Configuration Cache Issues

**Symptom**: Build fails with configuration cache errors

**Causes**:
1. Task captures script object references
2. Non-serializable objects in configuration

**Solution**:
- Use injected services instead of project-level methods
- Follow configuration cache best practices
- Disable temporarily: `GRADLE_OPTIONS="--no-configuration-cache"`

---

## Common Patterns

### Downloading Build Artifacts

```yaml
- name: Determine commit and run ID
  id: commit
  shell: bash
  run: |
      if [ "${{ github.event_name }}" = "workflow_run" ]; then
          COMMIT="${{ github.event.workflow_run.head_sha }}"
          RUN_ID="${{ github.event.workflow_run.id }}"
      else
          COMMIT="${{ github.sha }}"
          RUN_ID="${{ github.run_id }}"
      fi
      echo "commit=$COMMIT" >> $GITHUB_OUTPUT
      echo "run-id=$RUN_ID" >> $GITHUB_OUTPUT

- name: Download XDK build artifact
  uses: actions/download-artifact@v4
  with:
      name: xdk-dist-${{ steps.commit.outputs.commit }}
      path: ./artifacts
      github-token: ${{ secrets.GITHUB_TOKEN }}
      repository: ${{ github.repository }}
      run-id: ${{ steps.commit.outputs.run-id }}
```

### Conditional Execution

```yaml
jobs:
    my-job:
        runs-on: ubuntu-latest
        # Only run if CI succeeded (for workflow_run) or manual trigger
        if: ${{ github.event_name == 'workflow_dispatch' || github.event.workflow_run.conclusion == 'success' }}
```

### Version-Based Gating

```yaml
- name: Validate version
  run: |
      VERSION="${{ steps.versions.outputs.xdk-version }}"
      if [[ "$VERSION" != *-SNAPSHOT ]]; then
          echo "❌ Wrong version type"
          exit 1
      fi
```

---

## Summary

The XVM CI/CD pipeline provides:

✅ **Automatic snapshot publishing** on every master push
✅ **Manual release workflow** with staging and approval gates
✅ **Multi-platform Docker images** (amd64, arm64)
✅ **Homebrew tap** automatically updated with latest snapshots
✅ **Maven artifacts** published to GitHub Packages and Central
✅ **Version gating** prevents publishing wrong version types
✅ **Artifact tracking** with full commit hashes
✅ **Clear separation** between internal artifacts and external releases

All workflows support manual triggering for testing and emergency releases.
