#!/bin/bash -x

echo "WARNING: This script will clear all caches and kill all processes with build state."
read -p "Are you sure? " -n 1 -r
echo  # Add newline after the single character input
if [[ $REPLY =~ ^[Yy]$ ]]
then
    echo "Stopping Gradle daemons properly..."
    # Try to stop Gradle daemons gracefully first
    if command -v gradle >/dev/null 2>&1; then
        gradle --stop 2>/dev/null || true
    fi
    # Also try with local gradlew if it exists
    if [[ -f "./gradlew" ]]; then
        ./gradlew --stop 2>/dev/null || true
    fi
    
    echo "Force killing any remaining daemon processes..."
    if command -v jps >/dev/null 2>&1; then
        jps | grep Gradle | awk '{print $1}' | xargs -r kill -9 2>/dev/null || true
        jps | grep Kotlin | awk '{print $1}' | xargs -r kill -9 2>/dev/null || true
    fi
    
    git_root=$(git rev-parse --show-toplevel)
    echo "Running git clean on: $git_root (sparing .idea directory, you may want to delete it manually)."    
    pushd "$git_root" || exit 1
    git clean -xfd -e .idea
    popd || exit 1
    
    echo "Deleting remaining build folders..."
    find "$git_root" -name build -type d | grep -v src | xargs -r rm -rf
    
    echo "Deleting all Gradle cache directories from GRADLE_USER_HOME"
    gradle_home="${GRADLE_USER_HOME:-$HOME/.gradle}"
    rm -rf "$gradle_home/caches"
    rm -rf "$gradle_home/daemon"  # Fixed: was 'daemons', should be 'daemon'
    rm -rf "$gradle_home/wrapper"
    rm -rf "$gradle_home/build-cache"

    echo "Deleting local maven repository"
    rm -rf "$HOME/.m2/repository"
    
    # Docker cleanup if docker is available
    if command -v docker >/dev/null 2>&1; then
        echo "Docker found - performing Docker system prune..."
        docker system prune -af --volumes 2>/dev/null || echo "Docker prune failed (non-fatal)"
    else
        echo "Docker not found in PATH - skipping Docker cleanup"
    fi
    
    echo "Purged." 
fi
