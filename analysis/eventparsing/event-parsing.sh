##########################################################################
#!/bin/bash
##########################################################################
##
source ../environment.sh

INPUT="$1"
FILTER="$2"
DIMENSION="$3"
LIMIT="$4"
MP="$5"

## check some stuff firts
if [ ! "$#" -eq 5 ]; then
  echo "[$0] USAGE: $0 <source_paths> <filter> <dimension> <limit> <mapping_file>"
  echo "[$0] <source_paths>: Comma separated list of event directories for the given system. Use absolute path."
  echo "[$0] <filter>: Event filter. Could be \"L\" for loops or \"H\" for heap collections."
  echo "[$0] <dimension>: The dimension that is being targeted in the trace."
  echo "[$0] <limit>: The maximum size of the dimension for this experiment."
  echo "[$0] <mapping_file>: The mapping file, to map the run id to the actual data. Use absolute path."
  exit 1
fi

## also validate this...
if ! checkAgentDir; then
  echo "[$0] No agent found, exiting..."
  exit 1
fi

## main class
MAIN="scaleview.agent.event.EventParsingMain"
## We need the log files and the input/output as properties
PROPERTIES="-Dscaleview.logFile=$PWD/$AGENT_LOG"
## classpath
CLASSPATH="$PWD/$AGENT/ScaleLearningAgent.jar:$PWD/$AGENT/gson-2.8.6.jar:$PWD/$AGENT/commons-math3-3.6.1.jar"
## heap size, per node
A_HEAP="-Xmx248G -Xms248G"
## and start the stuff
CMD="$JAVA_CMD -cp \"$CLASSPATH\" -XX:+PrintGC -XX:+UseParallelGC $A_HEAP $PROPERTIES $MAIN \"$INPUT\" $FILTER $DIMENSION $LIMIT $MP"
echo "[$0] Executing [$CMD]"
$JAVA_CMD -XX:+PrintGC -XX:+UseParallelGC -cp $CLASSPATH $A_HEAP $PROPERTIES $MAIN $INPUT $FILTER $DIMENSION $LIMIT $MP
## exit correctly
exit 0
