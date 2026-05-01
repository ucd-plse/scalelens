##########################################################################
#!/bin/bash
##########################################################################
## This scripts looks for sd entry points using spoon. It needs the path 
## to the source code folder, an optional entrypoints input (see ep.sample.json) 
## and the scale dependdencies collected in prev. phases (see input.sample.json)
source ../environment.sh

SRC_INPUT="$1"
SD_INPUT="$2"
EP_CONF="$3"

## check some stuff firts
if ! isFile "$SD_INPUT" ||  [ "$#" -lt 2 ] ||  [ "$#" -gt 3 ]; then
  echo "[$0] USAGE: $0 <source_paths> <input> <endpoints>"
  echo "[$0] <source_paths>: Comma separated list of source directories for the given system. Use absolute path."
  echo "[$0] <input>: Scale-Dependencies file (see input.sample.json) for the target system."
  echo "[$0] <endpoints>: [OPTIONAL] Entrypoints configuration."
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
PROCESSOR="scaleview.analysis.processor.SDEntryPointProcessor"
## We need the log files and the input/output as properties
PROPERTIES="-Dscaleview.logFile=$PWD/$AGENT_LOG"
PROPERTIES="-Dscaleview.inputJsonPath=$SD_INPUT $PROPERTIES"
PROPERTIES="-Dscaleview.outputJsonPath=sdep-$RANDOM.json $PROPERTIES"
## classpath
CLASSPATH="$PWD/$AGENT/ScaleLearningAgent.jar:$PWD/$AGENT/gson-2.8.6.jar:$PWD/$AGENT/spoon-core-9.1.0-SNAPSHOT-jar-with-dependencies.jar"
## heap size, per node
A_HEAP="-Xmx4G -Xms4G"
## and start the stuff
CMD="$JAVA_CMD -cp \"$CLASSPATH\" $A_HEAP $PROPERTIES $MAIN -i $SRC_INPUT -p $PROCESSOR"
echo "[$0] Executing [$CMD]"
$JAVA_CMD -cp $CLASSPATH $A_HEAP $PROPERTIES $MAIN -i $SRC_INPUT -p $PROCESSOR
## exit correctly
exit 0
