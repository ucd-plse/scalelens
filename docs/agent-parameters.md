
## Agent Parameters

To run the agent, you need to specify certain parameters as part of the `javaagent` string.

### 1. Profiler

A profiler is a thread pool that handles profiling events. The profilers that the application will have are fixed, but its configuration parameters have to be specified. These parameters are:

- **1.1 coreThreads**: (int) Minimum number of threads in the thread pool. Defaults to available cores.
- **1.2 maxThreads**: (int) Maximum number of threads in the thread pool. Defaults to available cores.
- **1.3 outputDirectory**: (string) FULL PATH to where the threads will log the events. It must be an existing directory.
- **1.4 waitWhenShutDown**: (boolean) When the application ends, the thread pool needs to be shut down. If this boolean is true, shutdown waits until all in-flight events are written to the corresponding files and then closes those streams. If false, it just closes the streams, and pending events are lost.
- **1.5 flushThreshold**: (int) Profiling threads will flush the associated writer every flushThreshold events. This is useful in case the shutdown hooks fail.

By default, we need two profilers:
- **name=methods**, which is in charge of processing loop events.
- **name=heapMeasure**, which is in charge of processing heap measurements events.

### 2. CommandThread

CommandThread is a thread started with the system that processes commands that are written into a directory in `properties'` format. A configuration is like 

```
commandThread:commandDirectory=$PWD/cmds,sleepTime=5000
```

means that this thread will be looking for a file named `command` at `$PWD/cmds` every 5 seconds. If the file is found, the content can be:

- **2.1 Enabling loop profiling**: Upon class loading, every method that returns an iterator is instrumented to log a method entry event every time it's called. This is enabled conditionally, and the method is logged only if this type of events are enabled. The command file must look like:

  ```
  cmd=enable_loop_prof
  ```

- **2.2 Disabling loop profiling**: If this is executed, no iterator entry event will be logged. The command file must look like:

  ```
  cmd=disable_loop_prof
  ```

- **2.3 Measuring the heap**: This means iterating the whole heap looking for collections and their sizes, logging a HeapMeasure event for each one. The command file must look like (MIND THE NEWLINES, use `printf` instead of `echo`):

  ```
  cmd=heapMeasure
  runId=1
  gcBefore=false
  ```

    `runId` is an identifier provided by us to differentiate measurements, and `gcBefore` is a boolean that, when true, executes garbage collection before measuring.

### 3. Log Configuration

This is not really an agent configuration, it's an application property, thus it's passed as

```
-Dscaleview.logFile=<FULL PATH>
```

It is required and it should point to where the log configuration file is (FULL PATH). We use the `java.util.Logging` framework for this, so we have to follow their rules to create our appenders and configurations.
