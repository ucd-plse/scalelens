
# Use --patch-module to add your agent and javassist to the java.base module (or any other relevant system module)
SL_AGENT_FLAGS="--patch-module java.base=$AGENT_JAR:$AGENT_ASSIST"

# Allow reflective access to the agent's package for the premain method
SL_AGENT_FLAGS="$SL_AGENT_FLAGS --add-opens java.base/scaleview.agent.main=java.instrument"

# Allow java.base to read java.logging and java.instrument modules
SL_AGENT_FLAGS="$SL_AGENT_FLAGS --add-reads java.base=java.logging,java.instrument"

# Allow java.base to access java.util.logging for LogManager
SL_AGENT_FLAGS="$SL_AGENT_FLAGS --add-opens java.base/java.util=ALL-UNNAMED"
SL_AGENT_FLAGS="$SL_AGENT_FLAGS --add-exports java.logging/java.util.logging=java.base"

# Other agent configuration
SL_AGENT_FLAGS="$SL_AGENT_FLAGS -Djdk.attach.allowAttachSelf -javaagent:$AGENT_JAR"

# Method entry profiler configuration
SL_AGENT_FLAGS="$SL_AGENT_FLAGS=profiler:name=methods,coreThreads=16,maxThreads=16,outputDirectory=$NODE_BASE/$TRACES_DIR,waitWhenShutDown=true,flushThreshold=100,inclusions=$INCLUSIONS"

# Command thread configuration
SL_AGENT_FLAGS="$SL_AGENT_FLAGS;commandThread:commandDirectory=$NODE_BASE/$CMD_DIR,sleepTime=500"

# Logging configuration
SL_AGENT_FLAGS="$SL_AGENT_FLAGS -Dscaleview.logFile=$PWD/$AGENT_LOG"

# Include the agent and asset JARs in the regular classpath
SL_AGENT_FLAGS="$SL_AGENT_FLAGS -cp \"$AGENT_JAR:$AGENT_ASSIST:$CLASSPATH\""



## Previous Flags 
# Include the agent and asset JARs in the regular classpath
  ## Profiling agent, base part
  # SL_AGENT_FLAGS="-Xbootclasspath/p:$AGENT_JAR:$AGENT_ASSIST"
  # SL_AGENT_FLAGS="$SL_AGENT_FLAGS -Djdk.attach.allowAttachSelf -javaagent:$AGENT_JAR"
  # ## method entry profiler configuration
  # SL_AGENT_FLAGS="$SL_AGENT_FLAGS=profiler:name=methods,coreThreads=16,maxThreads=16,outputDirectory=$NODE_BASE/$TRACES_DIR,waitWhenShutDown=true,flushThreshold=100,inclusions=$INCLUSIONS"
  # ## command thread configuration
  # SL_AGENT_FLAGS="$SL_AGENT_FLAGS;commandThread:commandDirectory=$NODE_BASE/$CMD_DIR,sleepTime=500"
  # ## and finally logging
  # SL_AGENT_FLAGS="$SL_AGENT_FLAGS -Dscaleview.logFile=$PWD/$AGENT_LOG"