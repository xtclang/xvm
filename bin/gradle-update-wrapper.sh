#!/bin/sh

wrapper_version=$1
if [ -z $wrapper_version ]; then
    echo "No version argument suppied."
else
    root_dir=$(git rev-parse --abbrev-ref HEAD)
    $root_dir/gradlew --gradle-version $wrapper_version
fi
