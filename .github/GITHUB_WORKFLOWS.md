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
│  commit.yml (Verify Commit)                                     │
│  ├─ Build XDK                                                   │
│  ├─ Run tests (including manual tests)                          │
│  ├─ Upload artifact: xdk-dist-{COMMIT}                          │
│  │   • Temporary (10 days)                                      │
│  │   • Contains: xdk-{VERSION}.zip                              │
│  └─ Trigger publishing (if master or publish-snapshots=true)    │
│     └─ gh workflow run with --field ci-run-id={RUN_ID}          │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            │ Direct trigger (not workflow_run)
                            │ Passes ci-run-id for artifact download
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────────┐
│publish-docker│   │homebrew      │   │publish-snapshot  │
│.yml          │   │-update.yml   │   │.yml              │
├──────────────┤   ├──────────────┤   ├──────────────────┤
│Receive       │   │Receive       │   │Receive           │
│ci-run-id     │   │ci-run-id     │   │ci-run-id         │
│              │   │              │   │                  │
│Download      │   │Download      │   │Download          │
│artifact by   │   │artifact by   │   │artifact by       │
│run-id+commit │   │run-id+commit │   │run-id+commit     │
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
   - On master (or manual with `publish-snapshots=true`): Directly triggers publishing workflows

2. **Direct Publishing Triggers** (master only, or manual with flag):
   - `commit.yml` directly triggers workflows via `gh workflow run --field ci-run-id=...`
   - `publish-docker.yml` - Builds multi-platform Docker images
   - `homebrew-update.yml` - Updates Homebrew tap formula
   - `publish-snapshot.yml` - Publishes Maven + GitHub snapshot release
   - Each workflow receives `ci-run-id` to download artifacts from CI run

3. **Manual Release** (two-phase process):
   - `prepare-release.yml` - Creates release branch, stages artifacts, creates PR
   - `promote-release.yml` - Promotes staged artifacts to production (auto on PR merge)
   - See [RELEASE_PROCESS.md](RELEASE_PROCESS.md) for complete documentation

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
   ├─ Publish Maven snapshots to GitHub Packages + Maven Central Snapshots
   ├─ Clean up old Maven packages (keep 50)
   ├─ Publish GitHub snapshot release (overwrites)
   └─ Summary
   ```

### Manual Execution (any branch)

All workflows support `workflow_dispatch` for manual testing from any branch.

---

## Workflows Reference

### commit.yml (Verify Commit)

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
- `publish-snapshots`: Trigger publishing workflows after build (default: false)
  - Set `true` to test full publishing pipeline on non-master branches
  - Publishing workflows: snapshot, docker, homebrew
- `platforms`: Run on specific platform(s) or all
  - Options: `ubuntu-latest`, `windows-latest`, `all`
  - Default: `ubuntu-latest`
- `extra-gradle-options`: Extra Gradle CLI options
- `skip-tests`: Skip manual tests (default: true)
- `parallel-test-mode`: Run manual tests in parallel (default: true)

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
  -f skip-tests=false \
  -f parallel-test-mode=false

# Build, test, AND trigger publishing workflows
gh workflow run commit.yml \
  --ref your-branch \
  -f publish-snapshots=true \
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
5. Publish Maven snapshots to GitHub Packages + Maven Central Snapshots
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

### publish-docker.yml (Publish Docker Images)

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

# Upgrade to latest snapshot (choose one):
brew update && brew upgrade xdk-latest  # Standard: refresh tap first
brew reinstall xdk-latest               # Alternative: always gets latest
```

**Important**: Homebrew caches tap metadata locally. You must run `brew update` to refresh the tap before `brew upgrade` will detect new snapshot versions. Alternatively, use `brew reinstall xdk-latest` to always get the latest snapshot.

**Dependencies**: `openjdk@{version}` (from version.properties)

---

### Release Workflows (prepare-release.yml & promote-release.yml)

**Purpose**: Two-phase automated release process for XDK releases

**Architecture**:
- **Phase 1: Prepare** (`prepare-release.yml`) - Build and stage artifacts
- **Phase 2: Promote** (`promote-release.yml`) - Promote staged artifacts to production

**Understanding Artifact Publishing**:

| Artifact Type | Prepare Phase | Promote Phase |
|---------------|---------------|---------------|
| **GitHub Packages** (Maven) | ✅ Published immediately | No action (already live) |
| **Maven Central** | ⏸️ Staged in `orgxtclang-XXXX` | ✅ Close & release to production |
| **GitHub Release** (zip) | 📝 Uploaded as DRAFT | ✅ Publish draft → public |
| **Gradle Plugin Portal** | 🔍 Credentials validated | ✅ Published immediately |

**⚠️ Note**: GitHub Packages artifacts are **immediately public** when prepare-release runs, before PR approval. Most users consume from Maven Central, which remains staged until promotion.

**For complete release workflow documentation, see:**
**[📖 RELEASE_PROCESS.md](RELEASE_PROCESS.md)**

**Quick Summary**:

1. **Prepare Release** (Manual trigger):
   ```bash
   gh workflow run prepare-release.yml --field release-version=0.4.4
   ```
   - Creates `release/X.Y.Z` branch
   - Tags `vX.Y.Z`
   - Runs `./gradlew publish` (publishes to GitHub Packages + stages to Maven Central)
   - Uploads XDK zip as GitHub draft release
   - Validates Gradle Plugin Portal credentials
   - Creates PR to master with next snapshot version

2. **Review Staged Artifacts** (Manual):
   - Verify Maven Central staging repository at oss.sonatype.org
   - Review GitHub draft release
   - Test staged artifacts
   - Complete PR checklist

3. **Promote Release** (Automatic on PR merge):
   - Maven Central: Close & release staging → production (via Nexus API)
   - GitHub Release: Publish draft → public (via gh CLI)
   - Gradle Plugin Portal: Run `./gradlew :plugin:publishPlugins` (if enabled)
   - GitHub Packages: No action (already published in prepare)

**Key Features**:
- ✅ Automatic version bumps (no manual editing)
- ✅ Staging before production (reversible)
- ✅ PR-based approval gate
- ✅ Single merge = complete release
- ✅ Selective publishing via PR labels

**See [RELEASE_PROCESS.md](RELEASE_PROCESS.md) for**:
- Complete step-by-step instructions
- Selective publishing control
- Manual re-promotion
- Troubleshooting
- Rollback procedures

---

### dependency-updates.yml (Dependency Updates)

**Trigger**: Pull requests to master (Dependabot PRs), manual

**Purpose**: Validate dependency updates from Dependabot and auto-approve if tests pass

**Key Steps**:
1. Fetch sources and setup project
2. Analyze dependency changes
3. Generate dependency lock files
4. Run validation build (without tests)
5. Check for dependency vulnerabilities
6. Auto-approve Dependabot PR if all checks pass

**Manual Trigger**:
```bash
gh workflow run validate-dependabot.yml
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

To test the complete publishing pipeline (snapshot, Docker, Homebrew) from a non-master branch **without** merging to master, manually dispatch the **Verify Commit** workflow. When manually triggered with `publish-snapshots=true`, Verify Commit automatically triggers all three publishing workflows after successful completion.

### Single Command to Test Publishing

```bash
gh workflow run commit.yml --ref your-branch-name -f publish-snapshots=true
```

This command will:
1. Build and test your branch
2. Upload build artifact
3. Automatically trigger publish-snapshot.yml (with ci-run-id)
4. Automatically trigger publish-docker.yml (with ci-run-id)
5. Automatically trigger homebrew-update.yml (with ci-run-id)

**Without** `-f publish-snapshots=true`, only the build and test run (no publishing).

### How It Works

**Trigger Mechanism**:
- On master push or manual trigger with `publish-snapshots=true`, commit.yml completes its build
- At the end of commit.yml, a `trigger-publishing` job runs that:
  - Checks if this is a release merge (has release tag) - skips if true
  - Directly triggers each publishing workflow via `gh workflow run`
  - Passes `ci-run-id` field so workflows can download artifacts from the CI run
- Each publishing workflow receives the `ci-run-id` and downloads artifacts directly

**Automatic vs Manual Triggering**:

| Trigger Type | Branch | publish-snapshots | Publishing Runs? |
|--------------|--------|-----------------|------------------|
| Push (automatic) | master | N/A | ✅ YES (automatic on master, if not release tag) |
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

**2. Trigger Verify Commit (builds + triggers publishing)**:
```bash
gh workflow run commit.yml --ref feature/update-publishing -f publish-snapshots=true
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
- Look for these workflows that started after Verify Commit completed:
  - "Publish Snapshots"
  - "Publish Docker Images"
  - "Update Homebrew"

### What Gets Published

When you manually trigger Verify Commit from a non-master branch:

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
gh workflow run commit.yml --ref your-branch -f publish-snapshots=true

# Skip manual tests (faster CI, no publishing)
gh workflow run commit.yml \
  --ref your-branch \
  -f skip-tests=true

# Run only Ubuntu with publishing
gh workflow run commit.yml \
  --ref your-branch \
  -f platforms=ubuntu-latest \
  -f publish-snapshots=true
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
gh workflow run commit.yml --ref feature/docker-improvements -f publish-snapshots=true

# 4. Monitor progress (watch until complete)
gh run watch

# 5. Verify all three publishing workflows succeeded
gh run list --branch feature/docker-improvements --limit 10

# 6. If issues found, fix and re-test
vim .github/workflows/publish-docker.yml
git commit -am "Fix Docker build issue"
git push
gh workflow run commit.yml --ref feature/docker-improvements -f publish-snapshots=true

# 7. Once working, merge to master
gh pr create --title "Optimize Docker builds"
```

### Monitoring Progress in GitHub UI

1. Go to: `https://github.com/xtclang/xvm/actions`
2. Click on the running "Verify Commit" workflow
3. Wait for it to complete (shows green checkmark)
4. Look for triggered workflows below:
   - **Publish Snapshots** - Check it completed successfully
   - **Publish Docker Images** - Verify both amd64 and arm64 built
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
**Solution**: Check Verify Commit completed successfully. Publishing only triggers on success.

**Problem**: "Artifact not found" error in publishing workflows
**Solution**: Verify Commit must complete fully and upload artifact. Check the Verify Commit run succeeded.

**Problem**: Snapshot publishing fails with "not a SNAPSHOT version"
**Solution**: Your `version.properties` must contain `-SNAPSHOT`. Update it or skip snapshot testing.

---

## Manual Testing

### Running Manual Tests via CI Workflow

**Method 1: Via GitHub UI**
1. Go to Actions → Verify Commit workflow
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
  -f skip-tests=false \
  -f parallel-test-mode=false
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
