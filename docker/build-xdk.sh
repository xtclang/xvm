#!/bin/bash
set -e

# Map Docker arch to XVM arch names
arch_name() { 
    case "$1" in 
        amd64) echo "x86_64";; 
        arm64) echo "aarch64";; 
        *) echo "$1";; 
    esac; 
}

echo "Building XDK for ${TARGETOS}/${TARGETARCH}"

# Configure Gradle for container builds
export GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.caching=true -Dorg.gradle.parallel=true -Dorg.gradle.configureondemand=true -Dorg.gradle.workers.max=4"

# Container cache inspection function
inspect_container_cache() {
  local label="${1:-}"
  local gradle_home="/root/.gradle"
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

chmod +x ./gradlew
mkdir -p /root/.gradle

inspect_container_cache "before build"

# Build the XDK (skip tests since this is packaging)
./gradlew --no-daemon --parallel --build-cache --max-workers=4 --gradle-user-home=/root/.gradle -x test -x check xdk:installDist

echo "Build completed successfully"

inspect_container_cache "after build"

# Install native launchers
ARCH_NAME=$(arch_name ${TARGETARCH})
LAUNCHER_BINARY="javatools_launcher/src/main/resources/exe/linux_launcher_${ARCH_NAME}"

if [ -f "$LAUNCHER_BINARY" ]; then
    echo "Installing native launcher: $LAUNCHER_BINARY"
    cp "$LAUNCHER_BINARY" "xdk/build/install/xdk/bin/xec"
    cp "$LAUNCHER_BINARY" "xdk/build/install/xdk/bin/xcc"
    chmod +x "xdk/build/install/xdk/bin/xec" "xdk/build/install/xdk/bin/xcc"
else
    echo "Warning: Launcher binary not found at $LAUNCHER_BINARY"
    ls -la javatools_launcher/src/main/resources/exe/ || true
fi

# Create build info
BUILD_DATE_VAL="${BUILD_DATE:-$(date -u +"%Y-%m-%dT%H:%M:%SZ")}"
BRANCH_TO_USE="${GH_BRANCH:-master}"
GIT_COMMIT="${GH_COMMIT:-${VCS_REF:-unknown}}"

cat > "xdk/build/install/xdk/xvm.json" << JSONEOF
{
  "buildDate": "$BUILD_DATE_VAL",
  "commit": "$GIT_COMMIT", 
  "branch": "$BRANCH_TO_USE",
  "version": "$BRANCH_TO_USE",
  "platform": "${TARGETOS}/${TARGETARCH}"
}
JSONEOF

echo "XDK build complete"