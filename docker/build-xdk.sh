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

chmod +x ./gradlew
mkdir -p /root/.gradle

# Build the XDK (skip tests since this is packaging)
./gradlew --no-daemon --parallel --build-cache --max-workers=4 --gradle-user-home=/root/.gradle -x test -x check xdk:installDist

echo "Build completed successfully"

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
VERSION_TO_USE="${VERSION:-master}"
GIT_COMMIT="${VCS_REF:-${VERSION:-unknown}}"

cat > "xdk/build/install/xdk/xvm.json" << JSONEOF
{
  "buildDate": "$BUILD_DATE_VAL",
  "gitCommit": "$GIT_COMMIT", 
  "version": "$VERSION_TO_USE",
  "platform": "${TARGETOS}/${TARGETARCH}"
}
JSONEOF

echo "XDK build complete"