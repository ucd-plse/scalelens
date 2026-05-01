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
export WORKLOAD_FILE=$(realpath workload_file)

# get the class to be instrumented
MAIN=$1

# get the scale
SCALE=$2
MAPPING_FILE="$MAIN-$SCALE.nm"

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
if [ -f "$WORKLOAD_FILE" ]; then
  rm $WORKLOAD_FILE
fi
if [ -f "$MAPPING_FILE" ]; then
  rm $MAPPING_FILE
fi

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
# CMD="$JAVA_CMD -cp . $MAIN"
# printf "[$0] Running [$CMD]\n"
# eval $CMD

## build the command to run java instrumentation
CMD="$JAVA_CMD $SL_AGENT_FLAGS -cp . $MAIN $WORKLOAD_FILE"
printf "[$0] Running [$CMD]\n"

## create the traces and cmds directories
mkdir $TRACES_DIR
mkdir $CMD_DIR
mkdir $INSTRUMENTED_DIR

## make symbolic link to the agent
REPO_DIR=$(git rev-parse --show-toplevel)
ln -sf $REPO_DIR/$AGENT $AGENT

# start the program, use & to run it in the background
eval $CMD &
sleep 5

for ((i=1; i<=SCALE; i++)); do 
  printf "[$0] The $MAIN is scaling up to $i\n"
  # write the command file to enable loop profiling and assign run ID
  printf "cmd=heapMeasure\nrunId=$i\ngcBefore=false\nenableLoopProfiling=true" > $CMD_FILE
  # write the value of x (number of iterations) for the program
  printf "$i" > $WORKLOAD_FILE
  # prepare the mapping file
  ITERATION=$(($i - 1))
  SCALE_TO=$i
  echo "$ITERATION,$SCALE_TO" >> $MAPPING_FILE
  sleep 5
done


echo "[$0] Done with scaling up $MAIN to $SCALE"
echo "[$0] Shutdown $MAIN"
printf "cmd=shutdown" > $CMD_FILE

exit