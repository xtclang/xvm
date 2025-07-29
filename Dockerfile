# syntax=docker/dockerfile:1
#
# To build this, do something like:
#     Basic (latest master): docker buildx build -t xvm:latest .
#     Local source: docker buildx build --build-arg VERSION=local -t xvm:latest .
#     Specific commit: docker buildx build --build-arg VERSION=abc123 -t xvm:latest .
#     GitHub release: docker buildx build --build-arg VERSION=v1.0.0 -t xvm:latest .
#     Linux AMD64: docker buildx build --platform linux/amd64 -t xvm:linux-amd64 --load .
#     Linux ARM64: docker buildx build --platform linux/arm64 -t xvm:linux-arm64 --load .
#     Multi-platform: docker buildx build --platform linux/amd64,linux/arm64 -t xvm:latest --push .
#     Caching: docker buildx build --cache-from type=gha --cache-to type=gha,mode=max -t xvm:latest .

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
    ARCH_MAP() { case "$1" in amd64) echo "x86_64";; arm64) echo "aarch64";; *) echo "$1";; esac; }; \
    OS_ARCH=$(ARCH_MAP "$TARGETARCH"); \
    GRADLE_OPTS="-Dorg.gradle.jvmargs=-Dos.arch=$OS_ARCH"; \
    export GRADLE_OPTS; \
    if [ "$VERSION" = "local" ]; then \
        echo "Building from local source for $TARGETOS/$TARGETARCH (mapped to $OS_ARCH)"; \
        chmod +x ./gradlew; \
        ./gradlew --no-daemon xdk:installWithLaunchersDist; \
    elif [ "$VERSION" = "latest" ]; then \
        echo "Building from latest master commit for $TARGETOS/$TARGETARCH (mapped to $OS_ARCH)"; \
        rm -rf ./* ./.??* 2>/dev/null || true; \
        curl -L https://github.com/xtclang/xvm/archive/refs/heads/master.tar.gz | tar -xz --strip-components=1; \
        chmod +x ./gradlew; \
        ./gradlew --no-daemon xdk:installWithLaunchersDist; \
    elif echo "$VERSION" | grep -q "^v[0-9]"; then \
        echo "Building from GitHub release: $VERSION"; \
        rm -rf ./* ./.??* 2>/dev/null || true; \
        curl -L "https://github.com/xtclang/xvm/releases/download/$VERSION/xdk.tar.gz" | tar -xz; \
    else \
        echo "Building from commit: $VERSION for $TARGETOS/$TARGETARCH (mapped to $OS_ARCH)"; \
        rm -rf ./* ./.??* 2>/dev/null || true; \
        curl -L "https://github.com/xtclang/xvm/archive/$VERSION.tar.gz" | tar -xz --strip-components=1; \
        chmod +x ./gradlew; \
        ./gradlew --no-daemon xdk:installWithLaunchersDist; \
    fi

FROM eclipse-temurin:21-jre

RUN groupadd -r xtclang && useradd -r -g xtclang -d /home/xtclang -m xtclang

ENV XDK_HOME=/opt/xdk
RUN mkdir -p /opt/xdk && chown xtclang:xtclang /opt/xdk

COPY --from=builder --chown=xtclang:xtclang /workspace/xdk/build/install/xdk*/ /opt/xdk/

ENV PATH="${XDK_HOME}/bin:${PATH}"

USER xtclang
WORKDIR /home/xtclang

CMD ["xec"]
