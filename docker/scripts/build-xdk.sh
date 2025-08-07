#!/bin/bash
set -euo pipefail

# Dual-Mode XDK Builder Script
# Mode 1: Use pre-built artifacts from CI (fast path)  
# Mode 2: Build from source (local development path)

USE_PREBUILT="${USE_PREBUILT_ARTIFACTS:-false}"
TARGET_ARCH="${TARGETARCH:-}"

echo "🔧 XDK Builder - Dual Mode"
echo "  Mode: $([ "$USE_PREBUILT" = "true" ] && echo "Pre-built artifacts (CI)" || echo "Source build (local)")"
echo "  Target: ${TARGETOS}/${TARGETARCH}"
echo "  Branch: ${GH_BRANCH}"
echo "  Commit: ${GH_COMMIT}"

# Map Docker arch to XVM arch names
arch_name() { 
    case "$1" in 
        amd64) echo "x86_64";; 
        arm64) echo "aarch64";; 
        *) echo "$1";; 
    esac; 
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

# Extract and setup pre-built artifacts (early return pattern)
setup_prebuilt_artifacts() {
    echo "📦 Setting up pre-built XDK artifacts..."
    
    [ -d "/artifacts" ] && [ -n "$(ls -A /artifacts 2>/dev/null)" ] || {
        echo "❌ No artifacts found - cannot use pre-built mode"
        exit 1
    }
    
    mkdir -p "$XDK_INSTALL_DIR"
    
    [ -d "/artifacts/xdk/build/install" ] || {
        echo "❌ No XDK install artifacts found"
        exit 1
    }
    
    echo "  Found XDK install artifacts"
    cp -r /artifacts/xdk/build/install/* "$XDK_INSTALL_DIR/"
    
    # Handle launcher artifacts if available
    if find /artifacts -name "*launcher*" -type f | grep -q .; then
        echo "  Found launcher artifacts"
        local arch_name=$(arch_name "${TARGET_ARCH}")
        local launcher_src="/artifacts/javatools_launcher/src/main/resources/exe/linux_launcher_${arch_name}"
        local launcher_dest="$XDK_INSTALL_DIR/xdk/bin"
        
        [ -f "$launcher_src" ] && {
            cp "$launcher_src" "$launcher_dest/xec"
            cp "$launcher_src" "$launcher_dest/xcc" 
            chmod +x "$launcher_dest/xec" "$launcher_dest/xcc"
            echo "  Configured native launchers for $arch_name"
        } || echo "⚠️ Native launcher not found for $arch_name"
    else
        echo "⚠️ No launcher artifacts found - using Java wrapper launchers"
    fi
    
    echo "📁 Extracted XDK structure:"
    find "$XDK_INSTALL_DIR" -maxdepth 3 -type d | head -10
    echo "✅ Pre-built artifacts setup completed"
}

# Platform validation (early return)
[ -n "$TARGET_ARCH" ] && validate_platform "$TARGET_ARCH"

# Pre-built artifacts mode (early return)  
[ "$USE_PREBUILT" = "true" ] && {
    setup_prebuilt_artifacts
    echo "🎉 XDK setup completed using pre-built artifacts"
    exit 0
}

# Source build mode continues below
echo "🏗️ Proceeding with source build mode"

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
  else
    echo "  Status: ❌ (will be created)"
  fi
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

# Build the XDK (skip tests since this is packaging) - use environment variables directly
echo "Building XDK..."
./gradlew --gradle-user-home="$GRADLE_USER_HOME" -x test -x check xdk:installDist

echo "Build completed successfully"

inspect_container_cache "after build"

# Install native launchers (early return pattern)
ARCH_NAME=$(arch_name "${TARGETARCH}")
LAUNCHER_BINARY="javatools_launcher/src/main/resources/exe/linux_launcher_${ARCH_NAME}"

[ -f "$LAUNCHER_BINARY" ] && {
    echo "Installing native launcher: $LAUNCHER_BINARY"
    cp "$LAUNCHER_BINARY" "xdk/build/install/xdk/bin/xec"
    cp "$LAUNCHER_BINARY" "xdk/build/install/xdk/bin/xcc"
    chmod +x "xdk/build/install/xdk/bin/xec" "xdk/build/install/xdk/bin/xcc"
} || {
    echo "⚠️ Warning: Launcher binary not found at $LAUNCHER_BINARY"
    ls -la javatools_launcher/src/main/resources/exe/ || true
}

# Create build info (consistent variable naming)
BUILD_DATE_VAL="${BUILD_DATE:-$(date -u +"%Y-%m-%dT%H:%M:%SZ")}"

cat > "xdk/build/install/xdk/xvm.json" << JSONEOF
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
