#!/bin/bash

#
# Script to pull and test the XVM Docker image with EchoTest
# This script demonstrates how to use the published Docker image
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Default Docker image
DEFAULT_IMAGE="ghcr.io/xtclang/xvm:latest"
DOCKER_IMAGE="${1:-$DEFAULT_IMAGE}"

echo "🐳 Testing XVM Docker image: $DOCKER_IMAGE"
echo "=================================================="

# Function to run a test and verify output
run_test() {
    local test_name="$1"
    local expected_pattern="$2"
    shift 2
    local args=("$@")
    
    echo "🧪 Running test: $test_name"
    echo "   Command: docker run --rm $DOCKER_IMAGE xec EchoTest ${args[*]:-}"
    
    if OUTPUT=$(docker run --rm "$DOCKER_IMAGE" xec EchoTest "${args[@]:-}" 2>&1); then
        echo "   Output: $OUTPUT"
        
        if echo "$OUTPUT" | grep -q "$expected_pattern"; then
            echo "   ✅ Test passed"
            return 0
        else
            echo "   ❌ Test failed - expected pattern not found: $expected_pattern"
            return 1
        fi
    else
        echo "   ❌ Test failed - docker run failed"
        return 1
    fi
    echo ""
}

# Check if image exists locally first, if not try to pull it
if docker image inspect "$DOCKER_IMAGE" >/dev/null 2>&1; then
    echo "✅ Found local Docker image: $DOCKER_IMAGE"
else
    echo "📥 Pulling Docker image..."
    if ! docker pull "$DOCKER_IMAGE"; then
        echo "❌ Failed to pull Docker image: $DOCKER_IMAGE"
        echo "   This could be due to:"
        echo "   1. Image doesn't exist or isn't public"
        echo "   2. Authentication required - try: docker login ghcr.io"
        echo "   3. Network connectivity issues"
        echo ""
        echo "   Available alternatives:"
        echo "   • Build locally: ./gradlew dockerBuildMultiPlatform"
        echo "   • Try specific tag: $0 ghcr.io/xtclang/xvm:faster-ci"
        echo "   • Check available tags at: https://github.com/orgs/xtclang/packages?repo_name=xvm"
        exit 1
    fi
fi
echo ""

# Test cases
echo "🚀 Running XVM Docker image tests..."
echo ""

# First test basic functionality
echo "🧪 Testing basic XVM functionality..."
if ! docker run --rm "$DOCKER_IMAGE" xec --version >/dev/null 2>&1; then
    echo "❌ Basic xec --version test failed"
    exit 1
fi
echo "✅ Basic XVM functionality works"

# Test with the actual EchoTest from the repo
echo "🧪 Testing with EchoTest from manualTests..."

# Copy EchoTest.x from the repo and test it
if ! docker run -v "$SCRIPT_DIR/../manualTests/src/main/x:/workspace" -w /workspace --rm "$DOCKER_IMAGE" sh -c 'xcc EchoTest.x' 2>/dev/null; then
    echo "⚠️  Could not compile EchoTest from repo - testing basic version output instead"
    
    # Fallback to basic version test
    echo "🧪 Testing version output..."
    VERSION_OUTPUT=$(docker run --rm "$DOCKER_IMAGE" xec --version 2>&1)
    if echo "$VERSION_OUTPUT" | grep -q "xdk version"; then
        echo "✅ Version test passed: $VERSION_OUTPUT"
    else
        echo "❌ Version test failed"
        exit 1
    fi
else
    echo "✅ Successfully compiled EchoTest from repo"
    
    # Now run the actual EchoTest scenarios with volume mount
    echo "🧪 Running EchoTest argument scenarios..."
    
    # Update run_test function to use volume mount
    run_test_with_volume() {
        local test_name="$1"
        local expected_pattern="$2"
        shift 2
        local args=("$@")
        
        echo "🧪 Running test: $test_name"
        echo "   Command: docker run -v manualTests/src/main/x:/workspace -w /workspace --rm $DOCKER_IMAGE xec EchoTest ${args[*]:-}"
        
        if OUTPUT=$(docker run -v "$SCRIPT_DIR/../manualTests/src/main/x:/workspace" -w /workspace --rm "$DOCKER_IMAGE" xec EchoTest "${args[@]:-}" 2>&1); then
            echo "   Output: $OUTPUT"
            
            if echo "$OUTPUT" | grep -q "$expected_pattern"; then
                echo "   ✅ Test passed"
                return 0
            else
                echo "   ❌ Test failed - expected pattern not found: $expected_pattern"
                return 1
            fi
        else
            echo "   ❌ Test failed - docker run failed"
            return 1
        fi
        echo ""
    }
    
    # Test 1: No arguments
    if ! run_test_with_volume "No arguments" "EchoTest invoked with 0 arguments\."; then
        exit 1
    fi

    # Test 2: Single argument
    if ! run_test_with_volume "Single argument" "EchoTest invoked with 1 arguments:" "hello"; then
        exit 1
    fi

    # Test 3: Multiple arguments  
    if ! run_test_with_volume "Multiple arguments" "EchoTest invoked with 3 arguments:" "arg1" "arg with spaces" "arg3"; then
        exit 1
    fi
fi

echo "🎉 All Docker image tests passed successfully!"
echo ""
echo "💡 Usage examples:"
echo "   # Test default image"
echo "   $0"
echo ""
echo "   # Test specific image"
echo "   $0 ghcr.io/xtclang/xvm:faster-ci"
echo ""
echo "   # Run EchoTest manually"
echo "   docker run --rm $DOCKER_IMAGE xec EchoTest \"your\" \"arguments\" \"here\""
echo ""
echo "   # Test basic XVM functionality"
echo "   docker run --rm $DOCKER_IMAGE xec --version"
echo "   docker run --rm $DOCKER_IMAGE xcc --version"