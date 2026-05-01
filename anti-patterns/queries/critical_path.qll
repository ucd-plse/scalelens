predicate isCritical(string fullyQualifiedName) {
  fullyQualifiedName in [
      "org.apache.cassandra.cql3.QueryProcessor.process",
      "org.apache.cassandra.cql3.statements.BatchStatement.execute",
      "org.apache.cassandra.cql3.statements.SelectStatement.execute",
      "org.apache.cassandra.cql3.statements.UpdateStatement.execute",
      "org.apache.cassandra.db.compaction.CompactionManager.performCompaction",
      "org.apache.cassandra.db.compaction.CompactionTask.runMayThrow",
      "org.apache.cassandra.db.Memtable.flush", "org.apache.cassandra.db.Memtable.put",
      "org.apache.cassandra.db.Mutation.apply",
      "org.apache.cassandra.db.partitions.PartitionUpdate.apply",
    ] or
  fullyQualifiedName in [
      "org.apache.hadoop.hdfs.server.namenode.FSDirAttrOp.setReplication",
      "org.apache.hadoop.hdfs.server.namenode.FSDirDeleteOp.delete",
      "org.apache.hadoop.hdfs.server.namenode.FSDirAppendOp.appendFile",
      "org.apache.hadoop.hdfs.server.namenode.FSDirConcatOp.concat",
      "org.apache.hadoop.hdfs.server.namenode.FSDirRenameOp.renameTo",
      "org.apache.hadoop.hdfs.server.namenode.FSNamesystem.handleHeartbeat",
      "org.apache.hadoop.hdfs.server.namenode.FSNamesystem.registerDatanode",
      "org.apache.hadoop.hdfs.server.namenode.ha.EditLogTailer.doTailEdits"
    ]
}
