# Release Process

This document describes the automated release workflow for the XVM project.

## TL;DR - How to Manually Trigger the Workflow

### Starting the Workflow

You can start the `prepare-release.yml` workflow using the GitHub CLI:

```bash
gh workflow run prepare-release.yml --ref your-branch-name -f branch=your-branch-name
```

Or through the GitHub web UI:
1. Go to **Actions** tab
2. Select **Prepare Release** workflow
3. Click **Run workflow** button
4. Specify the branch to prepare release from

### What the Workflow Does

The workflow automates the entire release preparation process in 3 jobs:

**1. prepare-release job** - Creates the release branch and version commits:
- Creates a new branch `release/X.Y.Z` from your specified branch
- Updates `version.properties` to the release version (removes `-SNAPSHOT`)
- Commits and tags this as `vX.Y.Z`
- Bumps `version.properties` to the next snapshot version
- Pushes the release branch and tag to GitHub

**2. stage-artifacts job** - Builds and stages all release artifacts:
- Checks out the release tag
- Builds the XDK distribution using the root `distZip` aggregator task
- Stages to Maven Central (reviewable at https://central.sonatype.com/)
- Publishes to GitHub Packages (staging)
- Validates Gradle Plugin Portal credentials (doesn't publish yet)
- Creates a draft GitHub release with the XDK zip attached

**3. create-pr job** - Creates a PR to master:
- Opens a PR from `release/X.Y.Z` → `master`
- Includes a comprehensive checklist for reviewing staged artifacts
- **⚠️ Important**: Merging this PR triggers the `promote-release` workflow which **immediately and irreversibly publishes** to:
  - Maven Central (production)
  - GitHub Releases (public)
  - Gradle Plugin Portal (cannot be undone)

### Key Points

- The workflow expects the source branch to have a `-SNAPSHOT` version in `version.properties`
- Nothing is publicly released until you merge the PR
- All artifacts are staged/validated first for review
- The actual publication happens via the separate `promote-release.yml` workflow when you merge the PR

---

## Quick Reference

### Normal Release (3 Steps)

```bash
# 1. Prepare (creates PR with staged artifacts)
# Version is automatically computed from version.properties on master
gh workflow run "Prepare Release"

# 2. Review staged artifacts (Maven Central, GitHub draft release)

# 3. Merge PR (auto-promotes to production)
gh pr merge <PR-NUMBER>
```

### Manual Re-promotion

```bash
# Re-promote after failure (selective targets)
gh workflow run "Promote Release" \
  --field release-version=0.4.4 \
  --field promote-maven-central=false \
  --field publish-github-release=true \
  --field publish-gradle-plugin-portal=false
```

### Key URLs

- **Maven Central Search:** https://central.sonatype.com/ (for browsing released artifacts)
- **Maven Central Staging:** https://oss.sonatype.org/#stagingRepositories (for managing staging repositories)
- **GitHub Releases:** https://github.com/xtclang/xvm/releases
- **Plugin Portal:** https://plugins.gradle.org/plugin/org.xtclang.xtc-plugin

---

## Overview

The release process is **two-phase** with full automation:
1. **Prepare Phase**: Stage artifacts for review
2. **Promote Phase**: Publish staged artifacts to production (automatic on PR merge)

### Understanding "Staging"

Not all publishing targets support staging equally:

| Target | Staging Support | Prepare Phase | Promote Phase |
|--------|----------------|---------------|---------------|
| **GitHub Packages** (Maven) | ❌ None | Published immediately | Already live |
| **Maven Central** | ✅ Full staging | Artifacts in staging repo | Close & release to production |
| **GitHub Release** (zip) | ✅ Draft releases | Uploaded as DRAFT | Publish draft → public |
| **Gradle Plugin Portal** | ❌ None | Credentials validated only | Published immediately |

**Important**: GitHub Packages artifacts are **immediately public** when prepare-release runs. This is acceptable because:
- Few users consume artifacts from GitHub Packages (most use Maven Central)
- The PR approval gate still controls Maven Central publication
- GitHub Packages versions can be manually deleted if needed (unlike Maven Central)

## Why This Approach?

| Feature | Benefit |
|---------|---------|
| **Automatic version computation** | No manual version entry - reads from version.properties |
| **Release branches** | Isolates release work from ongoing development |
| **Automatic version bumps** | Next snapshot version computed automatically |
| **Staging before production** | Review artifacts before they go live |
| **PR-based promotion** | Clear approval gate with full audit trail |
| **Auto-promotion on merge** | One merge = complete release |

## Complete Release Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. Prepare Release (Manual Trigger)                             │
├─────────────────────────────────────────────────────────────────┤
│ • Read version from master (e.g., 0.4.4-SNAPSHOT)              │
│ • Compute release version (0.4.4)                              │
│ • Compute next snapshot (0.4.5-SNAPSHOT)                       │
│ • Create release/0.4.4 branch                                   │
│ • Commit version 0.4.4                                          │
│ • Tag v0.4.4                                                    │
│ • Commit version 0.4.5-SNAPSHOT                                 │
│ • Build and stage artifacts:                                    │
│   - Maven Central → Staging repository                          │
│   - GitHub Release → Draft                                      │
│   - Gradle Plugin Portal → Validate credentials only            │
│ • Create PR to master                                           │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. Review Staged Artifacts (Manual)                             │
├─────────────────────────────────────────────────────────────────┤
│ • Check Maven Central staging repository                        │
│ • Review GitHub draft release                                   │
│ • Test staged artifacts                                         │
│ • Update release notes                                          │
│ • Complete PR checklist                                         │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. Merge PR to Master (One Click)                               │
├─────────────────────────────────────────────────────────────────┤
│ • Master updated to 0.4.5-SNAPSHOT                              │
│ • v0.4.4 tag points to release commit                           │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼ (Automatic Promotion Triggered)
┌─────────────────────────────────────────────────────────────────┐
│ 4. Promote Release (Automatic)                                  │
├─────────────────────────────────────────────────────────────────┤
│ • Maven Central staging → Released                              │
│ • GitHub draft → Published                                      │
│ • Gradle Plugin Portal → Published                              │
└─────────────────────────────────────────────────────────────────┘
```

## Step-by-Step Instructions

### Prerequisites

#### GitHub Repository Secrets (CI/CD)

Configure these secrets in **GitHub repository settings** (Settings → Secrets and variables → Actions):

**GitHub Packages** (required for all publishing):
- `GITHUB_TOKEN` - ✅ Automatic (provided by GitHub Actions)
- `GITHUB_ACTOR` - ✅ Automatic (provided by GitHub Actions)

**Maven Central** (required):
- `ORG_XTCLANG_MAVEN_CENTRAL_USERNAME` - Sonatype OSSRH username
- `ORG_XTCLANG_MAVEN_CENTRAL_PASSWORD` - Sonatype OSSRH password/token

**Signing** (required for Maven Central):
- `ORG_XTCLANG_SIGNING_KEY_ID` - GPG key ID (8-char short or full fingerprint)
- `ORG_XTCLANG_SIGNING_PASSWORD` - GPG key password
- `ORG_XTCLANG_SIGNING_KEY` - GPG private key (ASCII-armored with escaped `\n`)

**Gradle Plugin Portal** (required - always published for real releases):
- `ORG_XTCLANG_GRADLE_PLUGIN_PORTAL_PUBLISH_KEY` - API key
- `ORG_XTCLANG_GRADLE_PLUGIN_PORTAL_PUBLISH_SECRET` - API secret

**Note**: Workflows automatically convert these secrets to Gradle properties using `ORG_GRADLE_PROJECT_*` prefix.

#### Local Development Properties (Optional)

For local testing, add to `~/.gradle/gradle.properties`:

```properties
# GitHub Packages
githubUsername=your-github-username
githubPassword=ghp_YourPersonalAccessToken

# Maven Central
mavenCentralUsername=your-sonatype-username
mavenCentralPassword=your-sonatype-password

# Signing
signing.keyId=YOUR_KEY_ID
signing.password=your-key-password
signing.secretKeyRingFile=/path/to/secring.gpg
# Or for in-memory signing:
# signing.key=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...\n-----END PGP PRIVATE KEY BLOCK-----

# Gradle Plugin Portal
gradle.publish.key=your-api-key
gradle.publish.secret=your-api-secret
```

### 1. Prepare Release

Trigger the prepare-release workflow:

```bash
# Simple - version computed from master
gh workflow run "Prepare Release"

# Test from a feature branch
gh workflow run "Prepare Release" --field branch=lagergren/release-stage
```

**What happens:**
- ✅ Reads current version from `version.properties` (e.g., `0.4.4-SNAPSHOT`)
- ✅ Computes release version (`0.4.4`) and next snapshot (`0.4.5-SNAPSHOT`)
- ✅ Creates `release/0.4.4` branch
- ✅ Updates `version.properties` to `0.4.4` and commits
- ✅ Creates tag `v0.4.4`
- ✅ Updates `version.properties` to `0.4.5-SNAPSHOT` and commits
- ✅ Pushes branch and tag
- ✅ Builds from tag `v0.4.4`
- ✅ **Publishes Maven artifacts** (runs `./gradlew publish`):
  - **GitHub Packages**: ⚠️ **Published immediately to `maven.pkg.github.com`** (no staging)
  - **Maven Central**: Staged in `orgxtclang-XXXX` repository (not live yet)
- ✅ Creates **GitHub Release** draft with XDK zip (not live yet)
- ✅ Validates Gradle Plugin Portal credentials (doesn't publish)
- ✅ Creates PR to `master`

**Time:** ~10-15 minutes

### 2. Review Staged Artifacts

The PR will contain a checklist. Complete each item:

#### Check Maven Central Staging

1. Go to https://oss.sonatype.org/
2. Log in with Sonatype credentials
3. Navigate to "Staging Repositories"
4. Find `orgxtclang-XXXX`
5. Verify:
   - [ ] All artifacts present (xdk, javatools, plugin)
   - [ ] POM files are correct
   - [ ] Signatures (`.asc`) are valid
   - [ ] Checksums (`.md5`, `.sha1`) are correct

#### Review GitHub Draft Release

1. Go to https://github.com/xtclang/xvm/releases
2. Find draft release `vX.Y.Z`
3. Verify:
   - [ ] XDK distribution ZIP is attached
   - [ ] Update release notes with actual changes
   - [ ] Tag points to correct commit

#### Test Staged Artifacts

**Finding Your Staging Repository ID:**

1. Log in to https://oss.sonatype.org/
2. Click "Staging Repositories" (left sidebar)
3. Find repository starting with `orgxtclang-` (e.g., `orgxtclang-1234`)
4. The number at the end is your staging repo ID

**Download URLs (replace `1234` with your actual staging repo ID):**

```bash
# XDK JAR
curl -O https://oss.sonatype.org/service/local/repositories/orgxtclang-1234/content/org/xtclang/xdk/0.4.4/xdk-0.4.4.jar

# XDK POM
curl -O https://oss.sonatype.org/service/local/repositories/orgxtclang-1234/content/org/xtclang/xdk/0.4.4/xdk-0.4.4.pom

# Plugin JAR
curl -O https://oss.sonatype.org/service/local/repositories/orgxtclang-1234/content/org/xtclang/xtc-plugin/0.4.4/xtc-plugin-0.4.4.jar

# Verify signatures
curl -O https://oss.sonatype.org/service/local/repositories/orgxtclang-1234/content/org/xtclang/xdk/0.4.4/xdk-0.4.4.jar.asc
gpg --verify xdk-0.4.4.jar.asc xdk-0.4.4.jar
```

### 3. Merge PR (Promote Release)

When all checks pass and artifacts are verified:

```bash
gh pr merge <PR_NUMBER>
```

Or click "Merge pull request" in the GitHub UI.

**⚠️ IMPORTANT: This makes the release PUBLIC!**

**What happens automatically:**
- ✅ PR merged to `master`
- ✅ Master is now at `0.4.5-SNAPSHOT`
- ✅ `promote-release` workflow triggers automatically
- ✅ **Maven Central**: Closes and releases staging repository
- ✅ **GitHub Release**: Publishes draft
- ✅ **Gradle Plugin Portal**: Publishes plugin
- ✅ **GitHub Packages**: No action needed (already published in prepare phase)

**Time:** ~2-5 minutes

### 4. Post-Release

After merge completes:

1. **Wait for Maven Central sync** (10-30 minutes)
   - Check: https://central.sonatype.com/artifact/org.xtclang/xdk/0.4.4

2. **Verify published artifacts**:
   ```bash
   # Maven Central
   curl -I https://repo1.maven.org/maven2/org/xtclang/xdk/0.4.4/xdk-0.4.4.jar

   # GitHub Release
   curl -I https://github.com/xtclang/xvm/releases/download/v0.4.4/xdk-0.4.4.zip

   # Gradle Plugin Portal
   curl -I https://plugins.gradle.org/m2/org/xtclang/xtc-plugin/0.4.4/xtc-plugin-0.4.4.jar
   ```

3. **Announce the release**
   - Update documentation
   - Post to social media
   - Notify users

## Rollback

### Before PR Merge (Manual Cleanup)

If you need to abort a prepared release before merging the PR:

```bash
# Close the PR without merging
gh pr close <PR_NUMBER>

# Drop Maven Central staging repository manually
# Log into https://oss.sonatype.org/, find your staging repo, and click "Drop"

# Delete draft release
gh release delete v0.4.4 --yes

# Delete tag
git push origin :v0.4.4

# Delete branch
git push origin :release/0.4.4
```

**Note:** GitHub Packages artifacts cannot be automatically deleted but can be removed manually if needed.

### After PR Merge (Difficult)

Once promoted to production:
- ❌ **Maven Central**: Cannot delete (can only mark as deprecated)
- ❌ **Gradle Plugin Portal**: Cannot delete or unpublish
- ✅ **GitHub Release**: Can be deleted or marked as pre-release

**Best practice**: Only merge when you're 100% certain.

## Key Design Decisions

### Why Automatic Version Computation

**Problem**: Manual version entry is error-prone and requires remembering the next version.

**Solution**:
- Read current version from `version.properties` on master
- Compute release version by stripping `-SNAPSHOT`
- Compute next version by incrementing patch and adding `-SNAPSHOT`

This ensures:
- ✅ Single source of truth (`version.properties`)
- ✅ No manual version entry errors
- ✅ Consistent version incrementing

### Why Gradle Plugin Portal Always Published

**Previous approach**: Optional plugin publishing via workflow input.

**Problem**: Extra complexity, easy to forget, inconsistent releases.

**Solution**: Always validate and publish Gradle Plugin Portal for real releases.

This ensures:
- ✅ Simpler workflow (no conditional logic)
- ✅ Consistent releases (all targets published together)
- ✅ Early credential validation

### Why Release Branch Has Two Commits

**Commit 1**: Release version (`0.4.4`)
- This is what gets built and released
- Tag `v0.4.4` points here

**Commit 2**: Next snapshot (`0.4.5-SNAPSHOT`)
- This is what master becomes after merge
- Ensures master is always ready for development

**Benefits**:
- ✅ Release commit is clean (only release version)
- ✅ Master is automatically bumped after release
- ✅ No manual version editing needed

### Why Automatic Promotion on PR Merge

**Alternative approaches:**
1. ❌ Manual button after PR merge → Extra step, easy to forget
2. ❌ Separate workflow trigger → Requires remembering run-id
3. ✅ **Auto-promote on merge** → One action = complete release

**Safety**: The PR review process is the approval gate. If you merge, you're committing to release.

## Advanced Topics

### Releasing from a Feature Branch

The workflow supports releasing from any branch (not just master):

```bash
# Prepare release from feature branch
gh workflow run "Prepare Release" --field branch=feature/my-hotfix
```

**Use cases:**
- Testing the release workflow on a feature branch
- Creating hotfix releases from maintenance branches
- Preparing releases for different product versions

### Manual Promotion After Failures

For selective re-promotion after partial failures:

```bash
# Re-promote specific targets
gh workflow run "Promote Release" \
  --field release-version=0.4.4 \
  --field promote-maven-central=false \
  --field publish-github-release=true \
  --field publish-gradle-plugin-portal=false
```

**Scenarios:**

**Maven Central Succeeded, GitHub Release Failed:**
```bash
gh workflow run "Promote Release" \
  --field release-version=0.4.4 \
  --field promote-maven-central=false \
  --field publish-github-release=true \
  --field publish-gradle-plugin-portal=false
```

**Only Publish Plugin Portal:**
```bash
gh workflow run "Promote Release" \
  --field release-version=0.4.4 \
  --field promote-maven-central=false \
  --field publish-github-release=false \
  --field publish-gradle-plugin-portal=true
```

## Troubleshooting

### "Staging repository not found" during promotion

**Cause**: Maven Central staging was dropped or already released.

**Solution**:
1. Check https://oss.sonatype.org/ manually
2. If dropped: Re-run prepare-release workflow
3. If already released: Skip Maven Central promotion

### "Draft release not found" during promotion

**Cause**: Draft was manually deleted or already published.

**Solution**:
1. Check https://github.com/xtclang/xvm/releases
2. If published: Skip GitHub release promotion
3. If deleted: Manually create release from tag

### Gradle Plugin Portal credentials invalid

**Cause**: Credentials expired or incorrect.

**Solution**:
1. Get new credentials from https://plugins.gradle.org/
2. Update secrets in GitHub repository settings
3. Re-run prepare-release workflow

### Tests fail during prepare

**Cause**: Code issues on source branch.

**Solution**:
1. Fix tests on the branch first
2. Don't create release from broken code

## FAQ

**Q: Can I release from a branch other than master?**
A: Yes, use `--field branch=your-branch-name`. The PR will still target master.

**Q: How do I know what version will be released?**
A: The workflow reads `version.properties` from your branch and strips `-SNAPSHOT`. For example, `0.4.4-SNAPSHOT` → `0.4.4`.

**Q: What if I want to release 0.5.0 instead of 0.4.5?**
A: Update `version.properties` to `0.5.0-SNAPSHOT` on master first, then run prepare-release. The auto-bump will create `0.5.1-SNAPSHOT`.

**Q: Can I skip Gradle Plugin Portal for a release?**
A: No, for real releases the plugin is always published. You can test on a feature branch if needed.

**Q: How do I do a hotfix release?**
A: Either use a maintenance branch with the appropriate snapshot version, or create from a release tag and update `version.properties` to the hotfix version (e.g., `0.4.4-hotfix.1-SNAPSHOT`).

**Q: Can I have multiple releases in progress?**
A: No, one release at a time per branch. Each release creates a PR that must be merged or closed before the next.

## Summary

**Modern release workflow with:**
- ✅ Full automation (version computation, bumps, staging, promotion)
- ✅ Single source of truth (version.properties)
- ✅ Safety (review before production via PR approval)
- ✅ Simplicity (one merge = complete release)
- ✅ Audit trail (PR + workflow logs)
- ✅ Industry best practices (staging, signed artifacts, semantic versioning)

**The only manual steps:**
1. Trigger prepare-release workflow (version computed automatically)
2. Review staged artifacts
3. Merge PR

Everything else is automated!
