## Event Traces Description


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

`S` lines are `system` lines, for system method registration. The format is:

```
S <method ID> <application method> <line number>
```


### `H` Lines (WIP)

`H` lines are `heap` lines. It is fired when an object of the heap is measured. The qualified object should be collection type and linked to an application class. 

```
H <thread ID> <collection ID> <collection length> <collection type> <descriptor>
```

`collection type` is a single character: `A` for arrays, `C` for collections, `M` for maps and `U` for others.

`descriptor` is a concatenation of the declaring class and the name of the field we are measuring, like a fully qualified field name.
