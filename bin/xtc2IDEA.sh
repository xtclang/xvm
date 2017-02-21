#!/bin/bash

# install the .x source code syntax highlighting into the IntelliJ IDEA 14
binDir=$(dirname "$BASH_SOURCE")
cp ${binDir}/Ecstasy.xml ~/Library/Preferences/IntelliJIdea14/filetypes/
echo "***" restart the IntelliJ "***"