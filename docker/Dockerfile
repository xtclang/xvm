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

# *** STAGE 5 (Export stage to copy launcher to host filesystem)
FROM scratch AS launcher-export
# Copy all available Linux launchers (both old and new naming schemes)
COPY --from=builder /workspace/javatools_launcher/src/main/resources/exe/linux_launcher* /

FROM bellsoft/liberica-runtime-container:jre-21-slim-musl

ENV XDK_HOME=/opt/xdk
ENV PATH="${XDK_HOME}/bin:${PATH}"

COPY --from=builder /workspace/xdk/ /opt/xdk/

CMD ["xec"]
