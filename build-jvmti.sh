#!/bin/bash

echo "[$0] Building jvmti agent"
HERE=$PWD

JVMTI_PATH=$1
if [ -z "$JVMTI_PATH" ]; then
    JVMTI_PATH="/usr/lib/jvm/java-8-openjdk-amd64/include"
    echo "[$0] Defaulting JVMTI to [$JVMTI_PATH]"
fi

## do the hustle...
cd src/main/cpp/scaleview/agent/jvmti
CMD="make all TARGET_FOLDER=$HERE JVMTI=$JVMTI_PATH"
echo "[$0] Running [$CMD]"
$CMD
## and return
cd $HERE