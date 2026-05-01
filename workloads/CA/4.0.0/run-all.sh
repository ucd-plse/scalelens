## this scrip runs all the exps. Is useful for overnight work...

SCALE_TO_SIZE=64
BATCH_SIZE=25
TRACES="all-traces-$SCALE_TO_SIZE"
CLUSTER_BASE="$PWD/test"
SLEEP_TIME_NORMAL=30
SLEEP_TIME_LONG=90

echo "[$0] Running all experiments for scale to size [$SCALE_TO_SIZE]"

rm -rf $CLUSTER_BASE
mkdir $CLUSTER_BASE
sleep $SLEEP_TIME_LONG

bash w-add-ks.sh $SCALE_TO_SIZE $SLEEP_TIME_NORMAL $CLUSTER_BASE
sleep $SLEEP_TIME_LONG

bash w-add-ks-compact.sh $SCALE_TO_SIZE $SLEEP_TIME_NORMAL $CLUSTER_BASE
sleep $SLEEP_TIME_LONG

bash w-add-ks-snapshot.sh $SCALE_TO_SIZE $SLEEP_TIME_NORMAL $CLUSTER_BASE
sleep $SLEEP_TIME_LONG

bash w-add-table.sh $SCALE_TO_SIZE $SLEEP_TIME_NORMAL $CLUSTER_BASE
sleep $SLEEP_TIME_LONG

bash w-add-table-compact.sh $SCALE_TO_SIZE $SLEEP_TIME_NORMAL $CLUSTER_BASE
sleep $SLEEP_TIME_LONG

bash w-add-table-snapshot.sh $SCALE_TO_SIZE $SLEEP_TIME_NORMAL $CLUSTER_BASE
sleep $SLEEP_TIME_LONG

bash w-add-table-row.sh $SCALE_TO_SIZE $SLEEP_TIME_NORMAL $CLUSTER_BASE
sleep $SLEEP_TIME_LONG

bash w-add-table-row-repair.sh $SCALE_TO_SIZE $SLEEP_TIME_NORMAL $CLUSTER_BASE
sleep $SLEEP_TIME_LONG

# w-add-row.sh <num_batches> <row_per_batch> <sleep_between_steps> <cluster_base>
bash w-add-row.sh $SCALE_TO_SIZE $BATCH_SIZE $SLEEP_TIME_NORMAL $CLUSTER_BASE
sleep $SLEEP_TIME_LONG

# w-add-row-scrub.sh <num_batches> <row_per_batch> <sleep_between_steps> <cluster_base>
bash w-add-row-scrub.sh $SCALE_TO_SIZE $BATCH_SIZE $SLEEP_TIME_NORMAL $CLUSTER_BASE
sleep $SLEEP_TIME_LONG

bash w-add-node.sh $SCALE_TO_SIZE $SLEEP_TIME_LONG $CLUSTER_BASE
sleep $SLEEP_TIME_LONG

bash w-add-node-tok.sh $SCALE_TO_SIZE $SLEEP_TIME_LONG $CLUSTER_BASE
sleep $SLEEP_TIME_LONG

bash w-add-dc-tok.sh $SCALE_TO_SIZE $SLEEP_TIME_LONG $CLUSTER_BASE
sleep $SLEEP_TIME_LONG

bash w-add-dc-tok-kill.sh $SCALE_TO_SIZE $SLEEP_TIME_LONG $CLUSTER_BASE
sleep $SLEEP_TIME_LONG

bash w-add-dc-tok-decomm.sh $SCALE_TO_SIZE $SLEEP_TIME_LONG $CLUSTER_BASE
sleep $SLEEP_TIME_LONG

rm -rf $CLUSTER_BASE

mkdir $TRACES
mv *.tar.gz $TRACES
tar -czf $TRACES.tar.gz $TRACES
rm -rf $TRACES
