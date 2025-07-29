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

# Copy launcher C source files
COPY javatools_launcher/src/main/c/ /launcher-src/

WORKDIR /launcher-src

# Build launcher for target architecture
RUN set -e && \
    echo "Building launcher for ${TARGETOS}/${TARGETARCH}" && \
    case "${TARGETARCH}" in \
        amd64) ARCH_FLAGS="" ;; \
        arm64) ARCH_FLAGS="-march=armv8-a" ;; \
        *) ARCH_FLAGS="" ;; \
    esac && \
    mkdir -p /launcher-output && \
    gcc -g -Wall -std=gnu11 -DlinuxLauncher -O0 ${ARCH_FLAGS} \
        launcher.c os_linux.c os_unux.c -o /launcher-output/linux_launcher_${TARGETARCH}

# Export stage to copy launcher to host filesystem  
FROM scratch AS launcher-export
COPY --from=launcher-builder /launcher-output/ /

FROM eclipse-temurin:${JAVA_VERSION}-jdk AS builder

ARG VERSION=local
ARG TARGETARCH
ARG TARGETOS

RUN apt-get update && apt-get install -y --no-install-recommends \
    unzip \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# For local builds, only copy git-tracked files to avoid build artifacts and IDE files
COPY --link .git .git/
RUN git archive HEAD | tar -x && rm -rf .git

# Copy the compiled launcher from launcher-builder stage
COPY --from=launcher-builder /launcher-output/linux_launcher_${TARGETARCH} javatools_launcher/src/main/resources/exe/linux_launcher_${TARGETARCH}

RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    set -e && \
    REPO_URL="https://github.com/xtclang/xvm" && \
    GRADLE_CMD="./gradlew --no-daemon xdk:installDist" && \
    arch_map() { case "$1" in amd64) echo "x86_64";; arm64) echo "aarch64";; *) echo "$1";; esac; } && \
    clean_workspace() { rm -rf ./* ./.??* 2>/dev/null || true; } && \
    setup_gradle() { chmod +x ./gradlew; } && \
    build_gradle() { setup_gradle && eval "$GRADLE_CMD"; } && \
    download_and_build() { curl -L "$1" | tar -xz --strip-components=1 && build_gradle; } && \
    echo "Building XDK for ${TARGETOS}/${TARGETARCH}" && \
    case "$VERSION" in \
        "local") echo "Using local source" && build_gradle ;; \
        "latest") echo "Downloading latest master" && clean_workspace && download_and_build "$REPO_URL/archive/refs/heads/master.tar.gz" ;; \
        v[0-9]*) echo "Using GitHub release: $VERSION" && clean_workspace && curl -L "$REPO_URL/releases/download/$VERSION/xdk.tar.gz" | tar -xz ;; \
        *) echo "Downloading commit: $VERSION" && clean_workspace && download_and_build "$REPO_URL/archive/$VERSION.tar.gz" ;; \
    esac && \
    echo "Build completed successfully" && \
    echo "Setting up native launchers for ${TARGETOS}/${TARGETARCH}" && \
    LAUNCHER_BINARY="javatools_launcher/src/main/resources/exe/linux_launcher_${TARGETARCH}" && \
    if [ -f "$LAUNCHER_BINARY" ]; then \
        echo "Using compiled launcher: $LAUNCHER_BINARY" && \
        cp "$LAUNCHER_BINARY" "xdk/build/install/xdk/bin/xec" && \
        cp "$LAUNCHER_BINARY" "xdk/build/install/xdk/bin/xcc" && \
        chmod +x "xdk/build/install/xdk/bin/xec" "xdk/build/install/xdk/bin/xcc" && \
        echo "Native launchers installed successfully"; \
    else \
        echo "Warning: Launcher binary not found at $LAUNCHER_BINARY" && \
        echo "Available launcher files:" && \
        ls -la javatools_launcher/src/main/resources/exe/ || true; \
    fi

FROM eclipse-temurin:${JAVA_VERSION}-jre

RUN groupadd -r xtclang && useradd -r -g xtclang -d /home/xtclang -m xtclang
ENV XDK_HOME=/opt/xdk
RUN mkdir -p /opt/xdk && chown xtclang:xtclang /opt/xdk
COPY --from=builder --chown=xtclang:xtclang /workspace/xdk/build/install/xdk*/ /opt/xdk/
ENV PATH="${XDK_HOME}/bin:${PATH}"
USER xtclang
WORKDIR /home/xtclang

CMD ["xec"]