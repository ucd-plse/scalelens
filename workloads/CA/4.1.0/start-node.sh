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
NODE_PID="$NODE_BASE/pid"
JMX_PORT="$2"
USE_AG="$3"
USE_SMALL_HEAP="$4"
HEAP_SIZE=""

## check some stuff firts
if ! isDirectory "$NODE_BASE" || ! isDirectory "$NODE_CONF" || ! isInteger "$JMX_PORT" || ! isBoolean "$USE_AG" || [ ! "$#" -le 4 ]; then
  echo "[$0] USAGE: $0 <directory_path> <jmx-port> <use-agent>"
  echo "[$0] <directory_path>: The content directory of an existing node (see setup-node.sh). Use absolute path."
  echo "[$0] <jmx-port>: The jmx port for this node."
  echo "[$0] <use-agent>: $B_TRUE if this node should start with agent, $B_FALSE otherwise."
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
MAIN="org.apache.cassandra.service.CassandraDaemon"
## confs and log files. Both the logging conf and the yaml file need to 
## be located in this folder.
RESOURCES="$NODE_CONF"
PROPERTIES="-Dlogback.configurationFile=$NODE_CONF/logback.xml"
PROPERTIES="-Dcassandra.logdir=$NODE_LOG $PROPERTIES"
PROPERTIES="-Dcassandra.config=file://$NODE_CONF/cassandra.yaml $PROPERTIES"
PROPERTIES="-Dcassandra-pidfile=$NODE_PID $PROPERTIES"
PROPERTIES="-Dcassandra.consistent.rangemovement=false $PROPERTIES"
PROPERTIES="-Dcassandra.skip_schema_check=true $PROPERTIES"
PROPERTIES="-Dcassandra.ring_delay_ms=30000 $PROPERTIES"
## classpath
CLASSPATH="$SYS/*:$RESOURCES"

## heap size, per node
if [ "$USE_AG" = "$B_TRUE" ]; then
  CA_HEAP="$HEAP_SIZE -XX:+UseG1GC -Xss256k -XX:ParallelGCThreads=8 -XX:CICompilerCount=2 -XX:ActiveProcessorCount=8  -XX:ConcGCThreads=8"
  PROPERTIES="-Dcassandra.available_processors=4 $PROPERTIES"
  PROPERTIES="-Dcassandra.jmx.local.port=$JMX_PORT $PROPERTIES"
else
  CA_HEAP="$NORMAL_HEAP -XX:+UseParallelGC -Xss256k -XX:ParallelGCThreads=2 -XX:CICompilerCount=2 -XX:ActiveProcessorCount=1  -XX:ConcGCThreads=2"
  PROPERTIES="-Dcassandra.available_processors=1 $PROPERTIES"
  PROPERTIES="-Dcassandra.jmx.local.port=$JMX_PORT $PROPERTIES"
fi

if [ "$USE_AG" = "$B_TRUE" ]; then
  ## Profiling agent, base part
  SL_AGENT_FLAGS="-Xbootclasspath/p:$AGENT_JAR:$AGENT_ASSIST"
  SL_AGENT_FLAGS="$SL_AGENT_FLAGS -Djdk.attach.allowAttachSelf -javaagent:$AGENT_JAR"
  ## method entry profiler configuration
  SL_AGENT_FLAGS="$SL_AGENT_FLAGS=profiler:name=methods,coreThreads=16,maxThreads=16,outputDirectory=$NODE_BASE/$TRACES_DIR,waitWhenShutDown=true,flushThreshold=100,inclusions=$INCLUSIONS"
  ## command thread configuration
  SL_AGENT_FLAGS="$SL_AGENT_FLAGS;commandThread:commandDirectory=$NODE_BASE/$CMD_DIR,sleepTime=500"
  ## and finally logging
  SL_AGENT_FLAGS="$SL_AGENT_FLAGS -Dscaleview.logFile=$PWD/$AGENT_LOG"
else
  SL_AGENT_FLAGS=" "
fi
## and start the stuff
CMD="$JAVA_CMD $SL_AGENT_FLAGS -cp \"$CLASSPATH\" $PROPERTIES $CA_HEAP $MAIN"
echo "[$0] Executing [$CMD]"

## start node with at most 10 attempts
for attempt in {1..10}; do
    $JAVA_CMD $SL_AGENT_FLAGS -cp "$CLASSPATH" $PROPERTIES $CA_HEAP $MAIN &
    echo $! > "$NODE_CONF/pid"
    echo "[$0] Node started with pid: $(cat $NODE_CONF/pid)"

    # case 1: not starting the first node in the workload
    # there is already Cassandra node running on the port
    # then skip the sleeps
    if lsof -n -i :$CASSANDRA_PORT | grep -q "LISTEN"; then
       break
    fi
    
    # case 2: starting the first node in the workload
    # so we should be careful: using sleeps to make sure
    # this node is started successfully and using the port

    # Sleep time for each attempt increases by 10 seconds
    sleep_time=$((110 + attempt * 10))
    echo "[$0] Sleeping for $sleep_time seconds to wait for Cassandra to start..."
    sleep $sleep_time

    if lsof -n -i :$CASSANDRA_PORT | grep -q "LISTEN"; then
        echo "[$0] Cassandra is now using port $CASSANDRA_PORT"
        break
    else
        echo "[$0] Cassandra is not using port $CASSANDRA_PORT, retrying... (Attempt $attempt)"
        killAllCassandraProcesses
        sleep 30
        if [ $attempt -eq 10 ]; then
            echo "[$0] Failed to start Cassandra after $attempt attempts."
            exit 1 
        fi
    fi
done

exit 0
