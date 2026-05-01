##########################################################################
#!/bin/bash
##########################################################################
## This scripts setups a single new node
source environment.sh
## we need to setup a new node in a target folder
NODE_ID="$1"
NODE_BASE="$2"
DN_RPC_PORT="$3"
DN_INTERNAL_PORT="$4"
DN_MPP_DATA_EXCHANGE_PORT="$5"
DN_SCHEMA_REGION_CONSENSUS_PORT="$6"
DN_DATA_REGION_CONSENSUS_PORT="$7"
DN_IP="$8"

## check some stuff firts
if ! isDirectory "$NODE_BASE" || [ "$#" -ne 8 ]; then
  echo "[$0] USAGE: $0 <node_id> <base_dir> <RPC_port> <DN_internal_port> <DN_MPP_data_exchange_port> <DN_schema_region_consensus_port> <DN_data_region_consensus_port> <DN_IP>"
  echo "[$0] <node_id>: The id of the node (int.)"
  echo "[$0] <base_dir>: The base directory where the node will be setup."
  echo "[$0] <RPC_port>: The RPC port for the node."
  echo "[$0] <DN_internal_port>: The internal port for the node."
  echo "[$0] <DN_MPP_data_exchange_port>: The MPP data exchange port for the node."
  echo "[$0] <DN_schema_region_consensus_port>: The schema region consensus port for the node."
  echo "[$0] <DN_data_region_consensus_port>: The data region consensus port for the node."
  echo "[$0] <DN_IP>: The IP address for the node."
  exit 1
fi

CLUS_TPL="$TPL/iotdb-cluster.properties.tpl"
COMM_TPL="$TPL/iotdb-common.properties.tpl"
DNODE_TPL="$TPL/iotdb-datanode.properties.tpl"
CNODE_TPL="$TPL/iotdb-confignode.properties.tpl"

## now, the targets
NODE_DIR="$NODE_BASE/datanode-$NODE_ID"
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
sed -i s:@DN_RPC_PORT:$DN_RPC_PORT:g $DNODE_CONF
sed -i s:@DN_INTERNAL_PORT:$DN_INTERNAL_PORT:g $DNODE_CONF
sed -i s:@DN_MPP_DATA_EXCHANGE_PORT:$DN_MPP_DATA_EXCHANGE_PORT:g $DNODE_CONF
sed -i s:@DN_SCHEMA_REGION_CONSENSUS_PORT:$DN_SCHEMA_REGION_CONSENSUS_PORT:g $DNODE_CONF
sed -i s:@DN_DATA_REGION_CONSENSUS_PORT:$DN_DATA_REGION_CONSENSUS_PORT:g $DNODE_CONF
sed -i s:@DN_IP:$DN_IP:g $DNODE_CONF

# echo the internal ip/port to ip/port file
echo $DN_IP > $CONF_DIR/$IP_FILE
echo $DN_INTERNAL_PORT > $CONF_DIR/$PORT_FILE
## this should be ok
echo "[$0] done, setup node [$NODE_ID] at [$NODE_DIR]"
## exit ok explicit
exit 0
