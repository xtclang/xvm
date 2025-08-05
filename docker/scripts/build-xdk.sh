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
