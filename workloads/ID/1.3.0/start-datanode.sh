
##########################################################################
#!/bin/bash
##########################################################################
## this script starts a config node from the jar files found in 'system'

## source env and common
source environment.sh
source iotdb-common.sh

## take arguments. We only need to know (1) the node base, (2) run with Agent or not, (3) optional: use small heap or not
NODE_BASE="$1"
NODE_CONF="$1/conf"
NODE_LOG="$1/logs"
NODE_DATA="$1/data"
USE_AG="$2"
USR_SMALL_HEAP="$3"

## check inputs
if ! isDirectory "$NODE_BASE" || [ "$#" -ne 2 ] || [ "$#" -ne 2 ]; then
  echo "[$0] USAGE: $0 <directory_path> <use-agent> <optional: small_heap>"
  echo "[$0] <directory_path>: The content directory of an existing node (see setup-node.sh). Use absolute path."
  echo "[$0] <use-agent>: $B_TRUE if this node should start with agent, $B_FALSE otherwise."
  echo "[$0] <small-heap>: $B_TRUE if this node should start with agent and a small heap, $B_FALSE otherwise."
  exit 1
fi

## also validate this...
if [ "$USE_AG" = "$B_TRUE" ] && ! checkAgentDir; then
  echo "[$0] exiting..."
  exit 1
fi

## setup the heap for the instrumented node
if [ "$#" -eq 3 ]; then
  USE_SMALL_HEAP=$B_FALSE
  HEAP_SIZE="$LARGE_HEAP"
else
  if [ "$USE_SMALL_HEAP" = "$B_TRUE" ]; then
    HEAP_SIZE="$SMALL_HEAP"
  else
    HEAP_SIZE="$LARGE_HEAP"
  fi
fi


CLASSPATH=""
for f in "${SYS}"/*.jar; do
  CLASSPATH=${CLASSPATH}":"$f
done
MAIN=org.apache.iotdb.db.service.DataNode


# flags for the agent
if [ "$USE_AG" = "$B_TRUE" ]; then
  ## Profiling agent, base part
  SL_AGENT_FLAGS="-Xbootclasspath/p:$AGENT_JAR:$AGENT_ASSIST"
  SL_AGENT_FLAGS="$SL_AGENT_FLAGS -Djdk.attach.allowAttachSelf -javaagent:$AGENT_JAR"
  ## method entry profiler configuration
  SL_AGENT_FLAGS="$SL_AGENT_FLAGS=profiler:name=methods,coreThreads=16,maxThreads=16,outputDirectory=$NODE_BASE/$TRACES_DIR,waitWhenShutDown=true,flushThreshold=100,inclusions=$INCLUSIONS,exclusions=$EXCLUSIONS"
  ## command thread configuration
  SL_AGENT_FLAGS="$SL_AGENT_FLAGS;commandThread:commandDirectory=$NODE_BASE/$CMD_DIR,sleepTime=500"
  ## and finally logging
  SL_AGENT_FLAGS="$SL_AGENT_FLAGS -Dscaleview.logFile=$PWD/$AGENT_LOG"
else
  SL_AGENT_FLAGS=" "
fi


# flags for IoTDB datanode
IOTDB_LOG_CONFIG="${NODE_CONF}/logback-datanode.xml"
IOTDB_HOME="${NODE_BASE}"
IOTDB_DATA_HOME="${NODE_DATA}"
IOTDB_CONF="${NODE_CONF}"
IOTDB_LOG_DIR="${NODE_LOG}"
iotdb_parms="-Dlogback.configurationFile=${IOTDB_LOG_CONFIG}"
iotdb_parms="$iotdb_parms -DIOTDB_HOME=${IOTDB_HOME}"
iotdb_parms="$iotdb_parms -DIOTDB_DATA_HOME=${IOTDB_DATA_HOME}"
iotdb_parms="$iotdb_parms -DTSFILE_HOME=${IOTDB_HOME}"
iotdb_parms="$iotdb_parms -DIOTDB_CONF=${IOTDB_CONF}"
iotdb_parms="$iotdb_parms -DTSFILE_CONF=${IOTDB_CONF}"
iotdb_parms="$iotdb_parms -Dname=iotdb\.IoTDB"
iotdb_parms="$iotdb_parms -DIOTDB_LOG_DIR=${IOTDB_LOG_DIR}"
# iotdb_parms="$iotdb_parms -DOFF_HEAP_MEMORY=${OFF_HEAP_MEMORY}"

# DataNode Parameters: -s for starting a node joining cluster
PARAMS="-s"

# make the command with agent flags and iotDB flags
CMD="$JAVA_CMD $JVM_OPTS $illegal_access_params $iotdb_parms $CONFIGNODE_JMX_OPTS -cp \"$CLASSPATH\" $IOTDB_JVM_OPTS $MAIN $PARAMS"
echo "[$0] Executing [$CMD]"
# start the system
$JAVA_CMD $SL_AGENT_FLAGS $JVM_OPTS $illegal_access_params $iotdb_parms $CONFIGNODE_JMX_OPTS -cp \"$CLASSPATH\" $IOTDB_JVM_OPTS $MAIN $PARAMS 2>&1 >$NODE_LOG/system.log &

echo $! > $NODE_CONF/pid
exit 0