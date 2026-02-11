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
3. **Continue the rebase**:
   ```bash
   git add .
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
- Each commit should build and pass tests
- Commit messages should be descriptive
- Related changes should be in the same commit
- Unrelated changes should be in separate commits
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
git pull origin master             # This might create merge commits!

# DON'T: Work directly on master
git checkout master
git commit -m "direct change"      # Use branches instead!

# DON'T: Create merge commits
git merge feature/my-branch        # Maintainers handle PR merging
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

**For Repository Administrators**: Configure GitHub to enforce this workflow:

**Settings -> Branches -> Add rule** for `master` branch:
- **"Restrict pushes that create merge commits"**
- **"Require pull request reviews before merging"**
- **"Require status checks to pass before merging"**
- **"Require branches to be up to date before merging"**
- **"Include administrators"** (enforce rules for everyone)

**GitHub CLI setup** (for administrators):
```bash
# Enable branch protection with merge commit prevention
gh api repos/xtclang/xvm/branches/master/protection \
  --method PUT \
  --field required_status_checks='{"strict":true,"checks":[]}' \
  --field enforce_admins=true \
  --field required_pull_request_reviews='{"required_approving_review_count":1}' \
  --field restrictions=null \
  --field allow_force_pushes=false \
  --field allow_deletions=false \
  --field block_creations=false \
  --field required_linear_history=true
```

The `required_linear_history=true` setting **blocks merge commits** and enforces the rebase-only workflow.

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
