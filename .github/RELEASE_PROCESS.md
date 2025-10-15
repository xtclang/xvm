# Release Process

This document describes the automated release workflow for the XVM project.

## Quick Reference

### Normal Release (3 Steps)

```bash
# 1. Prepare (creates PR with staged artifacts)
gh workflow run "Prepare Release" --field release-version=0.4.4

# 2. Review staged artifacts (Maven Central, GitHub draft release)

# 3. Merge PR (auto-promotes to production)
gh pr merge <PR-NUMBER>
```

### Selective Publishing (via Labels)

```bash
# Skip specific targets by adding labels before merge
gh pr edit <PR-NUMBER> --add-label "skip-maven-central"
gh pr edit <PR-NUMBER> --add-label "skip-github-release"
gh pr edit <PR-NUMBER> --add-label "skip-gradle-plugin-portal"
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

- **Maven Central Staging:** https://oss.sonatype.org/#stagingRepositories
- **GitHub Releases:** https://github.com/xtclang/xvm/releases
- **Plugin Portal:** https://plugins.gradle.org/plugin/org.xtclang.xtc-plugin

---

## Overview

The release process is **two-phase** with full automation:

1. **Prepare Phase**: Stage artifacts for review (reversible)
2. **Promote Phase**: Publish staged artifacts to production (automatic on PR merge)

## Why This Approach?

| Feature | Benefit |
|---------|---------|
| **Release branches** | Isolates release work from ongoing development |
| **Automatic version bumps** | No manual version.properties editing |
| **Staging before production** | Review artifacts before they go live |
| **PR-based promotion** | Clear approval gate with full audit trail |
| **Auto-promotion on merge** | One merge = complete release |

## Complete Release Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. Prepare Release (Manual Trigger)                             │
├─────────────────────────────────────────────────────────────────┤
│ • Create release/X.Y.Z branch                                   │
│ • Commit version X.Y.Z                                          │
│ • Tag vX.Y.Z                                                    │
│ • Commit version X.Y.(Z+1)-SNAPSHOT                             │
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
│ • Master updated to X.Y.(Z+1)-SNAPSHOT                          │
│ • vX.Y.Z tag points to release commit                           │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼ (Automatic Promotion Triggered)
┌─────────────────────────────────────────────────────────────────┐
│ 4. Promote Release (Automatic)                                  │
├─────────────────────────────────────────────────────────────────┤
│ • Maven Central staging → Released                              │
│ • GitHub draft → Published                                      │
│ • Gradle Plugin Portal → Published (if enabled)                 │
└─────────────────────────────────────────────────────────────────┘
```

## Step-by-Step Instructions

### Prerequisites

Ensure secrets are configured:
- `ORG_XTCLANG_MAVEN_CENTRAL_USERNAME`
- `ORG_XTCLANG_MAVEN_CENTRAL_PASSWORD`
- `ORG_XTCLANG_SIGNING_KEY`
- `ORG_XTCLANG_SIGNING_KEY_ID`
- `ORG_XTCLANG_SIGNING_PASSWORD`
- `ORG_XTCLANG_GRADLE_PLUGIN_PORTAL_PUBLISH_KEY` (if publishing plugin)
- `ORG_XTCLANG_GRADLE_PLUGIN_PORTAL_PUBLISH_SECRET` (if publishing plugin)

### 1. Prepare Release

Trigger the prepare-release workflow:

```bash
gh workflow run "Prepare Release" \
  --field release-version=0.4.4 \
  --field publish-gradle-plugin=false \
  --field skip-tests=false
```

**What happens:**
- ✅ Creates `release/0.4.4` branch
- ✅ Updates `version.properties` to `0.4.4` and commits
- ✅ Creates tag `v0.4.4`
- ✅ Updates `version.properties` to `0.4.5-SNAPSHOT` and commits
- ✅ Pushes branch and tag
- ✅ Builds from tag `v0.4.4`
- ✅ Runs tests
- ✅ Stages to Maven Central
- ✅ Creates GitHub draft release
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

**Or browse in browser:**
```
https://oss.sonatype.org/#stagingRepositories
→ Select orgxtclang-XXXX
→ Click "Content" tab
→ Navigate: org/xtclang/xdk/0.4.4/
```

**Test the artifacts:**
```bash
# Extract and test XDK
unzip xdk-0.4.4.jar
# ... your testing process ...
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
- ✅ Maven Central staging repository released
- ✅ GitHub draft release published
- ✅ Gradle Plugin Portal published (if `publish-gradle-plugin` was enabled)

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

   # Gradle Plugin Portal (if published)
   curl -I https://plugins.gradle.org/m2/org/xtclang/xtc-plugin/0.4.4/xtc-plugin-0.4.4.jar
   ```

3. **Announce the release**
   - Update documentation
   - Post to social media
   - Notify users

## Key Design Decisions

### Why Gradle Plugin Portal Publishes During Promotion

**Problem**: Gradle Plugin Portal has no staging - it's immediate and irreversible.

**Solution**:
- **Prepare Phase**: Only validates credentials (runs `validatePlugins`)
- **Promote Phase**: Actually publishes to Plugin Portal

This ensures:
- ✅ Credentials are tested early
- ✅ Plugin goes live with all other artifacts
- ✅ Can abort release before Plugin Portal publication

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

## Rollback / Abort

### Before PR Merge (Easy)

```bash
# Close the PR without merging
gh pr close <PR_NUMBER>

# Drop Maven Central staging repository
# (Log into https://oss.sonatype.org/ and click "Drop")

# Delete draft release
gh release delete v0.4.4 --yes

# Delete tag
git push origin :v0.4.4

# Delete branch
git push origin :release/0.4.4
```

Everything is reversible!

### After PR Merge (Difficult)

Once promoted to production:
- ❌ **Maven Central**: Cannot delete (can only mark as deprecated)
- ❌ **Gradle Plugin Portal**: Cannot delete or unpublish
- ✅ **GitHub Release**: Can be deleted or marked as pre-release

**Best practice**: Only merge when you're 100% certain.

## Comparison to Other Workflows

### Maven Release Plugin

```bash
# Traditional approach
mvn release:prepare  # Interactive, manual version entry
mvn release:perform  # Publishes immediately
```

**Our approach advantages:**
- ✅ Fully automated version bumps
- ✅ Staging phase with review
- ✅ PR-based approval
- ✅ Better audit trail

### Manual GitFlow

```bash
# Traditional approach
git checkout -b release/0.4.4
# Manually edit version.properties
git commit
git checkout master
git merge release/0.4.4
git tag v0.4.4
# Manually edit version.properties for next version
# Manually run build and publish commands
```

**Our approach advantages:**
- ✅ No manual editing
- ✅ No manual build commands
- ✅ Enforced review process
- ✅ Automatic artifact promotion

## Advanced: Controlling Promotion Targets

### Via PR Labels (Automatic Promotion)

When the release PR is merged, you can control which targets are promoted using PR labels:

#### Available Labels

| Label | Effect | Use Case |
|-------|--------|----------|
| `release` | Identifies release PR | Required (auto-added by prepare-release) |
| `publish-gradle-plugin` | Enable Plugin Portal | Added automatically if `publish-gradle-plugin=true` in prepare-release |
| `skip-maven-central` | Skip Maven Central promotion | Already manually released or Maven-only issue |
| `skip-github-release` | Skip GitHub release | Only want Maven artifacts |
| `skip-gradle-plugin-portal` | Skip Plugin Portal | Override even if enabled in prepare-release |

#### Default Behavior

**Without any skip labels:**
- ✅ Promotes to Maven Central
- ✅ Publishes GitHub Release
- ⏭️ Skips Gradle Plugin Portal (unless `publish-gradle-plugin` label exists)

#### Examples

**Example 1: Normal Release (All Targets)**
```bash
# Prepare with Plugin Portal enabled
gh workflow run "Prepare Release" \
  --field release-version=0.4.4 \
  --field publish-gradle-plugin=true

# PR is created with labels: release, publish-gradle-plugin
# On merge: Publishes to Maven Central + GitHub + Plugin Portal
```

**Example 2: Skip Plugin Portal at Last Minute**
```bash
# After PR created, add skip label
gh pr edit 123 --add-label "skip-gradle-plugin-portal"

# On merge: Publishes to Maven Central + GitHub only
```

**Example 3: Maven Central Only (Skip GitHub Release)**
```bash
# After PR created, add skip label
gh pr edit 123 --add-label "skip-github-release"

# On merge: Publishes to Maven Central only
```

**Example 4: Re-publish After Partial Failure**
```bash
# Suppose Maven Central succeeded but GitHub Release failed
# Add skip label to prevent re-publishing to Maven Central
gh pr edit 123 --add-label "skip-maven-central"

# Close and re-merge PR (or use manual promotion below)
```

### Via Manual Workflow Dispatch (Manual Promotion)

For more control or to re-promote after failures, use workflow_dispatch:

```bash
# Re-promote specific targets
gh workflow run "Promote Release" \
  --field release-version=0.4.4 \
  --field promote-maven-central=false \
  --field publish-github-release=true \
  --field publish-gradle-plugin-portal=false
```

**When to use:**
- ✅ Re-promoting after partial failure
- ✅ Publishing to specific targets independently
- ✅ Testing promotion without PR
- ✅ Emergency fixes to published releases

**Example scenarios:**

**Scenario 1: Maven Central Succeeded, GitHub Release Failed**
```bash
# Only re-publish GitHub release
gh workflow run "Promote Release" \
  --field release-version=0.4.4 \
  --field promote-maven-central=false \
  --field publish-github-release=true \
  --field publish-gradle-plugin-portal=false
```

**Scenario 2: Forgot to Publish Plugin Portal**
```bash
# Only publish plugin portal
gh workflow run "Promote Release" \
  --field release-version=0.4.4 \
  --field promote-maven-central=false \
  --field publish-github-release=false \
  --field publish-gradle-plugin-portal=true
```

**Scenario 3: Full Re-promotion (All Targets)**
```bash
# Promote everything again
gh workflow run "Promote Release" \
  --field release-version=0.4.4 \
  --field promote-maven-central=true \
  --field publish-github-release=true \
  --field publish-gradle-plugin-portal=true
```

### Label + Manual Workflow Comparison

| Aspect | PR Labels | workflow_dispatch |
|--------|-----------|-------------------|
| **Trigger** | Automatic on PR merge | Manual anytime |
| **Control** | Via labels | Via inputs |
| **Audit** | In PR history | In workflow history |
| **Use Case** | Normal releases | Recovery/testing |
| **Flexibility** | Limited to skip | Full control |

## Gradle Plugin Portal and Snapshots

### Important Constraint

**Gradle Plugin Portal does NOT accept SNAPSHOT versions.**

### Handling in Workflows

The workflows automatically handle this:

**During Staging (prepare-release.yml):**
- Only **validates** Plugin Portal credentials
- Does **not** publish (even if enabled)

**During Promotion (promote-release.yml):**
- Only publishes if:
  1. Version is NOT a snapshot
  2. `publish-gradle-plugin` label exists OR workflow input is true
  3. No `skip-gradle-plugin-portal` label

### Handling in Gradle Build

Your `plugin/build.gradle.kts` should include:

```kotlin
tasks.named("publishPlugins") {
    onlyIf {
        val version = project.version.toString()
        if (version.contains("SNAPSHOT")) {
            logger.warn("⏭️  Skipping Gradle Plugin Portal - SNAPSHOT versions not accepted")
            logger.warn("   Current version: $version")
            false // Skip task
        } else {
            val enabled = findProperty("org.xtclang.publish.gradlePluginPortal")?.toString()?.toBoolean() ?: false
            if (!enabled) {
                logger.info("⏭️  Gradle Plugin Portal publication disabled")
            }
            enabled // Only run if explicitly enabled
        }
    }
}
```

This ensures:
- ✅ SNAPSHOT versions **WARN** and **SKIP** (don't fail)
- ✅ Release versions **CHECK** property and proceed
- ✅ Can safely run `./gradlew publish` on snapshots

### Testing Plugin Publication

To test plugin publication without full release:

```bash
# Test validation only
./gradlew :plugin:validatePlugins

# Test publication to Plugin Portal (release version only)
./gradlew :plugin:publishPlugins \
  -Pversion=0.4.4 \
  -Porg.xtclang.publish.gradlePluginPortal=true
```

## Troubleshooting

### "Staging repository not found" during promotion

**Cause**: Maven Central staging was dropped or already released.

**Solution**:
1. Check https://oss.sonatype.org/ manually
2. If dropped: Re-run prepare-release workflow
3. If already released: Skip Maven Central promotion (comment out step)

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

**Cause**: Code issues on master.

**Solution**:
1. Fix tests on master first
2. Don't create release from broken code
3. Or use `skip-tests=true` (not recommended)

## FAQ

**Q: Can I release from a branch other than master?**
A: No, the workflow expects master. For hotfixes, merge to master first or modify workflow.

**Q: Can I release a version that's not the next patch version?**
A: Yes, use version-override. But the auto-bump still increments patch.

**Q: What if I want to release 0.5.0 instead of 0.4.5?**
A: Use `release-version=0.5.0`. Auto-bump will create `0.5.1-SNAPSHOT`.

**Q: Can I skip Gradle Plugin Portal for a release?**
A: Yes, set `publish-gradle-plugin=false` (default).

**Q: How do I do a hotfix release?**
A: Same process, but use version like `0.4.4-hotfix.1` or create from release tag.

**Q: Can I have multiple releases in progress?**
A: No, one release at a time. Each release creates a PR that must be merged before the next.

## Summary

**Modern release workflow with:**
- ✅ Full automation (version bumps, staging, promotion)
- ✅ Safety (review before production)
- ✅ Simplicity (one merge = complete release)
- ✅ Audit trail (PR + workflow logs)
- ✅ Industry best practices (staging, signed artifacts, semantic versioning)

**The only manual steps:**
1. Trigger prepare-release workflow
2. Review staged artifacts
3. Merge PR

Everything else is automated!