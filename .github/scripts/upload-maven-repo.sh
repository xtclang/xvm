#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 4 ] || [ "$#" -gt 5 ]; then
    echo "Usage: $0 <repo-root> <base-url> <username> <password> [label]" >&2
    exit 2
fi

REPO_ROOT="$1"
BASE_URL="${2%/}"
USERNAME="$3"
PASSWORD="$4"
LABEL="${5:-Maven repository}"

if [ ! -d "$REPO_ROOT" ]; then
    echo "❌ Maven repository root not found: $REPO_ROOT" >&2
    exit 1
fi

if [ ! -d "$REPO_ROOT/org/xtclang" ]; then
    echo "❌ Expected org/xtclang subtree not found under: $REPO_ROOT" >&2
    exit 1
fi

mapfile -t ARTIFACT_FILES < <(find "$REPO_ROOT" -type f ! -name 'maven-metadata*' | sort)
mapfile -t METADATA_FILES < <(find "$REPO_ROOT" -type f -name 'maven-metadata*' | sort)

TOTAL_COUNT=$(( ${#ARTIFACT_FILES[@]} + ${#METADATA_FILES[@]} ))
echo "📦 Upload target: $LABEL"
echo "📦 Uploading Maven repository from: $REPO_ROOT"
echo "📦 Destination URL: $BASE_URL"
echo "📦 Files: $TOTAL_COUNT"

upload_file() {
    local file="$1"
    local relative_path="${file#$REPO_ROOT/}"
    local target_url="$BASE_URL/$relative_path"
    echo "⬆️  $relative_path"
    curl \
        --fail-with-body \
        --silent \
        --show-error \
        --retry 3 \
        --retry-all-errors \
        -u "$USERNAME:$PASSWORD" \
        --upload-file "$file" \
        "$target_url"
}

for file in "${ARTIFACT_FILES[@]}"; do
    upload_file "$file"
done

for file in "${METADATA_FILES[@]}"; do
    upload_file "$file"
done

echo "✅ Maven repository upload complete"
