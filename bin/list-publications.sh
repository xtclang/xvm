#!/bin/bash

# List XVM Publications Script
# Replaces the Gradle publication listing tasks with a simple bash implementation

set -euo pipefail

# Default values
PROJECT_NAME=""
LIST_LOCAL=false
LIST_REMOTE=false
INCLUDE_ZIP=false
GITHUB_TOKEN="${GITHUB_TOKEN:-}"

show_help() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "List local and/or remote Maven publications for XTC projects"
    echo "Default: Lists both local and remote publications for xdk and xtc-plugin"
    echo ""
    echo "OPTIONS:"
    echo "  -p, --project PROJECT    Project name (e.g., 'xdk', 'xtc-plugin')"
    echo "  -l, --local             List only local publications from ~/.m2/repository"
    echo "  -r, --remote            List only remote publications from GitHub Packages"
    echo "  -z, --include-zip       Include .zip files in local listings"
    echo "  -t, --token TOKEN       GitHub token (can also use GITHUB_TOKEN env var)"
    echo "  -h, --help              Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                                    # List all publications for xdk and xtc-plugin"
    echo "  $0 --project xdk --local              # List local xdk publications only"
    echo "  $0 --project xtc-plugin --remote      # List remote xtc-plugin publications only"
    echo "  $0 --local                            # List local publications for both projects"
    echo "  $0 --remote                           # List remote publications for both projects"
}

list_local_publications() {
    local project=$1
    local include_zip=$2
    local repo_dir="$HOME/.m2/repository/org/xtclang/$project"

    if [[ ! -d "$repo_dir" ]]; then
        echo "[$project] No local publications found"
        return
    fi

    echo "[$project] Local Maven publications in $repo_dir:"

    # Show files with last modified timestamps (use ls -lt for modification time)
    find "$repo_dir" -type f \( -name "*.jar" -o -name "*.pom" ${include_zip:+-o -name "*.zip"} \) -exec ls -lt {} \; | while read -r line; do
        local timestamp=$(echo "$line" | awk '{print $6, $7, $8}')
        local file=$(echo "$line" | awk '{print $NF}')
        local filename=${file##*/}
        echo "[$project]   $filename (last modified: $timestamp)"
    done
}

get_maven_metadata_timestamp() {
    local package_name="$1"
    local token="$2"

    local metadata_url="https://maven.pkg.github.com/xtclang/xvm/${package_name//.//}/maven-metadata.xml"

    # Use curl with timeout and proper status code checking
    local http_status metadata

    http_status=$(curl -s --max-time 5 -w "%{http_code}" -H "Authorization: Bearer $token" "$metadata_url" -o /tmp/maven_metadata_$$.xml 2>/dev/null || echo "000")

    if [[ "$http_status" == "200" ]]; then
        metadata=$(cat /tmp/maven_metadata_$$.xml 2>/dev/null || echo "")
        rm -f /tmp/maven_metadata_$$.xml

        if [[ -n "$metadata" && "$metadata" == *"<lastUpdated>"* ]]; then
            # Parse XML exactly like the Kotlin regex: <lastUpdated>(\d{14})</lastUpdated>
            local last_updated
            last_updated=$(echo "$metadata" | grep -o '<lastUpdated>[0-9]\{14\}</lastUpdated>' | sed 's/<[^>]*>//g' || echo "")

            if [[ -n "$last_updated" && ${#last_updated} -eq 14 ]]; then
                # Convert: 20250923124545 -> 2025-09-23 12:45:45 (exactly like Kotlin)
                local year=${last_updated:0:4}
                local month=${last_updated:4:2}
                local day=${last_updated:6:2}
                local hour=${last_updated:8:2}
                local minute=${last_updated:10:2}
                local second=${last_updated:12:2}
                echo "$year-$month-$day $hour:$minute:$second"
                return 0
            fi
        fi
    else
        rm -f /tmp/maven_metadata_$$.xml
    fi

    return 1
}

list_maven_central_publications() {
    local project="$1"

    echo "[$project] Checking Maven Central snapshots..."

    # Check snapshots repository
    local snapshot_url="https://central.sonatype.com/repository/maven-snapshots/org/xtclang/$project/"
    local snapshot_metadata_url="https://central.sonatype.com/repository/maven-snapshots/org/xtclang/$project/maven-metadata.xml"

    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" "$snapshot_metadata_url" --max-time 5 2>/dev/null || echo "000")

    if [[ "$http_code" == "200" ]]; then
        # Fetch and parse maven-metadata.xml
        local metadata
        metadata=$(curl -s "$snapshot_metadata_url" --max-time 5 2>/dev/null || echo "")

        if [[ -n "$metadata" ]]; then
            # Extract versions (look for <version> tags)
            local versions
            versions=$(echo "$metadata" | grep -o '<version>[^<]*</version>' | sed 's/<[^>]*>//g' | grep SNAPSHOT | head -3)

            if [[ -n "$versions" ]]; then
                echo "[$project]   Maven Central snapshots found:"
                while IFS= read -r version; do
                    # Get timestamp for this version
                    local version_metadata_url="https://central.sonatype.com/repository/maven-snapshots/org/xtclang/$project/$version/maven-metadata.xml"
                    local version_metadata
                    version_metadata=$(curl -s "$version_metadata_url" --max-time 5 2>/dev/null || echo "")

                    if [[ -n "$version_metadata" && "$version_metadata" == *"<lastUpdated>"* ]]; then
                        local last_updated
                        last_updated=$(echo "$version_metadata" | grep -o '<lastUpdated>[0-9]\{14\}</lastUpdated>' | sed 's/<[^>]*>//g' || echo "")

                        if [[ -n "$last_updated" && ${#last_updated} -eq 14 ]]; then
                            local year=${last_updated:0:4}
                            local month=${last_updated:4:2}
                            local day=${last_updated:6:2}
                            local hour=${last_updated:8:2}
                            local minute=${last_updated:10:2}
                            local second=${last_updated:12:2}
                            echo "[$project]     $version (updated: $year-$month-$day $hour:$minute:$second)"
                        else
                            echo "[$project]     $version"
                        fi
                    else
                        echo "[$project]     $version"
                    fi
                done <<< "$versions"
                echo "[$project]   Note: Sonatype has temporarily disabled UI browsing for snapshots"
                echo "[$project]   Artifacts can still be consumed normally via Maven/Gradle"
            else
                echo "[$project]   No snapshots found"
            fi
        else
            echo "[$project]   Unable to fetch metadata"
        fi
    elif [[ "$http_code" == "404" ]]; then
        echo "[$project]   Not published to Maven Central snapshots"
    else
        echo "[$project]   Maven Central check failed (HTTP $http_code)"
    fi

    # Check releases repository
    echo "[$project] Checking Maven Central releases..."
    local release_metadata_url="https://repo1.maven.org/maven2/org/xtclang/$project/maven-metadata.xml"

    http_code=$(curl -s -o /dev/null -w "%{http_code}" "$release_metadata_url" --max-time 5 2>/dev/null || echo "000")

    if [[ "$http_code" == "200" ]]; then
        local metadata
        metadata=$(curl -s "$release_metadata_url" --max-time 5 2>/dev/null || echo "")

        if [[ -n "$metadata" ]]; then
            local versions
            versions=$(echo "$metadata" | grep -o '<version>[^<]*</version>' | sed 's/<[^>]*>//g' | grep -v SNAPSHOT | head -3)

            if [[ -n "$versions" ]]; then
                echo "[$project]   Maven Central releases found:"
                while IFS= read -r version; do
                    echo "[$project]     $version"
                done <<< "$versions"
            else
                echo "[$project]   No releases found"
            fi
        fi
    elif [[ "$http_code" == "404" ]]; then
        echo "[$project]   Not published to Maven Central releases"
    else
        echo "[$project]   Maven Central releases check failed (HTTP $http_code)"
    fi
}

list_remote_publications() {
    local project=${1:-}

    # Get GitHub token from gh CLI
    local github_token=""
    if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
        github_token=$(gh auth token 2>/dev/null || echo "")
    fi

    if [[ -z "$github_token" ]]; then
        echo "Unable to get GitHub token - cannot list remote publications"
        echo "Please authenticate with 'gh auth login'"
        return 1
    fi

    if [[ -n "$project" ]]; then
        echo "[$project] Project would publish to GitHub packages as org.xtclang:$project"
        list_maven_central_publications "$project"
        return
    fi

    echo "Fetching GitHub packages for xtclang/xvm..."

    # Use gh CLI to call GitHub API exactly like GitHubTasks.kt
    local packages_json
    packages_json=$(gh api 'orgs/xtclang/packages?package_type=maven' 2>/dev/null || echo "[]")

    echo "Found packages:"

    # Parse packages using jq exactly like the Kotlin parseJsonArray logic
    # Use process substitution to avoid subshell issues with environment variables
    while IFS= read -r package_obj; do
        local package_name
        package_name=$(echo "$package_obj" | jq -r '.name // empty')
        [[ -z "$package_name" ]] && continue

        local version_count
        version_count=$(echo "$package_obj" | jq -r '.version_count // 0')

        if [[ "$version_count" -le 0 ]]; then
            echo "  $package_name - no versions"
            continue
        fi

        # Clean up display name
        local display_name="$package_name"
        case "$package_name" in
            "org.xtclang.xtc-plugin.org.xtclang.xtc-plugin.gradle.plugin")
                display_name="xtc-plugin (Gradle Plugin Portal marker)"
                ;;
            "org.xtclang.xtc-plugin")
                display_name="xtc-plugin"
                ;;
            "org.xtclang.xdk")
                display_name="xdk"
                ;;
        esac

        echo "  $display_name"

        # Get versions exactly like GitHubTasks.kt
        local encoded_name
        encoded_name=$(printf '%s' "$package_name" | jq -sRr @uri)
        local versions_json
        versions_json=$(gh api "orgs/xtclang/packages/maven/$encoded_name/versions" 2>/dev/null || echo "[]")

        if [[ "$versions_json" == "[]" ]]; then
            echo "    No version information available"
            echo ""
            continue
        fi

        # Check if we have SNAPSHOT versions (simplified logic)
        local has_snapshots=false
        if echo "$versions_json" | jq -e '.[] | select(.name | contains("SNAPSHOT"))' >/dev/null 2>&1; then
            has_snapshots=true
        fi


        # For SNAPSHOT versions, get real Maven metadata timestamp (like GitHubTasks.kt did)
        if [[ "$has_snapshots" == "true" ]]; then
            local maven_timestamp
            maven_timestamp=$(get_maven_metadata_timestamp "$package_name" "$github_token" 2>/dev/null || echo "")

            if [[ -n "$maven_timestamp" ]]; then
                echo "    Latest artifacts: $maven_timestamp (from Maven metadata)"
            else
                echo "    Latest artifacts: Unable to fetch Maven metadata"
            fi
        fi

        # Show GitHub API versions as supplementary info (like GitHubTasks.kt)
        echo "$versions_json" | jq -r '.[0:3][] | "    \(.name) (GitHub API: \(.updated_at))"' 2>/dev/null || echo "    No GitHub API version info"

        local total_versions
        total_versions=$(echo "$versions_json" | jq '. | length' 2>/dev/null || echo 0)
        if [[ "$total_versions" -gt 3 ]]; then
            echo "    ... and $((total_versions - 3)) more versions (GitHub API)"
        fi

        echo ""
    done < <(echo "$packages_json" | jq -c '.[]')

    echo "View all packages: https://github.com/xtclang/xvm/packages"

    # Check Maven Central for both projects
    echo ""
    echo "=== Maven Central Publications ==="
    list_maven_central_publications "xdk"
    echo ""
    list_maven_central_publications "xtc-plugin"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -p|--project)
            PROJECT_NAME="$2"
            shift 2
            ;;
        -l|--local)
            LIST_LOCAL=true
            shift
            ;;
        -r|--remote)
            LIST_REMOTE=true
            shift
            ;;
        -z|--include-zip)
            INCLUDE_ZIP=true
            shift
            ;;
        -t|--token)
            GITHUB_TOKEN="$2"
            shift 2
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            show_help >&2
            exit 1
            ;;
    esac
done

# Set defaults if no options specified
if [[ "$LIST_LOCAL" == "false" && "$LIST_REMOTE" == "false" ]]; then
    LIST_LOCAL=true
    LIST_REMOTE=true
fi

# Execute requested operations
if [[ "$LIST_LOCAL" == "true" ]]; then
    if [[ -z "$PROJECT_NAME" ]]; then
        # Default to listing both main projects
        echo "=== Local Publications ==="
        list_local_publications "xdk" "$INCLUDE_ZIP"
        list_local_publications "xtc-plugin" "$INCLUDE_ZIP"
    else
        list_local_publications "$PROJECT_NAME" "$INCLUDE_ZIP"
    fi
fi

if [[ "$LIST_REMOTE" == "true" ]]; then
    if [[ "$LIST_LOCAL" == "true" && -z "$PROJECT_NAME" ]]; then
        echo ""
        echo "=== Remote Publications ==="
    fi
    list_remote_publications "$PROJECT_NAME"
fi