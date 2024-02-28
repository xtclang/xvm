#!/bin/bash

# get directory of this script
if [[ -e "$0" ]]; then
  DIR=$(dirname "$0")
  DIR="$(cd "$(dirname "$DIR")" || exit; pwd)/$(basename "$DIR")"
  EXP="Y"
fi

if [[ ! -e "${DIR}/cfg_linux.sh" ]]; then
  DIR="$( cd "$( dirname "${(%):-%N}" )" && pwd )"
  EXP="Y"
fi

# find the Java executable
JTYPE=`type -p java`
if [[ -n "${JTYPE}" ]]; then
  JEXEC="java"
  JADD=""
elif [[ -n "${JAVA_HOME}" ]] && [[ -x "${JAVA_HOME}/bin/java" ]];  then
  ADD="${JAVA_HOME}/bin/"
  JEXEC="${ADD}java"
else
  echo "Unable to find the java executable; add it to PATH, or set JAVA_HOME"
  exit 1
fi

# verify Java version 17 or later
JVER=$("${JEXEC}" -version 2>&1 | awk -F '"' '/version/ {print $2}')
JMAJ="${JVER%%.*}"
if [[ "${JMAJ}" -lt "21" ]]; then
  echo "Java version is ${JVER}; Java 21 or later required"
  exit 1
fi

if [[ -e "${DIR}/cfg_macos.sh" ]]; then
  # copy launcher to various command names
  echo "Creating command line tools: \"xcc\", \"xec\""
  cp "${DIR}/linux_launcher" "${DIR}/xcc"
  cp "${DIR}/linux_launcher" "${DIR}/xec"

  if [[ "${EXP}" == "Y" ]]; then
    if [[ -n "${ADD}" ]]; then
      echo "Adding Java to PATH"
      PATH="${PATH:+${PATH}:}${ADD}"
    fi
    case ":${PATH:=${DIR}}:" in
      *:"${DIR}":*)  ;;
      *) export PATH="${PATH:+${PATH}:}${DIR}"  ;;
    esac
  else
    echo "Unable to export PATH; use \".\" or \"source\" to execute this shell file"
  fi
else
  echo "Unable to identify directory containing cfg_linux.sh"
fi
