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
killALlConfigNodesProcesses

## first, lets clean the directory
rm -rf $CLUSTER_BASE/*

## setup config node
CLUSTER_IP=127.0.0.1
echo "[$0] Setting up config node at [$CLUSTER_BASE]"
bash setup-confignode.sh 1 $CLUSTER_BASE $CNODE_INTERNAL_PORT_BASE $CNODE_CONSENSUS_PORT_BASE $CLUSTER_IP

## setup data nodes, one by one
echo "[$0] Setting up [$NUM_NODES] datanodes at [$CLUSTER_BASE]"
for (( i = 1; i <= $NUM_NODES; i++ )); do
  bash setup-datanode.sh $i $CLUSTER_BASE $(($DNODE_RPC_PORT_BASE + $i)) $(($DNODE_INTERNAL_PORT_BASE + $i)) $(($DNODE_MPP_DATA_EXCHANGE_PORT_BASE + $i)) $(($DNODE_SCHEMA_REGION_CONSENSUS_PORT_BASE + $i)) $(($DNODE_DATA_REGION_CONSENSUS_PORT_BASE + $i)) $CLUSTER_IP
done

## start config node, with instrumentation agent on
echo "[$0] Starting config node..."
bash start-confignode.sh $CLUSTER_BASE/confignode-1 $B_TRUE

## make the mapping file for the workload
MAPPING_FILE=add-datanode-$NUM_NODES.nm
DATANODE_COUNT=0
ITERATION=0
rm $MAPPING_FILE

## start the datanodes one by one
echo "[$0] Executing workload...."
for (( i = 1; i <= $NUM_NODES; i++ )); do
    echo "[$0] Adding datanode [$i / $NUM_NODES]"
    printf "cmd=heapMeasure\nrunId=$i\ngcBefore=false\nenableLoopProfiling=true" > $CLUSTER_BASE/datanode-1/$CMD_FILE
    ## instrument the No.1 datanode
    if [ $i -eq 1 ]; then
        bash start-datanode.sh $CLUSTER_BASE/datanode-1 true
    else
        bash start-datanode.sh $CLUSTER_BASE/datanode-$i false
    fi 

    ## sleep for a while waiting for the datanode to start
    sleep $SLEEP_N

    # record the mapping
    DATANODE_COUNT=$(($DATANODE_COUNT + 1))
    echo "$ITERATION,$DATANODE_COUNT" >> $MAPPING_FILE
    ITERATION=$(($ITERATION + 1))
    ## sleep between steps
    sleep $SLEEP_BETWEEN_STEPS
done

# shutdown the nodes after workload
echo "[$0] Workload done, shutting down nodes..."
printf "cmd=shutdown" > $CLUSTER_BASE/datanode-1/$CMD_FILE

echo "[$0] Killing all processes..."
killAllDataNodesProcesses
killAllConfigNodesProcesses
sleep $SLEEP_N

## collect results
RESULTS="add-datanode-$RANDOM"
echo "[$0] Done, collecting measures and traces into $RESULTS"
mkdir $RESULTS
mkdir $RESULTS/confignode-1
mkdir $RESULTS/datanode-1
mkdir $RESULTS/confignode-1/logs
mkdir $RESULTS/datanode-1/logs
cp -r $CLUSTER_BASE/confignode-1/$TRACES_DIR $RESULTS/confignode-1/$TRACES_DIR
cp -r $CLUSTER_BASE/datanode-1/$TRACES_DIR $RESULTS/datanode-1/$TRACES_DIR
cp -r $CLUSTER_BASE/confignode-1/logs $RESULTS/confignode-1/logs
cp -r $CLUSTER_BASE/datanode-1/logs $RESULTS/datanode-1/logs
cp $MAPPING_FILE $RESULTS/$MAPPING_FILE

## compress results
TAR_NAME="$RESULTS.tar.gz"
echo "[$0] Compressing $RESULTS into $TAR_NAME"
tar -czvf $TAR_NAME $RESULTS
## delete the residue
rm -rf $RESULTS
echo "[$0] Mapping file is:"
cat $MAPPING_FILE
rm $MAPPING_FILE
echo "[$0] Done."
exit 0

