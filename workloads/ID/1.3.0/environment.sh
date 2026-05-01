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
export META_BASE=2001
export DATA_BASE=3001
export RPC_BASE=4001
export SYNC_BASE=5001
export DEFAULT_REPLICATION=1
export CMD_FILE="$CMD_DIR/command"
export SMALL_HEAP="-Xmx16G -Xms16G"
export LARGE_HEAP="-Xmx32G -Xms32G"
export NORMAL_HEAP="-Xmx4G -Xms4G"
export OP_HEAP="-Xmx1G -Xms1G"
export INCLUSIONS="org.apache.hadoop"
export EXCLUSIONS=""
export IP_FILE="ip"
export PORT_FILE='port'
export NAMENODE_IPC=9003
export NAMENODE_RPC=9002
export NAMENODE_SERVICE=9001
export NAMENODE_HTTP=8000
export DNNODE_IPC=2001
export DNNODE_SERVICE=3001
export DNNODE_HTTP=8001

export CNODE_INTERNAL_PORT_BASE=10700
export CNODE_CONSENSUS_PORT_BASE=10900
export DNODE_RPC_PORT_BASE=11500
export DNODE_INTERNAL_PORT_BASE=12500
export DNODE_MPP_DATA_EXCHANGE_PORT_BASE=13500
export DNODE_SCHEMA_REGION_CONSENSUS_PORT_BASE=14500
export DNODE_DATA_REGION_CONSENSUS_PORT_BASE=15500


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
function killAllNameNodesProcesses {
  for pid in $(jps | grep NameNode | cut -f1 -d' '); do
    echo "[$0] Killing [$pid]"
    kill -9 $pid
  done
}
##########################################################################
function killAllDataNodesProcesses {
  for pid in $(jps | grep DataNode | cut -f1 -d' '); do
    echo "[$0] Killing [$pid]"
    kill -9 $pid
  done
}
##########################################################################
function killAllConfigNodesProcesses {
  for pid in $(jps | grep ConfigNode | cut -f1 -d' '); do
    echo "[$0] Killing [$pid]"
    kill -9 $pid
  done
}
##########################################################################