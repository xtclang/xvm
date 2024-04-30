#!/bin/bash
# Automatic make-dependency discovery for X files
# Usage: src (without the .x)  dst  (without the .d)
echo -n $2.d $2.xtc ":	" > $2.d;
((test -d $2 && (/usr/bin/find $1 -name *.x | xargs echo)) >> $2.d ) || true;
