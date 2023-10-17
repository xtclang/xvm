#!/bin/sh
#
# These are the build scans for ./gradlew clean and ./gradlew build, respectively,
# starting from a completely clean environment, with the first version of the XTC
# aware Gradle plugin.
#

export CLEAN_SCAN="https://gradle.com/s/pibe2ndqobx4e"
export BUILD_SCAN="https://gradle.com/s/rqcul4rapscys"

echo "./gradlew clean; build scan at: $CLEAN_SCAN"
echo "./gradlew build; build scan at: $BUILD_SCAN"
