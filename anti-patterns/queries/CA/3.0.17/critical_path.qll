predicate isCritical(string fullyQualifiedName) {
  fullyQualifiedName in [
      "org.apache.cassandra.cql3.QueryProcessor.process",
      "org.apache.cassandra.cql3.statements.BatchStatement.execute",
      "org.apache.cassandra.cql3.statements.SelectStatement.execute",
      "org.apache.cassandra.cql3.statements.UpdateStatement.execute",
      "org.apache.cassandra.repair.consistent.LocalSessions.start",
      "org.apache.cassandra.repair.consistent.LocalSessions.stop",
      "org.apache.cassandra.utils.MergeIterator$ManyToOne.advance",
      "org.apache.cassandra.db.compaction.CompactionManager.performCompaction",
      "org.apache.cassandra.db.compaction.CompactionTask.runMayThrow",
      "org.apache.cassandra.db.Memtable.flush", "org.apache.cassandra.db.Memtable.put",
      "org.apache.cassandra.db.Mutation.apply",
      "org.apache.cassandra.db.partitions.PartitionUpdate.apply",
    ]
}
