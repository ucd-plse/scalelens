##########################################################################
#!/bin/bash
##########################################################################
## Always source first...
source environment.sh

NUM_NODES="$1"
SLEEP_BETWEEN_STEPS="$2"
CLUSTER_BASE="$3"
SLEEP_N=30

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
## now, lets make nodes
echo "[$0] Setting up [$NUM_NODES] at [$CLUSTER_BASE]"
for (( i = 1; i <= $NUM_NODES; i++ )); do
  ./setup-node.sh node-$i $CLUSTER_BASE $DEFAULT_TOKENS 127.0.0.$i
done

## the mapping file
MAPPING_FILE=add-node-$NUM_NODES.nm
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
  else
    ./start-node.sh $CLUSTER_BASE/node-$i $PORT false
  fi
  ##  wait a little to let it start correctly, this is usually not more than 20 sec,
  ## but since there is not too much rush (ha!) lets make it 30
  echo "[$0] Sleeping [$SLEEP_N] seconds before proceeding (wait for node start)"
  sleep $SLEEP_N
  ## now, we enable some entry events in all nodes
  for (( j = 1; j <= $i; j++ )); do
    printf "cmd=heapMeasure\nrunId=$i\ngcBefore=false\nenableLoopProfiling=true" >  $CLUSTER_BASE/node-$j/$CMD_FILE
  done
  PEER_COUNT=$i
  ITERATION=$(($i - 1))
  echo "$ITERATION,$PEER_COUNT" >> $MAPPING_FILE
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
RAND=$RANDOM
RESULTS=add-node-$RAND
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
