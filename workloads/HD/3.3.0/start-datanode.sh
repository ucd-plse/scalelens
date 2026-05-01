##########################################################################
#!/bin/bash
##########################################################################
## This scripts starts a new node using the jar files found in 'system'
## folder and some known configuration properties.
## Source environment files
source environment.sh
## the configuration and folders are already created for this step
NODE_BASE="$1"
NODE_CONF="$1/conf"
NODE_LOG="$1/logs"
USE_AG="$2"
USE_SMALL_HEAP="$3"
HEAP_SIZE=""

## check some stuff firts
if ! isDirectory "$NODE_BASE" || [ "$#" -ne 3 ]; then
  echo "[$0] USAGE: $0 <directory_path> <jmx-port> <use-agent>"
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

## main class
MAIN="org.apache.hadoop.hdfs.server.datanode.DataNode"
## confs and log files. Both the logging conf and the yaml file need to 
## be located in this folder.
RESOURCES="."
PROPERTIES="-Dlog4j.configuration=file://$NODE_CONF/log4j.properties"
## classpath
CLASSPATH="$RESOURCES:$SYS/*"

## heap size, per node
if [ "$USE_AG" = "$B_TRUE" ]; then
  NN_HEAP="$HEAP_SIZE -XX:+UseG1GC -Xss256k -XX:ParallelGCThreads=8 -XX:CICompilerCount=2 -XX:ActiveProcessorCount=8  -XX:ConcGCThreads=8"
else
  NN_HEAP="$NORMAL_HEAP -XX:+UseParallelGC -Xss256k -XX:ParallelGCThreads=2 -XX:CICompilerCount=2 -XX:ActiveProcessorCount=1  -XX:ConcGCThreads=2"
fi

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


## and start it
CMD="$JAVA_CMD $SL_AGENT_FLAGS -cp \"$CLASSPATH\" $PROPERTIES $NN_HEAP $MAIN -conf $NODE_CONF/core-site.xml -conf $NODE_CONF/hdfs-site.xml"
echo "[$0] Executing [$CMD]"
$JAVA_CMD $SL_AGENT_FLAGS $PROPERTIES -cp "$CLASSPATH" $PROPERTIES $NN_HEAP $MAIN -conf $NODE_CONF/core-site.xml -conf $NODE_CONF/hdfs-site.xml &> $NODE_BASE/logs/system.log &
## exit correctly
echo $! > $NODE_CONF/pid
exit 0
