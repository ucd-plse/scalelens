<?xml version="1.0" encoding="UTF-8"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
<configuration>
 <property>
    <name>dfs.datanode.ipc.address</name>
    <value>@DN_IP:@IPC_PORT</value>
 </property>
 <property>
    <name>dfs.datanode.address</name>
    <value>@DN_IP:@SERVICE_PORT</value>
 </property>
  <property>
    <name>dfs.datanode.data.dir</name>
    <value>@DATA_DIR</value>
  </property>
  <property>
    <name>dfs.datanode.http.address</name>
    <value>@DN_IP:@HTTP_PORT</value>
  </property>
</configuration>