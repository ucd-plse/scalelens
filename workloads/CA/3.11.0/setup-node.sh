##########################################################################
#!/bin/bash
##########################################################################
## This scripts setups a single new node
source environment.sh
## we need to setup a new node in a target folder
NODE_ID="$1"
NODE_BASE="$2"
NUM_TOKENS="$3"
IP="$4"
SNITCH="$5"
## check some stuff firts
if ! isInteger "$NUM_TOKENS" || ! isDirectory "$NODE_BASE" || [ "$#" -gt 5 ]; then
  echo "[$0] USAGE: $0 <node_id> <directory_path> <num_tokens> <ip> <snitch>"
  echo "[$0] <node_id>: The id of the node we are setting up (any id is fine as long is different for every node)."
  echo "[$0] <directory_path>: Location where the node will be setup."
  echo "[$0] <num_tokens>: Number of tokens the node owns. Its an integer."
  echo "[$0] <ip>: Ip (usually local) of the node."
  echo "[$0] [OPTIONAL] <snitch>: Snitch to use, useful for multiple datacenter setup"
  exit 1
fi

## we are done, so lets proceed, with templates. The heavy for the first,
## the light for the others
if [ "$IP" = "$DEFAULT_SEEDS" ]; then
  echo "[$0] Using heavy config for node [$NODE_ID]"
  CONFIG_TPL="$TPL/cassandra.heavy.yaml.tpl"
else
  echo "[$0] Using normal config for node [$NODE_ID]"
  CONFIG_TPL="$TPL/cassandra.yaml.tpl"
fi

## one more, we need to set the snitch
if [ -z "$SNITCH" ]; then
  SNITCH="$DEFAULT_SNITCH"
fi

echo "[$0] Usuing snitch [$SNITCH]"

LOG_TPL="$TPL/logback.xml.tpl"
## now, the targets
NODE_DIR="$NODE_BASE/$NODE_ID"
SETUP_DIR="$NODE_DIR/conf"
LOG_DIR="$NODE_DIR/logs"
CONFIG_NODE="$SETUP_DIR/cassandra.yaml"
LOG_NODE="$SETUP_DIR/logback.xml"
## lets delete and make
echo "[$0] Recreating [$NODE_DIR]"
rm -rf $NODE_DIR
mkdir -p $NODE_DIR
mkdir -p $SETUP_DIR
mkdir -p $LOG_DIR
mkdir -p $NODE_DIR/$TRACES_DIR
mkdir -p $NODE_DIR/$MS_DIR
mkdir -p $NODE_DIR/$CMD_DIR
## check a little
if ! isDirectory "$SETUP_DIR"; then
  echo "[$0] Could not setup [$SETUP_DIR], exiting..."
  exit 1
fi
echo "[$0] Copying templates to [$SETUP_DIR]"
## now, copy
cp $CONFIG_TPL $CONFIG_NODE
cp $LOG_TPL $LOG_NODE
## check again
if ! isFile "$CONFIG_NODE" || ! isFile "$LOG_NODE"; then
  echo "[$0] Could not copy [$CONFIG_NODE] or [$LOG_NODE] to [$SETUP_DIR], exiting..."
  exit 1
fi
## and proceed for sed...
echo "[$0] Performing replacements..."
sed -i s:@TOKENS:$NUM_TOKENS:g $CONFIG_NODE
sed -i s:@DATA_ROOT:$NODE_DIR:g $CONFIG_NODE
sed -i s:@IP:$IP:g $CONFIG_NODE
sed -i s:@SEEDS:$DEFAULT_SEEDS:g $CONFIG_NODE
sed -i s:@SNITCH:$SNITCH:g $CONFIG_NODE
## now echo the ip somewhere
echo $IP > $SETUP_DIR/$IP_FILE
## this should be ok
echo "[$0] done, setup node [$NODE_ID] at [$NODE_DIR]"
## exit ok explicit
exit 0
