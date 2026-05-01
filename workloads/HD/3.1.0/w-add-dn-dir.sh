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
  echo "[$0] USAGE: $0 <num_datanodes> <sleep_bet_steps> <cluster_base>" 
  echo "[$0] <num_datanodes>: Number of datanodes in the workload."
  echo "[$0] <sleep_bet_steps>: How many seconds to wait between steps."
  echo "[$0] <cluster_base>: Where to create the cluster."
  exit 1
fi

## lets kill everyone before we start...
killAllDataNodesProcesses
killAllNameNodesProcesses

## first, lets clean the directory
rm -rf $CLUSTER_BASE/*

## lets setup the NN
NN_IP=127.0.0.1
NN_SERVICE=$NAMENODE_SERVICE
REPLICATION=$NUM_NODES
./setup-namenode.sh NN-1 $CLUSTER_BASE $NN_IP $NAMENODE_IPC $NAMENODE_RPC $NN_SERVICE $NAMENODE_HTTP 1 $REPLICATION
## now, lets make datanodes
IPC_BASE=$DNNODE_IPC
SERVICE_BASE=$DNNODE_SERVICE
HTTP_BASE=$DNNODE_HTTP
echo "[$0] Setting up [$NUM_NODES] at [$CLUSTER_BASE]"
for (( i = 1; i <= $NUM_NODES; i++ )); do
  ./setup-datanode.sh datanode-$i $CLUSTER_BASE 0.0.0.0 $IPC_BASE $SERVICE_BASE $HTTP_BASE $NN_IP $NN_SERVICE
  IPC_BASE=$(($IPC_BASE + 1))
  SERVICE_BASE=$(($SERVICE_BASE + 1))
  HTTP_BASE=$(($HTTP_BASE + 1))
done

## now we can start the namenode, which will also fort it
echo "[$0] Starting and formating namenode..."
./start-namenode.sh $CLUSTER_BASE/NN-1 true true
sleep $SLEEP_N

## also, record in the trace directory the mapping
MAPPING_FILE=add-dn-dir-$NUM_NODES.nm
TOKEN_COUNT=0
PEER_COUNT=0
ITERATION=0
rm $MAPPING_FILE

BASE_BLOCKS=10
BLOCK_FILE=files/blk-128-10.txt

## then, we can start them one by one and issue measure commands...
echo "[$0] Executing workload...."
for (( i = 1; i <= $NUM_NODES; i++ )); do
  echo "[$0] Adding node [$i] of [$NUM_NODES]"
  ## start the next node...
  printf "cmd=heapMeasure\nrunId=$i\ngcBefore=false\nenableLoopProfiling=true" >  $CLUSTER_BASE/NN-1/$CMD_FILE
  ## one instrumented, the rest are not
  if [ $i -eq 1 ]; then
    ./start-datanode.sh $CLUSTER_BASE/datanode-$i true true
  else
   ./start-datanode.sh $CLUSTER_BASE/datanode-$i false false
  fi
  ##  wait a little to let it start correctly, this is usually not more than 20 sec,
  ## but since there is not too much rush (ha!) lets make it 30
  echo "[$0] Sleeping [$SLEEP_N] seconds before proceeding (wait for node start)"
  sleep $SLEEP_N
  ## each time we add more and more data
  echo -n "" > blk.txt
  for (( k = 0; k < $i; k++ )); do
    DDIR=/somedata-$i-$k
    ./nn-command.sh $PWD/test/NN-1 -mkdir $DDIR
  done
  ## and then here add data directory
  DDIR=/somedata-$i
  ./nn-command.sh $PWD/test/NN-1 -mkdir $DDIR
  ## and record the current numbers
  PEER_COUNT=$i
  DIR_COUNT=$i
  ITERATION=$(($i - 1))
  # TOKEN_COUNT=$(($TOKEN_COUNT + $BASE_BLOCKS * $PEER_COUNT))
  # echo "$ITERATION,$PEER_COUNT,$(($TOKEN_COUNT / $PEER_COUNT))" >> $MAPPING_FILE
  echo "$ITERATION,$PEER_COUNT,$DIR_COUNT" >> $MAPPING_FILE
  ## now, we wait...
  echo "[$0] Sleeping [$SLEEP_BETWEEN_STEPS] seconds before proceeding (wait for measurements to finish)..."
  sleep $SLEEP_BETWEEN_STEPS
  BASE_BLOCKS=$(($BASE_BLOCKS + 10))
done
## shutdown the nodes too...
echo "[$0] Shutting down [$NUM_NODES] nodes..."
printf "cmd=shutdown" >  $CLUSTER_BASE/NN-1/$CMD_FILE
echo "[$0] Killing all processes and waiting..."
killAllDataNodesProcesses
killAllNameNodesProcesses
sleep 20
## and collect results...
RESULTS="add-dn-dir-$RANDOM"
echo "[$0] Done, collecting measures and traces into $RESULTS"
mkdir $RESULTS
mkdir $RESULTS/NN-1
mkdir $RESULTS/NN-1/logs
mkdir $RESULTS/DN-1
mkdir $RESULTS/DN-1/logs
cp -r  $CLUSTER_BASE/NN-1/$TRACES_DIR $RESULTS/NN-1/$TRACES_DIR
cp -r $CLUSTER_BASE/NN-1/logs $RESULTS/NN-1/logs
cp -r  $CLUSTER_BASE/datanode-1/$TRACES_DIR $RESULTS/DN-1/$TRACES_DIR
cp -r $CLUSTER_BASE/datanode-1/logs $RESULTS/DN-1/logs
cp $MAPPING_FILE $RESULTS/$MAPPING_FILE
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
