#!/bin/bash

function semverParseInto() {
    local _re='[^0-9]*\([0-9]*\)[.]\([0-9]*\)[.]\([0-9]*\)\([0-9A-Za-z-]*\)'
    eval $2=`echo $1 | sed -e "s#$_re#\1#"`
    eval $3=`echo $1 | sed -e "s#$_re#\2#"`
    eval $4=`echo $1 | sed -e "s#$_re#\3#"`
    eval $5=`echo $1 | sed -e "s#$_re#\4#"`
}

#if [ "___semver.sh" == "___`basename $0`" ]; then

MAJOR=0
MINOR=0
PATCH=0
SPECIAL=""

semverParseInto $1 MAJOR MINOR PATCH SPECIAL
echo "$1 -> M: $MAJOR m:$MINOR p:$PATCH s:$SPECIAL"
