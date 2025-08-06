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

# *** STAGE 1 (Independent caching for C source files from the rest of the build)
#
# Introduce an in-container layer with the hash of the launcher source, to be able to cache it.

FROM alpine/git:latest AS launcher-source

ARG GH_BRANCH
ARG GH_COMMIT

WORKDIR /launcher-src
RUN apk add --no-cache git bash && \
    git clone --depth 1 --filter=blob:none --sparse https://github.com/xtclang/xvm.git . && \
    git sparse-checkout set javatools_launcher/src/main/c && \
    git checkout ${GH_COMMIT} && \
    find javatools_launcher/src/main/c -type f -exec sha256sum {} \; | sort > launcher-content-hash

#
# *** STAGE 2 (build launcher from C source code to target platform, independent of main source) ***
#
# This is the single source of truth for creating binary launchers, and it is rather horrible to
# keep them checked into the source code, as historically has been the case.
#
# Build launcher from C source for target platform - now independent of main source

FROM gcc:latest AS launcher-builder

ARG TARGETARCH
ARG TARGETOS
ENV TMPDIR=/tmp/gcc-cache
WORKDIR /source
# Copy ONLY launcher source (not entire repo) 
COPY --from=launcher-source /launcher-src/javatools_launcher/src/main/c/ javatools_launcher/src/main/c/
COPY --from=launcher-source /launcher-src/launcher-content-hash /launcher-hash
COPY scripts/build-launcher.sh /build-launcher.sh

RUN --mount=type=cache,target=/tmp/gcc-cache,sharing=locked \
    /build-launcher.sh

# *** STACK 3 (Source stage; clone repository once and reuse across stages)
FROM alpine/git:latest AS source-cloner

ARG GH_BRANCH
ARG GH_COMMIT
WORKDIR /source
COPY scripts/clone-xdk.sh /clone-xdk.sh
RUN --mount=type=cache,target=/git-cache,sharing=shared \
    apk add --no-cache bash && \
    /clone-xdk.sh "${GH_BRANCH}" "${GH_COMMIT}"

# *** STAGE 4 (Builder stage, create an XDK distribution from the cloned source)
FROM bellsoft/liberica-openjdk-alpine:${JAVA_VERSION} AS builder
ARG GH_BRANCH
ARG GH_COMMIT
ARG TARGETARCH
ARG TARGETOS
ARG BUILD_DATE
ARG VCS_REF
ARG NPROC

ENV JAVA_OPTS="-XX:+UseContainerSupport" \
    GRADLE_USER_HOME="/root/.gradle" \
    GRADLE_OPTS="-Dorg.gradle.daemon=false" \
    NPROC=${NPROC}


RUN apk add --no-cache unzip curl git bash
WORKDIR /workspace

# Copy source from source-cloner stage
# Copy the compiled launcher from launcher-builder stage  
# Copy the build script from docker/scripts directory
COPY --from=source-cloner /source .
COPY --from=launcher-builder /launcher-output/ javatools_launcher/src/main/resources/exe/
COPY scripts/build-xdk.sh /build-xdk.sh

# Build XDK using BuildKit cache mounts
RUN --mount=type=cache,target=/root/.gradle/caches,sharing=locked \
    --mount=type=cache,target=/root/.gradle/wrapper,sharing=locked \
    --mount=type=cache,target=/root/.gradle/build-cache,sharing=locked \
    chmod +x ./gradlew && \
    /build-xdk.sh

# *** STAGE 5 (Export stage to copy launcher to host filesystem)
FROM scratch AS launcher-export
COPY --from=builder /workspace/javatools_launcher/src/main/resources/exe/linux_launcher_* /

FROM bellsoft/liberica-runtime-container:jre-21-slim-musl

ENV XDK_HOME=/opt/xdk
ENV PATH="${XDK_HOME}/bin:${PATH}"

COPY --from=builder /workspace/xdk/build/install/xdk*/ /opt/xdk/

CMD ["xec"]
