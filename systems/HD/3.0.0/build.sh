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

# Build system
print_step "Building $SYS_VER"
# Replace the SNAPSHOT version with the release version
sed -i "s|<version>1.8-SNAPSHOT</version>|<version>1.8</version>|g" $SYS_VER/pom.xml

# Build CodeQL database
print_step "Building CodeQL database"
codeql database create $REPO_DIR/anti-patterns/databases/$SYS_VER --language=java --source-root=$SYS_VER --command="mvn clean package -DskipTests -fn"

# Build HDFS
(cd $SYS_VER && mvn clean package -DskipTests)

# Copy system jar files
print_step "Copying system jar files"
cd $WORKING_DIR
JAR_DIR="system"
if [[ -d $JAR_DIR ]]; then
    rm -rf $JAR_DIR
fi
mkdir -p $JAR_DIR

exclude_files=(
"aopalliance-1.0.jar"
"ehcache-3.3.1.jar"
"geronimo-jcache_1.0_spec-1.0-alpha-1.jar"
"guice-4.0.jar"
"guice-servlet-4.0.jar"
"HikariCP-java7-2.4.12.jar"
"jackson-jaxrs-base-2.7.8.jar"
"jackson-jaxrs-json-provider-2.7.8.jar"
"javax.inject-1.jar"
"jdk.tools-1.8.jar"
"jersey-client-1.19.jar"
"mssql-jdbc-6.2.1.jre7.jar"
"jackson-module-jaxb-annotations-2.7.8.jar"
"jersey-guice-1.19.jar"
)
find $SYS_VER/hadoop-tools/hadoop-distcp/target/lib/ -type f -name "*.jar" | grep -vFf <(printf '%s\n' "${exclude_files[@]}") | grep -vE 'hadoop-yarn|hadoop-mapreduce' | xargs -I {} cp -uf {}  $JAR_DIR
find $SYS_VER/hadoop-hdfs-project/ -type f -name "*.jar" -exec cp -uf {}  $JAR_DIR \;
find $SYS_VER/hadoop-common-project/ -type f -name "*.jar" -exec cp -uf {}  $JAR_DIR \;
## Download missing dependencies
wget -P $JAR_DIR https://repo.maven.apache.org/maven2/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar
wget -P $JAR_DIR https://repo.maven.apache.org/maven2/org/slf4j/jul-to-slf4j/1.7.25/jul-to-slf4j-1.7.25.jar

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
