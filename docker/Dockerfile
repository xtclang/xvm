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
ARG GH_BRANCH=master
ARG GH_COMMIT

# Download source and build launcher
WORKDIR /source
RUN --mount=type=cache,target=/tmp/gcc-cache,sharing=locked \
    --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt/lists,sharing=locked \
    set -e && \
    echo "Building from branch: $GH_BRANCH, commit: ${GH_COMMIT:-latest}" && \
    gh_ref=${GH_COMMIT:-$GH_BRANCH} && \
    echo "Downloading GitHub ref: $gh_ref" && \
    curl -L "https://github.com/xtclang/xvm/archive/$gh_ref.tar.gz" | tar -xz --strip-components=1 || \
    (echo "ERROR: Failed to download $gh_ref from GitHub" && exit 1) && \
    cd javatools_launcher/src/main/c && \
    echo "Building launcher for ${TARGETOS}/${TARGETARCH}" && \
    case "${TARGETARCH}" in \
        amd64) ARCH_FLAGS="-O3 -mtune=generic" && LAUNCHER_NAME="linux_launcher_x86_64" ;; \
        arm64) ARCH_FLAGS="-O3 -march=armv8-a -mtune=cortex-a72" && LAUNCHER_NAME="linux_launcher_aarch64" ;; \
        *) echo "ERROR: Unsupported platform ${TARGETOS}/${TARGETARCH}" && exit 1 ;; \
    esac && \
    mkdir -p /launcher-output /tmp/gcc-cache && \
    export TMPDIR=/tmp/gcc-cache && \
    gcc -static -g -Wall -std=gnu11 -DlinuxLauncher ${ARCH_FLAGS} \
        launcher.c os_linux.c os_unux.c -o /launcher-output/${LAUNCHER_NAME} || \
    (echo "ERROR: Failed to build launcher for ${TARGETOS}/${TARGETARCH}" && exit 1) && \
    echo "Successfully built launcher: ${LAUNCHER_NAME}"

FROM bellsoft/liberica-openjdk-alpine:${JAVA_VERSION} AS builder

ARG GH_BRANCH
ARG GH_COMMIT
ARG TARGETARCH
ARG TARGETOS
ARG BUILD_DATE
ARG VCS_REF

RUN apk add --no-cache \
    unzip \
    curl \
    git \
    bash

WORKDIR /workspace

# Download the same source code as launcher-builder
RUN echo "Building from branch: $GH_BRANCH, commit: ${GH_COMMIT:-latest}" && \
    gh_ref=${GH_COMMIT:-$GH_BRANCH} && \
    echo "Downloading GitHub ref: $gh_ref" && \
    curl -L "https://github.com/xtclang/xvm/archive/$gh_ref.tar.gz" | tar -xz --strip-components=1 || \
    (echo "ERROR: Failed to download $gh_ref from GitHub" && exit 1)

# Copy the compiled launcher from launcher-builder stage  
COPY --from=launcher-builder /launcher-output/ javatools_launcher/src/main/resources/exe/

# Copy the build script from docker directory
COPY build-xdk.sh /build-xdk.sh

# Pre-cache dependencies and build XDK
RUN --mount=type=cache,target=/root/.gradle/caches,sharing=locked \
    --mount=type=cache,target=/root/.gradle/wrapper,sharing=locked \
    --mount=type=cache,target=/root/.gradle/build-cache,sharing=locked \
    set -e && \
    echo "🔍 Docker Cache Mount Inspection (dependency pre-cache phase):" && \
    echo "  Gradle cache mounts active: /root/.gradle/{caches,wrapper,build-cache}" && \
    ls -la /root/.gradle/ 2>/dev/null | head -5 || echo "  Cache mount directory empty (first run)" && \
    chmod +x ./gradlew /build-xdk.sh && \
    export GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.parallel=true" && \
    ./gradlew --version && \
    echo "Pre-caching dependencies..." && \
    ./gradlew --no-daemon --parallel --build-cache --max-workers=4 dependencies || true && \
    echo "Dependencies cached, now building XDK..." && \
    /build-xdk.sh

# Export stage to copy launcher to host filesystem  
FROM scratch AS launcher-export
COPY --from=builder /workspace/javatools_launcher/src/main/resources/exe/linux_launcher_* /

FROM bellsoft/liberica-runtime-container:jre-21-slim-musl

ENV XDK_HOME=/opt/xdk
ENV PATH="${XDK_HOME}/bin:${PATH}"

COPY --from=builder /workspace/xdk/build/install/xdk*/ /opt/xdk/

CMD ["xec"]
