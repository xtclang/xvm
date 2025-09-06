#!/bin/bash

# Fix trailing whitespace and missing newlines
# This script will:
# 1. Remove trailing whitespace from all lines
# 2. Remove trailing blank lines
# 3. Ensure file ends with exactly one newline

set -e

echo "Finding files to fix..."

# Find all text files (excluding build directories and git)
find . -type f \( \
    -name "*.java" -o \
    -name "*.x" -o \
    -name "*.md" -o \
    -name "*.yml" -o \
    -name "*.yaml" -o \
    -name "*.json" -o \
    -name "*.txt" -o \
    -name "*.sh" -o \
    -name "*.kts" -o \
    -name "*.gradle" -o \
    -name "*.properties" -o \
    -name "*.xml" -o \
    -name "*.h" -o \
    -name "*.c" \
\) \
-not -path "./build/*" \
-not -path "./.gradle/*" \
-not -path "*/build/*" \
-not -path "./.git/*" \
-not -path "*/target/*" \
-not -path "*/classes/*" > /tmp/files_to_fix.txt

total_files=$(wc -l < /tmp/files_to_fix.txt)
echo "Found $total_files files to check"

fixed_files=0
processed=0

while IFS= read -r file; do
    processed=$((processed + 1))
    if [ $((processed % 100)) -eq 0 ]; then
        echo "Processed $processed/$total_files files..."
    fi

    # Skip empty files
    if [ ! -s "$file" ]; then
        continue
    fi

    # Create temp file
    temp_file=$(mktemp)

    # Remove trailing whitespace from lines and trailing blank lines, ensure final newline
    sed 's/[[:space:]]*$//' "$file" | sed -e :a -e '/^\s*$/N' -e 's/\n\s*$//' -e 'ta' > "$temp_file"

    # Ensure file ends with exactly one newline (if it has content)
    if [ -s "$temp_file" ]; then
        # Add newline if file doesn't end with one
        if [ "$(tail -c 1 "$temp_file" 2>/dev/null)" != "" ]; then
            echo >> "$temp_file"
        fi
    fi

    # Check if file changed
    if ! cmp -s "$file" "$temp_file"; then
        mv "$temp_file" "$file"
        fixed_files=$((fixed_files + 1))
    else
        rm "$temp_file"
    fi
done < /tmp/files_to_fix.txt

echo "Fixed $fixed_files out of $total_files files"
echo "Done!"

rm -f /tmp/files_to_fix.txt
