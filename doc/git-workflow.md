# Git Workflow: Rebase-Only, Linear History

**Core Principle**: We maintain a **strict linear history** in master. **NEVER merge from master to branches** - always rebase instead to avoid merge commits and keep history clean.

*Note: All commands below are supported by IDE Git integrations (IntelliJ, VSCode, etc.). This section uses command-line examples for clarity, but the same operations work in any modern IDE.*

## Initial Git Configuration

**Required:** Configure git to use rebase by default to prevent accidental merge commits:

```bash
# Check current setting
git config --get pull.rebase

# Set globally (recommended)
git config --global pull.rebase true

# Or set for this repository only
git config --local pull.rebase true
```

The output of the first command should be `true`. If not, run one of the configuration commands above.

## How Changes Reach Master

Every change reaches master through a pull request, and every pull request lands as **one squash
commit** whose title is the PR title (the commit message body is left blank). Squash merging is the
only merge method enabled on the repository; merge commits and rebase merges are turned off, and the
branch is deleted after the merge. Master itself requires:

- one approving review,
- the **All builds complete** status check, on a branch that is **up to date** with master,
- every review conversation resolved,
- linear history, with no force pushes or deletions.

Because a PR must be up to date with master before it can merge, you will rebase it whenever master
moves. Rebasing is only for catching up; it is never the way a PR is merged.

## Branch-Based Development Workflow

**Rule 1**: Always work in feature branches. Direct commits to master are prohibited.
**Rule 2**: Always rebase your branch on top of latest master. Never merge master into your branch.

### 1. Create and Set Up Your Feature Branch

```bash
# Create new branch from latest master
git checkout master
git pull                                    # This will rebase thanks to pull.rebase=true
git checkout -b feature/descriptive-name   # Use descriptive names

# Push and set upstream tracking
git push --set-upstream origin feature/descriptive-name
```

### 2. Work on Your Changes

```bash
# Make changes and commit frequently
git add .
git commit -m "Add feature X functionality"

# Push your work regularly
git push
```

### 3. Keep Your Branch Current (Critical Step)

**Before creating a PR**, and **anytime master moves ahead**, rebase your branch:

```bash
# Fetch latest changes from all remotes
git fetch origin

# Rebase your branch on top of latest master
git rebase origin/master
```

**If conflicts occur during rebase:**
1. **Fix conflicts** in your editor
2. **Test that everything still builds**: `./gradlew build`
3. **Stage only the files you resolved, then continue the rebase** (`git add .` would also stage
   unrelated untracked files):
   ```bash
   git add path/to/resolved/File.java    # or `git add -u` for tracked files only
   git rebase --continue
   ```
4. **If you get stuck**, abort and ask for help:
   ```bash
   git rebase --abort
   ```

**After successful rebase, force-push** (this is safe and necessary):
```bash
git push --force-with-lease  # Safer than git push -f
```

`--force-with-lease` refuses to overwrite the remote branch if someone else pushed to it since your
last fetch. If your local branch has a different name than the remote one, name the remote branch
explicitly: `git push --force-with-lease=<branch> origin HEAD:<branch>`.

**On GitHub**, a PR that is behind master shows an **Update branch** button. Its default action
merges master into your branch; open its menu and choose **Update with rebase** instead, or rebase
locally as above.

### 4. Clean Up Your Commits (Before PR)

Use interactive rebase to create clean, logical commits:

```bash
# Interactive rebase for last n commits
git rebase -i HEAD~3  # Example: last 3 commits

# In the editor, you can:
# - squash: combine commits
# - reword: change commit message
# - drop: remove commits
# - reorder: change commit order
```

**PR Quality Standards:**
- The PR title becomes the commit title on master, so write it as a descriptive commit title
- Each commit should build and pass tests, so reviewers can follow the PR commit by commit
- Commit messages should be descriptive
- Related changes should be in the same commit
- Unrelated changes should be in separate commits, or better, separate PRs
- No "fix typo", "wip", or broken commits

### 5. Create Pull Request

```bash
# Final push after cleanup
git push --force-with-lease

# Create PR using GitHub CLI (optional)
gh pr create --title "Add feature X" --body "Description of changes"
```

## What NOT to Do

**NEVER do these things:**
```bash
# DON'T: Merge master into your branch
git merge master                    # This creates merge commits!
git merge origin/master             # Same thing
git pull origin master             # Merges unless pull.rebase=true; fetch and rebase instead
# ...and don't use GitHub's "Update branch" merge: choose "Update with rebase"

# DON'T: Work directly on master
git checkout master
git commit -m "direct change"      # Use branches instead!

# DON'T: Create merge commits
git merge feature/my-branch        # PRs are squash-merged on GitHub
```

**ALWAYS do these instead:**
```bash
# DO: Rebase your branch on master
git rebase origin/master

# DO: Use pull with rebase configured
git pull                           # Safe with pull.rebase=true

# DO: Work in branches
git checkout -b feature/my-change
```

## Emergency: Fixing a Broken Rebase

If you get into a confusing state during rebase:

```bash
# 1. Abort the rebase to start over
git rebase --abort

# 2. Make sure you have the latest master
git fetch origin

# 3. Try again with a clean approach
git rebase origin/master

# 4. If still stuck, ask for help on the team chat
```

## GitHub Branch Protection Settings

**For Repository Administrators**: these are the settings that enforce this workflow, as configured
on 2026-10-21.

**Settings -> General -> Pull Requests**:
- **Allow squash merging** only, with the default commit message **Pull request title**
  (merge commits and rebase merging turned off)
- **Allow auto-merge** (opt-in per PR; never on by default)
- **Automatically delete head branches**

**Settings -> Branches**, the rule for `master`:
- **Require a pull request before merging**, with **1** required approval
- **Require status checks to pass before merging**: **All builds complete**, with **Require branches
  to be up to date before merging**
- **Require conversation resolution before merging**
- **Require linear history**
- Force pushes and deletions not allowed
- **Do not allow bypassing the above settings** is off, so administrators can still bypass the rule

**GitHub CLI setup** (for administrators). `gh api --field` sends a JSON object as a string, which
GitHub rejects, so the nested settings go in a request body. A `PUT` replaces the whole rule, so it
lists every setting:

```bash
# Squash merging only, auto-merge available, head branches deleted after merge
gh api repos/xtclang/xvm --method PATCH \
  -F allow_squash_merge=true -F allow_merge_commit=false -F allow_rebase_merge=false \
  -F allow_auto_merge=true -F delete_branch_on_merge=true \
  -f squash_merge_commit_title=PR_TITLE -f squash_merge_commit_message=BLANK

# Branch protection for master
gh api repos/xtclang/xvm/branches/master/protection --method PUT --input - <<'JSON'
{
  "required_status_checks": {"strict": true, "checks": [{"context": "All builds complete"}]},
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": false,
    "require_last_push_approval": false
  },
  "restrictions": null,
  "required_linear_history": true,
  "required_conversation_resolution": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "lock_branch": false,
  "allow_fork_syncing": false
}
JSON

# Check the result
gh api repos/xtclang/xvm/branches/master/protection
```

`required_linear_history` **blocks merge commits** on master; together with squash-only merging it
keeps master to one commit per PR.

## Why This Workflow Matters

**Benefits of linear history:**
- **Easy to follow**: `git log --oneline` shows clear chronological development
- **Simple debugging**: `git bisect` works reliably to find bugs
- **Clean commits**: Each commit represents a logical change
- **Fast builds**: CI doesn't waste time on merge commit combinations
- **Clear blame**: `git blame` points to actual changes, not merge commits

**Problems with merge commits:**
- **Complex history**: Hard to understand what actually changed
- **Difficult bisection**: Merge commits create confusing paths
- **CI overhead**: More commit combinations to test
- **Unclear metrics**: Commit counts and author statistics get distorted
