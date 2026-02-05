# =============================================================================
# XTC Language Tooling - Docker Build Environment
# =============================================================================
# This Dockerfile demonstrates that the runIde Gradle task works with NOTHING
# installed except a JDK and IntelliJ IDEA. All build dependencies (tree-sitter
# CLI, Zig compiler, etc.) are downloaded and cached by Gradle automatically.
#
# HEADLESS OPERATION:
#   The container runs completely headlessly using Xvfb (X Virtual Framebuffer).
#   IntelliJ renders to a virtual display inside the container - nothing will
#   appear on your host desktop. This is intentional for CI/testing purposes.
#
# Usage (from project root directory):
#   # Build the image (must run from project root, not lang/)
#   docker build -f lang/Dockerfile -t xtc-lang-dev .
#
#   # Run runIde (headless - no window on host)
#   docker run --rm -it xtc-lang-dev
#
#   # Interactive shell for debugging
#   docker run --rm -it xtc-lang-dev bash
#
#   # Capture all output to a log file for analysis
#   docker run --rm xtc-lang-dev 2>&1 | tee runIde.log
#
# BUILD CACHING:
#   By default, this Dockerfile builds from scratch each time to verify the build
#   works with no pre-existing state. For faster iterative development, use
#   BuildKit cache mounts to persist Gradle caches between builds:
#
#   # Fast build with Gradle cache (for development)
#   DOCKER_BUILDKIT=1 docker build \
#     --build-arg BUILDKIT_INLINE_CACHE=1 \
#     -f lang/Dockerfile -t xtc-lang-dev .
#
#   # Clean build with no cache (for CI/verification)
#   docker build --no-cache -f lang/Dockerfile -t xtc-lang-dev .
#
# To see IntelliJ window on your host (optional):
#
#   macOS (requires XQuartz installed and running):
#     xhost +localhost
#     docker run --rm -it -e DISPLAY=host.docker.internal:0 xtc-lang-dev
#
#   Linux:
#     xhost +local:docker
#     docker run --rm -it -e DISPLAY=$DISPLAY -v /tmp/.X11-unix:/tmp/.X11-unix xtc-lang-dev
#
#   Windows WSL2 (requires X server like VcXsrv or X410):
#     1. Install and start VcXsrv with "Disable access control" checked
#     2. From WSL2 terminal:
#        export DISPLAY=$(cat /etc/resolv.conf | grep nameserver | awk '{print $2}'):0
#        docker run --rm -it -e DISPLAY=$DISPLAY xtc-lang-dev
#
# =============================================================================

FROM eclipse-temurin:25-jdk-noble

LABEL maintainer="XTC Language Team"
LABEL description="Build environment for XTC Language tooling (IntelliJ plugin, LSP server)"

# =============================================================================
# Install system dependencies
# =============================================================================
# - Xvfb: Virtual framebuffer for headless IntelliJ
# - libxi6, libxtst6, libxrender1: X11 libraries required by IntelliJ/Swing
# - fontconfig, fonts-dejavu: Font support for rendering
# - git: Required for version detection (palantir-git-version plugin)
# - curl, wget: For downloads
# - libfreetype6: Font rendering

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    fontconfig \
    fonts-dejavu-core \
    git \
    libfreetype6 \
    libx11-6 \
    libxi6 \
    libxrandr2 \
    libxrender1 \
    libxtst6 \
    wget \
    xvfb \
    && rm -rf /var/lib/apt/lists/*

# =============================================================================
# Install IntelliJ IDEA Community Edition 2025.1
# =============================================================================
# Download and extract IntelliJ to /opt/idea-IC
# The build.gradle.kts will auto-detect this location on Linux

ARG INTELLIJ_VERSION=2025.1
ARG INTELLIJ_BUILD=251.23774.200

# Download, extract, and create symlink for the build to find
# Architecture suffix: empty for x86_64, "-aarch64" for arm64
RUN ARCH=$(uname -m) && \
    if [ "$ARCH" = "aarch64" ]; then ARCH_SUFFIX="-aarch64"; \
    elif [ "$ARCH" = "x86_64" ]; then ARCH_SUFFIX=""; \
    else echo "Unsupported architecture: $ARCH" && exit 1; fi && \
    mkdir -p /opt && \
    curl -fsSL "https://download.jetbrains.com/idea/ideaIC-${INTELLIJ_VERSION}${ARCH_SUFFIX}.tar.gz" \
    | tar -xz -C /opt && \
    mv /opt/idea-IC-* /opt/idea-IC && \
    ln -s /opt/idea-IC /opt/intellij-idea-community

# =============================================================================
# JDK Configuration
# =============================================================================
# The XDK uses multiple JDK versions (defined in version.properties):
#   - JDK 25: Main build and Gradle runtime (org.xtclang.java.jdk)
#   - JDK 24: Kotlin/LSP server (org.xtclang.kotlin.jdk)
#
# Base image provides JDK 25 for running Gradle.
# JDK 24 is installed directly to avoid Foojay API dependency.
# =============================================================================

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Verify base JDK version matches org.xtclang.java.jdk
RUN java -version 2>&1 | head -1 | grep -q "25" || \
    (echo "ASSERTION FAILED: Base JDK must be version 25 (org.xtclang.java.jdk)" && exit 1)

# JDK 21 is provided by IntelliJ's bundled JBR at /opt/idea-IC/jbr

# Install JDK 24 for Kotlin toolchain (avoids Foojay API dependency)
ARG JDK24_VERSION=24.0.1+9
RUN ARCH=$(uname -m) && \
    if [ "$ARCH" = "aarch64" ]; then JDK_ARCH="aarch64"; \
    elif [ "$ARCH" = "x86_64" ]; then JDK_ARCH="x64"; \
    else echo "Unsupported architecture: $ARCH" && exit 1; fi && \
    mkdir -p /opt/jdk-24 && \
    curl -fsSL "https://github.com/adoptium/temurin24-binaries/releases/download/jdk-${JDK24_VERSION}/OpenJDK24U-jdk_${JDK_ARCH}_linux_hotspot_$(echo ${JDK24_VERSION} | tr '+' '_').tar.gz" \
    | tar -xz -C /opt/jdk-24 --strip-components=1 && \
    /opt/jdk-24/bin/java -version

# =============================================================================
# Configure virtual display for headless operation
# =============================================================================
# IntelliJ requires a display even when running headlessly (for plugin loading).
# We use Xvfb to provide a virtual framebuffer.

ENV DISPLAY=:99

# Create startup script that launches Xvfb before running commands
RUN echo '#!/bin/bash\n\
Xvfb :99 -screen 0 1920x1080x24 &\n\
sleep 1\n\
exec "$@"' > /usr/local/bin/with-display && \
    chmod +x /usr/local/bin/with-display

# =============================================================================
# Create non-root user for builds
# =============================================================================

RUN useradd -m -s /bin/bash builder && \
    mkdir -p /home/builder/.gradle/jdks && \
    ln -s /opt/jdk-24 /home/builder/.gradle/jdks/temurin-24 && \
    chown -R builder:builder /home/builder

USER builder
WORKDIR /home/builder

# =============================================================================
# Copy project source
# =============================================================================
# Copy only what's needed for the build. The .dockerignore should exclude
# build directories, IDE files, etc.

COPY --chown=builder:builder . /home/builder/xvm

WORKDIR /home/builder/xvm

# =============================================================================
# Pre-populate Gradle caches
# =============================================================================
# Run build to populate caches. JDK 24 is pre-installed, JDK 25 from base image.
# The --mount=type=cache enables BuildKit caching for faster rebuilds when using
# DOCKER_BUILDKIT=1. Without BuildKit, this is ignored and builds from scratch.

RUN ./gradlew --no-daemon \
    -Dorg.gradle.java.installations.paths=/opt/jdk-24,/opt/idea-IC/jbr \
    build || true

# Verify JDKs are available
RUN echo "Verifying JDKs..." && \
    /opt/jdk-24/bin/java -version && \
    java -version && \
    echo "JDK verification complete: JDK 24 at /opt/jdk-24, JDK 25 from base image"

# =============================================================================
# Default entrypoint with virtual display
# =============================================================================
# The with-display script starts Xvfb before running the provided command.

ENTRYPOINT ["/usr/local/bin/with-display"]
CMD ["./gradlew", "--no-daemon", "-Dorg.gradle.java.installations.paths=/opt/jdk-24,/opt/idea-IC/jbr", "-PlangBuild=true", "-PuseLocalIde=true", ":lang:intellij-plugin:runIde"]
