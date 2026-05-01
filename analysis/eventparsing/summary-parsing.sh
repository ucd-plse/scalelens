##########################################################################
#!/bin/bash
##########################################################################
##
source ../environment.sh

WK="$1"
FOLDER="$2"
DIMENSION="$3"
FILTER="$4"
LOOPS="$5"
SIZE="$6"
E_PER_L="$7"
E_PER_H="$8"

## check some stuff firts
if [ ! "$#" -eq 8 ]; then
  echo "[$0] USAGE: $0 <workload> <folder> <dimensionName> <package_filter> <system_loop_list> <dim_max_size> <exec_per_low> <exec_per_high>"
  echo "[$0] <workload>: The workload we will run."
  echo "[$0] <folder>: Comma separated list of summary directories for the given system. Use absolute path"
  echo "[$0] <dimensionName>: The dimension that is being targeted in the trace."
  echo "[$0] <package_filter>: The packages we want to filter from."
  echo "[$0] <system_loop_list>: The main list of loops per system."
  echo "[$0] <dim_max_size>: Dimension size when needed."
  echo "[$0] <exec_per_low>: Minimum execution percentage to filter spiky functions."
  echo "[$0] <exec_per_high>: Maximum execution percentage to filter spiky functions."
  exit 1
fi

## also validate this...
if ! checkAgentDir; then
  echo "[$0] No agent found, exiting..."
  exit 1
fi

## main class
MAIN="scaleview.agent.event.SummaryParsingMain"
## We need the log files and the input/output as properties
PROPERTIES="-Dscaleview.logFile=$PWD/$AGENT_LOG"
## classpath
CLASSPATH="$PWD/$AGENT/ScaleLearningAgent.jar:$PWD/$AGENT/gson-2.8.6.jar:$PWD/$AGENT/commons-math3-3.6.1.jar"
## heap size, per node
A_HEAP="-Xmx4G -Xms4G"
## and start the stuff
CMD="$JAVA_CMD -cp \"$CLASSPATH\" $A_HEAP $PROPERTIES $MAIN \"$WK\" $FOLDER $DIMENSION $FILTER $LOOPS $SIZE $E_PER_L $E_PER_H"
echo "[$0] Executing [$CMD]"
$JAVA_CMD -cp $CLASSPATH $A_HEAP $PROPERTIES $MAIN $WK $FOLDER $DIMENSION $FILTER $LOOPS $SIZE $E_PER_L $E_PER_H
## exit correctly
exit 0
