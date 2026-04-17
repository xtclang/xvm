#!/bin/sh
set -euo pipefail

# XDK Distribution Extractor Script
# Simplified script that uses pre-built XDK distribution ZIP

DIST_ZIP_URL="${1:-}"

echo "📦 XDK Distribution Extractor"
echo "  Distribution URL: ${DIST_ZIP_URL}"

# Ensure we have a distribution ZIP URL
if [ -z "$DIST_ZIP_URL" ]; then
    echo "❌ ERROR: DIST_ZIP_URL is required but not provided"
    echo "Usage: docker build --build-arg DIST_ZIP_URL=/path/to/xdk.zip ..."
    exit 1
fi

# Get the distribution ZIP
if [ -f "$DIST_ZIP_URL" ]; then
    # Local file path (most common case - from Gradle build)
    DIST_ZIP="$DIST_ZIP_URL"
    echo "✅ Using local distribution ZIP: $DIST_ZIP"
elif [[ "$DIST_ZIP_URL" =~ ^https?:// ]]; then
    # URL - download it
    echo "📥 Downloading distribution from URL: $DIST_ZIP_URL"
    curl -fsSL "$DIST_ZIP_URL" -o dist.zip
    DIST_ZIP="dist.zip"
    echo "✅ Downloaded distribution ZIP"
else
    echo "❌ ERROR: Invalid DIST_ZIP_URL: $DIST_ZIP_URL"
    echo "Must be either a file path or HTTP(S) URL"
    exit 1
fi

# Verify the ZIP file exists and is valid
echo "📍 Verifying distribution: $DIST_ZIP"
if [ ! -f "$DIST_ZIP" ]; then
    echo "❌ Distribution ZIP not found: $DIST_ZIP"
    exit 1
fi

echo "📋 ZIP file info:"
file "$DIST_ZIP" || echo "Cannot detect file type"
ls -la "$DIST_ZIP"

# Test the ZIP file
echo "📋 Testing ZIP integrity..."
unzip -t "$DIST_ZIP" || {
    echo "❌ ZIP file is corrupted or invalid"
    exit 1
}

# Extract the distribution
echo "📋 Extracting ZIP contents..."
unzip -q "$DIST_ZIP"

# Find the extracted XDK directory
echo "📋 Looking for extracted XDK directory..."
ls -la . | grep "^d" || true  # Show directories for debugging

# Find the extracted distribution directory
XDK_EXTRACTED=$(find . -maxdepth 1 -name "xdk*" -type d | head -1)
if [ -z "$XDK_EXTRACTED" ]; then
    echo "❌ No extracted XDK directory found"
    echo "📋 Contents of current directory:"
    ls -la . | head -10
    exit 1
fi

echo "📁 Found extracted directory: $XDK_EXTRACTED"

# Create clean 'xdk/' structure
echo "📁 Creating clean xdk/ directory structure..."
mkdir -p xdk

# Move contents from extracted directory to xdk/
echo "📁 Moving contents from $XDK_EXTRACTED to xdk/..."
find "$XDK_EXTRACTED" -mindepth 1 -maxdepth 1 -exec mv {} xdk/ \;

# Remove the empty extracted directory
echo "📁 Removing empty extracted directory: $XDK_EXTRACTED"
rmdir "$XDK_EXTRACTED" || {
    echo "⚠️  Could not remove $XDK_EXTRACTED (not empty?)"
    ls -la "$XDK_EXTRACTED" || true
}

# Verify script launchers are present
echo "🚀 Verifying script launchers"
if [ -f "xdk/bin/xec" ] && [ -f "xdk/bin/xcc" ] && [ -f "xdk/bin/xtc" ] && [ -f "xdk/bin/protoc-gen-xtc" ] && [ -f "xdk/bin/protoc-gen-xtc.bat" ]; then
    echo "✅ Script launchers found: xec, xcc, xtc, protoc-gen-xtc"
    chmod +x "xdk/bin/xec" "xdk/bin/xcc" "xdk/bin/xtc" "xdk/bin/protoc-gen-xtc"
else
    echo "❌ Script launchers not found in distribution"
    echo "📋 Contents of xdk/bin/:"
    ls -la xdk/bin/ | head -10 || true
    exit 1
fi

# Create build metadata
echo "📍 Creating xvm.json metadata"
cat > "xdk/xvm.json" << JSONEOF
{
  "buildDate": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "distributionSource": "pre-built"
}
JSONEOF

echo "✅ XDK distribution extraction complete"
echo "📋 Final directory structure:"
ls -la xdk/ | head -10
ls -la xdk/bin/ | head -5
