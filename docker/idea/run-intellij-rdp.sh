#!/bin/bash

# Script to run IntelliJ IDEA in Docker with XRDP (Remote Desktop)
# Connect with Microsoft Remote Desktop to localhost:3389

set -e

echo "🚀 Setting up IntelliJ IDEA in Docker with RDP..."

# Build the Docker image
echo "🔨 Building Docker image..."
docker build -f Dockerfile.intellij-rdp -t xvm-intellij-rdp:latest .

# Check if container is already running
if docker ps -a --format '{{.Names}}' | grep -q '^xvm-intellij-rdp$'; then
    echo "⚠️  Container already exists. Removing..."
    docker rm -f xvm-intellij-rdp
fi

# Run the container
echo "🎯 Starting IntelliJ IDEA with RDP..."
docker run -d \
    -p 3389:3389 \
    -v "${HOME}/.gradle:/home/developer/.gradle" \
    -v "$(pwd)/.idea:/home/developer/xvm/.idea" \
    --name xvm-intellij-rdp \
    xvm-intellij-rdp:latest

echo "✅ Container is running!"
echo ""
echo "📝 Connection Instructions:"
echo "   1. Open Microsoft Remote Desktop"
echo "   2. Add a new PC with address: localhost:3389"
echo "   3. Username: developer"
echo "   4. Password: developer"
echo ""
echo "   Once connected, you'll see an Xfce desktop."
echo "   Double-click 'IntelliJ' on the desktop to launch IntelliJ IDEA."
echo ""
echo "🔍 To view container logs:"
echo "   docker logs -f xvm-intellij-rdp"
echo ""
echo "🛑 To stop the container:"
echo "   docker stop xvm-intellij-rdp"
echo ""
echo "🗑️  To remove the container:"
echo "   docker rm -f xvm-intellij-rdp"
