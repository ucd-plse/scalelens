##########################################################################
#!/bin/bash
##########################################################################
## Always source first...
source environment.sh

NUM_DATABATCH="$1"
SLEEP_BETWEEN_STEPS="$2"
CLUSTER_BASE="$3"
SLEEP_N=30

if ! isInteger "$NUM_DATABATCH" || ! isInteger "$SLEEP_BETWEEN_STEPS" ||  ! isDirectory "$CLUSTER_BASE" || [ "$#" -ne 3 ]; then
  echo "[$0] USAGE: $0 <num_databatch> <sleep_bet_steps> <cluster_base>" 
  echo "[$0] <num_databatch>: Number of datanodes in the workload."
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
REPLICATION=1
./setup-namenode.sh NN-1 $CLUSTER_BASE $NN_IP $NAMENODE_IPC $NAMENODE_RPC $NN_SERVICE $NAMENODE_HTTP 1 $REPLICATION
## now, lets make datanodes
IPC_BASE=$DNNODE_IPC
SERVICE_BASE=$DNNODE_SERVICE
HTTP_BASE=$DNNODE_HTTP
./setup-datanode.sh datanode-1 $CLUSTER_BASE 0.0.0.0 $IPC_BASE $SERVICE_BASE $HTTP_BASE $NN_IP $NN_SERVICE
## now we can start the namenode, which will also fort it
echo "[$0] Starting and formating namenode..."
./start-namenode.sh $CLUSTER_BASE/NN-1 true true
sleep $SLEEP_N
./start-datanode.sh $CLUSTER_BASE/datanode-1 true true
sleep $SLEEP_N

## also, record in the trace directory the mapping
MAPPING_FILE=add-data-snap-$NUM_DATABATCH.nm
rm $MAPPING_FILE

BASE_BLOCKS=10
BLOCK_FILE=files/blk-128-10.txt

SDIR=/snaps
DDIR=/somedata

## create a directory for snaps...
./nn-command.sh $PWD/test/NN-1 -mkdir $DDIR
./admin-command.sh $PWD/test/NN-1 -allowSnapshot $DDIR



## then, we can start them one by one and issue measure commands...
echo "[$0] Executing workload...."
for (( i = 1; i <= $NUM_DATABATCH; i++ )); do
  echo "[$0] Adding batch [$i] of [$NUM_DATABATCH]"
  printf "cmd=heapMeasure\nrunId=$i\ngcBefore=false\nenableLoopProfiling=true" >  $CLUSTER_BASE/NN-1/$CMD_FILE
  printf "cmd=heapMeasure\nrunId=$i\ngcBefore=false\nenableLoopProfiling=true" >  $CLUSTER_BASE/datanode-1/$CMD_FILE
  ## each time we add more and more data
  TMP_BLOCK_FILE=blk.txt
  echo -n "" > blk.txt
  for (( k = 0; k < $i; k++ )); do
    cat $BLOCK_FILE >> $TMP_BLOCK_FILE
  done
  ## and then here add some blocks
  ./nn-command.sh $PWD/test/NN-1 -put $TMP_BLOCK_FILE $DDIR/COPY_$i
  rm $TMP_BLOCK_FILE
  ## and do some snaps...
  ./nn-command.sh $PWD/test/NN-1 -createSnapshot $DDIR snap-$i
  ## and record the current numbers
  BLOCK_COUNT=$BASE_BLOCKS
  ITERATION=$(($i - 1))
  echo "$ITERATION,$BLOCK_COUNT" >> $MAPPING_FILE
  ## now, we wait...
  echo "[$0] Sleeping [$SLEEP_BETWEEN_STEPS] seconds before proceeding (wait for measurements to finish)..."
  sleep $SLEEP_BETWEEN_STEPS
  BASE_BLOCKS=$(($BASE_BLOCKS + 10))
done
## shutdown the nodes too...
echo "[$0] Shutting down nodes..."
printf "cmd=shutdown" >  $CLUSTER_BASE/NN-1/$CMD_FILE
echo "[$0] Killing all processes and waiting..."
killAllDataNodesProcesses
killAllNameNodesProcesses
sleep 20
## and collect results...
RESULTS="add-data-snap-$RANDOM"
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
TAR_NAME="$RESULTS.tar.gz "
echo "[$0] Compressing $RESULTS into $TAR_NAME"
tar -czvf $TAR_NAME $RESULTS
## and delete the rest...
rm -rf $RESULTS
echo "[$0] Mapping file is: "
cat $MAPPING_FILE
rm $MAPPING_FILE
echo "[$0] Done..."
exit 0
