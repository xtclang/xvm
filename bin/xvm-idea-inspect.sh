#!/bin/bash

# IntelliJ Warnings Dumper
# This script connects to IntelliJ IDEA and extracts all warnings/inspections
# from the currently open project

set -euo pipefail

# Default IntelliJ IDEA installation paths
INTELLIJ_PATHS=(
    # User Applications directory (most common for manual installs)
    "$HOME/Applications/IntelliJ IDEA Ultimate.app/Contents/bin/inspect.sh"
    "$HOME/Applications/IntelliJ IDEA.app/Contents/bin/inspect.sh"
    "$HOME/Applications/IntelliJ IDEA CE.app/Contents/bin/inspect.sh"
    # JetBrains Toolbox paths
    "$HOME/Library/Application Support/JetBrains/Toolbox/apps/IDEA-U/ch-0/*/IntelliJ IDEA.app/Contents/bin/inspect.sh"
    "$HOME/Library/Application Support/JetBrains/Toolbox/apps/IDEA-C/ch-0/*/IntelliJ IDEA CE.app/Contents/bin/inspect.sh"
    "$HOME/Library/Application Support/JetBrains/Toolbox/apps/intellij/ch-0/*/IntelliJ IDEA.app/Contents/bin/inspect.sh"
    # System Applications directory
    "/Applications/IntelliJ IDEA Ultimate.app/Contents/bin/inspect.sh"
    "/Applications/IntelliJ IDEA.app/Contents/bin/inspect.sh"
    "/Applications/IntelliJ IDEA CE.app/Contents/bin/inspect.sh"
    "/Applications/IntelliJ IDEA Community Edition.app/Contents/bin/inspect.sh"
    "/usr/local/bin/idea"
    "/opt/idea/bin/idea.sh"
)

# Find IntelliJ installation
INTELLIJ_BIN=""
for path_pattern in "${INTELLIJ_PATHS[@]}"; do
    # Handle paths with wildcards
    if [[ "$path_pattern" == *"*"* ]]; then
        # Expand wildcards
        for path in $path_pattern; do
            if [[ -x "$path" ]]; then
                INTELLIJ_BIN="$path"
                break 2
            fi
        done
    else
        # Direct path check
        if [[ -x "$path_pattern" ]]; then
            INTELLIJ_BIN="$path_pattern"
            break
        fi
    fi
done

if [[ -z "$INTELLIJ_BIN" ]]; then
    echo "❌ IntelliJ IDEA not found in standard locations."
    echo "Please specify the path manually:"
    echo "  INTELLIJ_BIN=/path/to/idea $0"
    exit 1
fi

# Get the repo root (where this script's bin/ directory is located)
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

PROJECT_DIR="${1:-$REPO_ROOT}"
OUTPUT_FILE="${2:-$REPO_ROOT/inspections.log}"

echo "🔍 Dumping IntelliJ warnings for project: $PROJECT_DIR"
echo "📄 Output will be saved to: $OUTPUT_FILE"

# Check if project directory exists
if [[ ! -d "$PROJECT_DIR" ]]; then
    echo "❌ Project directory does not exist: $PROJECT_DIR"
    exit 1
fi

# Use custom no-typos inspection profile if available, otherwise use project default
if [[ -f "$(dirname "$0")/xvm-inspection-profile.xml" ]]; then
    TEMP_PROFILE="$(dirname "$0")/xvm-inspection-profile.xml"
    echo "📋 Using XVM custom inspection profile"
else
    TEMP_PROFILE="-e"
    echo "📋 Using project default inspection profile"
fi

echo "🚀 Running IntelliJ inspections..."

# Run IntelliJ in headless mode to perform inspections
if [[ "$TEMP_PROFILE" == "-e" ]]; then
    "$INTELLIJ_BIN" "$PROJECT_DIR" -e "$OUTPUT_FILE" -v2 -format plain
else
    "$INTELLIJ_BIN" "$PROJECT_DIR" "$TEMP_PROFILE" "$OUTPUT_FILE" -v2 -format plain
fi

# Check if output exists
if [[ -f "$OUTPUT_FILE" ]]; then
    echo "✅ IntelliJ inspection completed successfully"
    echo "📄 Results saved to: $OUTPUT_FILE"
else
    echo "❌ IntelliJ inspection failed to generate output"
    echo "📄 Expected output file: $OUTPUT_FILE"
    exit 1
fi

# No cleanup needed since we're using existing profiles or moved the output file

