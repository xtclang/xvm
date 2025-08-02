# syntax=docker/dockerfile:1
#
# XDK Docker Container - Native multi-platform support
# See DOCKER.md for complete build and usage instructions
#

# Build arguments
ARG JAVA_VERSION=21

# Build launcher from C source for target platform
FROM gcc:latest AS launcher-builder

ARG TARGETARCH
ARG TARGETOS  
ARG VERSION

# Download source code first
WORKDIR /source
RUN if [ -z "$VERSION" ]; then \
        echo "Downloading latest master" && \
        curl -L "https://github.com/xtclang/xvm/archive/refs/heads/master.tar.gz" | tar -xz --strip-components=1; \
    else \
        echo "Downloading commit/ref: $VERSION" && \
        curl -L "https://github.com/xtclang/xvm/archive/$VERSION.tar.gz" | tar -xz --strip-components=1; \
    fi

# Build launcher for target architecture
WORKDIR /source/javatools_launcher/src/main/c
RUN --mount=type=cache,target=/tmp/gcc-cache,sharing=locked \
    --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt/lists,sharing=locked \
    set -e && \
    echo "Building launcher for ${TARGETOS}/${TARGETARCH}" && \
    case "${TARGETARCH}" in \
        amd64) ARCH_FLAGS="-O3 -mtune=generic" && LAUNCHER_NAME="linux_launcher_x86_64" ;; \
        arm64) ARCH_FLAGS="-O3 -march=armv8-a -mtune=cortex-a72" && LAUNCHER_NAME="linux_launcher_aarch64" ;; \
        *) ARCH_FLAGS="-O3" && LAUNCHER_NAME="linux_launcher_${TARGETARCH}" ;; \
    esac && \
    mkdir -p /launcher-output /tmp/gcc-cache && \
    export TMPDIR=/tmp/gcc-cache && \
    gcc -static -g -Wall -std=gnu11 -DlinuxLauncher ${ARCH_FLAGS} \
        launcher.c os_linux.c os_unux.c -o /launcher-output/${LAUNCHER_NAME}

FROM eclipse-temurin:${JAVA_VERSION}-jdk AS builder

ARG VERSION
ARG TARGETARCH
ARG TARGETOS
ARG BUILD_DATE
ARG VCS_REF

RUN apt-get update && apt-get install -y --no-install-recommends \
    unzip \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# Download the same source code as launcher-builder
RUN if [ -z "$VERSION" ]; then \
        echo "Downloading latest master" && \
        curl -L "https://github.com/xtclang/xvm/archive/refs/heads/master.tar.gz" | tar -xz --strip-components=1; \
    else \
        echo "Downloading commit/ref: $VERSION" && \
        curl -L "https://github.com/xtclang/xvm/archive/$VERSION.tar.gz" | tar -xz --strip-components=1; \
    fi

# Copy the compiled launcher from launcher-builder stage  
COPY --from=launcher-builder /launcher-output/ javatools_launcher/src/main/resources/exe/

# Pre-cache Gradle wrapper and dependencies in separate layer
RUN --mount=type=cache,target=/root/.gradle/caches,sharing=locked \
    --mount=type=cache,target=/root/.gradle/wrapper,sharing=locked \
    --mount=type=cache,target=/root/.gradle/build-cache,sharing=locked \
    chmod +x ./gradlew && \
    export GRADLE_OPTS="-Dorg.gradle.daemon=false" && \
    ./gradlew --version && \
    ./gradlew --no-daemon --build-cache dependencies || true

COPY <<EOF /tmp/build_xdk.sh
#!/bin/bash
set -e

arch_map() {
    case "\$1" in
        amd64) echo "x86_64" ;;
        arm64) echo "aarch64" ;;
        *) echo "\$1" ;;
    esac
}

echo "Building XDK for \${TARGETOS}/\${TARGETARCH}"
echo "Source already downloaded, starting build..."

# Configure Gradle for faster builds in containers
export GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.caching=true -Dorg.gradle.parallel=true -Dorg.gradle.configureondemand=true"

# Make gradle wrapper executable and build
chmod +x ./gradlew
./gradlew --no-daemon --parallel --build-cache --configuration-cache xdk:installDist

echo "Build completed successfully"

echo "Setting up native launchers for \${TARGETOS}/\${TARGETARCH}"
ARCH_NAME=\$(arch_map \${TARGETARCH})
LAUNCHER_BINARY="javatools_launcher/src/main/resources/exe/linux_launcher_\${ARCH_NAME}"

if [ -f "\$LAUNCHER_BINARY" ]; then
    echo "Using compiled launcher: \$LAUNCHER_BINARY"
    cp "\$LAUNCHER_BINARY" "xdk/build/install/xdk/bin/xec"
    cp "\$LAUNCHER_BINARY" "xdk/build/install/xdk/bin/xcc"
    chmod +x "xdk/build/install/xdk/bin/xec" "xdk/build/install/xdk/bin/xcc"
    echo "Native launchers installed successfully"
else
    echo "Warning: Launcher binary not found at \$LAUNCHER_BINARY"
    echo "Available launcher files:"
    ls -la javatools_launcher/src/main/resources/exe/ || true
fi

echo "Creating build info file..."
BUILD_DATE_VAL="\${BUILD_DATE:-\$(date -u +"%Y-%m-%dT%H:%M:%SZ")}"

# Get actual commit hash if VERSION is empty (latest master)
if [ -z "\$VERSION" ]; then
    ACTUAL_COMMIT=\$(curl -s https://api.github.com/repos/xtclang/xvm/commits/master | grep "\"sha\":" | head -1 | cut -d'"' -f4)
    VERSION_TO_USE="master-\${ACTUAL_COMMIT:0:8}"
    GIT_COMMIT="\${VCS_REF:-\$ACTUAL_COMMIT}"
else
    VERSION_TO_USE="\$VERSION"
    GIT_COMMIT="\${VCS_REF:-\$VERSION}"
fi

cat > "xdk/build/install/xdk/xvm.json" << JSONEOF
{
  "buildDate": "\$BUILD_DATE_VAL",
  "gitCommit": "\$GIT_COMMIT",
  "version": "\$VERSION_TO_USE",
  "platform": "\${TARGETOS}/\${TARGETARCH}"
}
JSONEOF

echo "Build info file created successfully"
EOF

RUN --mount=type=cache,target=/root/.gradle/caches,sharing=locked \
    --mount=type=cache,target=/root/.gradle/wrapper,sharing=locked \
    --mount=type=cache,target=/root/.gradle/build-cache,sharing=locked \
    chmod +x /tmp/build_xdk.sh && /tmp/build_xdk.sh

# Export stage to copy launcher to host filesystem  
FROM scratch AS launcher-export
COPY --from=builder /workspace/javatools_launcher/src/main/resources/exe/linux_launcher_* /

FROM bellsoft/liberica-runtime-container:jre-21-slim-musl

ENV XDK_HOME=/opt/xdk
ENV PATH="${XDK_HOME}/bin:${PATH}"

COPY --from=builder /workspace/xdk/build/install/xdk*/ /opt/xdk/

CMD ["xec"]
