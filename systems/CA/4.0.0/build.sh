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

# Fetch dependencies
wget -P $SYS_VER/build https://archive.apache.org/dist/maven/ant-tasks/2.1.3/binaries/maven-ant-tasks-2.1.3.jar
mvn dependency:get -fn -DgroupId=org.slf4j -DartifactId=slf4j-api -Dversion=1.5.2 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=com.sun.jersey -DartifactId=jersey-server -Dversion=1.0 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=tomcat -DartifactId=jasper-runtime -Dversion=5.5.12 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=commons-el -DartifactId=commons-el -Dversion=1.0 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=org.codehaus.jackson -DartifactId=jackson-mapper-asl -Dversion=1.0.1 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=org.mortbay.jetty -DartifactId=jetty -Dversion=6.1.26 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=net.sf.kosmosfs -DartifactId=kfs -Dversion=0.3 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=net.ju-n.compile-command-annotations -DartifactId=compile-command-annotations -Dversion=1.2.0 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=xmlenc -DartifactId=xmlenc -Dversion=0.52 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=com.google.code.findbugs -DartifactId=jsr305 -Dversion=2.0.2 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=org.caffinitas.ohc -DartifactId=ohc-core -Dversion=0.4.4 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=junit -DartifactId=junit -Dversion=4.6 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=commons-httpclient -DartifactId=commons-httpclient -Dversion=3.0.1 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=org.eclipse.jdt.core.compiler -DartifactId=ecj -Dversion=4.4.2 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=hsqldb -DartifactId=hsqldb -Dversion=1.8.0.10 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=com.datastax.cassandra -DartifactId=cassandra-driver-core -Dversion=3.0.1 -Dclassifier=shaded -Dpackaging=jar
mvn dependency:get -fn -DgroupId=net.java.dev.jets3t -DartifactId=jets3t -Dversion=0.7.1 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=oro -DartifactId=oro -Dversion=2.0.8 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=tomcat -DartifactId=jasper-compiler -Dversion=5.5.12 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=org.openjdk.jmh -DartifactId=jmh-core -Dversion=1.13 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=org.antlr -DartifactId=antlr -Dversion=3.5.2 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=org.mortbay.jetty -DartifactId=jetty-util -Dversion=6.1.26 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=org.caffinitas.ohc -DartifactId=ohc-core-j8 -Dversion=0.4.4 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=org.openjdk.jmh -DartifactId=jmh-generator-annprocess -Dversion=1.13 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=org.mortbay.jetty -DartifactId=jsp-api-2.1 -Dversion=6.1.14 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=org.mortbay.jetty -DartifactId=jsp-2.1 -Dversion=6.1.14 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=io.netty -DartifactId=netty-all -Dversion=4.0.44.Final -Dpackaging=jar
mvn dependency:get -fn -DgroupId=org.jacoco -DartifactId=org.jacoco.agent -Dversion=0.7.5.201505241946 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=org.jboss.byteman -DartifactId=byteman -Dversion=3.0.3 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=org.jacoco -DartifactId=org.jacoco.ant -Dversion=0.7.5.201505241946 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=org.jboss.byteman -DartifactId=byteman-submit -Dversion=3.0.3 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=org.jboss.byteman -DartifactId=byteman-bmunit -Dversion=3.0.3 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=com.datastax.wikitext -DartifactId=wikitext-core-ant -Dversion=1.3 -Dpackaging=jar
mvn dependency:get -fn -DgroupId=org.fusesource.wikitext -DartifactId=textile-core -Dversion=1.3 -Dpackaging=jar

# Build CodeQL database
print_step "Building CodeQL database"
codeql database create $REPO_DIR/anti-patterns/databases/$SYS_VER --language=java --source-root=$SYS_VER --command="ant -Drat.skip=true"

# Build system
print_step "Building $SYS_VER"
(cd $SYS_VER && ant -Drat.skip=true)

# Copy system jar files
print_step "Copying system jar files"
cd $WORKING_DIR
JAR_DIR="system"
if [[ -d $JAR_DIR ]]; then
    rm -rf $JAR_DIR
fi
mkdir -p $JAR_DIR
cp -r $SYS_VER/lib/* $JAR_DIR
cp $SYS_VER/build/*.jar $JAR_DIR

# Generate loop statistics
print_step "Generating loop statistics"
GEN_STAT_DIR=$REPO_DIR/analysis/generalstats
SRC_DIR=$(realpath $SYS_VER)
CLASS_DIR=$(realpath $SYS_VER)
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
