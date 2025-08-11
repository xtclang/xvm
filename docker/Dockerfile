# syntax=docker/dockerfile:1
#
# XDK Docker Container - Native multi-platform support
# See DOCKER.md for complete build and usage instructions
#

# Build arguments with defaults
ARG JAVA_VERSION=21
ARG GH_BRANCH=master
ARG GH_COMMIT
ARG TARGETARCH
ARG TARGETOS
ARG BUILD_DATE
ARG VCS_REF
ARG NPROC=4
ARG DIST_ZIP_URL

# *** STAGE 1: Source stage (clone repository once and reuse across stages) ***
FROM alpine/git:latest AS source-cloner

ARG GH_BRANCH
ARG GH_COMMIT
WORKDIR /source
COPY scripts/clone-xdk.sh /clone-xdk.sh
RUN --mount=type=cache,target=/git-cache,sharing=shared \
    apk add --no-cache bash curl && \
    /clone-xdk.sh "${GH_BRANCH}" "${GH_COMMIT}"

# *** STAGE 4 (Dual-Mode Builder: Use pre-built artifacts from CI or build from source)
FROM bellsoft/liberica-openjdk-alpine:${JAVA_VERSION} AS builder
ARG GH_BRANCH
ARG GH_COMMIT
ARG TARGETARCH
ARG TARGETOS
ARG BUILD_DATE
ARG VCS_REF
ARG NPROC
ARG DIST_ZIP_URL

ENV JAVA_OPTS="-XX:+UseContainerSupport" \
    GRADLE_USER_HOME="/root/.gradle" \
    GRADLE_OPTS="-Dorg.gradle.daemon=false" \
    NPROC=${NPROC} \
    XDK_INSTALL_DIR="/workspace/xdk/build/install"

RUN apk add --no-cache unzip curl git bash file
WORKDIR /workspace

# Copy source and build script
COPY --from=source-cloner /source .
COPY scripts/build-xdk.sh /build-xdk.sh
# Copy CI distribution if it exists (optional - for CI builds)  
COPY ci-dist.zi[p] /workspace/

# Build XDK - handles both pre-built artifacts and source build modes
RUN --mount=type=cache,target=/root/.gradle/caches,sharing=locked \
    --mount=type=cache,target=/root/.gradle/wrapper,sharing=locked \
    --mount=type=cache,target=/root/.gradle/build-cache,sharing=locked \
    chmod +x /build-xdk.sh && \
    /build-xdk.sh

# *** STAGE 5 (Native Launcher Builder - ensures compatibility with target runtime)
FROM alpine:latest AS launcher-builder
ARG TARGETARCH
ARG TARGETOS

# Install build tools for C compilation
RUN apk add --no-cache gcc musl-dev make

WORKDIR /launcher-build
COPY --from=source-cloner /source .

# Build the native launcher from C source for the container's architecture
RUN echo "🔧 Building native launcher for ${TARGETOS}/${TARGETARCH} (musl libc)" && \
    cd javatools_launcher/src/main/c && \
    gcc -static -O2 -o launcher launcher.c os_linux.c os_unux.c && \
    echo "✅ Launcher build complete" && \
    ls -la launcher

# *** STAGE 6 (Export stage to copy launcher to host filesystem)
FROM scratch AS launcher-export
# Copy the container-built launcher
COPY --from=launcher-builder /launcher-build/javatools_launcher/src/main/c/launcher /

# *** STAGE 7 (Final Runtime Image)
FROM bellsoft/liberica-runtime-container:jre-21-slim-musl

ENV XDK_HOME=/opt/xdk
ENV PATH="${XDK_HOME}/bin:${PATH}"

# Copy XDK distribution from builder stage
COPY --from=builder /workspace/xdk/ /opt/xdk/

# Copy container-built native launcher (overwrites any pre-built launchers)
COPY --from=launcher-builder /launcher-build/javatools_launcher/src/main/c/launcher /opt/xdk/bin/xec
COPY --from=launcher-builder /launcher-build/javatools_launcher/src/main/c/launcher /opt/xdk/bin/xcc

# Ensure launchers are executable
RUN chmod +x /opt/xdk/bin/xec /opt/xdk/bin/xcc

CMD ["xec"]
