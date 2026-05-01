#!/usr/bin/env bash

# Exit immediately if a command exits with a non-zero value.
# See https://stackoverflow.com/q/821396/5007059.
set -e
set -o pipefail

ANSI_RED="\033[31;1m"
ANSI_GREEN="\033[32;1m"
ANSI_RESET="\033[0m"
ANSI_CLEAR="\033[0K"

print_step () {
    echo -e "\n${ANSI_GREEN}$1${ANSI_RESET}"
}

# get tar.gz file for system source code, e.g., cassandra-4.0.0.tar.gz
SYS_SRC=$1
# get system-version string, e.g., CA-4.0.0
SYS_VER=$2

if [ $# -ne 2 ] || [ -z "$SYS_SRC" ] || [ -z "$SYS_VER" ]; then
    echo "[$0] USAGE: $0 <source_tarfile> <system-version>"
    echo "[$0] <source_tarfile>: The system source tar.gz file."
    echo "[$0] <system-version>: The system version string (e.g. CA-4.0.0)."
    exit 1
fi

WORKING_DIR=$(pwd)

# the following based on the assumption that everything is managed by git
REPO_DIR=$(git rev-parse --show-toplevel)
AGENT_DIR="$REPO_DIR/agent"


# Unpack system 
print_step "Unpacking $SYS_SRC"
if [[ -d $SYS_VER ]]; then
    rm -rf $SYS_VER
fi
mkdir $SYS_VER
tar -xzf $SYS_SRC -C $SYS_VER --strip-components 1

# Build CodeQL database
print_step "Building CodeQL database"
codeql database create $REPO_DIR/anti-patterns/databases/$SYS_VER --language=java --source-root=$SYS_VER --command="mvn clean package -DskipTests -fn"

# Build system
print_step "Building $SYS_VER"
(cd $SYS_VER && mvn clean package -DskipTests)

# Copy system jar files
print_step "Copying system jar files"
cd $WORKING_DIR
JAR_DIR="system"
if [[ -d $JAR_DIR ]]; then
    rm -rf $JAR_DIR
fi
mkdir -p $JAR_DIR
find $SYS_VER/hadoop-tools/hadoop-distcp/target/lib/ -type f -name "*.jar" | grep -vE 'hadoop-yarn|hadoop-mapreduce' | xargs -I {} cp -uf {}  $JAR_DIR
find $SYS_VER/hadoop-hdfs-project/ -type f -name "*.jar" -exec cp -uf {}  $JAR_DIR \;
find $SYS_VER/hadoop-common-project/ -type f -name "*.jar" -exec cp -uf {}  $JAR_DIR \;


# Generate loop statistics
echo "Generating loop statistics"
GEN_STAT_DIR=$REPO_DIR/analysis/generalstats
MODULE_DIR=hadoop-hdfs-project/hadoop-hdfs
SRC_DIR=$(realpath $SYS_VER/$MODULE_DIR)
CLASS_DIR=$(realpath $SYS_VER/$MODULE_DIR)
CSV_FILE=$SYS_VER.csv
if [[ -f $CSV_FILE ]]; then
    rm $CSV_FILE
fi
(cd $GEN_STAT_DIR && ln -sf $AGENT_DIR agent &&
bash general-stats.sh $SRC_DIR $CLASS_DIR false &&
mv $GEN_STAT_DIR/loops.csv $WORKING_DIR/$CSV_FILE)

# Clean up
print_step "Cleaning up"
if [[ -d $SYS_VER ]]; then
    rm -rf $SYS_VER
fi  