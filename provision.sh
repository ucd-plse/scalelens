#!/usr/bin/env bash

# This provisioning script is used to setup ScaleView in a Docker container.
# This is specific for the systems with Java 8.
# Assumptions:
# 1. The user running this script is root.
# 2. The operating system is Ubuntu 22.04.



# If parameter not passed JAVA_VERSION defaults to Version 8 
JAVA_VERSION=${1:-8}

REPO_DIR=""
AGENT_DIR=""


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


# Install dependencies: Maven
print_step "Installing Build Systems"
apt-get update
apt-get install -y maven software-properties-common ant

# Install Java
if [ "$JAVA_VERSION" == "8" ]; then
    print_step "Installing Java 8"
    apt-get install -y openjdk-8-jdk
    # Set Java 8 as the default Java version
    update-alternatives --set java /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java
    update-alternatives --set javac /usr/lib/jvm/java-8-openjdk-amd64/bin/javac
    export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
    export PATH=$JAVA_HOME/bin:$PATH
    print_step "Java 8 installed and set as default"
elif [ "$JAVA_VERSION" == "11" ]; then  
    print_step "Installing Java 11"
    apt-get install -y openjdk-11-jdk # update-alterantives gives problems with Java 11. Install Java 11 only, another version may have diff req
    print_step "Java 11 installed"  
fi

# Verify installation
print_step "Verifying Java installation"
java -version
javac -version


# REPO_DIR=""
# AGENT_DIR=""


# # Exit immediately if a command exits with a non-zero value.
# # See https://stackoverflow.com/q/821396/5007059.
# set -e
# set -o pipefail


# ANSI_RED="\033[31;1m"
# ANSI_GREEN="\033[32;1m"
# ANSI_RESET="\033[0m"
# ANSI_CLEAR="\033[0K"

# print_step () {
#     echo -e "\n${ANSI_GREEN}$1${ANSI_RESET}"
# }


# # Install dependencies: Java 8, Maven
# print_step "Installing Java 8 and Build Systems"
# apt-get update
# apt-get install -y openjdk-8-jdk maven software-properties-common ant
# # Set Java 8 as the default Java version
# update-alternatives --set java /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java
# update-alternatives --set javac /usr/lib/jvm/java-8-openjdk-amd64/bin/javac
# export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
# export PATH=$JAVA_HOME/bin:$PATH

# Install dependencies: Python 3.10
print_step "Setup Python 3.10"
apt-get install -y python3.10 python3-pip
update-alternatives --install /usr/bin/python3 python3 /usr/bin/python3.10 1
update-alternatives --install /usr/bin/python python /usr/bin/python3.10 1

# Install misc dependencies
print_step "Installing miscellaneous dependencies"
apt-get install wget vim git curl sudo lsof net-tools autoconf automake build-essential libtool -y
wget https://github.com/mikefarah/yq/releases/download/v4.2.0/yq_linux_amd64 -O /usr/bin/yq
chmod +x /usr/bin/yq

# Install CodeQL
print_step "Installing CodeQL"
CODEQL_DIR="/home/scaleview/utils/codeql"
mkdir -p $CODEQL_DIR
wget https://github.com/github/codeql-action/releases/download/codeql-bundle-v2.18.1/codeql-bundle-linux64.tar.gz -O $CODEQL_DIR/codeql-bundle-linux64.tar.gz
tar -xzf $CODEQL_DIR/codeql-bundle-linux64.tar.gz -C $CODEQL_DIR
echo "export PATH=$PATH:$CODEQL_DIR/codeql" >> /home/scaleview/.bashrc

# Clean up
apt-get clean

# Set up git
git config --global --add safe.directory '*'

# Set the working directory
REPO_DIR=$(git rev-parse --show-toplevel)

# Install Python dependencies
print_step "Installing Python dependencies"
pip install -r $REPO_DIR/requirements.txt



# Build ScaleView
print_step "Building ScaleView"
cd $REPO_DIR
bash build-pack.sh

if [[ -d $(realpath target) ]]; then
    AGENT_DIR=$(realpath target)
    print_step "Build successful"
else
    print_step "Build failed"
    exit 1
fi

# Prepare working directory
print_step "Preparing working directory"
mkdir -p workspace
chmod -R 777 workspace

