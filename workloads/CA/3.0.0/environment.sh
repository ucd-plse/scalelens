##########################################################################
#!/bin/bash
##########################################################################
## This script holds common variables used in all stuff.
##########################################################################
export TRUE="0"
export FALSE="1"
export B_TRUE="true"
export B_FALSE="false"
export INT_RE='^[0-9]+$'
export JAVA_CMD="java"
export PYTHON_CMD="python3"
export TPL="templates"
export SYS="system"
export AGENT="agent"
export AGENT_JAR="$AGENT/ScaleLearningAgent.jar"
export AGENT_ASSIST="$AGENT/javassist-3.21.0-GA.jar"
export AGENT_JVMTI="$PWD/$AGENT/heap_measurement_agent.so"
export AGENT_LOG="$AGENT/log-default.properties"
export AGENT_JOL="$AGENT/jol-core-0.16.jar"
export TRACES_DIR="traces"
export MS_DIR="measures"
export CMD_DIR="cmds"
export JMX_BASE=2000
export DEFAULT_TOKENS=4
export JMX_DEFAULT=2001
export CMD_FILE="$CMD_DIR/command"
export DEFAULT_SEEDS="127.0.0.1"
export SMALL_HEAP="-Xmx32G -Xms32G"
export LARGE_HEAP="-Xmx128G -Xms128G"
export NORMAL_HEAP="-Xmx4G -Xms4G"
export INCLUSIONS="org.apache.cassandra"
export DEFAULT_RAC="RAC1"
export DC_CONF="cassandra-topology.properties"
export DEFAULT_SNITCH="SimpleSnitch"
export DC_SNITCH="PropertyFileSnitch"
export IP_FILE="ip"
export CASSANDRA_PORT=9042
###########################################################################
function isBoolean {
  if  isEmpty "$1" || [ "$1" != "$B_TRUE" ] && [ "$1" != "$B_FALSE" ];  then
     return $FALSE
  fi
  return $TRUE
}
##########################################################################
function isInteger {
  if  isEmpty "$1" || ! [[ "$1" =~ $INT_RE ]]; then
     return $FALSE
  fi
  return $TRUE
}
##########################################################################
function isEmpty {
  ## $1: The param to check
  if [ -z "$1" ]; then
    return $TRUE
  else
    return $FALSE
  fi
}
##########################################################################
function isFile {
  ## $1: The param to check
  if isEmpty "$1" || [ ! -f "$1" ]; then
    return $FALSE
  else
    return $TRUE
  fi
}
##########################################################################
function isDirectory {
  ## $1: The param to check
  if isEmpty "$1" || [ ! -d "$1" ]; then
    return $FALSE
  else
    return $TRUE
  fi
}
##########################################################################
function checkAgentDir {
  ## here, the whole agent setup is validated
  if [ ! -d "$AGENT" ]; then
    echo "[$0] [$AGENT] folder does not exist. See readme for instructions."
    return $FALSE
  fi
}
##########################################################################
function killAllCassandraProcesses {
  for pid in $(jps | grep CassandraDaemon | cut -f1 -d' '); do
    echo "[$0] Killing [$pid]"
    kill -9 $pid
  done
}
##########################################################################
function createTopologyFile {
  TOPOLOGY_FILE="$1"
  COUNT="$2"
  echo "" > $TOPOLOGY_FILE
  for (( i = 1; i <= $COUNT; i++ )); do
    echo "127.0.0.$i=DC$i:$DEFAULT_RAC" >> $TOPOLOGY_FILE
  done
  echo "[$0] Topology file created for [$COUNT] nodes as "
  cat $TOPOLOGY_FILE
}
##########################################################################
