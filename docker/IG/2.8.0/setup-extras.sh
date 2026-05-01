
#!/usr/bin/env bash

# This script is used to provision extra requirements for Hadoop-HDFS in a Docker container.
# Assumptions:
# 1. The user running this script is root.
# 2. The operating system is Ubuntu 22.04.
# 3. The main provisioning script has already been run.


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

# Install Python dependencies
print_step "Installing Numa Library for 2.8.0 versions starting september"

sudo apt-get install -y libnuma-dev

# Install Protobuf 2.5.0 - specific for Hadoop 3.0.0
