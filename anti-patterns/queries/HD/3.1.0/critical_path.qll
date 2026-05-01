predicate isCritical(string fullyQualifiedName) {
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
