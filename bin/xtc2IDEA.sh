#!/bin/bash

# install the .x source code syntax highlighting into the IntelliJ IDEA 14

binDir=$(dirname "$BASH_SOURCE")
ideaDir=~/Library/Preferences/IntelliJIdea14

if [ ! -d ${ideaDir} ]
then
  echo "***" Intellij IDEA 14 is not installed "***"
  exit
fi

typesDir=${ideaDir}/filetypes

if [ ! -d ${typesDir} ]
then
  mkdir ${typesDir}
fi
cp ${binDir}/Ecstasy.xml ${typesDir}

echo "***" restart the IntelliJ "***"