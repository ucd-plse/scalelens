##########################################################################
#!/bin/bash
##########################################################################
##
source ../environment.sh

INPUT="$1"
OUTPUT="$2"

## check some stuff firts
if [ ! "$#" -eq 2 ]; then
  echo "[$0] USAGE: $0 <event_paths> <output_file>"
  echo "[$0] <source_paths>: Root folder containing the events to merge. Use absolute path."
  echo "[$0] <output_file>: File where the events will be merged. Use absolute path."
  exit 1
fi

## also validate this...
if ! checkAgentDir; then
  echo "[$0] No agent found, exiting..."
  exit 1
fi

## main class
MAIN="scaleview.agent.event.EventMergingMain"
## We need the log files and the input/output as properties
PROPERTIES="-Dscaleview.logFile=$PWD/$AGENT_LOG"
## classpath
CLASSPATH="$PWD/$AGENT/ScaleLearningAgent.jar:$PWD/$AGENT/gson-2.8.6.jar"
## heap size, per node
A_HEAP="-Xmx8G -Xms8G"
## and start the stuff
CMD="$JAVA_CMD -cp \"$CLASSPATH\" $A_HEAP $PROPERTIES $MAIN \"$INPUT\" \"$OUTPUT\""
echo "[$0] Executing [$CMD]"
$JAVA_CMD -cp $CLASSPATH $A_HEAP $PROPERTIES $MAIN $INPUT $OUTPUT
## exit correctly
exit 0
