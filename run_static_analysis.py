# this script is to be invoked by run.sh 
# after we have sdeps.json in the working space and 
# 1. do pre-check for the existence of sdeps.json, codeql database, and codeql query files
# 2. run static analysis with codeql
# 3. parse the output of codeql and save the results into the working space

import os
import sys
import logging
import argparse
import getopt
import subprocess
import yaml
import json

from collections import defaultdict

def _run_command(command, cwd=None):
    if cwd:
        process = subprocess.Popen(
            command, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True)
    else:
        process = subprocess.Popen(
            command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True)
    stdout, stderr = process.communicate()
    stdout = stdout.decode('utf-8').strip()
    stderr = stderr.decode('utf-8').strip()
    ok = process.returncode == 0
    return process, stdout, stderr, ok


def _pre_check(config_file, working_dir, query_base):
    if not os.path.exists(config_file):
        logging.error('Config file not found: {}'.format(config_file))
        sys.exit(1)
    if not os.path.exists(working_dir):
        logging.error('Working directory not found: {}'.format(working_dir))
        sys.exit(1)
    if not os.path.exists(query_base):
        logging.error('Query base directory not found: {}'.format(query_base))
        sys.exit(1)
    
    with open(config_file, 'r') as f:
        config = yaml.safe_load(f)

    metadata = config['experiment']
    system_id = metadata['system_id']
    version_tag = metadata['version']
    system_version = '{}-{}'.format(system_id, version_tag)
    experiment_tag = '{}-{}'.format(
        system_version, metadata['scale'])
    
    sdeps_file = os.path.join(working_dir, 'sdeps.json')
    if not os.path.exists(sdeps_file):
        logging.error('sdeps.json not found: {}'.format(sdeps_file))
        sys.exit(1)

    ql_db_dir = os.path.join(query_base, 'databases', system_version)
    if not os.path.exists(ql_db_dir):
        logging.error('CodeQL database not found: {}'.format(ql_db_dir))
        sys.exit(1)
    
    ql_query_dir = os.path.join(query_base, 'queries', system_id, version_tag)
    if not os.path.exists(ql_query_dir):
        logging.error('CodeQL query directory not found: {}'.format(ql_query_dir))
        sys.exit(1)
    
    logging.info('Pre-check passed.')
    return True

def read_dcf_list(working_dir):
    sdeps_file = os.path.join(working_dir, 'sdeps.json')
    if not os.path.exists(sdeps_file):
        logging.error('sdeps.json not found: {}'.format(sdeps_file))
        sys.exit(1)
    
    with open(sdeps_file, 'r') as f:
        sdeps = json.load(f)
    
    dcf_list =[]
    for item in sdeps:
        dcf_list.append(item['methodName'])
    
    return dcf_list

def process_lambda_methods(dcf):
    if 'lambda$' in dcf:
        dcf = dcf.replace('lambda$', '')
        dcf = dcf.split('$')[0]
    return dcf

def build_qll_file(dcf_list, ql_query_dir):
    qll_file = os.path.join(ql_query_dir, 'dimensional.qll')
    if os.path.exists(qll_file):
        os.remove(qll_file)
    
    content = 'predicate isDimensional(string fullyQualifiedName) {\n'
    content += '  fullyQualifiedName in [\n'
    content += ',\n'.join('      "{}"'.format(process_lambda_methods(dcf)) for dcf in dcf_list)
    content += '  ]\n'
    content += '}\n'

    with open(qll_file, 'w') as f:
        f.write(content)
    
    return qll_file

def _codeql_parser(ql_results):
    ql_results = ql_results.splitlines()
    ql_results = ql_results[2:]
    methods = []
    for line in ql_results:
        line = line.strip()
        if line.startswith('|'):
            method = line.split('|')[1].strip()
            methods.append(method)
    return methods


def _codeql_query(ql_file, db_path, cwd=None):
    command = f'codeql query run --database={db_path} {ql_file}'
    process, stdout, stderr, ok = _run_command(command, cwd=cwd)
    if not ok:
        raise Exception(f'Error running CodeQL query: {stderr}')
    return stdout 

def run_static_analysis(patterns: list[str], config_file: str, working_dir: str, query_base: str):

    with open(config_file, 'r') as f:
        config = yaml.safe_load(f)

    metadata = config['experiment']
    system_id = metadata['system_id']
    version_tag = metadata['version']
    system_version = '{}-{}'.format(system_id, version_tag)
    experiment_tag = '{}-{}'.format(system_version, metadata['scale'])
    
    ql_query_dir = os.path.join(query_base, 'queries', system_id, version_tag)
    ql_db_dir = os.path.join(query_base, 'databases', system_version)
    
    dcf_list = read_dcf_list(working_dir)
    logging.info(f'Building QLL file with {len(dcf_list)} DCFs found for {system_version}')
    qll_file = build_qll_file(dcf_list, ql_query_dir)

    analysis_results = {} # pattern -> list of methods

    for pattern in patterns:
        ql_file = os.path.join(ql_query_dir, f'{pattern}.ql')
        logging.info(f'Running query {ql_file} on database {ql_db_dir}')
        ql_results = _codeql_query(ql_file, ql_db_dir)
        methods = _codeql_parser(ql_results)
        analysis_results[pattern] = methods
    
    return analysis_results
    

def write_analysis_results(analysis_results: dict, working_dir: str):
    sdeps_file = os.path.join(working_dir, 'sdeps.json')
    if not os.path.exists(sdeps_file):
        logging.error('sdeps.json not found: {}'.format(sdeps_file))
        sys.exit(1)
    
    with open(sdeps_file, 'r') as f:
        sdeps = json.load(f)
    
    for item in sdeps:
        dcf = item['methodName']
        item['antiPatterns'] = []
        for pattern in analysis_results:
            if dcf in analysis_results[pattern] or process_lambda_methods(dcf) in analysis_results[pattern]:
                item['antiPatterns'].append(pattern)
    
    output_file = os.path.join(working_dir, 'sdeps-patterns.json')
    with open(output_file, 'w') as f:
        json.dump(sdeps, f, indent=4)
    
    logging.info(f'Analysis results written to {output_file}')
    return True


def generate_statistics(working_dir: str):

    sdeps_file = os.path.join(working_dir, 'sdeps-patterns.json')
    if not os.path.exists(sdeps_file):
        logging.error('sdeps-patterns.json not found: {}'.format(sdeps_file))
        sys.exit(1)
    
    with open(sdeps_file, 'r') as f:
        sdeps = json.load(f)

    num_total_dcfs = 0
    num_dcfs_with_patterns = 0
    num_patterns_to_num_dcfs = defaultdict(int)
    dcfs_list = []
    unique_dcfs_with_patterns = set()

    for item in sdeps:
        dcf = item['methodName']
        patterns = item['antiPatterns']
        num_patterns = len(patterns)
        num_total_dcfs += 1
        if num_patterns > 0:
            num_dcfs_with_patterns += 1
            if dcf not in unique_dcfs_with_patterns:
                unique_dcfs_with_patterns.add(dcf)
        num_patterns_to_num_dcfs[num_patterns] += 1
        dcfs_list.append(dcf)
    
    dcfs_list = list(set(dcfs_list))
    
    output_file = os.path.join(working_dir, 'statistics.txt')
    with open(output_file, 'w') as f:
        f.write(f'Total DCFs: {num_total_dcfs}\n')
        f.write(f'DCFs with patterns: {num_dcfs_with_patterns}\n')
        for num_patterns, num_dcfs in num_patterns_to_num_dcfs.items():
            f.write(f'{num_patterns} patterns: {num_dcfs}\n')
        f.write(f'Deduplicated DCFs: {len(dcfs_list)}\n')
        f.write(f'Deduplicated DCFs with patterns: {len(unique_dcfs_with_patterns)}\n')
    return True
    

def _print_usage(msg=None):
    if msg:
        logging.error(msg)

    print('Usage: python run_static_analysis OPTIONS')
    print('Options:')
    print('  -c, --config=CONFIG_FILE    Configuration file for the experiment')
    print('  -d, --working-dir=WORKING_DIR    Working directory for the experiment')
    print('  -q, --query-base=QUERY_BASE   Query base directory')


def _validate_input(argv):
    short_options = 'c:d:q:'
    long_options = ['config=', 'working-dir=', 'query-base=']
    try:
        opts, args = getopt.getopt(argv, short_options, long_options)
    except getopt.GetoptError as e:
        _print_usage(str(e))
        sys.exit(2)

    config_file = None
    working_dir = None
    query_base = None

    for opt, arg in opts:
        if opt in ('-c', '--config'):
            config_file = arg
        elif opt in ('-d', '--working-dir'):
            working_dir = arg
        elif opt in ('-q', '--query-base'):
            query_base = arg

    return config_file, working_dir, query_base


def main(argv=None):
    argv = argv or sys.argv[1:]

    logging.basicConfig(level=logging.INFO,
                        format='%(asctime)s %(levelname)-8s %(message)s',
                        datefmt='%Y-%m-%d %H:%M:%S')
    
    config_file, working_dir, query_base = _validate_input(argv)

    logging.info('Running static analysis with config file: {}, working directory: {}, query base: {}'.format(config_file, working_dir, query_base))

    _pre_check(config_file, working_dir, query_base)
    patterns = ['compute-app', 'compute-cross', 'compute-sync', 'unbound-allocation', 'unbound-collection', 'unbound-os']
    analysis_results = run_static_analysis(patterns, config_file, working_dir, query_base)
    write_analysis_results(analysis_results, working_dir)
    generate_statistics(working_dir)
    return True


if __name__ == '__main__':
    sys.exit(main())
    
    