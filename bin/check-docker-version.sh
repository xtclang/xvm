#!/bin/sh

# Minimum required Docker version. Also supportes dot versions, and not just major versions.
required_version="24.0"
docker_version=$(docker version --format '{{.Server.Version}}')

if [[ "$(printf '%s\n' "$required_version" "$docker_version" | sort -V | head -n1)" != "$required_version" ]]; then
    echo "Required Docker version >= $required_version is required to build."
    echo "Current Docker version is $docker_version" 
    exit 1
else
    echo "Docker version is recent enough for build: $docker_version"
fi
