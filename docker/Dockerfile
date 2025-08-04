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

FROM bellsoft/liberica-openjdk-alpine:${JAVA_VERSION} AS builder

ARG VERSION
ARG TARGETARCH
ARG TARGETOS
ARG BUILD_DATE
ARG VCS_REF

RUN apk add --no-cache \
    unzip \
    curl \
    git

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
    export GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.parallel=true" && \
    ./gradlew --version && \
    ./gradlew --no-daemon --parallel --build-cache --max-workers=4 dependencies || true

# Copy the build script from docker directory
COPY build-xdk.sh /tmp/build-xdk.sh

RUN --mount=type=cache,target=/root/.gradle/caches,sharing=locked \
    --mount=type=cache,target=/root/.gradle/wrapper,sharing=locked \
    --mount=type=cache,target=/root/.gradle/build-cache,sharing=locked \
    chmod +x /tmp/build-xdk.sh && /tmp/build-xdk.sh

# Export stage to copy launcher to host filesystem  
FROM scratch AS launcher-export
COPY --from=builder /workspace/javatools_launcher/src/main/resources/exe/linux_launcher_* /

FROM bellsoft/liberica-runtime-container:jre-21-slim-musl

ENV XDK_HOME=/opt/xdk
ENV PATH="${XDK_HOME}/bin:${PATH}"

COPY --from=builder /workspace/xdk/build/install/xdk*/ /opt/xdk/

CMD ["xec"]
