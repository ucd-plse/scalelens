
import os
import sys
import shutil
import yaml
import logging
import subprocess
import getopt


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


def extract_tarfile(file_path: str, dest_dir: str = None):
    if dest_dir is None:
        dest_dir = file_path.replace(".tar.gz", "")

    if os.path.exists(dest_dir):
        logging.warning(
            "Directory already exists, overriding: {}".format(dest_dir))
        shutil.rmtree(dest_dir)
    os.makedirs(dest_dir)

    command = "tar -xvzf {} -C {} --strip-components 1".format(
        file_path, dest_dir)
    process, stdout, stderr, ok = _run_command(command)
    if not ok:
        logging.error("Failed to extract tar file: {}".format(file_path))
        logging.error("stdout: {}".format(stdout))
        logging.error("stderr: {}".format(stderr))
        return False
    logging.debug("Extracted tar file: {}".format(file_path))
    file_list = os.listdir(dest_dir)
    return file_list


def pre_processing(config_file):

    trace_dir_list = []

    with open(config_file, 'r') as f:
        config = yaml.safe_load(f)

    metadata = config['experiment']
    experiment_slug = '{}-{}-{}'.format(
        metadata['system_id'], metadata['version'], metadata['scale'])
    main_tarfile = '{}-traces.tar.gz'.format(experiment_slug)
    all_finished_workloads = extract_tarfile(main_tarfile, experiment_slug)

    for workload_name, workload_details in config['workloads'].items():
        found = False
        run_or_not = workload_details['run']
        if run_or_not == False:
            continue
        for workload_tarfile in all_finished_workloads:
            if not workload_tarfile.endswith('.tar.gz'):
                # skip .log files
                continue
            workload_name_from_tarfile = '-'.join(
                workload_tarfile.split('-')[:-1])
            if workload_name_from_tarfile == workload_name:
                found = True
                logging.debug('Found tarfile {} for workload {}, extracting.'.format(
                    workload_tarfile, workload_name))
                workload_tarfile_path = os.path.join(
                    experiment_slug, workload_tarfile)
                extract_tarfile(workload_tarfile_path, workload_name)
                trace_dir_list.append(workload_name)
                break
        if not found:
            logging.warning(
                'No tarfile found for workload {}'.format(workload_name))

    logging.info('Pre-processing done for: {}'.format(trace_dir_list))
    return trace_dir_list


def event_parsing(config_file, trace_dir_list):

    _, stdout, stderr, ok = _run_command('git rev-parse --show-toplevel')
    if not ok:
        logging.error("Failed to get git root directory")
        logging.error("stdout: {}".format(stdout))
        logging.error("stderr: {}".format(stderr))

    repo_dir = stdout.strip()
    event_parsing_dir = os.path.join(repo_dir, 'analysis', 'eventparsing')

    result_dir_list = []

    with open(config_file, 'r') as f:
        config = yaml.safe_load(f)

    metadata = config['experiment']
    scale_size = metadata['scale']
    trace_locations = metadata['trace_location']
    if not isinstance(trace_locations, list):
        trace_locations = [trace_locations]
    loop_or_method = 'L'
    experiment_slug = '{}-{}-{}'.format(
        metadata['system_id'], metadata['version'], metadata['scale'])

    for trace_dir in trace_dir_list:
        workload_name = trace_dir
        mapping_file_location = None
        for file in os.listdir(trace_dir):
            if file.endswith('.nm'):
                mapping_file_location = os.path.join(
                    os.getcwd(), trace_dir, file)
                break
        trace_file_locations = []
        for trace_location in trace_locations:
            trace_file_location = os.path.join(
                os.getcwd(), trace_dir, trace_location, 'traces')
            trace_file_locations.append(trace_file_location)
        trace_file_location = ','.join(trace_file_locations)
        commands = ['bash', 'event-parsing.sh', trace_file_location,
                    loop_or_method, workload_name, str(scale_size), mapping_file_location]
        cmd = ' '.join(commands)

        logging.info("Running event parsing for: {}".format(workload_name))
        process, stdout, stderr, ok = _run_command(cmd, cwd=event_parsing_dir)
        if not ok:
            logging.error(
                "Failed to run event parsing for workload: {}".format(workload_name))
            logging.error("stdout: {}".format(stdout))
            logging.error("stderr: {}".format(stderr))
            return False

        if os.path.exists(os.path.join(event_parsing_dir, workload_name)):
            shutil.rmtree(os.path.join(os.getcwd(), workload_name))
            shutil.move(os.path.join(event_parsing_dir, workload_name),
                        os.path.join(os.getcwd(), workload_name))
            result_dir_list.append(workload_name)
        else:
            logging.warning(
                'Event parsing generated no output for: {}'.format(workload_name))

    logging.info('Event parsing done for: {}'.format(result_dir_list))
    return result_dir_list


def event_filtering(config_file, event_parsing_dirs):

    _, stdout, stderr, ok = _run_command('git rev-parse --show-toplevel')
    if not ok:
        logging.error("Failed to get git root directory")
        logging.error("stdout: {}".format(stdout))
        logging.error("stderr: {}".format(stderr))

    repo_dir = stdout.strip()
    event_filtering_dir = os.path.join(repo_dir, 'analysis', 'eventfiltering')

    result_dir_list = []

    with open(config_file, 'r') as f:
        config = yaml.safe_load(f)

    metadata = config['experiment']
    scale_size = metadata['scale']
    system_id = metadata['system_id']
    version = metadata['version']
    experiment_slug = '{}-{}-{}'.format(
        metadata['system_id'], metadata['version'], metadata['scale'])

    for workload_name, workload_details in config['workloads'].items():
        run_or_not = workload_details['run']
        if run_or_not == False:
            continue
        if workload_name not in event_parsing_dirs:
            logging.warning(
                'No event parsing output found for: {}'.format(workload_name))
            continue

        num_dimensions = workload_details['number_of_dimensions']
        dimensions = workload_details['dimensions']

        for event_type in ['loop', 'method']:
            arg_dimension = workload_name
            arg_data = os.path.join(
                os.getcwd(), workload_name, event_type, '{}-data'.format(scale_size))
            arg_dir = os.path.join(os.getcwd(), workload_name, event_type)
            arg_dimension_list = ','.join(dimensions + ['iteration'])
            arg_csv = os.path.join(os.getcwd(), '{}-{}.csv'.format(
                system_id, version))

            if not os.path.exists(arg_data):
                logging.warning('No data file found for: {}'.format(workload_name))
                continue

            if not os.path.exists(arg_csv):
                logging.warning('No csv file for application loops found')
                continue

            commands = ['python', '{}/curve_classifier.py'.format(event_filtering_dir), 'complexity', arg_data, arg_dir, 'frechet_dist', str(
                scale_size), str(num_dimensions), arg_dimension_list, arg_csv]

            cmd = ' '.join(commands)

            logging.info("Running {} event filtering for: {}".format(event_type, workload_name))
            process, stdout, stderr, ok = _run_command(cmd)
            if not ok:
                logging.error(
                    "Failed to run event filtering for workload: {}".format(workload_name))
                logging.error("stdout: {}".format(stdout))
                logging.error("stderr: {}".format(stderr))
                return False

        result_dir_list.append(workload_name)

    logging.info('Event filtering done for: {}'.format(result_dir_list))
    return result_dir_list


def event_merging(config_file, event_filtering_dirs):

    _, stdout, stderr, ok = _run_command('git rev-parse --show-toplevel')
    if not ok:
        logging.error("Failed to get git root directory")
        logging.error("stdout: {}".format(stdout))
        logging.error("stderr: {}".format(stderr))

    repo_dir = stdout.strip()
    event_filtering_dir = os.path.join(repo_dir, 'analysis', 'eventfiltering')

    with open(config_file, 'r') as f:
        config = yaml.safe_load(f)

    metadata = config['experiment']
    scale_size = metadata['scale']
    experiment_slug = '{}-{}-{}'.format(
        metadata['system_id'], metadata['version'], metadata['scale'])

    # merging results to get loops from all workload results
    result_dir = os.getcwd()
    element_location = os.path.join('loop', 'frechet_dist', 'elements.json')
    arguments = [os.path.join(result_dir, workload_name, element_location)
                 for workload_name in event_filtering_dirs]
    element_location = os.path.join('method', 'frechet_dist', 'elements.json')
    arguments += [os.path.join(result_dir, workload_name, element_location)
                 for workload_name in event_filtering_dirs]
    commands = ['python', '{}/curve_classifier.py'.format(
        event_filtering_dir), 'merge', *arguments, result_dir]
    cmd = ' '.join(commands)
    logging.info("Merging results from: {}".format(event_filtering_dirs))
    process, stdout, stderr, ok = _run_command(cmd)
    if not ok:
        logging.error("Failed to merge event filtering results")
        logging.error("stdout: {}".format(stdout))
        logging.error("stderr: {}".format(stderr))
        return False

    # cleaning: remove duplicates and sublinear loops
    to_keep = ','.join(['linear', 'super-linear'])
    result_json_location = os.path.join(result_dir, 'sdeps.json')
    output_json_location = os.path.join(result_dir, 'sdeps.json.clean')
    for goal in ['Loop', 'Cross']:
        commands = ['python', '{}/curve_classifier.py'.format(
            event_filtering_dir), 'clean', result_json_location, to_keep, goal]
        cmd = ' '.join(commands)
        logging.info("Cleaning with: {}".format(goal))
        process, stdout, stderr, ok = _run_command(cmd)
        if not ok:
            logging.error("Failed to clean event filtering results")
            logging.error("stdout: {}".format(stdout))
            logging.error("stderr: {}".format(stderr))
            return False
        if os.path.exists(output_json_location):
            shutil.move(output_json_location, os.path.join(
                result_dir, '{}-{}.json'.format(experiment_slug, str.lower(goal))))
        else:
            logging.warning("No output json found after cleaning")

    logging.info('Event merging done for: {}'.format(event_filtering_dirs))
    return True


def _print_usage(msg=None):
    if msg:
        logging.error(msg)

    print('Usage: python run_trace_analysis.py OPTIONS')
    print('Options:')
    print('  -c, --config=CONFIG_FILE    Configuration file for the experiment')


def _validate_input(argv):
    short_options = 'c:'
    long_options = ['config=']
    try:
        opts, args = getopt.getopt(argv, short_options, long_options)
    except getopt.GetoptError as e:
        _print_usage(str(e))
        sys.exit(2)

    config_file = None
    for opt, arg in opts:
        if opt in ('-c', '--config'):
            config_file = arg
        else:
            _print_usage('Invalid option: {}'.format(opt))
            sys.exit(2)

    return config_file


def main(argv=None):
    argv = argv or sys.argv[1:]

    logging.basicConfig(level=logging.INFO,
                        format='%(asctime)s %(levelname)-8s %(message)s',
                        datefmt='%Y-%m-%d %H:%M:%S')

    config_file = _validate_input(argv)

    if os.path.exists(config_file):
        logging.info(
            'Running analysis with config file: {}'.format(config_file))
    else:
        logging.error('Config file not found: {}'.format(config_file))
        sys.exit(1)

    trace_dirs = pre_processing(config_file)
    event_result_dirs = event_parsing(config_file, trace_dirs)
    event_result_dirs = event_filtering(config_file, event_result_dirs)
    event_merging(config_file, event_result_dirs)

    logging.info('Analysis done.')


if __name__ == '__main__':
    sys.exit(main())
