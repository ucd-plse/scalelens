# Cassandra Workloads

The following table describes the name, scaling dimensions, and description of the Cassandra workloads.
| Name                        | # Scaling Dimensions | Scaling Dimensions | Description                                                                         |
|-----------------------------|----------------------|--------------------|-------------------------------------------------------------------------------------|
| `w-add-ks.sh`               | 1                    | keyspace           | adds keyspace to a single-node cluster                                              |
| `w-add-node.sh`             | 1                    | node               | adds nodes to a cluster                                                             |
| `w-add-ks-snapshot.sh`      | 1                    | keyspace           | adds keyspace to a single-node cluster and takes a snapshot                         |
| `w-add-ks-compact.sh`       | 1                    | keyspace           | adds keyspace to a single-node cluster and runs compaction                          |
| `w-add-node-tok.sh`         | 2                    | node & token       | add nodes to a cluster and assigns tokens                                           |
| `w-add-row.sh`              | 1                    | row                | adds rows (by batches) to a table                                                   |
| `w-add-row-scrub.sh`        | 1                    | row                | adds rows (by batches) to a table and runs scrub                                    |
| `w-add-dc-tok.sh`           | 2                    | datacenter & token | adds datacenter to a cluster and assigns tokens                                     |
| `w-add-dc-tok-kill.sh`      | 2                    | datacenter & token | adds datacenter to a cluster and assigns tokens then kills the node                 |
| `w-add-dc-tok-decomm.sh`    | 2                    | datacenter & token | adds datacenter to a cluster and assigns tokens then forcely decommissions the node |
| `w-add-table.sh`            | 1                    | table              | adds a table to a keyspace                                                          |
| `w-add-table-snapshot.sh`   | 1                    | table              | adds a table to a keyspace and takes a snapshot                                     |
| `w-add-table-compact.sh`    | 1                    | table              | adds a table to a keyspace and runs compaction                                      |
| `w-add-table-row.sh`        | 2                    | table & row        | adds a table and adds rows to the table                                             |
| `w-add-table-row-repair.sh` | 2                    | table & row        | adds a table and adds rows to the table then runs repair                            |