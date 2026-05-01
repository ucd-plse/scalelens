##########################################################################
#!/bin/bash
##########################################################################
## This scripts calculates general stats for a given code base. It takes the source 
## code folders, no jars or nothing else, and outputs to stdout.
source ../environment.sh

SRC_INPUT="$1"
SRC_CP="$2"
SRC_INSTRUMENT="$3"

## check some stuff firts
if [ ! "$#" -eq 3 ]; then
  echo "[$0] USAGE: $0 <source_paths> <classpath> <instrument_loops>"
  echo "[$0] <source_paths>: Comma separated list of source directories for the given system. Use absolute path."
  echo "[$0] <classpath>: Comma separated list of source directories for the given system. Use absolute path."
  echo "[$0] <instrument_loops>: True if you want to add code to the stuff, false otherwise"
  exit 1
fi

## also validate this...
if ! checkAgentDir; then
  echo "[$0] No agent found, exiting..."
  exit 1
fi

## main class
MAIN="spoon.Launcher"
## processor class
PROCESSOR="scaleview.analysis.processor.GeneralStatsProcessor"
## We need the log files and the input/output as properties
PROPERTIES="-Dscaleview.logFile=$PWD/$AGENT_LOG -DdoInstrument=$SRC_INSTRUMENT"
## classpath
CLASSPATH="$PWD/$AGENT/ScaleLearningAgent.jar:$PWD/$AGENT/gson-2.8.6.jar:$PWD/$AGENT/spoon-core-9.1.0-SNAPSHOT-jar-with-dependencies.jar:$SRC_CP"
## heap size, per node
A_HEAP="-Xmx4G -Xms4G"
## and start the stuff
CMD="$JAVA_CMD -cp \"$CLASSPATH\" $A_HEAP $PROPERTIES $MAIN -a --level TRACE -i $SRC_INPUT --source-classpath $SRC_CP --with-imports --cpmode FULLCLASSPATH -p $PROCESSOR"
echo "[$0] Executing [$CMD]"
$JAVA_CMD -cp $CLASSPATH $A_HEAP $PROPERTIES $MAIN -a --level TRACE -i $SRC_INPUT --source-classpath $SRC_CP -p $PROCESSOR
## $JAVA_CMD -cp $CLASSPATH $A_HEAP $PROPERTIES $MAIN --help
## $JAVA_CMD -cp $CLASSPATH $A_HEAP $PROPERTIES $MAIN
## exit correctly
exit 0
