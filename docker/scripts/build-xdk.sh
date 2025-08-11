#!/bin/bash
set -euo pipefail

# XDK Builder Script
# Downloads pre-built distribution ZIP or builds from source

TARGET_ARCH="${TARGETARCH:-}"

echo "🔧 XDK Builder"
echo "  Target: ${TARGETOS}/${TARGETARCH}"
echo "  Branch: ${GH_BRANCH}"
echo "  Commit: ${GH_COMMIT}"
echo "  Distribution URL: ${DIST_ZIP_URL:-'build from source'}"

# Use Docker arch names directly (no mapping needed - launchers now use Docker conventions)
arch_name() { 
    echo "$1"
}

# Platform validation to prevent cross-architecture contamination
validate_platform() {
    local expected_arch="$1"
    local actual_arch=$(uname -m)
    
    echo "🔍 Platform validation:"
    echo "  Container arch: $actual_arch, Target arch: $expected_arch"
    
    # Map Docker arch to uname arch
    local expected_uname
    case "$expected_arch" in
        amd64) expected_uname="x86_64" ;;
        arm64) expected_uname="aarch64" ;;
        *) echo "❌ Unknown target architecture: $expected_arch"; exit 1 ;;
    esac
    
    [ "$actual_arch" = "$expected_uname" ] || {
        echo "❌ Architecture mismatch! Container=$actual_arch, Expected=$expected_uname"
        echo "❌ This could cause cross-platform contamination!"
        exit 1
    }
    
    echo "✅ Platform validation passed"
}

# Platform validation
[ -n "$TARGET_ARCH" ] && validate_platform "$TARGET_ARCH"


# Check if a pre-built distribution is available (CI artifact mode)
if [ -n "${DIST_ZIP_URL:-}" ] && [ "$DIST_ZIP_URL" != "test-local" ]; then
    echo "📦 Using pre-built distribution: $DIST_ZIP_URL"
    if [ -f "$DIST_ZIP_URL" ]; then
        # Local file path (from CI build context)
        DIST_ZIP="$DIST_ZIP_URL"
        echo "✅ Using CI distribution ZIP: $DIST_ZIP"
    else
        # URL - download it (future external URLs)
        curl -fsSL "$DIST_ZIP_URL" -o dist.zip
        DIST_ZIP="dist.zip"
        echo "✅ Downloaded distribution ZIP from: $DIST_ZIP_URL"
    fi
elif [ -f "ci-dist.zip" ]; then
    # CI artifact mode - platform-agnostic pre-built distribution
    echo "📦 Using pre-built CI artifact: ci-dist.zip"
    DIST_ZIP="ci-dist.zip"
    echo "✅ Using CI distribution ZIP: $DIST_ZIP"
else
    # Build distribution ZIP from source (local development)
    echo "🏗️ Building XDK distribution from source..."
    
    # Source git information from clone step to get the actual commit
    if [ -f "/tmp/git-info.env" ]; then
        source /tmp/git-info.env
        echo "📍 Updated commit from git: $GH_COMMIT"
    fi
    
    # Show current environment configuration (all set via Dockerfile ENV)
    echo "🔧 JVM Configuration:"
    echo "  JAVA_OPTS: $JAVA_OPTS" 
    echo "  GRADLE_OPTS: $GRADLE_OPTS"
    echo "  GRADLE_USER_HOME: $GRADLE_USER_HOME"
    
    # Print JVM flags for debugging (without causing exit)
    echo "🔍 JVM Flag Information:"
    java -XX:+UseContainerSupport -XX:+PrintFlagsFinal -version 2>&1 || echo "  Flag printing failed (non-fatal)"
    
    # Container cache inspection function
    inspect_container_cache() {
      local label="${1:-}"
      local gradle_home="$GRADLE_USER_HOME"
      echo "🔍 Container Gradle Cache${label:+ ($label)}:"
      echo "  Location: $gradle_home"
      if [ -d "$gradle_home" ]; then
        local total_size=$(du -sh "$gradle_home" 2>/dev/null | cut -f1 || echo "unknown")
        local total_bytes=$(du -sb "$gradle_home" 2>/dev/null | cut -f1 || echo "unknown")
        echo "  Size: $total_size ($total_bytes bytes)"
        for dir in caches wrapper build-cache; do
          if [ -d "$gradle_home/$dir" ]; then
            local dir_size=$(du -sh "$gradle_home/$dir" 2>/dev/null | cut -f1 || echo "unknown")
            echo "    $dir: $dir_size"
          fi
        done
        return 0
      fi
      echo "  Status: ❌ (will be created)"
      return 0
    }
    
    mkdir -p "$GRADLE_USER_HOME"
    
    # Show Docker BuildKit cache mount info
    echo "🔍 Gradle Cache Inspection:"
    echo "  BuildKit cache mounts at: /root/.gradle/{caches,wrapper,build-cache}"
    ls -la /root/.gradle/ 2>/dev/null | head -5 || echo "  Cache directories will be created"
    
    inspect_container_cache "before build"
    
    # Use NPROC from environment (set via build arg)  
    export GRADLE_OPTS="$GRADLE_OPTS -Dorg.gradle.workers.max=${NPROC} -Dorg.gradle.vfs.watch=false"
    echo "🔧 Final GRADLE_OPTS: $GRADLE_OPTS"
    
    # Show Gradle version
    ./gradlew --version
    
    # Clean any stale distributions with old launcher naming to prevent cache issues
    echo "🧹 Clearing distribution cache to ensure fresh build with current launcher names..."
    rm -rf xdk/build/distributions xdk/build/install
    echo "  Cleared distribution and install cache"
    
    # Build the XDK distribution ZIP with native launchers renamed to xec/xcc
    echo "Building XDK distribution ZIP with native launchers for ${TARGETOS}/${TARGETARCH}..."
    ./gradlew --gradle-user-home="$GRADLE_USER_HOME" -x test -x check xdk:withLaunchersDistZip
    
    echo "Build completed successfully"
    
    inspect_container_cache "after build"
    
    # Find the built distribution ZIP
    echo "📦 Using source-built XDK distribution with native launchers..."
    DIST_ZIP=$(find xdk/build/distributions -name "xdk-*-*.zip" | head -1)
    if [ -z "$DIST_ZIP" ]; then
        echo "❌ No XDK distribution ZIP found"
        ls -la xdk/build/distributions/ || true
        exit 1
    fi
    echo "✅ Built distribution: $DIST_ZIP"
fi

echo "📍 Extracting distribution: $DIST_ZIP"
echo "📋 ZIP file info:"
file "$DIST_ZIP" || echo "Cannot detect file type"
ls -la "$DIST_ZIP" || echo "ZIP file doesn't exist"
unzip -t "$DIST_ZIP" || echo "ZIP test failed"
echo "📋 Extracting ZIP contents..."
unzip "$DIST_ZIP"

# Find any extracted directory and strip the outer directory layer
# Handle both platform-specific (e.g., xdk-0.4.4-SNAPSHOT-linux_aarch64) and platform-agnostic (e.g., xdk0.4.4SNAPSHOT)
echo "📋 Looking for extracted XDK directory..."
ls -la . | grep "^d" || true  # Show directories for debugging

# Find the extracted distribution directory (exclude the source xdk directory)
XDK_EXTRACTED=$(find . -maxdepth 1 -name "xdk*" -type d -not -name "xdk" | head -1)
if [ -z "$XDK_EXTRACTED" ]; then
    echo "❌ No extracted XDK directory found"
    echo "📋 Contents of current directory:"
    ls -la . | head -10
    exit 1
fi

echo "📁 Stripping outer directory layer from: $XDK_EXTRACTED"
# Move contents up one level to create clean 'xdk/' structure

# Remove existing xdk directory if it exists from source (it will be replaced with distribution)
if [ -d "xdk" ]; then
    echo "📁 Removing existing xdk directory (will be replaced with distribution)..."
    rm -rf xdk
fi

echo "📁 Creating clean xdk/ directory..."
mkdir -p xdk

echo "📁 Moving contents from $XDK_EXTRACTED to xdk/..."
# Use explicit loop instead of glob to avoid SIGPIPE on large file lists
find "$XDK_EXTRACTED" -mindepth 1 -maxdepth 1 -exec mv {} xdk/ \;

echo "📁 Removing empty extracted directory: $XDK_EXTRACTED"
rmdir "$XDK_EXTRACTED" || {
    echo "⚠️  Could not remove $XDK_EXTRACTED (not empty?)"
    ls -la "$XDK_EXTRACTED" || true
}
echo "📁 Created clean xdk/ directory structure"

# Install correct native launchers based on target architecture
echo "🚀 Installing native launchers for ${TARGETOS}/${TARGETARCH}"
LAUNCHER_EXT=""
[ "${TARGETOS}" = "windows" ] && LAUNCHER_EXT=".exe"

# Check if we're using pre-built distribution (launchers already in xdk/bin/) or source build
if [ -f "xdk/bin/${TARGETOS}_launcher_${TARGETARCH}${LAUNCHER_EXT}" ]; then
    # Pre-built distribution - launchers already exist, just rename them
    echo "📦 Using pre-built launchers from distribution"
    cp "xdk/bin/${TARGETOS}_launcher_${TARGETARCH}${LAUNCHER_EXT}" "xdk/bin/xec${LAUNCHER_EXT}"
    cp "xdk/bin/${TARGETOS}_launcher_${TARGETARCH}${LAUNCHER_EXT}" "xdk/bin/xcc${LAUNCHER_EXT}"
    chmod +x "xdk/bin/xec${LAUNCHER_EXT}" "xdk/bin/xcc${LAUNCHER_EXT}"
    echo "✅ Installed pre-built launchers: ${TARGETOS}_launcher_${TARGETARCH}${LAUNCHER_EXT} → xec, xcc"
elif [ -f "javatools_launcher/src/main/resources/exe/${TARGETOS}_launcher_${TARGETARCH}${LAUNCHER_EXT}" ]; then
    # Source build - copy launchers from source tree
    echo "🔧 Using source launchers from build tree"
    LAUNCHER_FILE="javatools_launcher/src/main/resources/exe/${TARGETOS}_launcher_${TARGETARCH}${LAUNCHER_EXT}"
    cp "$LAUNCHER_FILE" "xdk/bin/xec${LAUNCHER_EXT}"
    cp "$LAUNCHER_FILE" "xdk/bin/xcc${LAUNCHER_EXT}"
    chmod +x "xdk/bin/xec${LAUNCHER_EXT}" "xdk/bin/xcc${LAUNCHER_EXT}"
    echo "✅ Installed source launchers: $LAUNCHER_FILE → xec, xcc"
else
    echo "❌ No native launchers found for ${TARGETOS}/${TARGETARCH}"
    echo "📋 Checked locations:"
    echo "  - xdk/bin/${TARGETOS}_launcher_${TARGETARCH}${LAUNCHER_EXT} (pre-built)"
    echo "  - javatools_launcher/src/main/resources/exe/${TARGETOS}_launcher_${TARGETARCH}${LAUNCHER_EXT} (source)"
    ls -la xdk/bin/ | head -5 || true
    exit 1
fi

# Create build info (consistent variable naming)
BUILD_DATE_VAL="${BUILD_DATE:-$(date -u +"%Y-%m-%dT%H:%M:%SZ")}"

echo "📍 Creating xvm.json in: xdk/"

cat > "xdk/xvm.json" << JSONEOF
{
  "buildDate": "$BUILD_DATE_VAL",
  "commit": "${GH_COMMIT:-unknown}", 
  "branch": "${GH_BRANCH:-master}",
  "version": "${GH_BRANCH:-master}",
  "platform": "${TARGETOS}/${TARGETARCH}"
}
JSONEOF

echo "✅ XDK build complete"

# Output final commit hash for potential use in additional tagging
echo "FINAL_COMMIT=${GH_COMMIT:-unknown}"
