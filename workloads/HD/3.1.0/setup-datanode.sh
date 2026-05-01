##########################################################################
#!/bin/bash
##########################################################################
## This scripts setups a single new node
source environment.sh
## we need to setup a new node in a target folder
NODE_ID="$1"
NODE_BASE="$2"
IP="$3"
IPC_PORT="$4"
SERVICE_PORT="$5"
HTTP_PORT="$6"
NN_IP="$7"
NN_PORT="$8"

## check some stuff firts
if ! isDirectory "$NODE_BASE" || [ "$#" -ne 8 ]; then
  echo "[$0] USAGE: $0 <node_id> <directory_path> <ip> <ipc-port> <service-port> <http-port> <nn-ip> <nn-port>"
  echo "[$0] <node_id>: The id of the node we are setting up (any id is fine as long is different for every node)."
  echo "[$0] <directory_path>: Location where the node will be setup."
  echo "[$0] <ip>: The DN ip address."
  echo "[$0] <ipc-port>: The DN ipc port."
  echo "[$0] <service-port>: The DN service port."
  echo "[$0] <http-port>: The DN HTTP port."
  echo "[$0] <nn-ip>: The NN ip address."
  echo "[$0] <nn-service-port>: The NN SERVICE port."
  exit 1
fi

HDFS_TPL="$TPL/hdfs-site-dn.xml.tpl"
CORE_TPL="$TPL/core-site-dn.xml.tpl"
LOG_TPL="$TPL/log4j.properties.tpl"

## now, the targets
NODE_DIR="$NODE_BASE/$NODE_ID"
SETUP_DIR="$NODE_DIR/conf"
LOG_DIR="$NODE_DIR/logs"
CONFIG_HDFS="$SETUP_DIR/hdfs-site.xml"
CONFIG_CORE="$SETUP_DIR/core-site.xml"
LOG_NODE="$SETUP_DIR/log4j.properties"
## lets delete and make
echo "[$0] Recreating [$NODE_DIR]"
rm -rf $NODE_DIR
mkdir -p $NODE_DIR
mkdir -p $SETUP_DIR
mkdir -p $LOG_DIR
mkdir -p $NODE_DIR/$TRACES_DIR
mkdir -p $NODE_DIR/$CMD_DIR
mkdir "$NODE_DIR/data"

## check a little
if ! isDirectory "$SETUP_DIR"; then
  echo "[$0] Could not setup [$SETUP_DIR], exiting..."
  exit 1
fi
echo "[$0] Copying templates to [$SETUP_DIR]"
## now, copy
cp $HDFS_TPL $CONFIG_HDFS
cp $CORE_TPL $CONFIG_CORE
cp $LOG_TPL $LOG_NODE
## check again
if ! isFile "$CONFIG_HDFS" || ! isFile "$CONFIG_CORE" || ! isFile "$LOG_NODE"; then
  echo "[$0] Could not copy [$CONFIG_HDFS] or [$CONFIG_CORE] to [$SETUP_DIR], exiting..."
  exit 1
fi
## and proceed for sed...
echo "[$0] Performing replacements..."
sed -i s:@DATA_DIR:$NODE_DIR/data:g $CONFIG_HDFS
sed -i s:@IPC_PORT:$IPC_PORT:g $CONFIG_HDFS
sed -i s:@SERVICE_PORT:$SERVICE_PORT:g $CONFIG_HDFS
sed -i s:@HTTP_PORT:$HTTP_PORT:g $CONFIG_HDFS
sed -i s:@DN_IP:$IP:g $CONFIG_HDFS

sed -i s:@NN_IP:$NN_IP:g $CONFIG_CORE
sed -i s:@NN_PORT:$NN_PORT:g $CONFIG_CORE

sed -i s:@LOG_DIR:$NODE_DIR/logs:g $LOG_NODE

## now echo the ip somewhere
echo $IP > $SETUP_DIR/$IP_FILE
## this should be ok
echo "[$0] done, setup node [$NODE_ID] at [$NODE_DIR]"
## exit ok explicit
exit 0
