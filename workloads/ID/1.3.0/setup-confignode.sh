##########################################################################
#!/bin/bash
##########################################################################
## This scripts setups a single new node
source environment.sh
## we need to setup a new node in a target folder
NODE_ID="$1"
NODE_BASE="$2"
CN_INTERNAL_PORT="$3"
CN_CONSENSUS_PORT="$4"
CN_IP="$5"
## check some stuff firts
if ! isDirectory "$NODE_BASE" || [ "$#" -ne 5 ]; then
  echo "[$0] USAGE: $0 <node_id> <base_dir> <internal_port> <consensus_port> <IP>"
  echo "[$0] <node_id>: The id of the node (int.)"
  echo "[$0] <base_dir>: The base directory where the node will be setup."
  echo "[$0] <internal_port>: The internal port for the node."
  echo "[$0] <consensus_port>: The consensus port for the node."
  echo "[$0] <IP>: The IP address for the node."
  exit 1
fi


CLUS_TPL="$TPL/iotdb-cluster.properties.tpl"
COMM_TPL="$TPL/iotdb-common.properties.tpl"
DNODE_TPL="$TPL/iotdb-datanode.properties.tpl"
CNODE_TPL="$TPL/iotdb-confignode.properties.tpl"

## now, the targets
NODE_DIR="$NODE_BASE/confignode-$NODE_ID"
CONF_DIR="$NODE_DIR/conf"
LOG_DIR="$NODE_DIR/logs"
CLUS_CONF="$CONF_DIR/iotdb-cluster.properties"
COMM_CONF="$CONF_DIR/iotdb-common.properties"
DNODE_CONF="$CONF_DIR/iotdb-datanode.properties"
CNODE_CONF="$CONF_DIR/iotdb-confignode.properties"

## lets delete and make
echo "[$0] Recreating [$NODE_DIR]"
rm -rf $NODE_DIR
mkdir -p $NODE_DIR
mkdir -p $CONF_DIR
mkdir -p $LOG_DIR
mkdir -p $NODE_DIR/$TRACES_DIR
mkdir -p $NODE_DIR/$CMD_DIR

## check a little
if ! isDirectory "$CONF_DIR"; then
  echo "[$0] Could not setup [$CONF_DIR], exiting..."
  exit 1
fi
echo "[$0] Copying templates to [$CONF_DIR]"
## copy everything from templates, then copy the properties
cp $TPL/* $CONF_DIR
cp $CLUS_TPL $CLUS_CONF
cp $COMM_TPL $COMM_CONF
cp $DNODE_TPL $DNODE_CONF
cp $CNODE_TPL $CNODE_CONF
## check again
if ! isFile "$CLUS_CONF" || ! isFile "$COMM_CONF" || ! isFile "$DNODE_CONF" || ! isFile "$CNODE_CONF"; then
  echo "[$0] Could not copy [$CLUS_CONF] or [$COMM_CONF] or [$DNODE_CONF] or [$CNODE_CONF] to [$CONF_DIR], exiting..."
  exit 1
fi
## and proceed for sed...
echo "[$0] Performing replacements..."
sed -i s:@CN_INTERNAL_PORT:$CN_INTERNAL_PORT:g $CNODE_CONF
sed -i s:@CN_CONSENSUS_PORT:$CN_CONSENSUS_PORT:g $CNODE_CONF
sed -i s:@CN_IP:$CN_IP:g $CNODE_CONF


## echo ip and port into file
echo $IP > $CONF_DIR/$IP_FILE
echo $CN_INTERNAL_PORT > $CONF_DIR/$PORT_FILE
## this should be ok
echo "[$0] done, setup node [$NODE_ID] at [$NODE_DIR]"
## exit ok explicit
exit 0
