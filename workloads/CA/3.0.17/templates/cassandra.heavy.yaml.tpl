#################################################################################
## Configurable
#################################################################################
cluster_name: 'Scale Cluster'
num_tokens: @TOKENS
hints_directory: @DATA_ROOT/hints
data_file_directories:
     - @DATA_ROOT/data
commitlog_directory: @DATA_ROOT/commitlog
saved_caches_directory: @DATA_ROOT/saved_caches
seed_provider:
    - class_name: org.apache.cassandra.locator.SimpleSeedProvider
      parameters:
          - seeds: "@SEEDS"
rpc_address: @IP
listen_address: @IP
broadcast_address: @IP
#################################################################################
## Others
#################################################################################
hinted_handoff_enabled: false
max_hint_window_in_ms: 10800000 # 3 hours
hinted_handoff_throttle_in_kb: 1024
max_hints_delivery_threads: 16
hints_flush_period_in_ms: 10000
max_hints_file_size_in_mb: 128
batchlog_replay_throttle_in_kb: 1024
authenticator: AllowAllAuthenticator
authorizer: AllowAllAuthorizer
role_manager: CassandraRoleManager
roles_validity_in_ms: 200000
permissions_validity_in_ms: 200000
partitioner: org.apache.cassandra.dht.Murmur3Partitioner
disk_failure_policy: stop
commit_failure_policy: stop
key_cache_save_period: 144000
row_cache_size_in_mb: 0
row_cache_save_period: 0
counter_cache_save_period: 7200
commitlog_sync: periodic
commitlog_sync_period_in_ms: 9000000
commitlog_segment_size_in_mb: 128
concurrent_reads: 32
concurrent_writes: 32
concurrent_counter_writes: 32
concurrent_materialized_view_writes: 32
memtable_allocation_type: heap_buffers
memtable_heap_space_in_mb: 2048
index_summary_resize_interval_in_minutes: 600
trickle_fsync: false
trickle_fsync_interval_in_kb: 10240
storage_port: 7000
ssl_storage_port: 7001
start_native_transport: true
native_transport_port: 9042
native_transport_max_threads: 16
start_rpc: false
rpc_port: 9160
rpc_keepalive: true
rpc_server_type: sync
rpc_max_threads: 16
thrift_framed_transport_size_in_mb: 32
incremental_backups: false
snapshot_before_compaction: false
auto_snapshot: false
column_index_size_in_kb: 64
compaction_throughput_mb_per_sec: 16
sstable_preemptive_open_interval_in_mb: 50
read_request_timeout_in_ms: 500000
range_request_timeout_in_ms: 100000
write_request_timeout_in_ms: 200000
counter_write_request_timeout_in_ms: 500000
cas_contention_timeout_in_ms: 100000
truncate_request_timeout_in_ms: 600000
request_timeout_in_ms: 1000000
cross_node_timeout: false
endpoint_snitch: SimpleSnitch
dynamic_snitch_update_interval_in_ms: 100 
dynamic_snitch_reset_interval_in_ms: 600000
dynamic_snitch_badness_threshold: 0.1
request_scheduler: org.apache.cassandra.scheduler.NoScheduler
internode_compression: dc
inter_dc_tcp_nodelay: false
tracetype_query_ttl: 86400
tracetype_repair_ttl: 604800
enable_user_defined_functions: false
enable_scripted_user_defined_functions: false
windows_timer_interval: 1
batch_size_warn_threshold_in_kb: 500
batch_size_fail_threshold_in_kb: 50000
unlogged_batch_across_partitions_warn_threshold: 10
compaction_large_partition_warning_threshold_mb: 100
gc_warn_threshold_in_ms: 1000
enable_materialized_views: true
