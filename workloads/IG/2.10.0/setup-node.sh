##########################################################################
#!/bin/bash
##########################################################################
## This scripts setups a single new node
source environment.sh
## we need to setup a new node in a target folder
NODE_ID="$1"
NODE_BASE="$2"
IP="$3"
## check some stuff first
if ! isDirectory "$NODE_BASE" || [ "$#" -gt 3 ]; then
  echo "[$0] USAGE: $0 <node_id> <directory_path> <ip>"
  echo "[$0] <node_id>: The id of the node we are setting up (any id is fine as long is different for every node)."
  echo "[$0] <directory_path>: Location where the node will be setup."
  echo "[$0] <ip>: Ip (usually local) of the node."
  exit 1
fi


LOG_TPL="$TPL/config/ignite-log4j.xml"
CONFIG_TPL="$TPL/config/ignite-config.xml" 
## now, the targets
NODE_DIR="$NODE_BASE/node-$NODE_ID"
CONFIG_DIR="$NODE_DIR/conf"
LOG_DIR="$NODE_DIR/logs"
NODE_CONFIG="$CONFIG_DIR/ignite-config.xml"
LOG_CONFIG="$CONFIG_DIR/ignite-log4j.xml"
LOCAL_PORT=$((PORT_BASE + NODE_ID))
PORT_BASE_END=$(($PORT_BASE+256))
## lets delete and make
echo "[$0] Recreating [$NODE_DIR]"
rm -rf $NODE_DIR
mkdir -p $NODE_DIR
mkdir -p $CONFIG_DIR
mkdir -p $LOG_DIR
mkdir -p $NODE_DIR/$TRACES_DIR
mkdir -p $NODE_DIR/$MS_DIR
mkdir -p $NODE_DIR/$CMD_DIR
## check a little
if ! isDirectory "$CONFIG_DIR"; then
  echo "[$0] Could not setup [$CONFIG_DIR], exiting..."
  exit 1
fi
echo "[$0] Copying templates to [$CONFIG_DIR]"
## now, copy
cp $CONFIG_TPL $NODE_CONFIG
cp $LOG_TPL $LOG_CONFIG
## check again
if ! isFile "$NODE_CONFIG" || ! isFile "$LOG_CONFIG"; then
  echo "[$0] Could not copy [$NODE_CONFIG] or [$LOG_CONFIG] to [$CONFIG_DIR], exiting..."
  exit 1
fi
## and proceed for sed...
echo "[$0] Performing replacements..."
sed -i s:@IG_INSTANCE_NAME@:"IG-$NODE_ID":g $NODE_CONFIG
sed -i s:@IP@:$IP:g $NODE_CONFIG
sed -i s:@WORKING_DIR@:$(realpath $NODE_DIR):g $NODE_CONFIG 
sed -i s:@LOCAL_PORT@:$LOCAL_PORT:g $NODE_CONFIG
sed -i s:@PORT_BASE@:$PORT_BASE:g $NODE_CONFIG
sed -i s:@PORT_BASE_END@:$PORT_BASE_END:g $NODE_CONFIG
## now echo the ip somewhere
echo $IP > $CONFIG_DIR/$IP_FILE
## this should be ok
echo "[$0] done, setup node [$NODE_ID] at [$NODE_DIR]"
## exit ok explicit
exit 0
