import yaml
import shutil
import time
import os
import sys
import subprocess
import logging
import getopt


def _run_command(command, cwd=None, output=None):
    # Open the file for redirecting both stdout and stderr if provided
    if output:
        logging.info('Redirecting STDOUT to file: {}'.format(output))
        outfile = open(output, 'w+')
        stdout_arg = stderr_arg = outfile
    else:
        stdout_arg = stderr_arg = subprocess.PIPE

    # Execute the command with specified working directory and output redirection
    # when cwd == None, subprocess.Popen by default uses the current working directory
    process = subprocess.Popen(
        command, cwd=cwd, stdout=stdout_arg, stderr=stderr_arg, shell=True, bufsize=1, universal_newlines=True)

    if not output:
        # If not redirecting to file, use communicate to capture stdout and stderr
        stdout, stderr = process.communicate()
        stdout = stdout.strip() if stdout else ''
        stderr = stderr.strip() if stderr else ''
    else:
        # For real-time output redirection, wait for process to complete without communicate()
        process.wait()
        outfile.close()  # Close the file after process completion
        stdout = None
        stderr = None

    ok = process.returncode == 0
    return process, stdout, stderr, ok


def pre_processing(config_file_path):
    with open(config_file_path, 'r') as f:
        config = yaml.safe_load(f)

    metadata = config['experiment']
    cluster_base = metadata['cluster_base']

    cluster_base = os.path.join(os.getcwd(), cluster_base)
    shutil.rmtree(cluster_base, ignore_errors=True)

    shutil.rmtree('instrumented', ignore_errors=True)

    os.mkdir(cluster_base)
    os.mkdir('instrumented')

    # sleep for 5 seconds
    time.sleep(5)

    logging.info('Pre-processing done.')
    return True


def run_workloads(config_file_path):
    with open(config_file_path, 'r') as f:
        config = yaml.safe_load(f)

    metadata = config['experiment']
    scale_size = metadata['scale']
    cluster_base = metadata['cluster_base']
    sleep_time_normal = metadata['sleep_time_normal']
    sleep_time_long = metadata['sleep_time_long']

    cluster_base = os.path.join(os.getcwd(), cluster_base)

    if not os.path.exists(cluster_base):
        os.mkdir(cluster_base)

    for workload_name, workload_details in config['workloads'].items():
        file_name = workload_details['filename']
        run_or_not = workload_details['run']
        if run_or_not == False:
            continue
        # if workload has special options, use them
        if 'special_options' in workload_details:
            special_options = workload_details['special_options']
            for idx, option in enumerate(special_options):
                if option == metadata['cluster_base']:
                    special_options[idx] = cluster_base
            command = ['bash', file_name,  *special_options]
        else:
            command = ['bash', file_name, scale_size,
                       sleep_time_normal, cluster_base]

        command = [str(x) for x in command]
        cmd = ' '.join(command)

        workload_logfile = '{}.log'.format(workload_name)
        workload_logfile_path = os.path.join(os.getcwd(), workload_logfile)
        logging.info('Running workload {}: {}'.format(workload_name, cmd))
        # TODO: setup timeouts for workloads
        process, stdout, stderr, ok = _run_command(
            cmd, output=workload_logfile_path)
        if not ok:
            logging.error('Error running command: {}'.format(cmd))
            logging.error('stdout: {}'.format(stdout))
            logging.error('stderr: {}'.format(stderr))

        # sleep to clean up
        command = ['sleep', str(sleep_time_long)]
        cmd = ' '.join(command)
        process, stdout, stderr, ok = _run_command(cmd)

    logging.info('Workloads done.')
    return True


def post_processing(config_file_path):
    with open(config_file_path, 'r') as f:
        config = yaml.safe_load(f)

    metadata = config['experiment']
    system = metadata['system']
    system_id = metadata['system_id']
    version = metadata['version']
    scale_size = metadata['scale']
    cluster_base = metadata['cluster_base']

    cluster_base = os.path.join(os.getcwd(), cluster_base)
    shutil.rmtree(cluster_base, ignore_errors=True)

    result_dir = '{}-{}-{}-traces'.format(system_id, version, scale_size)
    os.mkdir(result_dir)

    # move all .tar.gz and .log files to result_dir
    for file in os.listdir(os.getcwd()):
        if file.endswith('.tar.gz') or file.endswith('.log'):
            shutil.move(file, result_dir)

    # tar result_dir
    cmd = 'tar -cvzf {}.tar.gz {}'.format(result_dir, result_dir)
    process, stdout, stderr, ok = _run_command(cmd)
    if not ok:
        logging.error('Error running command: {}'.format(cmd))
        logging.error('stdout: {}'.format(stdout))
        logging.error('stderr: {}'.format(stderr))

    shutil.rmtree(result_dir, ignore_errors=True)

    logging.info('Post-processing done.')
    return True


def _print_usage(msg=None):
    if msg:
        logging.error(msg)

    print('Usage: python run_workloads.py OPTIONS')
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
            'Running experiment with config file: {}'.format(config_file))
    else:
        logging.error('Config file not found: {}'.format(config_file))
        sys.exit(1)

    pre_processing(config_file)
    run_workloads(config_file)
    post_processing(config_file)


if __name__ == '__main__':
    sys.exit(main())
