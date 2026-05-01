##########################################################################
#!/bin/bash
##########################################################################
## Ignite cluster scaling workload script
## Scale up the number of tables in the cluster
source environment.sh

NUM_DATA_BATCH="$1"
SLEEP_BETWEEN_STEPS="$2"
CLUSTER_BASE="$3"

## Input validation
if ! isInteger "$NUM_DATA_BATCH" || ! isInteger "$SLEEP_BETWEEN_STEPS" || ! isDirectory "$CLUSTER_BASE" || [ "$#" -ne 3 ]; then
  echo "[$0] USAGE: $0 <NUM_DATA_BATCH> <sleep_between_steps> <cluster_base>"
  echo "[$0] <NUM_DATA_BATCH>: Total number of data batches to add in cluster, each batch is 256 records"
  echo "[$0] <sleep_between_steps>: Seconds between node additions"
  echo "[$0] <cluster_base>: Base directory for node data"
  exit 1
fi

## set up 3 nodes in the cluster
NUM_NODES=3

## Clean previous state
echo "[$0] Killing existing Ignite processes"
killAllIgniteProcesses
rm -rf "$CLUSTER_BASE"/*

## Create folder for instrumented class
if [ ! -d "instrumented" ]; then
  mkdir "instrumented"
fi

## Create seed nodes file (for discovery)
SEEDS_FILE="$CLUSTER_BASE/seeds.txt"
echo "127.0.0.1:$PORT_BASE" > $SEEDS_FILE  # First node as seed

## Setup nodes
echo "[$0] Creating $NUM_NODES nodes in $CLUSTER_BASE"
for ((i=1; i<=$NUM_NODES; i++)); do
  IP="127.0.0.1"
  bash setup-node.sh "$i" "$CLUSTER_BASE" "$IP"
  
  ## Update seeds file after first node
  [ $i -eq 1 ] || echo "127.0.0.1:$(($PORT_BASE + $i))" >> $SEEDS_FILE
done

for ((i=1; i<=$NUM_NODES; i++)); do
  NODE_DIR="$CLUSTER_BASE/node-$i"
  JMX_PORT=$(($JMX_BASE + $i))
  echo "[$0] Starting node $i/$NUM_NODES"

  ## First node starts with agent
  if [ $i -eq 1 ]; then
    bash start-node.sh "$NODE_DIR" "$JMX_PORT" "$B_TRUE"
  else
    bash start-node.sh "$NODE_DIR" "$JMX_PORT" "$B_FALSE"
  fi

  ## Wait for node initialization
  # sleep 30  # Reduced from 30s since we check success file
  ## Wait between scale steps
  echo "[$0] Sleeping ${SLEEP_BETWEEN_STEPS}s between steps..."
  sleep $SLEEP_BETWEEN_STEPS
done

## Workload execution
MAPPING_FILE="add-data-$NUM_DATA_BATCH.nm"
DATA_BATCH_COUNT=0
ITERATION=0
echo "[$0] Starting cluster scaling workload..."

echo "[$0] creating tables"

echo "CREATE TABLE TestTable (
    id INT PRIMARY KEY,
    col0 VARCHAR,
    col1 INT,
    col2 VARCHAR,
    col3 VARCHAR
    );" | bash sqlline.sh -u jdbc:ignite:thin://127.0.0.1/

for ((i=1; i<=$NUM_DATA_BATCH; i++)); do
  echo "[$0] adding data $i/$NUM_DATA_BATCH"

  printf "cmd=heapMeasure\nrunId=$i\ngcBefore=false\nenableLoopProfiling=true" > "$CLUSTER_BASE/node-1/$CMD_FILE"

  # insert 256 records for each batch 
  for ((j=1; j<=256; j++)); do
    IDX=$(($i * 300 + $j))
    echo "INSERT INTO TestTable (id, col0, col1, col2, col3) VALUES
    ($IDX, 'col0-$j', $j, 'col2-$j', 'col3-$j');" | bash sqlline.sh -u jdbc:ignite:thin://127.0.0.1/
  done

  ## Record cluster state
  DATA_BATCH_COUNT=$i
  ITERATION=$(($i - 1))
  echo "$ITERATION,$DATA_BATCH_COUNT" >> $MAPPING_FILE
  
  ## Wait between scale steps
  echo "[$0] Sleeping ${SLEEP_BETWEEN_STEPS}s between steps..."
  sleep $SLEEP_BETWEEN_STEPS
done
## Shutdown and collect results
echo "[$0] Stopping cluster..."
sleep 150
for (( i = 1; i <= $NUM_NODES; i++ )); do
  printf "cmd=shutdown" >  $CLUSTER_BASE/node-$i/$CMD_FILE
done
killAllIgniteProcesses
sleep 20  # Allow graceful shutdown

RESULTS="add-data-$(date +%Y%m%d%H%M%S)"
mkdir -p "$RESULTS"

## Copy node data
for ((i=1; i<=$NUM_NODES; i++)); do
  NODE_DATA="$RESULTS/node-$i"
  mkdir -p "$NODE_DATA"
  
  cp -r "$CLUSTER_BASE/node-$i/$TRACES_DIR" "$NODE_DATA/"
  cp -r "$CLUSTER_BASE/node-$i/$MS_DIR" "$NODE_DATA/"
  cp "$CLUSTER_BASE/node-$i/work/log/"*.log "$NODE_DATA/"
done

cp $MAPPING_FILE "$RESULTS/"
tar -czf "$RESULTS.tar.gz" "$RESULTS"
rm -rf "$RESULTS"

echo "[$0] Results archived: $RESULTS.tar.gz"
exit 0