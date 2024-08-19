# syntax=docker/dockerfile:1

# For format and simple guide, see: https://docs.docker.com/go/dockerfile-reference/

ARG PLATFORM_NAME_LINUX="linux_amd64"
ARG PLATFORM_NAME_MAC_AARCH64="macos_aarch64"
ARG PLATFORM_NAME=$PLATFORM_NAME_LINUX

# Image contains Gradle user and home directory, Gradle v8.10 and JDK 21.
FROM gradle:jdk21-alpine as build-image

WORKDIR /home/gradle/
COPY --chown=gradle:gradle . .
RUN git clean -fxd
# Use Docker's cache mount feature for Gradle's build cache and dependencies
RUN --mount=type=cache,target=/home/gradle/.gradle gradle installDist --no-daemon
RUN ls -l xdk/build/install/ >out.txt
RUN cat out.txt
FROM openjdk:21-jdk
ARG PLATFORM_NAME
ARG XDK_HOME="/opt/xdk-$PLATFORM_NAME"
ARG XDK_HOME_UNIVERSAL="/opt/xdk/"
ENV XDK_HOME=$XDK_HOME

COPY --from=build-image "/home/gradle/xdk/build/install/" "/install"
#COPY --from=build-image "/home/gradle/xdk/build/install/xdk" $XDK_HOME_UNIVERSAL
#COPY --from=build-image "/home/gradle/xdk/build/install/xdk-$PLATFORM_NAME" $XDK_HOME
ENV PATH=$XDK_HOME/bin:$PATH
ENTRYPOINT [ "/bin/bash" ]

#ENTRYPOINT [ "xcc", "$XDK_HOME/examples/FizzBuzz.x" ]
