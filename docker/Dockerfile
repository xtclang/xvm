# syntax=docker/dockerfile:1
#
# XDK Docker Container - Simplified version using pre-built distribution
# See README.md for complete build and usage instructions
#

# Build arguments
ARG JAVA_VERSION=MISSING
ARG TARGETARCH
ARG TARGETOS
ARG DIST_ZIP_URL

# *** STAGE 1: Java version validation ***
FROM alpine:latest AS java-version-validator
ARG JAVA_VERSION=MISSING
RUN if [ "$JAVA_VERSION" = "MISSING" ]; then \
        echo "ERROR: JAVA_VERSION build argument is required but not provided"; \
        echo "Usage: docker build --build-arg JAVA_VERSION=21 ..."; \
        exit 1; \
    fi

# *** STAGE 2: Distribution extractor ***
FROM alpine:latest AS distribution-extractor
ARG DIST_ZIP_URL
ARG TARGETARCH
ARG TARGETOS

# Install required tools
RUN apk add --no-cache unzip curl file

WORKDIR /workspace

# Copy the distribution ZIP from build context and extraction script
COPY xdk-dist.zip /workspace/
COPY scripts/extract-distribution.sh /extract-distribution.sh
RUN chmod +x /extract-distribution.sh

# Extract the distribution using just the DIST_ZIP_URL
RUN /extract-distribution.sh "/workspace/xdk-dist.zip"

# *** STAGE 3: Final Runtime Image ***
ARG JAVA_VERSION=MISSING
FROM bellsoft/liberica-runtime-container:jre-${JAVA_VERSION}-slim-musl

ENV XDK_HOME=/opt/xdk
ENV PATH="${XDK_HOME}/bin:${PATH}"

# Copy XDK distribution from extractor stage
COPY --from=distribution-extractor /workspace/xdk/ /opt/xdk/

# Copy Docker test program
COPY test/DockerTest.x /opt/xdk/test/

# Verify the distribution and test launchers
RUN echo "✅ Using script launchers from distribution" && \
    chmod +x /opt/xdk/bin/xcc /opt/xdk/bin/xec /opt/xdk/bin/xtc && \
    ls -la /opt/xdk/bin/ && \
    echo "📋 Testing script launcher functionality..." && \
    /opt/xdk/bin/xcc --version && \
    /opt/xdk/bin/xec --version && \
    /opt/xdk/bin/xtc --version && \
    echo "✅ Script launchers are working correctly!"

CMD ["xec"]
