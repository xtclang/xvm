#!/bin/bash
set -e

# Smart git caching script for XDK Docker builds
# Usage: xdk-clone.sh [GH_BRANCH] [GH_COMMIT]

# Configuration
readonly GH_BRANCH="${1:-${GH_BRANCH:-master}}"
readonly GH_COMMIT="${2:-${GH_COMMIT}}"
readonly CACHE_DIR="${CACHE_DIR:-/git-cache/xvm}"
readonly REPO_URL="https://github.com/xtclang/xvm.git"

# Logging functions
log_info() { echo "🔄 $*"; }
log_success() { echo "✅ $*"; }
log_cache() { echo "📁 $*"; }
log_warning() { echo "⚠️ $*"; }
log_error() { echo "❌ ERROR: $*"; }

# Error handler with context
handle_git_error() {
    local operation="$1"
    local ref="$2"
    log_error "Failed to $operation ref: $ref"
    exit 1
}

# Main execution
main() {
    local target_ref="${GH_COMMIT:-$GH_BRANCH}"
    
    log_info "Cloning XVM from branch: $GH_BRANCH, commit: ${GH_COMMIT:-latest}"
    echo "📍 Target ref: $target_ref"
    echo "💾 Cache dir: $CACHE_DIR"

    # Guard clause: check if we have cached repository
    if [[ ! -d "$CACHE_DIR/.git" ]]; then
        fresh_clone_repository
        update_cache_and_complete
        return 0
    fi

    # Use cached repository
    log_cache "Found cached git repository, copying to workspace"
    cp -r "$CACHE_DIR/." . 
    git remote set-url origin "$REPO_URL"

    # Handle commit vs branch with early returns
    if [[ -n "$GH_COMMIT" ]]; then
        handle_commit_checkout "$GH_COMMIT"
    else
        handle_branch_checkout "$GH_BRANCH"
    fi

    update_cache_and_complete
}

# Fresh clone when no cache exists
fresh_clone_repository() {
    log_info "No cache found, cloning fresh from GitHub"
    
    if [[ -n "$GH_COMMIT" ]]; then
        clone_specific_commit "$GH_COMMIT"
    else
        clone_branch "$GH_BRANCH"
    fi
}

# Clone specific commit
clone_specific_commit() {
    local commit="$1"
    git clone --depth 1 "$REPO_URL" . || handle_git_error "clone repository" "initial"
    git fetch --depth 1 origin "$commit" || handle_git_error "fetch commit" "$commit"
    git checkout "$commit" || handle_git_error "checkout commit" "$commit"
}

# Clone branch
clone_branch() {
    local branch="$1"
    git clone --depth 1 --branch "$branch" "$REPO_URL" . || handle_git_error "clone branch" "$branch"
}

# Handle cached commit checkout
handle_commit_checkout() {
    local commit="$1"
    
    # Early return if commit already cached
    if git cat-file -e "$commit" 2>/dev/null; then
        log_success "Commit $commit already cached, checking out"
        git checkout "$commit" || handle_git_error "checkout cached commit" "$commit"
        return 0
    fi

    # Fetch and checkout new commit
    log_info "Fetching new commit: $commit"
    git fetch --depth 1 origin "$commit" || handle_git_error "fetch commit" "$commit"
    git checkout "$commit" || handle_git_error "checkout commit" "$commit"
}

# Handle cached branch checkout
handle_branch_checkout() {
    local branch="$1"
    log_info "Updating branch: $branch"
    git fetch --depth 1 origin "$branch" || handle_git_error "fetch branch" "$branch"
    git checkout "origin/$branch" || handle_git_error "checkout branch" "$branch"
}

# Update cache and show completion status
update_cache_and_complete() {
    log_info "Updating git cache for future builds"
    
    # Update cache (non-fatal if fails)
    mkdir -p "$CACHE_DIR"
    if ! cp -r .git "$CACHE_DIR/" 2>/dev/null; then
        log_warning "Failed to update git cache (non-fatal)"
    fi
    if ! cp -r * "$CACHE_DIR/" 2>/dev/null; then
        log_warning "Failed to update source cache (non-fatal)"
    fi

    # Success summary
    log_success "Git setup completed successfully"
    echo "📊 Current commit: $(git rev-parse HEAD 2>/dev/null || echo 'unknown')"
    echo "📊 Current branch: $(git branch --show-current 2>/dev/null || echo 'detached')"
}

# Execute main function
main "$@"