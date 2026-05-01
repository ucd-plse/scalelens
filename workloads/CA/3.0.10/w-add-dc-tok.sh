##########################################################################
#!/bin/bash
##########################################################################
## Always source first...
source environment.sh

NUM_NODES="$1"
SLEEP_BETWEEN_STEPS="$2"
CLUSTER_BASE="$3"
SLEEP_N=60

if ! isInteger "$NUM_NODES" || ! isInteger "$SLEEP_BETWEEN_STEPS" ||  ! isDirectory "$CLUSTER_BASE" || [ "$#" -ne 3 ]; then
  echo "[$0] USAGE: $0 <num_nodes> <sleep_bet_steps> <cluster_base>"
  echo "[$0] <num_nodes>: Number of nodes in the workload."
  echo "[$0] <sleep_bet_steps>: How many seconds to wait between steps."
  echo "[$0] <cluster_base>: Where to create the cluster."
  exit 1
fi

## lets kill everyone before we start...
killAllCassandraProcesses

## first, lets clean the directory
rm -rf $CLUSTER_BASE/*
## lets make the dc file
createTopologyFile $DC_CONF $NUM_NODES
## now, lets make nodes
BASE_TOKENS=8
echo "[$0] Setting up [$NUM_NODES] at [$CLUSTER_BASE] with snitch [$DC_SNITCH]"
for (( i = 1; i <= $NUM_NODES; i++ )); do
   T=$(($BASE_TOKENS * $i))
  ./setup-node.sh node-$i $CLUSTER_BASE $T 127.0.0.$i $DC_SNITCH
  cp $DC_CONF $CLUSTER_BASE/node-$i/conf/$DC_CONF
done

rm $DC_CONF

## also, record in the trace directory the mapping
MAPPING_FILE=add-dc-tok-$NUM_NODES.nm
TOKEN_COUNT=0
PEER_COUNT=0
ITERATION=0
rm $MAPPING_FILE
## then, we can start them pne by one and issue measure commands...
echo "[$0] Executing workload...."
for (( i = 1; i <= $NUM_NODES; i++ )); do
  echo "[$0] Adding node [$i] of [$NUM_NODES]"
  ## start the next node...
  PORT=$(($JMX_BASE + $i))
  if [ "$i" -eq 1 ]; then
    ./start-node.sh $CLUSTER_BASE/node-$i $PORT true true
    echo "[$0] Sleeping [$SLEEP_N] seconds before proceeding (wait for node start)"
    sleep $SLEEP_N
  else
    ./start-node.sh $CLUSTER_BASE/node-$i $PORT false
    echo "[$0] Sleeping [$SLEEP_N] seconds before proceeding (wait for node start)"
    sleep $SLEEP_N
  fi
  ## now, we enable some entry events in all nodes
  for (( j = 1; j <= $i; j++ )); do
    printf "cmd=heapMeasure\nrunId=$i\ngcBefore=false\nenableLoopProfiling=true" >  $CLUSTER_BASE/node-$j/$CMD_FILE
  done
  ## and record the current numbers
  PEER_COUNT=$i
  ITERATION=$(($i - 1))
  TOKEN_COUNT=$(($TOKEN_COUNT + $BASE_TOKENS * $PEER_COUNT))
  echo "$ITERATION,$PEER_COUNT,$(($TOKEN_COUNT / $PEER_COUNT))" >> $MAPPING_FILE
  ## now, we wait...
  echo "[$0] Sleeping [$SLEEP_BETWEEN_STEPS] seconds before proceeding (wait for measurements to finish)..."
  sleep $SLEEP_BETWEEN_STEPS
done
## shutdown the nodes too...
echo "[$0] Shutting down [$NUM_NODES] nodes..."
for (( i = 1; i <= $NUM_NODES; i++ )); do
  printf "cmd=shutdown" >  $CLUSTER_BASE/node-$i/$CMD_FILE
done
echo "[$0] Killing all processes and waiting..."
killAllCassandraProcesses
sleep 20
## and collect results...
RESULTS=add-dc-tok-$RANDOM
echo "[$0] Done, collecting measures and traces into $RESULTS"
mkdir $RESULTS
for (( i = 1; i <= $NUM_NODES; i++ )); do
    mkdir $RESULTS/node-$i
    cp -r  $CLUSTER_BASE/node-$i/$MS_DIR $RESULTS/node-$i/$MS_DIR
    cp -r  $CLUSTER_BASE/node-$i/$TRACES_DIR $RESULTS/node-$i/$TRACES_DIR
    cp $CLUSTER_BASE/node-$i/logs/system.log $RESULTS/node-$i/system.log
    cp $MAPPING_FILE $RESULTS/$MAPPING_FILE
done
## compress it
TAR_NAME="$RESULTS.tar.gz"
echo "[$0] Compressing $RESULTS into $TAR_NAME"
tar -czvf $TAR_NAME $RESULTS
## and delete the rest...
rm -rf $RESULTS
echo "[$0] Mapping file is: "
cat $MAPPING_FILE
rm $MAPPING_FILE
echo "[$0] Done..."
exit 0
