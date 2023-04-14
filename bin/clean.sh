#!/bin/sh

project_root_dir=$(git rev-parse --show-toplevel)
if [ ! -d $project_root_dir ]; then
    echo "Cannot find project root directory."
fi

pushd $project_root_dir

find . -name "*.iml" -type f -delete
find . -name "*.ipr" -type f -delete
find . -name "*.iws" -type f -delete
rm -fr .idea
git clean -fxd

./gradlew clean --info

popd
