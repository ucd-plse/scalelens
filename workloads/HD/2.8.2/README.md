# HDFS Workloads

The following table describes the name, scaling dimensions, and description of the HDFS workloads.
| Name                        | # Scaling Dimensions | Scaling Dimensions | Description                                                     |
| --------------------------- | -------------------- | ------------------ | --------------------------------------------------------------- |
| `w-add-data-snap.sh`        | 1                    | data               | adds data block to a single-namenode + single datanode cluster  |
| `w-add-dn-block.sh`         | 2                    | datanode & data    | adds datanodes to a cluster and push data blocks                |
| `w-add-dn-file-snapdiff.sh` | 2                    | datanode & data    | adds datanode to a cluster, push data blocks, and do a snapdiff |