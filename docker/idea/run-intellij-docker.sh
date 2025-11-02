#!/bin/bash

# Script to run IntelliJ IDEA in Docker with X11 forwarding
# Works on macOS with XQuartz

set -e

echo "🚀 Setting up IntelliJ IDEA in Docker..."

# Check if XQuartz is installed (macOS)
if [[ "$OSTYPE" == "darwin"* ]]; then
    if [ ! -d "/Applications/Utilities/XQuartz.app" ]; then
        echo "❌ XQuartz is not installed."
        echo "📦 Install it with: brew install --cask xquartz"
        echo "⚠️  After installation, log out and log back in, then run:"
        echo "    xhost + 127.0.0.1"
        exit 1
    fi

    # Check if XQuartz is running
    if ! pgrep -x "Xquartz" > /dev/null; then
        echo "⚠️  XQuartz is not running. Starting XQuartz..."
        open -a XQuartz
        echo "⏳ Waiting for XQuartz to start..."
        sleep 3
    fi

    # Allow connections from localhost
    echo "🔓 Allowing X11 connections from localhost..."
    xhost + 127.0.0.1 || true

    # Get the IP for host.docker.internal on macOS
    DISPLAY_IP="host.docker.internal:0"
else
    # Linux
    xhost +local:docker || true
    DISPLAY_IP="${DISPLAY:-:0}"
fi

# Build the Docker image
echo "🔨 Building Docker image..."
docker build -f Dockerfile.intellij -t xvm-intellij:latest .

# Run the container
echo "🎯 Starting IntelliJ IDEA..."
docker run -it --rm \
    -e DISPLAY="${DISPLAY_IP}" \
    -v /tmp/.X11-unix:/tmp/.X11-unix:rw \
    -v "${HOME}/.gradle:/home/developer/.gradle" \
    -v "$(pwd)/.idea:/home/developer/xvm/.idea" \
    --name xvm-intellij \
    xvm-intellij:latest \
    /home/developer/xvm

echo "✅ IntelliJ IDEA should now be opening..."
echo "📝 Note: The first startup may take a few minutes while IntelliJ indexes the project."
