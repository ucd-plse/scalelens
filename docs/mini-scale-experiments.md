# Mini-Scale Experiments

The mini-scale experiments are intended as a quick way to exercise the full ScaleLens pipeline end-to-end. 

We have provided 3 self-contained Docker images for the mini-scale experiment with Scalelens:
- `ucdavisplse/scalelens:CA-3.11.0-mini` for CASSANDRA-3.11.0 (`add-node` workload)
- `ucdavisplse/scalelens:HD-3.1.0-mini` for HDFS-3.1.0 (`add-dn-block` workload)
- `ucdavisplse/scalelens:IG-2.8.0-mini` for IGNITE-2.8.0 (`add-node` workload)

The mini experiments have been tested on a 32 GB / 8-core Ubuntu 20.04 host, where each run completes in approximately 20 minutes ([video](https://github.com/ucd-plse/scalability/releases/tag/replication-package)). Other platforms might also work but have not been validated.

To run the experiments, start a container from the corresponding image and execute the `run.sh` script with the appropriate arguments. For example, to run the mini-scale experiment for CASSANDRA-3.11.0, start the container:

```bash
docker run -it --pull always ucdavisplse/scalelens:CA-3.11.0-mini
```

This drops you into a shell at `/home/scaleview/scaleview-core` inside the container. From inside the container, run:

```bash
bash run.sh ./experiments/CA-3.11.0.yaml CA-3.11.0-workspace
```

After the experiment completes, you can find the results in the `CA-3.11.0-workspace/` directory inside the container:
- `sdeps.json`
- `sdeps-patterns.json`
- `statistics.txt`


Likewise, to run mini-scale experiments for HDFS-3.1.0, start the container:

```bash
docker run -it --pull always ucdavisplse/scalelens:HD-3.1.0-mini
```

From inside the container, run:

```bash
bash run.sh ./experiments/HD-3.1.0.yaml HD-3.1.0-workspace
```

And for IGNITE-2.8.0, start the container:

```bash
docker run -it --pull always ucdavisplse/scalelens:IG-2.8.0-mini
```

From inside the container, run:

```bash
bash run.sh ./experiments/IG-2.8.0.yaml IG-2.8.0-workspace
```
