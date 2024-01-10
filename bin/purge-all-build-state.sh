#!/bin/bash -x

echo "WARNING: This script will clear all caches and kill all processes with build state."
read -p "Are you sure? " -n 1 -r
if [[ $REPLY =~ ^[Yy]$ ]]
then
    echo "Killing all running daemons..."
    jps | grep Gradle | awk {'print $1'} | xargs kill -9
    jps | grep Kotlin | awk {'print $1'} | xargs kill -9
    
    git_root=$(git rev-parse --show-toplevel)
    echo "Running git clean on: $git_root (sparing .idea directory, you may want to delete it manually)."    
    pushd $git_root
    git clean -xfd -e .idea
    popd
    
    echo "Deleting remaining build folders..."
    find $git_root -name build -type d | grep -v src | xargs rm -rf
    
    echo "Deleting build and daemon cache from GRADLE_USER_HOME"
    rm -fr $HOME/.gradle/caches
    rm -fr $HOME/.gradle/daemons

    echo "Deleting local maven repository"
    rm -fr $HOME/.m2/repository
    
    echo "Purged." 
fi
