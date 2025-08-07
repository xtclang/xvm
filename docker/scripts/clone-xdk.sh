#!/bin/bash
set -euo pipefail

# Ultra-fast source fetching for XDK Docker builds using GitHub API tarballs only
# Usage: clone-xdk.sh [GH_BRANCH] [GH_COMMIT]
# 
# Performance Strategy:
# 1. Use cache with validation (branch/commit specific)
# 2. GitHub tarball download (no git operations)
# 3. Always resolve final commit hash

# Configuration
readonly GH_BRANCH="${1:-${GH_BRANCH:-master}}"
readonly GH_COMMIT="${2:-${GH_COMMIT:-}}"
readonly CACHE_DIR="${CACHE_DIR:-/git-cache/xvm}"
readonly GITHUB_REPO="xtclang/xvm"

# Logging functions
log_info() { echo "🔄 $*"; }
log_success() { echo "✅ $*"; }
log_cache() { echo "📁 $*"; }
log_warning() { echo "⚠️ $*"; }
log_error() { echo "❌ ERROR: $*"; }

# Ultra-fast source download using GitHub API (NO GIT OPERATIONS)
download_github_tarball() {
    local ref="$1"
    local desc="$2"
    
    command -v curl >/dev/null || { log_error "curl not available"; return 1; }
    
    log_info "Downloading $desc from GitHub API"
    local tarball_url="https://api.github.com/repos/$GITHUB_REPO/tarball/$ref"
    
    # Download and extract tarball directly 
    curl -fsSL "$tarball_url" | tar -xz --strip-components=1 || return 1
    
    log_success "Downloaded $desc successfully"
    return 0
}

# Determine actual commit hash using GitHub API (NO GIT OPERATIONS)
resolve_commit_hash() {
    local target_ref="$1"
    
    command -v curl >/dev/null || { echo "unknown"; return 0; }
    
    log_info "Resolving commit hash for '$target_ref' via GitHub API"
    local api_url="https://api.github.com/repos/$GITHUB_REPO/commits/$target_ref"
    local commit_hash
    
    commit_hash=$(curl -fsSL "$api_url" 2>/dev/null | sed -n 's/.*"sha": "\([^"]*\)".*/\1/p' | head -1)
    
    [[ -n "$commit_hash" ]] && { echo "$commit_hash"; return 0; }
    
    log_warning "Could not resolve commit hash for '$target_ref'"
    echo "unknown"
}

# Check cache validity (branch/commit specific validation)
is_cache_valid() {
    [[ -d "$CACHE_DIR" ]] || return 1
    [[ -n "$(ls -A "$CACHE_DIR" 2>/dev/null)" ]] || return 1
    
    # Check if cache metadata matches current request
    local cache_info="$CACHE_DIR/.cache-info"
    [[ -f "$cache_info" ]] || return 1
    
    local cached_branch cached_commit
    cached_branch=$(grep "^BRANCH=" "$cache_info" 2>/dev/null | cut -d= -f2-)
    cached_commit=$(grep "^COMMIT=" "$cache_info" 2>/dev/null | cut -d= -f2-)
    
    # For specific commit requests, match exactly
    [[ -n "$GH_COMMIT" ]] && [[ "$cached_commit" = "$GH_COMMIT" ]] && return 0
    
    # For branch requests, check if branch matches and we have valid commit
    [[ "$cached_branch" = "$GH_BRANCH" ]] && [[ -n "$cached_commit" ]] && [[ "$cached_commit" != "unknown" ]] && return 0
    
    return 1
}

# Use cached repository
use_cache() {
    log_cache "Using valid cache for branch '$GH_BRANCH'"
    cp -r "$CACHE_DIR/." .
    
    # Get cached commit for consistency
    local cache_info="$CACHE_DIR/.cache-info"
    [[ -f "$cache_info" ]] && {
        local cached_commit
        cached_commit=$(grep "^COMMIT=" "$cache_info" 2>/dev/null | cut -d= -f2-)
        [[ -n "$cached_commit" ]] && echo "$cached_commit"
    }
}

# Fresh source fetch - TARBALL ONLY, NO GIT OPERATIONS
fresh_fetch() {
    log_info "Fetching fresh source via GitHub API"
    
    local target_ref actual_commit
    
    # Determine target ref and resolve to actual commit
    if [[ -n "$GH_COMMIT" ]]; then
        target_ref="$GH_COMMIT"
        actual_commit="$GH_COMMIT"
        log_info "Using specific commit: $actual_commit"
    else
        target_ref="$GH_BRANCH" 
        actual_commit=$(resolve_commit_hash "$GH_BRANCH")
        log_info "Resolved branch '$GH_BRANCH' to commit: $actual_commit"
    fi
    
    # Download tarball
    download_github_tarball "$target_ref" "$([ -n "$GH_COMMIT" ] && echo "commit $target_ref" || echo "branch $target_ref")" || {
        log_error "Failed to download source from GitHub API"
        exit 1
    }
    
    echo "$actual_commit"
}

# Update cache with metadata
update_cache() {
    local final_commit="$1"
    
    log_info "Updating cache for future builds"
    mkdir -p "$CACHE_DIR"
    
    # Cache source files
    cp -r * "$CACHE_DIR/" 2>/dev/null || {
        log_warning "Failed to cache source files (non-fatal)"
        return 0
    }
    
    # Cache metadata for validation
    cat > "$CACHE_DIR/.cache-info" << EOF
BRANCH=$GH_BRANCH
COMMIT=$final_commit
CACHED_AT=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
EOF
    
    log_cache "Cache updated with branch '$GH_BRANCH', commit '$final_commit'"
}

# Main execution - PURE TARBALL APPROACH, NO GIT OPERATIONS
main() {
    log_info "Ultra-fast XVM source fetching (GitHub API only)"
    echo "📍 Branch: $GH_BRANCH"
    echo "📍 Commit: ${GH_COMMIT:-<will be resolved from branch>}"
    echo "💾 Cache dir: $CACHE_DIR"
    
    local final_commit
    
    # Try cache first, then fresh fetch
    if is_cache_valid; then
        final_commit=$(use_cache)
    else
        log_info "Cache invalid or missing, fetching fresh source"
        final_commit=$(fresh_fetch)
        update_cache "$final_commit"
    fi
    
    # Always ensure we have a resolved commit (never empty)
    [[ -z "$final_commit" || "$final_commit" = "unknown" ]] && {
        log_warning "Commit resolution failed, attempting to resolve from branch"
        final_commit=$(resolve_commit_hash "$GH_BRANCH")
    }
    
    # Export final commit for Docker build
    echo "export GH_COMMIT=$final_commit" > /tmp/git-info.env
    
    log_success "Source setup completed successfully"
    echo "📊 Final branch: $GH_BRANCH"
    echo "📊 Final commit: $final_commit ($(echo "$final_commit" | cut -c1-8))"
    echo "📊 Source type: GitHub tarball (zero git operations)"
}

# Execute main function
main "$@"