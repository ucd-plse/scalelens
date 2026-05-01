##########################################################################
#!/bin/bash
##########################################################################
## This script holds common variables used in all stuff.
##########################################################################
export TRUE="0"
export FALSE="1"
export B_TRUE="true"
export B_FALSE="false"
export INT_RE='^[0-9]+$'
export JAVA_CMD="java"
export PYTHON_CMD="python3"
export AGENT="agent"
export AGENT_JAR="$AGENT/ScaleLearningAgent.jar"
export SPOON_JAR="$AGENT/spoon-core-9.1.0-SNAPSHOT-jar-with-dependencies.jar"
export GSON_JAR="$AGENT/gson-2.8.6.jar"
export AGENT_LOG="$AGENT/log-test.properties"
export AGENT_PLOT_TEMPLATE="$AGENT/trend_graph_template.plt"
###########################################################################
function isBoolean {
  if  isEmpty "$1" || [ "$1" != "$B_TRUE" ] && [ "$1" != "$B_FALSE" ];  then
     return $FALSE
  fi
  return $TRUE
}
##########################################################################
function isInteger {
  if  isEmpty "$1" || ! [[ "$1" =~ $INT_RE ]]; then
     return $FALSE
  fi
  return $TRUE
}
##########################################################################
function isEmpty {
  ## $1: The param to check
  if [ -z "$1" ]; then
    return $TRUE
  else
    return $FALSE
  fi
}
##########################################################################
function isFile {
  ## $1: The param to check
  if isEmpty "$1" || [ ! -f "$1" ]; then
    return $FALSE
  else
    return $TRUE
  fi
}
##########################################################################
function isDirectory {
  ## $1: The param to check
  if isEmpty "$1" || [ ! -d "$1" ]; then
    return $FALSE
  else
    return $TRUE
  fi
}


##########################################################################
function checkAgentDir {
  ## here, the whole agent setup is validated
  if [ ! -d "$AGENT" ]; then
    echo "[$0] [$AGENT] folder does not exist. See readme for instructions."
    return $FALSE
  fi
}
