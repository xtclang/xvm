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

# Source stage - clone repository once and reuse across stages
FROM alpine/git:latest AS source-cloner

ARG GH_BRANCH
ARG GH_COMMIT

WORKDIR /source
COPY scripts/clone-xdk.sh /clone-xdk.sh
RUN --mount=type=cache,target=/git-cache,sharing=shared \
    apk add --no-cache bash && \
    /clone-xdk.sh "${GH_BRANCH}" "${GH_COMMIT}"

# Build launcher from C source for target platform
FROM gcc:latest AS launcher-builder

ARG TARGETARCH
ARG TARGETOS

# Environment variables for launcher build
ENV TMPDIR=/tmp/gcc-cache

# Copy source from source-cloner stage
WORKDIR /source
COPY --from=source-cloner /source .
COPY scripts/build-launcher.sh /build-launcher.sh
RUN --mount=type=cache,target=/tmp/gcc-cache,sharing=locked \
    /build-launcher.sh

FROM bellsoft/liberica-openjdk-alpine:${JAVA_VERSION} AS builder

ARG GH_BRANCH
ARG GH_COMMIT
ARG TARGETARCH
ARG TARGETOS
ARG BUILD_DATE
ARG VCS_REF
ARG NPROC

# Environment variables for JVM and Gradle configuration  
ENV JAVA_OPTS="-XX:+UseContainerSupport" \
    GRADLE_USER_HOME="/root/.gradle" \
    GRADLE_OPTS="-Dorg.gradle.daemon=false" \
    NPROC=${NPROC}

RUN apk add --no-cache \
    unzip \
    curl \
    git \
    bash

WORKDIR /workspace

# Copy source from source-cloner stage
COPY --from=source-cloner /source .

# Copy the compiled launcher from launcher-builder stage  
COPY --from=launcher-builder /launcher-output/ javatools_launcher/src/main/resources/exe/

# Copy the build script from docker/scripts directory
COPY scripts/build-xdk.sh /build-xdk.sh

# Build XDK using BuildKit cache mounts
RUN --mount=type=cache,target=/root/.gradle/caches,sharing=locked \
    --mount=type=cache,target=/root/.gradle/wrapper,sharing=locked \
    --mount=type=cache,target=/root/.gradle/build-cache,sharing=locked \
    chmod +x ./gradlew && \
    /build-xdk.sh

# Export stage to copy launcher to host filesystem  
FROM scratch AS launcher-export
COPY --from=builder /workspace/javatools_launcher/src/main/resources/exe/linux_launcher_* /

FROM bellsoft/liberica-runtime-container:jre-21-slim-musl

ENV XDK_HOME=/opt/xdk
ENV PATH="${XDK_HOME}/bin:${PATH}"

COPY --from=builder /workspace/xdk/build/install/xdk*/ /opt/xdk/

CMD ["xec"]
