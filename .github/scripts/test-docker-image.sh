#!/bin/bash
set -euo pipefail

# Docker Image Testing Script
# Tests Docker image functionality including XTC program compilation and execution
#
# Usage: test-docker-image.sh IMAGE EXPECTED_COMMIT [EXPECTED_BRANCH] [BRANCH_IMAGE] [IS_MASTER]

IMAGE="$1"
EXPECTED_COMMIT="$2" 
EXPECTED_BRANCH="${3:-}"
BRANCH_IMAGE="${4:-}"
IS_MASTER="${5:-false}"

echo "🧪 Testing Docker image: $IMAGE"
echo "This tests compilation and execution of XTC programs with different parameters"

# Verify we're testing the correct image that was just built in this pipeline
echo "🔍 Verifying image matches current CI run..."
echo "  Expected commit: $EXPECTED_COMMIT"
if [[ -n "$EXPECTED_BRANCH" ]]; then
    echo "  Expected branch: $EXPECTED_BRANCH"
fi

# Check that the commit-tagged image version info matches the current CI context
version_output=$(docker run --rm ${IMAGE} xec --version)
echo "📋 Commit-tagged image version info:"
echo "$version_output"

if echo "$version_output" | grep -q "$EXPECTED_COMMIT"; then
    echo "✅ Commit-tagged image contains correct commit SHA: $EXPECTED_COMMIT"
else
    echo "❌ ERROR: Commit-tagged image does not contain expected commit SHA!"
    echo "Expected: $EXPECTED_COMMIT"
    echo "Actual version output: $version_output"
    exit 1
fi

# For master branch, verify that 'latest' tag points to the same image
# For non-master branches, verify that branch tag points to the same image  
if [[ -n "$BRANCH_IMAGE" ]]; then
    if [[ "$IS_MASTER" == "true" ]]; then
        echo "🔍 Verifying 'latest' tag for master branch..."
    else
        echo "🔍 Verifying branch tag for non-master branch..."
    fi
    
    branch_version_output=$(docker run --rm ${BRANCH_IMAGE} xec --version)
    echo "📋 Branch-tagged image version info:"
    echo "$branch_version_output"
    
    if echo "$branch_version_output" | grep -q "$EXPECTED_COMMIT"; then
        if [[ "$IS_MASTER" == "true" ]]; then
            echo "✅ Latest tag contains correct commit SHA: $EXPECTED_COMMIT"
        else
            echo "✅ Branch tag contains correct commit SHA: $EXPECTED_COMMIT"
        fi
    else
        if [[ "$IS_MASTER" == "true" ]]; then
            echo "❌ ERROR: Latest tag does not contain expected commit SHA!"
        else
            echo "❌ ERROR: Branch tag does not contain expected commit SHA!"
        fi
        echo "Expected: $EXPECTED_COMMIT"
        echo "Branch-tagged image version output: $branch_version_output"
        exit 1
    fi
fi

# Test launcher versions
echo "🔧 Testing launchers..."
docker run --rm ${IMAGE} xec --version
docker run --rm ${IMAGE} xcc --version

# Verify we're using script launchers, not native launchers
echo "🔍 Verifying Docker image uses script launchers..."
echo "📋 Showing launcher content preview:"
launcher_content=$(docker run --rm ${IMAGE} head -5 /opt/xdk/bin/xcc)
echo "$launcher_content"
if echo "$launcher_content" | grep -q "#!/bin/sh\|#!/bin/bash\|exec.*java"; then
    echo "✅ Docker image is using script launchers (as expected after fix)"
else
    echo "❌ Launcher doesn't appear to be a shell script - this indicates the distribution changes didn't work"
    echo "Full launcher content:"
    docker run --rm ${IMAGE} head -20 /opt/xdk/bin/xcc
    exit 1
fi

# Verify script launchers contain XTC module paths
echo "🔍 Verifying script launchers contain XTC module paths..."
script_content=$(docker run --rm ${IMAGE} cat /opt/xdk/bin/xcc)
if echo "$script_content" | grep -q "XDK_HOME.*APP_HOME" && \
   echo "$script_content" | grep -q "\-L.*lib" && \
   echo "$script_content" | grep -q "javatools_turtle.xtc" && \
   echo "$script_content" | grep -q "javatools_bridge.xtc"; then
    echo "✅ Script launchers contain expected XTC module paths"
else
    echo "❌ Script launchers missing XTC module paths - critical bug detected!"
    echo "📋 Script launcher content:"
    echo "$script_content"
    exit 1
fi

# Test compilation and execution with different argument patterns (like original EchoTest)
echo "🧪 Testing XTC program compilation and execution..."

echo "📋 Testing with no arguments..."
output_0=$(docker run --rm ${IMAGE} xec /opt/xdk/test/DockerTest.x 2>&1)
echo "$output_0"
if ! echo "$output_0" | grep -q "DockerTest invoked with 0 arguments\."; then
    echo "❌ No arguments test failed"
    exit 1
fi
echo "✅ No arguments test passed"

echo "📋 Testing with single argument..."
output_1=$(docker run --rm ${IMAGE} xec /opt/xdk/test/DockerTest.x "hello" 2>&1)
echo "$output_1"
if ! echo "$output_1" | grep -q "DockerTest invoked with 1 arguments:" || ! echo "$output_1" | grep -q '\[1\]="hello"'; then
    echo "❌ Single argument test failed"
    exit 1
fi
echo "✅ Single argument test passed"

echo "📋 Testing with multiple arguments..."
output_3=$(docker run --rm ${IMAGE} xec /opt/xdk/test/DockerTest.x "arg1" "arg with spaces" "arg3" 2>&1)
echo "$output_3"
if ! echo "$output_3" | grep -q "DockerTest invoked with 3 arguments:" || \
   ! echo "$output_3" | grep -q '\[1\]="arg1"' || \
   ! echo "$output_3" | grep -q '\[2\]="arg with spaces"' || \
   ! echo "$output_3" | grep -q '\[3\]="arg3"'; then
    echo "❌ Multiple arguments test failed"
    exit 1
fi
echo "✅ Multiple arguments test passed"

echo "🎉 All Docker image functionality tests passed!"
echo "✅ Script launcher functionality validated (fixed and now working!)"
echo "✅ XTC module path injection verified"
echo "✅ XTC program compilation validated" 
echo "✅ Program execution with 0, 1, and multiple arguments validated"