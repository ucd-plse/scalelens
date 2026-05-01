##########################################################################
#!/bin/bash
##########################################################################
## This script is used to run the instrumentation agent on a given jar
## file. It is assumed that the jar file is in the same directory as
## this script.

## Some common variables
export JAVA_CMD="java"
export AGENT="agent"
export AGENT_JAR="$AGENT/ScaleLearningAgent.jar"
export AGENT_ASSIST="$AGENT/javassist-3.21.0-GA.jar"
export AGENT_LOG="$AGENT/log-default.properties"
export AGENT_JOL="$AGENT/jol-core-0.16.jar"
export TRACES_DIR="traces"
export CMD_DIR="cmds"
export CMD_FILE="$CMD_DIR/command"
export INSTRUMENTED_DIR="instrumented"


## delete the traces and cmds directories if they exist
if [ -d "$TRACES_DIR" ]; then
  rm -rf $TRACES_DIR
fi
if [ -d "$CMD_DIR" ]; then
  rm -rf $CMD_DIR
fi
if [ -d "$INSTRUMENTED_DIR" ]; then
  rm -rf $INSTRUMENTED_DIR
fi

# get the class to be instrumented
MAIN=$1

## flags for instrumentation agent
# all ";" should be escaped with "\" so Bash does not take them as shell commands.
# path to the agent and javaassist
SL_AGENT_FLAGS="-Xbootclasspath/p:$AGENT_JAR:$AGENT_ASSIST" 
SL_AGENT_FLAGS="$SL_AGENT_FLAGS -Djdk.attach.allowAttachSelf=true -javaagent:$AGENT_JAR"
# method entry profiler configuration
SL_AGENT_FLAGS="$SL_AGENT_FLAGS=profiler:name=methods,coreThreads=1,maxThreads=1,outputDirectory=$PWD/$TRACES_DIR,waitWhenShutDown=true,flushThreshold=100,inclusions=$MAIN"
# command thread configuration
# sleep time is in milliseconds, 500 means an independent thread command is checking the command file every 0.5 seconds
SL_AGENT_FLAGS="$SL_AGENT_FLAGS\;commandThread:commandDirectory=$PWD/$CMD_DIR,sleepTime=500"
# logging comfiguration
SL_AGENT_FLAGS="$SL_AGENT_FLAGS -Dscaleview.logFile=$PWD/$AGENT_LOG"


## run the java program without instrumentation
CMD="$JAVA_CMD -cp . $MAIN"
printf "[$0] Running [$CMD]\n"
eval $CMD

## build the command to run java instrumentation
CMD="$JAVA_CMD $SL_AGENT_FLAGS -cp . $MAIN"
printf "[$0] Running [$CMD]\n"

## create the traces and cmds directories
mkdir $TRACES_DIR
mkdir $CMD_DIR
mkdir $INSTRUMENTED_DIR

# write the command file to enable loop profiling
printf "cmd=heapMeasure\nrunId=1\ngcBefore=false\nenableLoopProfiling=true" > $CMD_FILE
sleep 5

## run the command
eval $CMD 