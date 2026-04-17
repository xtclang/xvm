#!/usr/bin/env bash
#
# Upload a locally-staged Maven repository (directory tree) to a remote Maven
# HTTP endpoint, one artifact at a time.
#
# Expected input: <repo-root> is a plain filesystem Maven 2 layout, i.e. the
# exact directory tree Gradle produces when publishing to a file:// repository
# (`publishAllPublicationsTo<RepoName>Repository` where the repository URL is
# a file:// path). Both the XDK and the Gradle plugin are staged into the
# SAME <repo-root> by `publishSnapshotBundle` (see root build.gradle.kts), so
# a single invocation of this script uploads every artifact produced by the
# build. Concretely, <repo-root> contains three publications, all under
# group `org.xtclang`:
#
#   <repo-root>/
#   └── org/xtclang/
#         ├── xdk/<version>/                                    (XDK distribution, groupId=org.xtclang, artifactId=xdk)
#         │     ├── xdk-<version>.zip                           (distZip — the XDK is a ZIP, not a jar)
#         │     ├── xdk-<version>.zip.asc                       (GPG signature, signed builds only)
#         │     ├── xdk-<version>.zip.md5
#         │     ├── xdk-<version>.zip.sha1
#         │     ├── xdk-<version>.zip.sha256                    (release mode only — stripped for snapshots)
#         │     ├── xdk-<version>.zip.sha512                    (release mode only — stripped for snapshots)
#         │     ├── xdk-<version>.pom                           (+ .asc/.md5/.sha1/.sha256/.sha512 sidecars)
#         │     └── maven-metadata.xml                          (+ sidecars; snapshots only)
#         │
#         ├── xtc-plugin/<version>/                             (Gradle plugin jar, groupId=org.xtclang, artifactId=xtc-plugin)
#         │     ├── xtc-plugin-<version>.jar                    (plugin classes)
#         │     ├── xtc-plugin-<version>-sources.jar            (and .asc/.md5/.sha1/.sha256/.sha512)
#         │     ├── xtc-plugin-<version>.pom                    (+ sidecars)
#         │     ├── xtc-plugin-<version>.module                 (Gradle Module Metadata, + sidecars)
#         │     └── maven-metadata.xml                          (+ sidecars; snapshots only)
#         │
#         └── xtc-plugin/org.xtclang.xtc-plugin.gradle.plugin/<version>/
#               ├── org.xtclang.xtc-plugin.gradle.plugin-<version>.pom   (marker POM — points at xtc-plugin:xtc-plugin)
#               └── maven-metadata.xml                          (+ sidecars; snapshots only)
#
# For SNAPSHOT versions the `<artifactId>-<version>` filenames are expanded to
# timestamped form (e.g. `xdk-1.0.0-20260417.100000-1.zip`, plus a
# build-number-per-timestamp); Central rewrites the snapshot
# `maven-metadata.xml` server-side from those timestamps, which is why we
# upload artifacts before metadata in two strict phases.
#
# The script is repository-agnostic (works for Maven Central, Central Portal
# Snapshots, GitHub Packages, or any other HTTP-PUT Maven repo) and stateless:
# every file is PUT to `<base-url>/<path-relative-to-repo-root>` with Basic
# auth. The `org/xtclang` subtree is sanity-checked to catch "wrong directory
# passed in" accidents early.
#
# Not handled here (by design): GPG signing, metadata generation, version
# bumping — all of that happens in Gradle before this script runs.

set -euo pipefail

usage() {
    cat >&2 <<'EOF'
Usage: upload-maven-repo.sh <repo-root> <base-url> <username> <password> [label] [mode]
  mode: snapshot (default) or release
    snapshot mode drops .sha256/.sha512 sidecars (Central's snapshot repo only
    requires .md5/.sha1, plus .asc for signed artifacts).
    release mode uploads every file in the staged repository as-is.
EOF
}

if [ "$#" -lt 4 ] || [ "$#" -gt 6 ]; then
    usage
    exit 2
fi

REPO_ROOT="$1"
BASE_URL="${2%/}"
USERNAME="$3"
PASSWORD="$4"
LABEL="${5:-Maven repository}"
MODE="${6:-snapshot}"

case "$MODE" in
    snapshot|release) ;;
    *)
        echo "❌ Invalid mode: $MODE (expected: snapshot|release)" >&2
        exit 2
        ;;
esac

if [ ! -d "$REPO_ROOT" ]; then
    echo "❌ Maven repository root not found: $REPO_ROOT" >&2
    exit 1
fi

if [ ! -d "$REPO_ROOT/org/xtclang" ]; then
    echo "❌ Expected org/xtclang subtree not found under: $REPO_ROOT" >&2
    exit 1
fi

# Build the artifact list. In snapshot mode we drop .sha256/.sha512 sidecars:
# Central's snapshot repo only requires .md5/.sha1 (plus .asc for signed jars),
# so the 256/512 files are ~30% of the file count with no upside on snapshots.
# Release uploads must stay complete — Central Portal release validation is
# stricter and there's no reason to risk a rejected deployment.
FIND_ARTIFACT_ARGS=(-type f ! -name 'maven-metadata*')
if [ "$MODE" = "snapshot" ]; then
    FIND_ARTIFACT_ARGS+=(! -name '*.sha256' ! -name '*.sha512')
fi

mapfile -t ARTIFACT_FILES < <(find "$REPO_ROOT" "${FIND_ARTIFACT_ARGS[@]}" | sort)
mapfile -t METADATA_FILES < <(find "$REPO_ROOT" -type f -name 'maven-metadata*' | sort)

TOTAL_COUNT=$(( ${#ARTIFACT_FILES[@]} + ${#METADATA_FILES[@]} ))
echo "📦 Upload target       : $LABEL"
echo "📦 Mode                : $MODE"
echo "📦 Repository root     : $REPO_ROOT"
echo "📦 Destination URL     : $BASE_URL"
echo "📦 Files to upload     : $TOTAL_COUNT (artifacts=${#ARTIFACT_FILES[@]}, metadata=${#METADATA_FILES[@]})"

# Dump the resolved staged repository tree so it is visible both in CI logs and
# to a human running the script locally. This catches "wrong path resolved"
# accidents (empty tree, missing publication, stale version dir from a previous
# build) before any upload is attempted. Portable: no `tree`, no GNU `-printf`,
# no BSD/GNU stat flag differences — just `find` + `wc -c` for file sizes.
# Output is capped to keep CI logs reasonable on pathological staging layouts.
echo "📋 Resolved repository contents ($REPO_ROOT):"
LISTING_CAP=200
ALL_FILES_COUNT=$(find "$REPO_ROOT" -type f | wc -l | tr -d ' ')
(
    cd "$REPO_ROOT"
    find . -type f | sort | head -n "$LISTING_CAP" | while IFS= read -r f; do
        size=$(wc -c < "$f" 2>/dev/null || echo 0)
        printf "  %10s  %s\n" "$size" "${f#./}"
    done
)
if [ "$ALL_FILES_COUNT" -gt "$LISTING_CAP" ]; then
    echo "  … (truncated: $ALL_FILES_COUNT files total, showing first $LISTING_CAP)"
fi

# Parallelize via `curl -Z`: HTTP/2 multiplexing + TLS session reuse over a
# small pool of authenticated connections. --parallel-max 4 keeps us a polite
# client (server sees one client pipelining, not a swarm of xargs processes)
# and stays well below Central's rate-limit threshold. --retry-all-errors with
# --retry-delay 2 gives us exponential backoff on transient 429/5xx.
#
# Two phases with a hard barrier between them:
#   1. artifacts + signatures + checksums
#   2. maven-metadata.xml (+ its sidecars)
# Sonatype rewrites snapshot metadata server-side from the timestamped files
# we PUT, so metadata must land strictly after the artifacts it references —
# otherwise a consumer resolving in that window gets a 404.
upload_batch() {
    local phase="$1"
    shift
    local files=("$@")

    if [ "${#files[@]}" -eq 0 ]; then
        echo "… $phase: nothing to upload"
        return 0
    fi

    echo "🚚 $phase: uploading ${#files[@]} file(s)"
    local curl_args=(
        --fail-with-body
        --silent
        --show-error
        --http2
        --parallel
        --parallel-max 4
        --retry 5
        --retry-all-errors
        --retry-delay 2
        --user "$USERNAME:$PASSWORD"
    )
    local file relative target
    for file in "${files[@]}"; do
        relative="${file#$REPO_ROOT/}"
        target="$BASE_URL/$relative"
        echo "⬆️  $relative"
        curl_args+=(--upload-file "$file" "$target")
    done
    curl "${curl_args[@]}"
}

upload_batch "Phase 1/2: artifacts" "${ARTIFACT_FILES[@]}"
upload_batch "Phase 2/2: metadata"  "${METADATA_FILES[@]}"

echo "✅ Maven repository upload complete"
