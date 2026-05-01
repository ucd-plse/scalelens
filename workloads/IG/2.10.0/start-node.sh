##########################################################################
#!/bin/bash
##########################################################################
## This scripts starts a new Ignite node with proper JVM arguments
## Source environment files
source environment.sh

## Configuration and directories
NODE_BASE="$1"
JMX_PORT="$2"
USE_AG="$3"
NODE_CONF="$NODE_BASE/conf"
NODE_LOG="$NODE_BASE/logs"
NODE_PID="$NODE_BASE/pid"
SUCCESS_FILE="$NODE_BASE/work/ignite_success_$(uuidgen)"

## Validation checks
if ! isDirectory "$NODE_BASE" || ! isDirectory "$NODE_CONF" || ! isInteger "$JMX_PORT" || ! isBoolean "$USE_AG" || [ "$#" -ne 3 ]; then
  echo "[$0] USAGE: $0 <directory_path> <jmx-port> <use-agent>"
  echo "[$0] <directory_path>: Node directory created by setup-node.sh (absolute path)"
  echo "[$0] <jmx-port>: JMX port for monitoring"
  echo "[$0] <use-agent>: $B_TRUE to enable profiling agent, $B_FALSE otherwise"
  exit 1
fi

## Validate agent directory if needed
if [ "$USE_AG" = "$B_TRUE" ] && ! checkAgentDir; then
  echo "[$0] exiting..."
  exit 1
fi

## JVM Configuration (Aligned with observed production settings)
JVM_OPTS="-XX:+AggressiveOpts -server $SMALL_HEAP"
JVM_OPTS+=" -XX:MaxMetaspaceSize=256m -ea"
PROPERTIES="-DIGNITE_QUIET=true"
PROPERTIES+=" -DIGNITE_SUCCESS_FILE=$SUCCESS_FILE"
PROPERTIES+=" -Dcom.sun.management.jmxremote"
PROPERTIES+=" -Dcom.sun.management.jmxremote.port=$JMX_PORT"
PROPERTIES+=" -Dcom.sun.management.jmxremote.authenticate=false"
PROPERTIES+=" -Dcom.sun.management.jmxremote.ssl=false"
PROPERTIES+=" -DIGNITE_UPDATE_NOTIFIER=false"
PROPERTIES+=" -DIGNITE_HOME=$(realpath $NODE_BASE)"
PROPERTIES+=" -DIGNITE_PROG_NAME=$0"
PROPERTIES+=" -DIGNITE_LOG_DIR=$NODE_LOG"
PROPERTIES+=" -Dlog4j.configurationFile=file://$NODE_CONF/ignite-log4j.xml"

## Classpath and main class
buildClassPath
MAIN_CLASS="org.apache.ignite.startup.cmdline.CommandLineStartup"
CONFIG_FILE="$NODE_CONF/ignite-config.xml"

## Agent instrumentation
if [ "$USE_AG" = "$B_TRUE" ]; then
  AGENT_FLAGS="-Xbootclasspath/p:$AGENT_JAR:$AGENT_ASSIST"
  AGENT_FLAGS+=" -javaagent:$AGENT_JAR=profiler:name=methods,coreThreads=16,maxThreads=16,outputDirectory=$NODE_BASE/$TRACES_DIR,inclusions=$INCLUSIONS,exclusions=$EXCLUSIONS"
  AGENT_FLAGS+=";commandThread:commandDirectory=$NODE_BASE/$CMD_DIR,sleepTime=500"
  AGENT_FLAGS+=" -Dscaleview.logFile=$PWD/$AGENT_LOG"


  # SL_AGENT_FLAGS="-Xbootclasspath/p:$AGENT_JAR:$AGENT_ASSIST"
  # SL_AGENT_FLAGS="$SL_AGENT_FLAGS -Djdk.attach.allowAttachSelf -javaagent:$AGENT_JAR"
  # ## method entry profiler configuration
  # SL_AGENT_FLAGS="$SL_AGENT_FLAGS=profiler:name=methods,coreThreads=16,maxThreads=16,outputDirectory=$NODE_BASE/$TRACES_DIR,waitWhenShutDown=true,flushThreshold=100,inclusions=$INCLUSIONS"
  # ## command thread configuration
  # SL_AGENT_FLAGS="$SL_AGENT_FLAGS;commandThread:commandDirectory=$NODE_BASE/$CMD_DIR,sleepTime=500"
  # ## and finally logging
  # SL_AGENT_FLAGS="$SL_AGENT_FLAGS -Dscaleview.logFile=$PWD/$AGENT_LOG"
  # AGENT_FLAGS="$SL_AGENT_FLAGS"
else
  AGENT_FLAGS=""
fi

## Build full command
CMD="$JAVA_CMD $JVM_OPTS $PROPERTIES $AGENT_FLAGS -cp \"$CLASSPATH\" $MAIN_CLASS $CONFIG_FILE"
echo "[$0] Starting Ignite node with command:"
echo "$CMD"

## Create required directories
mkdir -p "$NODE_BASE/work"
$JAVA_CMD $JVM_OPTS $PROPERTIES $AGENT_FLAGS -cp "$CLASSPATH" $MAIN_CLASS $CONFIG_FILE &
echo $! > "$NODE_PID"
echo "[$0] Node started with PID: $(cat $NODE_PID)"


exit 0