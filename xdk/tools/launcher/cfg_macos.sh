#!/bin/bash

# get directory of this script
if [[ -e "$0" ]]; then
  DIR=$(dirname "$0")
  EXP="N"
fi

if [[ ! -e "$DIR/cfg_macos.sh" ]]; then
  DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
  EXP="Y"
fi

if [[ -e "$DIR/cfg_macos.sh" ]]; then
  # copy launcher to various command names
  echo "Creating command line tools: \"xtc\", \"xec\", \"xam\""
  cp $DIR/macos_launcher $DIR/xtc
  cp $DIR/macos_launcher $DIR/xec
  cp $DIR/macos_launcher $DIR/xam

  if [[ "$EXP" == "Y" ]]; then
    case ":${PATH:=$DIR}:" in
        *:"$DIR":*)  ;;
        *) export PATH="${PATH:+${PATH}:}$DIR"  ;;
    esac
  else
    echo "Unable to export PATH; use \".\" or \"source\" to execute this shell file"
  fi
else
  echo "Unable to identify directory containing cfg_macos.sh"
fi
