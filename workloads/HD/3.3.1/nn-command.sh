##########################################################################
#!/bin/bash
##########################################################################
## This scripts starts a new node using the jar files found in 'system'
## folder and some known configuration properties.
## Source environment files
source environment.sh
## the configuration and folders are already created for this step
NODE_BASE="$1"
NODE_CONF="$NODE_BASE/conf"

## check some stuff firts
if ! isDirectory "$NODE_BASE" || [ "$#" -lt 1 ]; then
  echo "[$0] USAGE: $0 <directory_path> <jmx-port> <use-agent>"
  echo "[$0] <directory_path>: The content directory of an existing node (see setup-node.sh). Use absolute path."
  exit 1
  
fi

## main class
MAIN="org.apache.hadoop.fs.FsShell"
## confs and log files. Both the logging conf and the yaml file need to 
## be located in this folder.
RESOURCES="."
PROPERTIES="-Dlog4j.configuration=file://$NODE_CONF/log4j.properties"
## classpath
CLASSPATH="$RESOURCES:$SYS/*"

## we need to apparently copy the conf here...
cp $NODE_CONF/core-site.xml core-site.xml
cp $NODE_CONF/hdfs-site.xml hdfs-site.xml
cp $TPL/mapred-site.xml.tpl mapred-site.xml
cp $TPL/yarn-site.xml.tpl yarn-site.xml

## execute the command here
CMD="$JAVA_CMD -cp \"$CLASSPATH\" $PROPERTIES $OP_HEAP $MAIN -conf core-site.xml -conf hdfs-site.xml -conf mapred-site.xml -conf yarn-site.xml  ${@:2}"
echo "[$0] Executing [$CMD]"
$JAVA_CMD $PROPERTIES -cp "$CLASSPATH" $PROPERTIES $OP_HEAP $MAIN -conf core-site.xml -conf hdfs-site.xml -conf mapred-site.xml -conf yarn-site.xml "${@:2}"

## and clean
rm core-site.xml
rm hdfs-site.xml
rm mapred-site.xml
rm yarn-site.xml

exit 0
