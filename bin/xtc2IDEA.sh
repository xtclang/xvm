#!/bin/bash

# install the .x source code syntax highlighting into the IntelliJ IDEA

binDir=$(dirname "$BASH_SOURCE")

ideaDir=$(find ~/Library/Application\ Support/JetBrains/ -name Idea\*) 
typesDir=${ideaDir}/filetypes

if [ ! -d "${typesDir}" ]
then
  mkdir "${typesDir}"
fi
cp ${binDir}/Ecstasy.xml "${typesDir}"

echo "***" restart the IntelliJ "***"
