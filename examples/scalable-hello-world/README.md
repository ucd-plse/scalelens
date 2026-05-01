# Example 2: Instrumenting a Scalable Java HelloWorld Program

This example shows how to use ScaleView identify scale-dependent loops from a **scalable** Java program. For fundamental concepts, please refer to [Example 1](../hello-world/README.md).

## How can a HelloWorld program be scalable?

The [HelloWorld](./HelloWorld.java) program in this example is scalable because it contains two loops, one iterate `x` times and the other iterate `x^2` times. It will read the value of `x` from a file with the file path passed as the first argument. 

## How does the HelloWorld mimic a scalable system?

The script [`run.sh`](./run.sh) will first start the `HelloWorld` program. While `HelloWorld` program is looking for the file to read the value in a loop, the script will create a file with value `1` in it, and increase the value in every 5 seconds.

At the meantime, `HelloWorld` program will read the value from the file every 5 seconds. When the value is read, the program will execute some loops that are scale-dependent to the value. 


## Run the example

### Run instrumentation on HelloWorld and scale it up to 64

To scale the `HelloWorld` up to 64, run the following command:

```bash
bash run.sh HelloWorld 64
```

You will see the traces files generated in the `traces` folder, as well as a `.nm` mapping file generated.

### Run event parsing

Go to the `analysis/eventparsing` folder, and run the following commands.

First, create a symbolic link to the `agent` folder in the `eventparsing` folder:

```sh
REPO_DIR=$(git rev-parse --show-toplevel)
cd $REPO_DIR/analysis/eventparsing
ln -sf $REPO_DIR/agent .
```

Then, run the following command to parse the traces:
```sh
REPO_DIR=$(git rev-parse --show-toplevel)
bash event-parsing.sh $REPO_DIR/examples/scalable-hello-world/traces L x 64 $REPO_DIR/examples/scalable-hello-world/HelloWorld-64.nm
```


The output of event parsing includes csv files that contains one loop per line, as well as a summary.log showing the number of loops identified as scale-dependent.

### Run event filtering 

Before running event filtering, we need to get a `csv` file that contains the loop information for the target system. Go to `analysis/generalstats` folder, and run the following command:

```sh
REPO_DIR=$(git rev-parse --show-toplevel)
cd $REPO_DIR/analysis/generalstats
bash general-stats.sh $REPO_DIR/examples/scalable-hello-world $REPO_DIR/examples/scalable-hello-world false
```

The output is a `loops.csv` with one loop per line, copy it to the `analysis/eventfiltering` folder as `app_loops.csv`.

Go to the `analysis/eventfiltering` folder, and run the following commands.

```sh
REPO_DIR=$(git rev-parse --show-toplevel)
cp $REPO_DIR/analysis/generalstats/loops.csv $REPO_DIR/analysis/eventparsing/app_loops.csv
cd $REPO_DIR/analysis/eventfiltering

S=64
D=$REPO_DIR/analysis/eventparsing
DIM=x
F=$D/$DIM/loop/$S-data
O=$D/$DIM/loop
python3 curve_classifier.py complexity $F $O frechet_dist $S 1 x,iterations $D/app_loops.csv
```


