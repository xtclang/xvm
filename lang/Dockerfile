# =============================================================================
# XTC Language Tooling - Docker Build Environment
# =============================================================================
# This Dockerfile demonstrates that the runIde Gradle task works with NOTHING
# installed except a JDK and IntelliJ IDEA. All build dependencies (tree-sitter
# CLI, Zig compiler, etc.) are downloaded and cached by Gradle automatically.
#
# Usage:
#   # Build the image
#   docker build -t xtc-lang-dev .
#
#   # Run runIde (headless with virtual display)
#   docker run --rm -it xtc-lang-dev ./gradlew :lang:intellij-plugin:runIde
#
#   # Interactive shell for debugging
#   docker run --rm -it xtc-lang-dev bash
#
# =============================================================================

FROM eclipse-temurin:21-jdk-jammy

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
    xvfb \
    libxi6 \
    libxtst6 \
    libxrender1 \
    libxrandr2 \
    libx11-6 \
    fontconfig \
    fonts-dejavu-core \
    libfreetype6 \
    git \
    curl \
    wget \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# =============================================================================
# Install IntelliJ IDEA Community Edition 2025.1
# =============================================================================
# Download and extract IntelliJ to /opt/idea-IC
# The build.gradle.kts will auto-detect this location on Linux

ARG INTELLIJ_VERSION=2025.1
ARG INTELLIJ_BUILD=251.23774.200

RUN mkdir -p /opt && \
    curl -fsSL "https://download.jetbrains.com/idea/ideaIC-${INTELLIJ_VERSION}.tar.gz" \
    | tar -xz -C /opt && \
    mv /opt/idea-IC-* /opt/idea-IC && \
    # Create a simpler symlink for the build to find
    ln -s /opt/idea-IC /opt/intellij-idea-community

# =============================================================================
# JDK Configuration
# =============================================================================
# The XDK uses multiple JDK versions (defined in version.properties):
#   - JDK 25: Main build (org.xtclang.java.jdk)
#   - JDK 24: Kotlin/LSP server (org.xtclang.kotlin.jdk)
#   - JDK 21: IntelliJ plugin only (libs.versions.toml intellij-jdk)
#
# Base image provides JDK 21 for running Gradle.
# Gradle's toolchain auto-provisioning downloads JDK 24 and 25 as needed.
# =============================================================================

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Verify base JDK version matches what IntelliJ expects
RUN java -version 2>&1 | head -1 | grep -q "21" || \
    (echo "ASSERTION FAILED: Base JDK must be version 21 for IntelliJ" && exit 1)

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
    mkdir -p /home/builder/.gradle && \
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
# Pre-populate Gradle caches and verify JDK provisioning
# =============================================================================
# This compiles the DSL project which triggers Gradle toolchain provisioning
# for JDK 24 (Kotlin) and JDK 25 (Java). The JDKs are cached in ~/.gradle/jdks/

RUN ./gradlew --no-daemon -PlangBuild=true :lang:dsl:compileKotlin :lang:dsl:compileJava || true

# Verify Gradle auto-provisioned the expected JDKs (from version.properties)
# These assertions ensure the Dockerfile stays in sync with version.properties
RUN echo "Verifying Gradle-provisioned JDKs..." && \
    ls -la ~/.gradle/jdks/ && \
    (ls ~/.gradle/jdks/ | grep -q "24" || (echo "ASSERTION FAILED: JDK 24 not provisioned (expected for org.xtclang.kotlin.jdk)" && exit 1)) && \
    (ls ~/.gradle/jdks/ | grep -q "25" || (echo "ASSERTION FAILED: JDK 25 not provisioned (expected for org.xtclang.java.jdk)" && exit 1)) && \
    echo "JDK provisioning verified: JDK 24 and 25 present"

# =============================================================================
# Default entrypoint with virtual display
# =============================================================================
# The with-display script starts Xvfb before running the provided command.

ENTRYPOINT ["/usr/local/bin/with-display"]
CMD ["./gradlew", "--no-daemon", "-PlangBuild=true", "-PuseLocalIde=true", ":lang:intellij-plugin:runIde"]
