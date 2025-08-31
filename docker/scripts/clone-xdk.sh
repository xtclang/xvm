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

# Logging functions - all output to stderr to avoid contaminating return values
log_info() { echo "🔄 $*" >&2; }
log_success() { echo "✅ $*" >&2; }
log_cache() { echo "📁 $*" >&2; }
log_warning() { echo "⚠️ $*" >&2; }
log_error() { echo "❌ ERROR: $*" >&2; }

# Ultra-fast source download using GitHub API (NO GIT OPERATIONS)
download_github_tarball() {
    local ref="$1"
    local desc="$2"
    
    command -v curl >/dev/null || { log_error "curl not available"; return 1; }
    
    log_info "Downloading $desc from GitHub API"
    # URL-encode the ref to handle branch names with special characters like slashes
    local encoded_ref=$(printf "%s" "$ref" | sed 's|/|%2F|g')
    local tarball_url="https://api.github.com/repos/$GITHUB_REPO/tarball/$encoded_ref"
    
    # Download and extract tarball directly 
    curl -fsSL "$tarball_url" | tar -xz --strip-components=1 || return 1
    
    log_success "Downloaded $desc successfully"
    return 0
}

# Determine actual commit hash using GitHub API (NO GIT OPERATIONS)
resolve_commit_hash() {
    local target_ref="$1"
    
    command -v curl >/dev/null || { echo "unknown"; return 0; }
    
    log_info "Resolving commit hash for '$target_ref' via GitHub API" >&2
    # URL-encode the ref to handle branch names with special characters like slashes
    local encoded_ref=$(printf "%s" "$target_ref" | sed 's|/|%2F|g')
    local api_url="https://api.github.com/repos/$GITHUB_REPO/commits/$encoded_ref"
    local commit_hash
    
    commit_hash=$(curl -fsSL "$api_url" 2>/dev/null | sed -n 's/.*"sha": "\([^"]*\)".*/\1/p' | head -1)
    
    [[ -n "$commit_hash" ]] && { echo "$commit_hash"; return 0; }
    
    log_warning "Could not resolve commit hash for '$target_ref'" >&2
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
    
    # Get cached commit for consistency - return only the clean commit hash
    local cache_info="$CACHE_DIR/.cache-info"
    [[ -f "$cache_info" ]] && {
        local cached_commit
        cached_commit=$(grep "^COMMIT=" "$cache_info" 2>/dev/null | cut -d= -f2-)
        # Ensure we only return the full 40-character commit hash, no emoji or log text
        [[ -n "$cached_commit" ]] && echo "$cached_commit" | grep -oE '^[a-f0-9]{40}$' | head -1
    }
}

# Fresh source fetch - TARBALL ONLY, NO GIT OPERATIONS
fresh_fetch() {
    log_info "Fetching fresh source via GitHub API" >&2
    
    local target_ref actual_commit
    
    # Determine target ref and resolve to actual commit
    if [[ -n "$GH_COMMIT" ]]; then
        target_ref="$GH_COMMIT"
        actual_commit="$GH_COMMIT"
        log_info "Using specific commit: $actual_commit" >&2
    else
        target_ref="$GH_BRANCH" 
        actual_commit=$(resolve_commit_hash "$GH_BRANCH")
        log_info "Resolved branch '$GH_BRANCH' to commit: $actual_commit" >&2
    fi
    
    # Download tarball
    download_github_tarball "$target_ref" "$([ -n "$GH_COMMIT" ] && echo "commit $target_ref" || echo "branch $target_ref")" || {
        log_error "Failed to download source from GitHub API" >&2
        exit 1
    }
    
    # Return only the clean commit hash - no emojis or log messages
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
    echo "📍 Branch: $GH_BRANCH" >&2
    echo "📍 Commit: ${GH_COMMIT:-<will be resolved from branch>}" >&2
    echo "💾 Cache dir: $CACHE_DIR" >&2
    
    local final_commit
    
    # Try cache first, then fresh fetch
    if is_cache_valid; then
        final_commit=$(use_cache)
    else
        log_info "Cache invalid or missing, fetching fresh source"
        final_commit=$(fresh_fetch)
        update_cache "$final_commit"
    fi
    
    # Always ensure we have a resolved commit (never empty) and clean it of any emojis
    [[ -z "$final_commit" || "$final_commit" = "unknown" ]] && {
        log_warning "Commit resolution failed, attempting to resolve from branch"
        final_commit=$(resolve_commit_hash "$GH_BRANCH")
    }
    
    # Clean the commit hash - extract only the full 40-character hash, removing any emoji or log text
    final_commit=$(echo "$final_commit" | grep -oE '[a-f0-9]{40}' | head -1)
    [[ -z "$final_commit" ]] && final_commit="unknown"
    
    # Export final commit for Docker build - ensure it's clean
    echo "export GH_COMMIT=$final_commit" > /tmp/git-info.env
    echo "export GH_BRANCH=$GH_BRANCH" >> /tmp/git-info.env
    
    log_success "Source setup completed successfully"
    echo "📊 Final branch: $GH_BRANCH" >&2
    echo "📊 Final commit: $final_commit ($(echo "$final_commit" | cut -c1-8))" >&2
    echo "📊 Source type: GitHub tarball (zero git operations)" >&2
}

# Execute main function
main "$@"