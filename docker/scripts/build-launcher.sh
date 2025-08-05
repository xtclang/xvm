#!/bin/bash
set -e

# Build launcher from source already copied from source-cloner stage
cd javatools_launcher/src/main/c

echo "Building launcher for ${TARGETOS}/${TARGETARCH}"
case "${TARGETARCH}" in
    amd64) 
        ARCH_FLAGS="-O3 -mtune=generic" 
        LAUNCHER_NAME="linux_launcher_x86_64" 
        ;;
    arm64) 
        ARCH_FLAGS="-O3 -march=armv8-a -mtune=cortex-a72" 
        LAUNCHER_NAME="linux_launcher_aarch64" 
        ;;
    *) 
        echo "ERROR: Unsupported platform ${TARGETOS}/${TARGETARCH}" 
        exit 1 
        ;;
esac

mkdir -p /launcher-output /tmp/gcc-cache
gcc -static -g -Wall -std=gnu11 -DlinuxLauncher ${ARCH_FLAGS} \
    launcher.c os_linux.c os_unux.c -o /launcher-output/${LAUNCHER_NAME} || \
(echo "ERROR: Failed to build launcher for ${TARGETOS}/${TARGETARCH}" && exit 1)

echo "Successfully built launcher: ${LAUNCHER_NAME}"