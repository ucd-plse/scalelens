import subprocess
import os
import sys
import logging


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


def _make_ql_file(method, ql_file):
    query_path = os.path.dirname(ql_file)
    base_ql_file = os.path.basename(ql_file)
    target_ql_file = f'{method}-{base_ql_file}'
    target_ql_file = target_ql_file.replace('$', '.')
    target_ql_file = os.path.join(query_path, target_ql_file)

    copy_command = f'cp {ql_file} {target_ql_file}'
    _run_command(copy_command)

    # replace @DCF@ with actual method
    sed_command = f"sed -i 's/@DCF@/{method}/g' {target_ql_file}"
    _run_command(sed_command)

    return target_ql_file


def _get_connection_lists(dimensional_methods, db_path, query_path):
    ql_file_list_connection = 'list-connections.ql'
    ql_file_list_connection = os.path.join(query_path, ql_file_list_connection)

    connected_lists = []

    for idx, method in enumerate(dimensional_methods):
        logging.info(
            f'Processing method {idx+1}/{len(dimensional_methods)}: {method}')
        connected_dimensional = []
        connection_ql_file = _make_ql_file(method, ql_file_list_connection)
        ql_stdout = _codeql_query(
            connection_ql_file, db_path, query_path)
        connected_methods = _codeql_parser(ql_stdout)
        for connected_method in connected_methods:
            if connected_method in dimensional_methods:
                connected_dimensional.append(connected_method)

        connected_lists.append(connected_dimensional)
        os.remove(connection_ql_file)

    return connected_lists


def _merge_lists_by_connections(list_of_lists):
    merged = []

    for lst in list_of_lists:
        lst_set = set(lst)
        merged_with = []

        for other_set in merged:
            if lst_set & other_set:
                lst_set |= other_set
            else:
                merged_with.append(other_set)

        merged_with.append(lst_set)
        merged = merged_with

    return [list(s) for s in merged]


def static_analysis(pattern: str, system_tag: str):
    logging.info(
        f'Running static analysis for {pattern} with DCFs from {system_tag}')

    system_tag_lower = system_tag.replace('.', '_').replace('-', '_')

    template_ql_file = os.path.join(os.getcwd(), 'queries', f'{pattern}.ql')
    dimensional_qll_file = f'dimensional_{system_tag_lower}'
    ql_file = os.path.join(os.getcwd(), 'queries',
                           f'{pattern}-{system_tag_lower}.ql')
    query_path = os.path.join(os.getcwd(), 'queries')
    qldb_path = os.path.join(os.getcwd(), 'databases', system_tag)

    copy_command = f'cp {template_ql_file} {ql_file}'
    _run_command(copy_command)
    sed_command = f"sed -i 's/@DCFLIB@/{dimensional_qll_file}/g' {ql_file}"
    _run_command(sed_command)

    ql_stdout = _codeql_query(ql_file, qldb_path, query_path)

    output_file = f'{system_tag}-{pattern}.txt'
    logging.info(
        f'DCFs with {pattern} anti-pattern in {system_tag} ({output_file}):')
    print(ql_stdout)
    with open(output_file, 'w') as f:
        f.write(ql_stdout)

    GROUP = False
    if GROUP:
        logging.info(
            f'Aggregating DCFs with {pattern} anti-pattern in {system_tag}')
        dcf_list = _codeql_parser(ql_stdout)
        connected_lists = _get_connection_lists(dcf_list, qldb_path, query_path)
        merged_groups = _merge_lists_by_connections(connected_lists)

        output_file = f'{system_tag}-{pattern}-aggregated.txt'
        logging.info(
            f'Aggregated DCFs with {pattern} anti-pattern in {system_tag} ({output_file})')
        with open(output_file, 'w') as f:
            for idx, group in enumerate(merged_groups):
                f.write(f'Group {idx+1}:\n')
                for dcf in group:
                    f.write(f'{dcf}\n')
                    print(dcf)
                print()
                f.write('\n')

    os.remove(ql_file)
    logging.info(
        f'Finished static analysis for {pattern} with DCFs from {system_tag}')


def _print_usage(msg=None):
    if msg:
        logging.error(msg)
    print('Usage: python analysis.py <pattern> <system_tag>')
    print('Example: python analysis.py compute-sync CA-3.11.0')
    sys.exit(1)


def _validate_input(argv):
    if len(argv) != 2:
        _print_usage('Invalid number of arguments')
    pattern = argv[0]
    system_tag = argv[1]
    return pattern, system_tag


def main(argv=None):
    argv = argv or sys.argv[1:]

    logging.basicConfig(level=logging.INFO,
                        format='%(asctime)s %(levelname)-8s %(message)s',
                        datefmt='%Y-%m-%d %H:%M:%S')

    pattern, system_tag = _validate_input(argv)
    static_analysis(pattern, system_tag)


if __name__ == '__main__':
    sys.exit(main())
