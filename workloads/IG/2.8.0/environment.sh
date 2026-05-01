
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
export JMX_DEFAULT=2001
export CMD_FILE="$CMD_DIR/command"
export DEFAULT_SEEDS="127.0.0.1"
export SMALL_HEAP="-Xmx32G -Xms32G"
export LARGE_HEAP="-Xmx128G -Xms128G"
export NORMAL_HEAP="-Xmx4G -Xms4G"
export INCLUSIONS="org.apache.ignite"
export EXCLUSIONS="java,org.omg,org.apache.ignite.internal.util,org.apache.ignite.logger"
export IP_FILE="ip"
export PORT_BASE=47500 

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
function buildClassPath {
SEP=":";
CLASSPATH=""
for file in $PWD/system/*
do
    if [ -d ${file} ] && [ -d "${file}/target" ]; then
        if [ -d "${file}/target/classes" ]; then
            CLASSPATH=${CLASSPATH}${SEP}${file}/target/classes
        fi

        if [ -d "${file}/target/test-classes" ]; then
            CLASSPATH=${CLASSPATH}${SEP}${file}/target/test-classes
        fi

        if [ -d "${file}/target/libs" ]; then
            CLASSPATH=${CLASSPATH}${SEP}${file}/target/libs/*
        fi
    fi
done
export CLASSPATH
}

##########################################################################

function setupIgniteClientEnv {
    local CLUSTER_BASE="$1"

    # Define environment variables for Ignite client
    IGNITE_CLIENT_HOME="$CLUSTER_BASE/ignite-client"
    IGNITE_CLIENT_CONF="$IGNITE_CLIENT_HOME/conf"
    IGNITE_CLIENT_WORKDIR="$IGNITE_CLIENT_HOME/work"

    # Create directories for Ignite client
    mkdir -p "$IGNITE_CLIENT_CONF" "$IGNITE_CLIENT_WORKDIR"

    # Copy configuration files
    cp "$CLUSTER_BASE/node-1/conf/ignite-config.xml" "$IGNITE_CLIENT_CONF/ignite-config.xml"
    cp "$CLUSTER_BASE/node-1/conf/ignite-log4j.xml" "$IGNITE_CLIENT_CONF/ignite-log4j.xml"

    # Update work directory path in ignite-config.xml
   # Update work directory path in ignite-config.xml
    sed -i 's|workDirectory" value="[^"]*|workDirectory" value="'"$IGNITE_CLIENT_WORKDIR"'|' "$IGNITE_CLIENT_CONF/ignite-config.xml"

    # Export the client config file and environment variables
    export CLIENT_CONFIG_FILE="$IGNITE_CLIENT_CONF/ignite-config.xml"
    export IGNITE_CLIENT_HOME
    export IGNITE_CLIENT_CONF
    export IGNITE_CLIENT_WORKDIR
} 


##########################################################################

function killAllIgniteProcesses {
  ## Kills all running Ignite processes identified by their main class
  for pid in $(jps -l | grep org.apache.ignite.startup.cmdline.CommandLineStartup | cut -f1 -d' '); do
    echo "[$0] Killing Ignite process [$pid]"
    kill -9 $pid 2>/dev/null
  done
}
##########################################################################