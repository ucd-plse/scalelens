# Example 1: Instrumenting a Simple Java HelloWorld Program

This example demonstrates how to instrument a simple Java HelloWorld program with ScaleView.

## Compile the Java HelloWorld Program

You don't need to write one. [Here](./HelloWorld.java) is one for you. Compile it with the following command:

```bash
javac HelloWorld.java
```

It will output a `HelloWorld.class` file.

## Run the Java HelloWorld Program

Simply run the Java HelloWorld program with the following command:

```bash
java -cp . HelloWorld
```

## Run the Instrumented HelloWorld Program

1. Do some preparations:

```bash
ln -s ../../agent . # Link the agent to the current directory
mkdir -p traces # Create a directory to store traces
mkdir -p cmds # Create a directory to store commands
mkdir -p instrumented # Create a directory to store instrumented classes
```

2. Run instrumented HelloWorld program:

On a high level, the command is like:

```bash
java [some options that enable the agent] [some options that config the agent] -cp . HelloWorld
```

The command will still run HelloWorld program, but the with the agent attached, it will instrument the code first before running it.


To actually run the instrumentation, simply run the following command:

```bash
bash run_instrumentation.sh HelloWorld
```

The script will run the HelloWorld first, then run the instrumented HelloWorld again. You will also see the traces in the `traces` directory. Also, all instrumented classes will be stored in the `instrumented` directory.

## Decompile the Instrumented HelloWorld Program

There is a packaged tool `cfr-0.152.jar` which can help us decompile the instrumented classes, so we have a better understanding. 

Use the following command to decompile the instrumented HelloWorld class:

``` bash
java -jar cfr-0.152.jar instrumented/HelloWorld
```


## Understand the Outputted Traces

From the traces directory, you will see a bunch of trace files. Each trace file corresponds to a thread that been used to run instrumented code. The [script](./run_instrumentation.sh#L37) provided used only one thread, so you will see a file named `1`.

There is an [example trace file](./example-traces). It has three types of lines.

### `R` Lines

`R` lines are `registration` lines. They are used to register a method when loading the class file. `R` lines happen only once for loaded methods. The format is:

```
R <method ID> <class name> <method name> <parameters>
```

### `L` Lines

`L` lines are `loop` lines. They are used to log an event when a loop is entered. The format is:

```
L <iteration ID> <run ID> <thread ID> <method ID> <line number> <should resolve> <system method> <iteration count>
```

Please note that each L line is for one entry of a loop. If a loop is entered multiple times, there will be multiple L lines for it, with different iteration IDs.

`run ID` is configured in `cmds/command` file. We use `run ID` to identify different runs of the same program, but with different inputted scales.

`method ID` is the ID of the method that contains the loop, you should be able to find the corresponding `R` line in the trace file.

`should resolve` is a boolean value. When it is `false`, it means the method contains the loop is in a list of included packages.

`system method` is the same with `method ID`, for some reason.

`iteration count` is the most important part. From this, we know how many iteration the loop has been executed.

### `E` Lines

`E` lines are `entry` lines. They are used to log an event when a method is entered. The format is:

```
E <run ID> <thread ID> <method ID> <entry count>
```

`entry count` is number of times that the method (`method ID` as the identifier) has been entered.


### `S` Lines

It may not exist in this example, but there are `S` lines in some trace files. `S` lines are `system` lines, for system method registration. The format is:

```
S <method ID> <application method> <line number>
```

