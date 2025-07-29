# syntax=docker/dockerfile:1
#
# XDK Docker Container - Multi-platform support
#
# DEFAULTS:
#   - VERSION=latest (builds from GitHub master branch)
#   - Platform matches host architecture (linux/amd64 on x86_64, linux/arm64 on Apple Silicon)
#   - Automatically builds correct native binaries (xcc/xec) for target platform
#
# BUILDING:
#   Basic (latest master): docker buildx build -t xvm:latest .
#   Local source: docker buildx build --build-arg VERSION=local -t xvm:latest .
#   Specific commit: docker buildx build --build-arg VERSION=abc123 -t xvm:latest .
#   GitHub release: docker buildx build --build-arg VERSION=v1.0.0 -t xvm:latest .
#
# PLATFORM-SPECIFIC BUILDS:
#   Linux AMD64: docker buildx build --platform linux/amd64 -t xvm:linux-amd64 --load .
#   Linux ARM64: docker buildx build --platform linux/arm64 -t xvm:linux-arm64 --load .
#   Multi-platform: docker buildx build --platform linux/amd64,linux/arm64 -t xvm:latest --push .
#
# RUNNING:
#   Interactive shell: docker run -it --rm xvm:latest /bin/bash
#   Run xec directly: docker run --rm xvm:latest
#   Run xcc: docker run --rm xvm:latest xcc
#   Mount local files: docker run -v $(pwd):/workspace -w /workspace --rm xvm:latest xcc myfile.x
#
# ADVANCED:
#   With caching: docker buildx build --cache-from type=gha --cache-to type=gha,mode=max -t xvm:latest .
#   Push to registry: docker buildx build --platform linux/amd64,linux/arm64 -t myregistry/xvm:latest --push .

FROM eclipse-temurin:21-jdk AS builder

ARG VERSION=latest
ARG TARGETARCH
ARG TARGETOS

RUN apt-get update && apt-get install -y --no-install-recommends \
    unzip \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

COPY --link . ./

RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    set -e && \
    REPO_URL="https://github.com/xtclang/xvm" && \
    GRADLE_CMD="./gradlew --no-daemon xdk:installWithLaunchersDist" && \
    arch_map() { case "$1" in amd64) echo "x86_64";; arm64) echo "aarch64";; *) echo "$1";; esac; } && \
    clean_workspace() { rm -rf ./* ./.??* 2>/dev/null || true; } && \
    setup_gradle() { chmod +x ./gradlew; } && \
    build_gradle() { setup_gradle && eval "$GRADLE_CMD"; } && \
    download_and_build() { curl -L "$1" | tar -xz --strip-components=1 && build_gradle; } && \
    export GRADLE_OPTS="-Dorg.gradle.jvmargs=-Dos.arch=$(arch_map "$TARGETARCH")" && \
    echo "Building XDK for $TARGETOS/$TARGETARCH" && \
    case "$VERSION" in \
        "local") echo "Using local source" && build_gradle ;; \
        "latest") echo "Downloading latest master" && clean_workspace && download_and_build "$REPO_URL/archive/refs/heads/master.tar.gz" ;; \
        v[0-9]*) echo "Using GitHub release: $VERSION" && clean_workspace && curl -L "$REPO_URL/releases/download/$VERSION/xdk.tar.gz" | tar -xz ;; \
        *) echo "Downloading commit: $VERSION" && clean_workspace && download_and_build "$REPO_URL/archive/$VERSION.tar.gz" ;; \
    esac && \
    echo "Build completed successfully"

FROM eclipse-temurin:21-jre

RUN groupadd -r xtclang && useradd -r -g xtclang -d /home/xtclang -m xtclang
ENV XDK_HOME=/opt/xdk
RUN mkdir -p /opt/xdk && chown xtclang:xtclang /opt/xdk
COPY --from=builder --chown=xtclang:xtclang /workspace/xdk/build/install/xdk*/ /opt/xdk/
ENV PATH="${XDK_HOME}/bin:${PATH}"
USER xtclang
WORKDIR /home/xtclang

CMD ["xec"]
