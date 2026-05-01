## utilities to communicate with a cassandra cluster
from cassandra import ConsistencyLevel
from cassandra.cluster import Cluster
from cassandra.query import SimpleStatement
from cassandra.query import BatchStatement
import argparse
import sys
import json
import os
import random

this_file = os.path.basename(__file__)
RET_OK = 0
RET_ERROR = 1
DEFAUTL_TO_SEC = 500

def log(text):
  print('[{:s}] {:s}'.format(this_file, text))

def drop_keyspace(keyspace):
  return """ DROP KEYSPACE IF EXISTS %s; """ % (keyspace)

def create_keyspace(keyspace, replication):
  return """ CREATE KEYSPACE IF NOT EXISTS %s WITH replication = {'class':'SimpleStrategy', 'replication_factor' : %d}; """ % (keyspace, replication)

def alter_table(keyspace, table):
  tbl_template = """ALTER TABLE %s.%s WITH compaction = { 'class' : 'LeveledCompactionStrategy' };""" % (keyspace, table)
  log(tbl_template)
  return tbl_template

def create_int_table(keyspace, table, num_cols):
  tbl_template = """CREATE TABLE %s.%s (id_column INT PRIMARY KEY,""" % (keyspace, table)
  i = 0
  while i < num_cols:
    tbl_template = tbl_template + (('column_%d INT, ' % i) if i != (num_cols - 1) else ('column_%d INT' % i))
    i = i + 1
  tbl_template = tbl_template + ');'
  return tbl_template


def insert_int_table(keyspace, table, id, num_cols):
  insert_template = """ INSERT INTO %s.%s (id_column, """ % (keyspace, table)
  i = 0
  while i < num_cols:
    insert_template = insert_template + (('column_%d, ' % i) if i != (num_cols - 1) else ('column_%d' % i))
    i = i + 1
  insert_template = insert_template + (') values (%d, ' % id)
  i = 0
  while i < num_cols:
    insert_template = insert_template + (('%d, ' % (random.randint(0, 100001))) if i != (num_cols - 1) else ('%d' % (random.randint(0, 100001))))
    i = i + 1
  insert_template = insert_template + ');'
  return insert_template

def connect(ip_list, connect_to_sec):
  try:
    ## create the cluster
    cluster = Cluster(ip_list, connect_timeout=connect_to_sec)
    ## and then connect
    session = cluster.connect()
    ## if this throws no exception, i guess we are fine
    return (cluster, session)
  except Exception as e:
    log('Exception when connecting to nodes: [{}]'.format(e))
    return None, None

## shutdown as instructed in the api...
def shutdown(cluster):
  try:
    cluster.shutdown()
  except Exception as e:
    log('Could not close session: [{}]'.format(e))

def do_alter_table(ip_list, keyspace, table):
  ## lets try to connect first
  cluster = None
  session = None
  statements = []
  ## build the list of queries
  statements.append(SimpleStatement(alter_table(keyspace, table), consistency_level=ConsistencyLevel.QUORUM))
  ## open
  cluster, session = connect(ip_list, DEFAUTL_TO_SEC)
  if session is None:
    return RET_ERROR
  ## connected, lets execute
  log('Altering table [{:s}.{:s}]'.format(keyspace, table))
  for statement in statements:
    ## this one does NOT raise an exception when successfull....
    try:
      log('Issuing: [{}]'.format(statement))
      result = session.execute(statement, timeout=DEFAUTL_TO_SEC)
    except Exception as e:
      log('Exception when issuing query: {}'.format(e))
      return RET_ERROR
  ## and finally, close everything
  shutdown(cluster)
  return RET_OK


def do_create_int_table(ip_list, keyspace, replication, table, num_cols, drop_ks, new_ks):
  ## lets try to connect first
  cluster = None
  session = None
  statements = []
  ## build the list of queries
  if drop_ks is True:
    statements.append(SimpleStatement(drop_keyspace(keyspace), consistency_level=ConsistencyLevel.QUORUM))
  if new_ks is True:
    statements.append(SimpleStatement(create_keyspace(keyspace, replication), consistency_level=ConsistencyLevel.QUORUM))
  ## add the create table statement
  statements.append(SimpleStatement(create_int_table(keyspace, table, num_cols), consistency_level=ConsistencyLevel.QUORUM))
  ## open
  cluster, session = connect(ip_list, DEFAUTL_TO_SEC)
  if session is None:
    return RET_ERROR
  ## connected, lets execute
  log('Creating table [{:s}] on ks [{:s}] with [{:d}] INT columns and RF [{:d}]'.format(keyspace, table, num_cols, replication))
  for statement in statements:
    ## this one does NOT raise an exception when successfull....
    try:
      log('Issuing: [{}]'.format(statement))
      result = session.execute(statement, timeout=DEFAUTL_TO_SEC)
    except Exception as e:
      log('Exception when issuing query: {}'.format(e))
      return RET_ERROR
  ## and finally, close everything
  shutdown(cluster)
  return RET_OK

def do_create_ks(ip_list, keyspace, replication):
  ## lets try to connect first
  cluster = None
  session = None
  statements = []
  ## build the list of queries
  statements.append(SimpleStatement(create_keyspace(keyspace, replication), consistency_level=ConsistencyLevel.QUORUM))
  ## open
  cluster, session = connect(ip_list, DEFAUTL_TO_SEC)
  if session is None:
    return RET_ERROR
  ## connected, lets execute
  log('Creating ks [{:s}] with RF [{:d}]'.format(keyspace, replication))
  for statement in statements:
    ## this one does NOT raise an exception when successfull....
    try:
      log('Issuing: [{}]'.format(statement))
      result = session.execute(statement, timeout=DEFAUTL_TO_SEC)
    except Exception as e:
      log('Exception when issuing query: {}'.format(e))
      return RET_ERROR
  ## and finally, close everything
  shutdown(cluster)
  return RET_OK

def do_insert_simple(ip_list, keyspace, table, replication, num_cols, next_id, batch_size, consistency, setup):
  ## first, see if we need to setup
  if setup is True:
    ret = do_create_int_table(ip_list, keyspace, replication, table, num_cols, True, True)
    if ret == RET_ERROR:
      log('Error when setting up table, exiting...')
      return RET_ERROR
  ## open
  cluster, session = connect(ip_list, DEFAUTL_TO_SEC)
  if session is None:
    return RET_ERROR
  ## now, insert some rows...
  batch = BatchStatement(consistency_level=consistency)
  i = 0
  while i < batch_size:
    batch.add(SimpleStatement(insert_int_table(keyspace, table, next_id + i, num_cols)))
    i = i + 1
  ## and go ahead...
  try:
    log('Inserting [{:d}] rows into [{:s}.{:s}]'.format(batch_size, keyspace, table))
    result = session.execute(batch, timeout=DEFAUTL_TO_SEC)
    ## TODO: sO THIS JUST WONT THROW AN EXCEPTION IF ITS OK?
  except:
    log('Could not connect to cluster with [{:d}] nodes, exiting...'.format(this_file, len(ip_list)))
    return RET_ERROR
  ## should be done here
  shutdown(cluster)
  return RET_OK

## main function here...
if __name__ == "__main__":
  ## arguments
  arg_parser = argparse.ArgumentParser(prog=this_file, description='Executes cassandra CQL workloads')
  arg_parser.add_argument('IPList', metavar='ip_list', type=str, help='Comma sep. list of ips to wich the session will be created.')
  arg_parser.add_argument('WorkloadName', metavar='workload', type=str, help='The name of workload to execute.')
  arg_parser.add_argument('WorkloadArgs', metavar='workload_args', type=str, help='The arguments for the workload, formatted as json string.')
  ## parse the arguments
  args = arg_parser.parse_args()
  ## and execute
  ip_str = args.IPList
  ip_list = ip_str.split(',')
  workload = args.WorkloadName
  params = json.loads(args.WorkloadArgs)
  log('Executing [{:s}] with args [{}] to [{:s}]'.format(workload, params, ip_str))
  ret = 0
  if workload == 'create_simple_table':
    ## here, we expect the table name, the keyspace name
    ret = do_create_int_table(ip_list, params['ks'], params['repfactor'], params['table'], params['num_cols'], params['drop_ks'], params['new_ks'])
  elif workload == 'create_simple_ks':
    ## here, we expect the table name, the keyspace name
    ret = do_create_ks(ip_list, params['ks'], params['repfactor'])
  elif workload == 'insert_simple_rows':
    ## here, we expect the table name, the keyspace name
    ret = do_insert_simple(ip_list, 'scale', 'test', 1, 1, params['next_id'], params['num_rows'], ConsistencyLevel.QUORUM, params['do_setup'])
  elif workload == 'insert_rows_to':
    ## here, we expect the table name, the keyspace name
    ret = do_insert_simple(ip_list, params['ks'], params['tbl'], 1, 1, params['next_id'], params['num_rows'], ConsistencyLevel.QUORUM, False)
  elif workload == 'alter_table':
    ret = do_alter_table(ip_list, params['ks'], params['tbl'])
  elif workload == 'insert_local_consistency_rows':
    ## here, we expect the table name, the keyspace name
    ret = do_insert_simple(ip_list, 'scale', 'test', params['replication_factor'], 1, params['next_id'], params['num_rows'], ConsistencyLevel.ONE, params['do_setup'])
  else:
    log('Workload [{:s}] does not exists, exiting...'.format(workload))
    ret = RET_ERROR
  ## and done...
  log('Exiting with code [{:d}]'.format(ret))
  sys.exit(ret)
