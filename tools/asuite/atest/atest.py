#!/usr/bin/env python3
#
# Copyright 2017, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Command line utility for running Android tests through TradeFederation.

atest helps automate the flow of building test modules across the Android
code base and executing the tests via the TradeFederation test harness.

atest is designed to support any test types that can be ran by TradeFederation.
"""

# pylint: disable=line-too-long
# pylint: disable=too-many-lines

from __future__ import print_function

import collections
import logging
import os
import sys
import tempfile
import time
import platform
import re

from pathlib import Path

import atest_arg_parser
import atest_configs
import atest_error
import atest_execution_info
import atest_utils
import bazel_mode
import bug_detector
import cli_translator
import constants
import module_info
import result_reporter
import test_runner_handler

from atest_enum import DetectType, ExitCode
from metrics import metrics
from metrics import metrics_base
from metrics import metrics_utils
from test_finders import test_finder_utils
from test_runners import regression_test_runner
from tools import atest_tools as at

EXPECTED_VARS = frozenset([
    constants.ANDROID_BUILD_TOP,
    'ANDROID_TARGET_OUT_TESTCASES',
    constants.ANDROID_OUT])
TEST_RUN_DIR_PREFIX = "%Y%m%d_%H%M%S"
CUSTOM_ARG_FLAG = '--'
OPTION_NOT_FOR_TEST_MAPPING = (
    'Option "{}" does not work for running tests in TEST_MAPPING files')

DEVICE_TESTS = 'tests that require device'
HOST_TESTS = 'tests that do NOT require device'
RESULT_HEADER_FMT = '\nResults from %(test_type)s:'
RUN_HEADER_FMT = '\nRunning %(test_count)d %(test_type)s.'
TEST_COUNT = 'test_count'
TEST_TYPE = 'test_type'
MAINLINE_MODULES_EXT_RE = re.compile(r'(.apex|.apks|.apk)$')
# Tasks that must run in the build time but unable to build by soong.
# (e.g subprocesses that invoke host commands.)
ACLOUD_CREATE = at.acloud_create
INDEX_TARGETS = at.index_targets
END_OF_OPTION = '--'
HAS_IGNORED_ARGS = False
# Conditions that atest should exit without sending result to metrics.
EXIT_CODES_BEFORE_TEST = [ExitCode.ENV_NOT_SETUP,
                          ExitCode.TEST_NOT_FOUND,
                          ExitCode.OUTSIDE_ROOT,
                          ExitCode.AVD_CREATE_FAILURE,
                          ExitCode.AVD_INVALID_ARGS]

def _get_args_from_config():
    """Get customized atest arguments in the config file.

    If the config has not existed yet, atest will initialize an example
    config file for it without any effective options.

    Returns:
        A list read from the config file.
    """
    _config = Path(atest_utils.get_misc_dir()).joinpath('.atest', 'config')
    if not _config.parent.is_dir():
        _config.parent.mkdir(parents=True)
    args = []
    if not _config.is_file():
        with open(_config, 'w+', encoding='utf8') as cache:
            cache.write(constants.ATEST_EXAMPLE_ARGS)
        return args
    warning = 'Line {} contains {} and will be ignored.'
    print('\n{} {}'.format(
        atest_utils.colorize('Reading config:', constants.CYAN),
        atest_utils.colorize(_config, constants.YELLOW)))
    # pylint: disable=global-statement:
    global HAS_IGNORED_ARGS
    with open(_config, 'r', encoding='utf8') as cache:
        for entry in cache.readlines():
            # Strip comments.
            arg_in_line = entry.partition('#')[0].strip()
            # Strip test name/path.
            if arg_in_line.startswith('-'):
                # Process argument that contains whitespaces.
                # e.g. ["--serial foo"] -> ["--serial", "foo"]
                if len(arg_in_line.split()) > 1:
                    # remove "--" to avoid messing up atest/tradefed commands.
                    if END_OF_OPTION in arg_in_line.split():
                        HAS_IGNORED_ARGS = True
                        print(warning.format(
                            atest_utils.colorize(arg_in_line, constants.YELLOW),
                            END_OF_OPTION))
                    args.extend(arg_in_line.split())
                else:
                    if END_OF_OPTION == arg_in_line:
                        HAS_IGNORED_ARGS = True
                        print(warning.format(
                            atest_utils.colorize(arg_in_line, constants.YELLOW),
                            END_OF_OPTION))
                    args.append(arg_in_line)
    return args

def _parse_args(argv):
    """Parse command line arguments.

    Args:
        argv: A list of arguments.

    Returns:
        An argspace.Namespace class instance holding parsed args.
    """
    # Store everything after '--' in custom_args.
    pruned_argv = argv
    custom_args_index = None
    if CUSTOM_ARG_FLAG in argv:
        custom_args_index = argv.index(CUSTOM_ARG_FLAG)
        pruned_argv = argv[:custom_args_index]
    parser = atest_arg_parser.AtestArgParser()
    parser.add_atest_args()
    args = parser.parse_args(pruned_argv)
    args.custom_args = []
    if custom_args_index is not None:
        for arg in argv[custom_args_index+1:]:
            logging.debug('Quoting regex argument %s', arg)
            args.custom_args.append(atest_utils.quote(arg))
    return args


def _configure_logging(verbose):
    """Configure the logger.

    Args:
        verbose: A boolean. If true display DEBUG level logs.
    """
    # Clear the handlers to prevent logging.basicConfig from being called twice.
    logging.getLogger('').handlers = []
    log_format = '%(asctime)s %(filename)s:%(lineno)s:%(levelname)s: %(message)s'
    datefmt = '%Y-%m-%d %H:%M:%S'
    if verbose:
        logging.basicConfig(level=logging.DEBUG,
                            format=log_format, datefmt=datefmt)
    else:
        logging.basicConfig(level=logging.INFO,
                            format=log_format, datefmt=datefmt)


def _missing_environment_variables():
    """Verify the local environment has been set up to run atest.

    Returns:
        List of strings of any missing environment variables.
    """
    missing = list(filter(None, [x for x in EXPECTED_VARS if not os.environ.get(x)]))
    if missing:
        logging.error('Local environment doesn\'t appear to have been '
                      'initialized. Did you remember to run lunch? Expected '
                      'Environment Variables: %s.', missing)
    return missing


def make_test_run_dir():
    """Make the test run dir in ATEST_RESULT_ROOT.

    Returns:
        A string of the dir path.
    """
    if not os.path.exists(constants.ATEST_RESULT_ROOT):
        os.makedirs(constants.ATEST_RESULT_ROOT)
    ctime = time.strftime(TEST_RUN_DIR_PREFIX, time.localtime())
    test_result_dir = tempfile.mkdtemp(prefix='%s_' % ctime,
                                       dir=constants.ATEST_RESULT_ROOT)
    return test_result_dir


def get_extra_args(args):
    """Get extra args for test runners.

    Args:
        args: arg parsed object.

    Returns:
        Dict of extra args for test runners to utilize.
    """
    extra_args = {}
    if args.wait_for_debugger:
        extra_args[constants.WAIT_FOR_DEBUGGER] = None
    steps = args.steps or constants.ALL_STEPS
    if constants.INSTALL_STEP not in steps:
        extra_args[constants.DISABLE_INSTALL] = None
    # The key and its value of the dict can be called via:
    # if args.aaaa:
    #     extra_args[constants.AAAA] = args.aaaa
    arg_maps = {'all_abi': constants.ALL_ABI,
                'annotation_filter': constants.ANNOTATION_FILTER,
                'bazel_arg': constants.BAZEL_ARG,
                'collect_tests_only': constants.COLLECT_TESTS_ONLY,
                'custom_args': constants.CUSTOM_ARGS,
                'disable_teardown': constants.DISABLE_TEARDOWN,
                'disable_upload_result': constants.DISABLE_UPLOAD_RESULT,
                'dry_run': constants.DRY_RUN,
                'enable_device_preparer': constants.ENABLE_DEVICE_PREPARER,
                'flakes_info': constants.FLAKES_INFO,
                'generate_baseline': constants.PRE_PATCH_ITERATIONS,
                'generate_new_metrics': constants.POST_PATCH_ITERATIONS,
                'host': constants.HOST,
                'instant': constants.INSTANT,
                'iterations': constants.ITERATIONS,
                'no_enable_root': constants.NO_ENABLE_ROOT,
                'request_upload_result': constants.REQUEST_UPLOAD_RESULT,
                'bazel_mode_features': constants.BAZEL_MODE_FEATURES,
                'rerun_until_failure': constants.RERUN_UNTIL_FAILURE,
                'retry_any_failure': constants.RETRY_ANY_FAILURE,
                'serial': constants.SERIAL,
                'sharding': constants.SHARDING,
                'test_filter': constants.TEST_FILTER,
                'test_timeout': constants.TEST_TIMEOUT,
                'tf_early_device_release': constants.TF_EARLY_DEVICE_RELEASE,
                'tf_debug': constants.TF_DEBUG,
                'tf_template': constants.TF_TEMPLATE,
                'user_type': constants.USER_TYPE,
                'verify_env_variable': constants.VERIFY_ENV_VARIABLE}
    not_match = [k for k in arg_maps if k not in vars(args)]
    if not_match:
        raise AttributeError('%s object has no attribute %s'
                             % (type(args).__name__, not_match))
    extra_args.update({arg_maps.get(k): v for k, v in vars(args).items()
                       if arg_maps.get(k) and v})
    return extra_args


def _get_regression_detection_args(args, results_dir):
    """Get args for regression detection test runners.

    Args:
        args: parsed args object.
        results_dir: string directory to store atest results.

    Returns:
        Dict of args for regression detection test runner to utilize.
    """
    regression_args = {}
    pre_patch_folder = (os.path.join(results_dir, 'baseline-metrics') if args.generate_baseline
                        else args.detect_regression.pop(0))
    post_patch_folder = (os.path.join(results_dir, 'new-metrics') if args.generate_new_metrics
                         else args.detect_regression.pop(0))
    regression_args[constants.PRE_PATCH_FOLDER] = pre_patch_folder
    regression_args[constants.POST_PATCH_FOLDER] = post_patch_folder
    return regression_args


def _validate_exec_mode(args, test_infos, host_tests=None):
    """Validate all test execution modes are not in conflict.

    Exit the program with error code if have device-only and host-only.
    If no conflict and host side, add args.host=True.

    Args:
        args: parsed args object.
        test_info: TestInfo object.
        host_tests: True if all tests should be deviceless, False if all tests
            should be device tests. Default is set to None, which means
            tests can be either deviceless or device tests.
    """
    all_device_modes = {x.get_supported_exec_mode() for x in test_infos}
    err_msg = None
    # In the case of '$atest <device-only> --host', exit.
    if (host_tests or args.host) and constants.DEVICE_TEST in all_device_modes:
        device_only_tests = [x.test_name for x in test_infos
                             if x.get_supported_exec_mode() == constants.DEVICE_TEST]
        err_msg = ('Specified --host, but the following tests are device-only:\n  ' +
                   '\n  '.join(sorted(device_only_tests)) + '\nPlease remove the option '
                   'when running device-only tests.')
    # In the case of '$atest <host-only> <device-only> --host' or
    # '$atest <host-only> <device-only>', exit.
    if (constants.DEVICELESS_TEST in all_device_modes and
            constants.DEVICE_TEST in all_device_modes):
        err_msg = 'There are host-only and device-only tests in command.'
    if host_tests is False and constants.DEVICELESS_TEST in all_device_modes:
        err_msg = 'There are host-only tests in command.'
    if err_msg:
        logging.error(err_msg)
        metrics_utils.send_exit_event(ExitCode.ERROR, logs=err_msg)
        sys.exit(ExitCode.ERROR)
    # The 'adb' may not be available for the first repo sync or a clean build; run
    # `adb devices` in the build step again.
    if at.has_command('adb'):
        _validate_adb_devices(args, test_infos)
    # In the case of '$atest <host-only>', we add --host to run on host-side.
    # The option should only be overridden if `host_tests` is not set.
    if not args.host and host_tests is None:
        logging.debug('Appending "--host" for a deviceless test...')
        args.host = bool(constants.DEVICELESS_TEST in all_device_modes)


def _validate_adb_devices(args, test_infos):
    """Validate the availability of connected devices via adb command.

    Exit the program with error code if have device-only and host-only.

    Args:
        args: parsed args object.
        test_info: TestInfo object.
    """
    all_device_modes = {x.get_supported_exec_mode() for x in test_infos}
    device_tests = [x.test_name for x in test_infos
        if x.get_supported_exec_mode() != constants.DEVICELESS_TEST]
    # Only block testing if it is a device test.
    if constants.DEVICE_TEST in all_device_modes:
        if (not any((args.host, args.start_avd, args.acloud_create))
            and not atest_utils.get_adb_devices()):
            err_msg = (f'Stop running test(s): '
                       f'{", ".join(device_tests)} require a device.')
            atest_utils.colorful_print(err_msg, constants.RED)
            logging.debug(atest_utils.colorize(
                constants.REQUIRE_DEVICES_MSG, constants.RED))
            metrics_utils.send_exit_event(ExitCode.DEVICE_NOT_FOUND,
                                          logs=err_msg)
            sys.exit(ExitCode.DEVICE_NOT_FOUND)


def _validate_tm_tests_exec_mode(args, test_infos):
    """Validate all test execution modes are not in conflict.

    Split the tests in Test Mapping files into two groups, device tests and
    deviceless tests running on host. Validate the tests' host setting.
    For device tests, exit the program if any test is found for host-only.
    For deviceless tests, exit the program if any test is found for device-only.

    Args:
        args: parsed args object.
        test_info: TestInfo object.
    """
    device_test_infos, host_test_infos = _split_test_mapping_tests(
        test_infos)
    # No need to verify device tests if atest command is set to only run host
    # tests.
    if device_test_infos and not args.host:
        _validate_exec_mode(args, device_test_infos, host_tests=False)
    if host_test_infos:
        _validate_exec_mode(args, host_test_infos, host_tests=True)


def _will_run_tests(args):
    """Determine if there are tests to run.

    Currently only used by detect_regression to skip the test if just running
    regression detection.

    Args:
        args: parsed args object.

    Returns:
        True if there are tests to run, false otherwise.
    """
    return not (args.detect_regression and len(args.detect_regression) == 2)


# pylint: disable=no-else-return
# This method is going to dispose, let's ignore pylint for now.
def _has_valid_regression_detection_args(args):
    """Validate regression detection args.

    Args:
        args: parsed args object.

    Returns:
        True if args are valid
    """
    if args.generate_baseline and args.generate_new_metrics:
        logging.error('Cannot collect both baseline and new metrics'
                      'at the same time.')
        return False
    if args.detect_regression is not None:
        if not args.detect_regression:
            logging.error('Need to specify at least 1 arg for'
                          ' regression detection.')
            return False
        elif len(args.detect_regression) == 1:
            if args.generate_baseline or args.generate_new_metrics:
                return True
            logging.error('Need to specify --generate-baseline or'
                          ' --generate-new-metrics.')
            return False
        elif len(args.detect_regression) == 2:
            if args.generate_baseline:
                logging.error('Specified 2 metric paths and --generate-baseline'
                              ', either drop --generate-baseline or drop a path')
                return False
            if args.generate_new_metrics:
                logging.error('Specified 2 metric paths and --generate-new-metrics, '
                              'either drop --generate-new-metrics or drop a path')
                return False
            return True
        else:
            logging.error('Specified more than 2 metric paths.')
            return False
    return True


def _has_valid_test_mapping_args(args):
    """Validate test mapping args.

    Not all args work when running tests in TEST_MAPPING files. Validate the
    args before running the tests.

    Args:
        args: parsed args object.

    Returns:
        True if args are valid
    """
    is_test_mapping = atest_utils.is_test_mapping(args)
    if not is_test_mapping:
        return True
    options_to_validate = [
        (args.annotation_filter, '--annotation-filter'),
        (args.generate_baseline, '--generate-baseline'),
        (args.detect_regression, '--detect-regression'),
        (args.generate_new_metrics, '--generate-new-metrics'),
    ]
    for arg_value, arg in options_to_validate:
        if arg_value:
            logging.error(atest_utils.colorize(
                OPTION_NOT_FOR_TEST_MAPPING.format(arg), constants.RED))
            return False
    return True


def _validate_args(args):
    """Validate setups and args.

    Exit the program with error code if any setup or arg is invalid.

    Args:
        args: parsed args object.
    """
    if _missing_environment_variables():
        sys.exit(ExitCode.ENV_NOT_SETUP)
    if args.generate_baseline and args.generate_new_metrics:
        logging.error(
            'Cannot collect both baseline and new metrics at the same time.')
        sys.exit(ExitCode.ERROR)
    if not _has_valid_regression_detection_args(args):
        sys.exit(ExitCode.ERROR)
    if not _has_valid_test_mapping_args(args):
        sys.exit(ExitCode.ERROR)


def _print_module_info_from_module_name(mod_info, module_name):
    """print out the related module_info for a module_name.

    Args:
        mod_info: ModuleInfo object.
        module_name: A string of module.

    Returns:
        True if the module_info is found.
    """
    title_mapping = collections.OrderedDict()
    title_mapping[constants.MODULE_COMPATIBILITY_SUITES] = 'Compatibility suite'
    title_mapping[constants.MODULE_PATH] = 'Source code path'
    title_mapping[constants.MODULE_INSTALLED] = 'Installed path'
    target_module_info = mod_info.get_module_info(module_name)
    is_module_found = False
    if target_module_info:
        atest_utils.colorful_print(module_name, constants.GREEN)
        for title_key in title_mapping:
            atest_utils.colorful_print("\t%s" % title_mapping[title_key],
                                       constants.CYAN)
            for info_value in target_module_info[title_key]:
                print("\t\t{}".format(info_value))
        is_module_found = True
    return is_module_found


def _print_test_info(mod_info, test_infos):
    """Print the module information from TestInfos.

    Args:
        mod_info: ModuleInfo object.
        test_infos: A list of TestInfos.

    Returns:
        Always return EXIT_CODE_SUCCESS
    """
    for test_info in test_infos:
        _print_module_info_from_module_name(mod_info, test_info.test_name)
        atest_utils.colorful_print("\tRelated build targets", constants.MAGENTA)
        sorted_build_targets = sorted(list(test_info.build_targets))
        print("\t\t{}".format(", ".join(sorted_build_targets)))
        for build_target in sorted_build_targets:
            if build_target != test_info.test_name:
                _print_module_info_from_module_name(mod_info, build_target)
        atest_utils.colorful_print("", constants.WHITE)
    return ExitCode.SUCCESS


def is_from_test_mapping(test_infos):
    """Check that the test_infos came from TEST_MAPPING files.

    Args:
        test_infos: A set of TestInfos.

    Returns:
        True if the test infos are from TEST_MAPPING files.
    """
    return list(test_infos)[0].from_test_mapping


def _split_test_mapping_tests(test_infos):
    """Split Test Mapping tests into 2 groups: device tests and host tests.

    Args:
        test_infos: A set of TestInfos.

    Returns:
        A tuple of (device_test_infos, host_test_infos), where
        device_test_infos: A set of TestInfos for tests that require device.
        host_test_infos: A set of TestInfos for tests that do NOT require
            device.
    """
    assert is_from_test_mapping(test_infos)
    host_test_infos = {info for info in test_infos if info.host}
    device_test_infos = {info for info in test_infos if not info.host}
    return device_test_infos, host_test_infos


# pylint: disable=too-many-locals
def _run_test_mapping_tests(results_dir, test_infos, extra_args, mod_info):
    """Run all tests in TEST_MAPPING files.

    Args:
        results_dir: String directory to store atest results.
        test_infos: A set of TestInfos.
        extra_args: Dict of extra args to add to test run.
        mod_info: ModuleInfo object.

    Returns:
        Exit code.
    """
    device_test_infos, host_test_infos = _split_test_mapping_tests(test_infos)
    # `host` option needs to be set to True to run host side tests.
    host_extra_args = extra_args.copy()
    host_extra_args[constants.HOST] = True
    test_runs = [(host_test_infos, host_extra_args, HOST_TESTS)]
    if extra_args.get(constants.HOST):
        atest_utils.colorful_print(
            'Option `--host` specified. Skip running device tests.',
            constants.MAGENTA)
    else:
        test_runs.append((device_test_infos, extra_args, DEVICE_TESTS))

    test_results = []
    for tests, args, test_type in test_runs:
        if not tests:
            continue
        header = RUN_HEADER_FMT % {TEST_COUNT: len(tests), TEST_TYPE: test_type}
        atest_utils.colorful_print(header, constants.MAGENTA)
        logging.debug('\n'.join([str(info) for info in tests]))
        tests_exit_code, reporter = test_runner_handler.run_all_tests(
            results_dir, tests, args, mod_info, delay_print_summary=True)
        atest_execution_info.AtestExecutionInfo.result_reporters.append(reporter)
        test_results.append((tests_exit_code, reporter, test_type))

    all_tests_exit_code = ExitCode.SUCCESS
    failed_tests = []
    for tests_exit_code, reporter, test_type in test_results:
        atest_utils.colorful_print(
            RESULT_HEADER_FMT % {TEST_TYPE: test_type}, constants.MAGENTA)
        result = tests_exit_code | reporter.print_summary()
        if result:
            failed_tests.append(test_type)
        all_tests_exit_code |= result

    # List failed tests at the end as a reminder.
    if failed_tests:
        atest_utils.colorful_print(
            atest_utils.delimiter('=', 30, prenl=1), constants.YELLOW)
        atest_utils.colorful_print(
            '\nFollowing tests failed:', constants.MAGENTA)
        for failure in failed_tests:
            atest_utils.colorful_print(failure, constants.RED)

    return all_tests_exit_code


def _dry_run(results_dir, extra_args, test_infos, mod_info):
    """Only print the commands of the target tests rather than running them in actual.

    Args:
        results_dir: Path for saving atest logs.
        extra_args: Dict of extra args for test runners to utilize.
        test_infos: A list of TestInfos.
        mod_info: ModuleInfo object.

    Returns:
        A list of test commands.
    """
    all_run_cmds = []
    for test_runner, tests in test_runner_handler.group_tests_by_test_runners(test_infos):
        runner = test_runner(results_dir, mod_info=mod_info)
        run_cmds = runner.generate_run_commands(tests, extra_args)
        for run_cmd in run_cmds:
            all_run_cmds.append(run_cmd)
            print('Would run test via command: %s'
                  % (atest_utils.colorize(run_cmd, constants.GREEN)))
    return all_run_cmds

def _print_testable_modules(mod_info, suite):
    """Print the testable modules for a given suite.

    Args:
        mod_info: ModuleInfo object.
        suite: A string of suite name.
    """
    testable_modules = mod_info.get_testable_modules(suite)
    print('\n%s' % atest_utils.colorize('%s Testable %s modules' % (
        len(testable_modules), suite), constants.CYAN))
    print(atest_utils.delimiter('-'))
    for module in sorted(testable_modules):
        print('\t%s' % module)

def _is_inside_android_root():
    """Identify whether the cwd is inside of Android source tree.

    Returns:
        False if the cwd is outside of the source tree, True otherwise.
    """
    build_top = os.getenv(constants.ANDROID_BUILD_TOP, ' ')
    return build_top in os.getcwd()

def _non_action_validator(args):
    """Method for non-action arguments such as --version, --help, --history,
    --latest_result, etc.

    Args:
        args: An argspace.Namespace class instance holding parsed args.
    """
    if not _is_inside_android_root():
        atest_utils.colorful_print(
            "\nAtest must always work under ${}!".format(
                constants.ANDROID_BUILD_TOP), constants.RED)
        sys.exit(ExitCode.OUTSIDE_ROOT)
    if args.version:
        if os.path.isfile(constants.VERSION_FILE):
            with open(constants.VERSION_FILE, encoding='utf8') as version_file:
                print(version_file.read())
        sys.exit(ExitCode.SUCCESS)
    if args.help:
        atest_arg_parser.print_epilog_text()
        sys.exit(ExitCode.SUCCESS)
    if args.history:
        atest_execution_info.print_test_result(constants.ATEST_RESULT_ROOT,
                                               args.history)
        sys.exit(ExitCode.SUCCESS)
    if args.latest_result:
        atest_execution_info.print_test_result_by_path(
            constants.LATEST_RESULT_FILE)
        sys.exit(ExitCode.SUCCESS)
    # TODO(b/131879842): remove below statement after they are fully removed.
    if any((args.detect_regression,
            args.generate_baseline,
            args.generate_new_metrics)):
        stop_msg = ('Please STOP using arguments below -- they are obsolete and '
                    'will be removed in a very near future:\n'
                    '\t--detect-regression\n'
                    '\t--generate-baseline\n'
                    '\t--generate-new-metrics\n')
        msg = ('Please use below arguments instead:\n'
               '\t--iterations\n'
               '\t--rerun-until-failure\n'
               '\t--retry-any-failure\n')
        atest_utils.colorful_print(stop_msg, constants.RED)
        atest_utils.colorful_print(msg, constants.CYAN)

def _dry_run_validator(args, results_dir, extra_args, test_infos, mod_info):
    """Method which process --dry-run argument.

    Args:
        args: An argspace.Namespace class instance holding parsed args.
        result_dir: A string path of the results dir.
        extra_args: A dict of extra args for test runners to utilize.
        test_infos: A list of test_info.
        mod_info: ModuleInfo object.
    Returns:
        Exit code.
    """
    test_commands = atest_utils.get_verify_key(args.tests, extra_args)
    dry_run_cmds = _dry_run(results_dir, extra_args, test_infos, mod_info)
    if args.verify_cmd_mapping:
        try:
            atest_utils.handle_test_runner_cmd(test_commands,
                                               dry_run_cmds,
                                               do_verification=True)
        except atest_error.DryRunVerificationError as e:
            atest_utils.colorful_print(str(e), constants.RED)
            return ExitCode.VERIFY_FAILURE
    if args.update_cmd_mapping:
        atest_utils.handle_test_runner_cmd(test_commands,
                                           dry_run_cmds)
    return ExitCode.SUCCESS

def _exclude_modules_in_targets(build_targets):
    """Method that excludes MODULES-IN-* targets.

    Args:
        build_targets: A set of build targets.

    Returns:
        A set of build targets that excludes MODULES-IN-*.
    """
    shrank_build_targets = build_targets.copy()
    logging.debug('Will exclude all "%s*" from the build targets.',
                  constants.MODULES_IN)
    for target in build_targets:
        if target.startswith(constants.MODULES_IN):
            logging.debug('Ignore %s.', target)
            shrank_build_targets.remove(target)
    return shrank_build_targets

# pylint: disable=protected-access
def need_rebuild_module_info(force_build):
    """Method that tells whether we need to rebuild module-info.json or not.

    Args:
        force_build: A boolean flag that determine everything.

    Returns:
        - When force_build is True, return True (will rebuild module-info).
        - When force_build is False, then check the consistency of build files.
        If the checksum file of build files is missing, considered check False
        (need to rebuild module-info.json)
    """
    logging.debug('Examinating the consistency of build files...')
    if force_build:
        msg = (f'`{constants.REBUILD_MODULE_INFO_FLAG}` is no longer needed '
               f'since Atest can smartly rebuild {module_info._MODULE_INFO} '
               r'only when needed.')
        atest_utils.colorful_print(msg, constants.YELLOW)
        return True
    if atest_utils.check_md5(constants.BUILDFILES_MD5, missing_ok=False):
        logging.debug('All build files stay untouched.')
        return False
    logging.debug('Found build files were changed.')
    return True

def acloud_create_validator(results_dir, args):
    """Check lunch'd target before running 'acloud create'.

    Args:
        results_dir: A string of the results directory.
        args: A list of arguments.

    Returns:
        If the target is valid:
            A tuple of (multiprocessing.Process,
                        string of report file path)
        else:
            None, None
    """
    if not any((args.acloud_create, args.start_avd)):
        return None, None
    if args.start_avd:
        args.acloud_create = ['--num=1']
    acloud_args = ' '.join(args.acloud_create)
    target = os.getenv('TARGET_PRODUCT', "")
    if 'cf_x86' in target:
        report_file = at.get_report_file(results_dir, acloud_args)
        acloud_proc = atest_utils.run_multi_proc(
            func=ACLOUD_CREATE,
            args=[report_file],
            kwargs={'args':acloud_args,
                    'no_metrics_notice':args.no_metrics})
        return acloud_proc, report_file
    atest_utils.colorful_print(
        '{} is not cf_x86 family; will not create any AVD.'.format(target),
        constants.RED)
    return None, None

def perm_consistency_metrics(test_infos, mod_info, args):
    """collect inconsistency between preparer and device root permission.

    Args:
        test_infos: TestInfo obj.
        mod_info: ModuleInfo obj.
        args: An argspace.Namespace class instance holding parsed args.
    """
    try:
        # whether device has root permission
        adb_root = atest_utils.is_adb_root(args)
        logging.debug('is_adb_root: %s', adb_root)
        for test_info in test_infos:
            config_path, _ = test_finder_utils.get_test_config_and_srcs(
                test_info, mod_info)
            atest_utils.perm_metrics(config_path, adb_root)
    # pylint: disable=broad-except
    except Exception as err:
        logging.debug('perm_consistency_metrics raised exception: %s', err)
        return

def get_device_count_config(test_infos, mod_info):
    """Get the amount of desired devices from the test config.

    Args:
        test_infos: A set of TestInfo instances.
        mod_info: ModuleInfo object.

    Returns: the count of devices in test config. If there are more than one
             configs, return the maximum.
    """
    max_count = 0
    for tinfo in test_infos:
        test_config, _ = test_finder_utils.get_test_config_and_srcs(
            tinfo, mod_info)
        if test_config:
            devices = atest_utils.get_config_device(test_config)
            if devices:
                max_count = max(len(devices), max_count)
    return max_count

# pylint: disable=too-many-statements
# pylint: disable=too-many-branches
# pylint: disable=too-many-return-statements
def main(argv, results_dir, args):
    """Entry point of atest script.

    Args:
        argv: A list of arguments.
        results_dir: A directory which stores the ATest execution information.
        args: An argspace.Namespace class instance holding parsed args.

    Returns:
        Exit code.
    """
    _configure_logging(args.verbose)
    _validate_args(args)
    metrics_utils.get_start_time()
    os_pyver = '{}:{}'.format(platform.platform(), platform.python_version())
    metrics.AtestStartEvent(
        command_line=' '.join(argv),
        test_references=args.tests,
        cwd=os.getcwd(),
        os=os_pyver)
    _non_action_validator(args)
    proc_acloud, report_file = acloud_create_validator(results_dir, args)
    is_clean = not os.path.exists(
        os.environ.get(constants.ANDROID_PRODUCT_OUT, ''))
    # Do not index targets while the users intend to dry-run tests.
    dry_run_args = (args.update_cmd_mapping, args.verify_cmd_mapping, args.dry_run)
    extra_args = get_extra_args(args)
    verify_env_variables = extra_args.get(constants.VERIFY_ENV_VARIABLE, False)
    proc_idx = None
    if not (any(dry_run_args) or verify_env_variables):
        proc_idx = atest_utils.run_multi_proc(INDEX_TARGETS)
    smart_rebuild = need_rebuild_module_info(args.rebuild_module_info)
    mod_start = time.time()
    mod_info = module_info.ModuleInfo(force_build=smart_rebuild)
    metrics.LocalDetectEvent(detect_type=DetectType.MODULE_INFO_INIT_TIME,
                             result=int(time.time() - mod_start))
    atest_utils.generate_buildfiles_checksum()
    if args.bazel_mode:
        bazel_mode.generate_bazel_workspace(
            mod_info,
            enabled_features=set(args.bazel_mode_features or []))
    translator = cli_translator.CLITranslator(
        mod_info=mod_info,
        print_cache_msg=not args.clear_cache,
        bazel_mode_enabled=args.bazel_mode,
        host=args.host,
        bazel_mode_features=args.bazel_mode_features)
    if args.list_modules:
        _print_testable_modules(mod_info, args.list_modules)
        return ExitCode.SUCCESS
    build_targets = set()
    mm_build_targets = set()
    test_infos = set()
    if _will_run_tests(args):
        if proc_idx:
            proc_idx.join()
        find_start = time.time()
        build_targets, test_infos = translator.translate(args)
        given_amount  = len(args.serial) if args.serial else 0
        required_amount = get_device_count_config(test_infos, mod_info)
        args.device_count_config = required_amount
        # Only check when both given_amount and required_amount are non zero.
        if all((given_amount, required_amount)):
            # Base on TF rules, given_amount can be greater than or equal to
            # required_amount.
            if required_amount > given_amount:
                atest_utils.colorful_print(
                    f'The test requires {required_amount} devices, '
                    f'but {given_amount} were given.',
                    constants.RED)
                return 0
        # Remove MODULE-IN-* from build targets if not bazel mode and user not
        # force set --use-modules-in.
        if not args.bazel_mode and not args.use_modules_in:
            build_targets = _exclude_modules_in_targets(build_targets)
        find_duration = time.time() - find_start
        if not test_infos:
            return ExitCode.TEST_NOT_FOUND
        if not is_from_test_mapping(test_infos):
            if not (any(dry_run_args) or verify_env_variables):
                _validate_exec_mode(args, test_infos)
                # _validate_exec_mode appends --host automatically when pure
                # host-side tests, so re-parsing extra_args is a must.
                extra_args = get_extra_args(args)
        else:
            _validate_tm_tests_exec_mode(args, test_infos)
        for test_info in test_infos:
            if test_info.mainline_modules:
                for module in test_info.mainline_modules.split('+'):
                    mm_build_targets.add(re.sub(
                         MAINLINE_MODULES_EXT_RE, '', module))

    # For TEST_MAPPING, set timeout to 600000ms.
    if args.test_timeout is None:
        if is_from_test_mapping(test_infos):
            extra_args.update({constants.TEST_TIMEOUT: 600000})
            logging.debug(
                'Set test timeout to %sms to align it in TEST_MAPPING.',
                extra_args.get(constants.TEST_TIMEOUT))

    if args.info:
        return _print_test_info(mod_info, test_infos)
    build_targets |= test_runner_handler.get_test_runner_reqs(
        mod_info, test_infos, extra_args=extra_args)
    if any(dry_run_args):
        if not verify_env_variables:
            return _dry_run_validator(args, results_dir, extra_args, test_infos,
                                      mod_info)
    if verify_env_variables:
        # check environment variables.
        verify_key = atest_utils.get_verify_key(args.tests, extra_args)
        if not atest_utils.handle_test_env_var(verify_key, pre_verify=True):
            print('No environment variables need to verify.')
            return 0
    if args.detect_regression:
        build_targets |= (regression_test_runner.RegressionTestRunner('')
                          .get_test_runner_build_reqs())
    # args.steps will be None if none of -bit set, else list of params set.
    steps = args.steps if args.steps else constants.ALL_STEPS
    if build_targets and constants.BUILD_STEP in steps:
        # Add module-info.json target to the list of build targets to keep the
        # file up to date.
        build_targets.add(mod_info.module_info_target)
        build_start = time.time()
        success = atest_utils.build(build_targets, verbose=args.verbose,
                                    mm_build_targets=mm_build_targets)
        build_duration = time.time() - build_start
        build_targets.update(mm_build_targets)
        metrics.BuildFinishEvent(
            duration=metrics_utils.convert_duration(build_duration),
            success=success,
            targets=build_targets)
        rebuild_module_info = DetectType.NOT_REBUILD_MODULE_INFO
        if is_clean:
            rebuild_module_info = DetectType.CLEAN_BUILD
        elif args.rebuild_module_info:
            rebuild_module_info = DetectType.REBUILD_MODULE_INFO
        elif smart_rebuild:
            rebuild_module_info = DetectType.SMART_REBUILD_MODULE_INFO
        metrics.LocalDetectEvent(
            detect_type=rebuild_module_info,
            result=int(build_duration))
        if not success:
            return ExitCode.BUILD_FAILURE
        if proc_acloud:
            proc_acloud.join()
            status = at.probe_acloud_status(report_file)
            if status != 0:
                return status
            acloud_duration = at.get_acloud_duration(report_file)
            find_build_duration = find_duration + build_duration
            if find_build_duration - acloud_duration >= 0:
                # find+build took longer, saved acloud create time.
                logging.debug('Saved acloud create time: %ss.',
                              acloud_duration)
                metrics.LocalDetectEvent(
                    detect_type=DetectType.ACLOUD_CREATE,
                    result=round(acloud_duration))
            else:
                # acloud create took longer, saved find+build time.
                logging.debug('Saved Find and Build time: %ss.',
                              find_build_duration)
                metrics.LocalDetectEvent(
                    detect_type=DetectType.FIND_BUILD,
                    result=round(find_build_duration))
        # After build step 'adb' command will be available, and stop forward to
        # Tradefed if the tests require a device.
        _validate_adb_devices(args, test_infos)
    elif constants.TEST_STEP not in steps:
        logging.warning('Install step without test step currently not '
                        'supported, installing AND testing instead.')
        steps.append(constants.TEST_STEP)
    tests_exit_code = ExitCode.SUCCESS
    test_start = time.time()
    if constants.TEST_STEP in steps:
        perm_consistency_metrics(test_infos, mod_info, args)
        if not is_from_test_mapping(test_infos):
            tests_exit_code, reporter = test_runner_handler.run_all_tests(
                results_dir, test_infos, extra_args, mod_info)
            atest_execution_info.AtestExecutionInfo.result_reporters.append(reporter)
        else:
            tests_exit_code = _run_test_mapping_tests(
                results_dir, test_infos, extra_args, mod_info)
    if args.detect_regression:
        regression_args = _get_regression_detection_args(args, results_dir)
        # TODO(b/110485713): Should not call run_tests here.
        reporter = result_reporter.ResultReporter(
            collect_only=extra_args.get(constants.COLLECT_TESTS_ONLY))
        atest_execution_info.AtestExecutionInfo.result_reporters.append(reporter)
        tests_exit_code |= regression_test_runner.RegressionTestRunner(
            '').run_tests(
                None, regression_args, reporter)
    metrics.RunTestsFinishEvent(
        duration=metrics_utils.convert_duration(time.time() - test_start))
    preparation_time = atest_execution_info.preparation_time(test_start)
    if preparation_time:
        # Send the preparation time only if it's set.
        metrics.RunnerFinishEvent(
            duration=metrics_utils.convert_duration(preparation_time),
            success=True,
            runner_name=constants.TF_PREPARATION,
            test=[])
    if tests_exit_code != ExitCode.SUCCESS:
        tests_exit_code = ExitCode.TEST_FAILURE
    return tests_exit_code

if __name__ == '__main__':
    RESULTS_DIR = make_test_run_dir()
    final_args = [*sys.argv[1:], *_get_args_from_config()]
    if END_OF_OPTION in sys.argv:
        end_position = sys.argv.index(END_OF_OPTION)
        final_args = [*sys.argv[1:end_position],
                      *_get_args_from_config(),
                      *sys.argv[end_position:]]
    if final_args != sys.argv[1:]:
        print('The actual cmd will be: \n\t{}\n'.format(
            atest_utils.colorize("atest " + " ".join(final_args),
                                 constants.CYAN)))
        metrics.LocalDetectEvent(
            detect_type=DetectType.ATEST_CONFIG, result=1)
        if HAS_IGNORED_ARGS:
            atest_utils.colorful_print(
                'Please correct the config and try again.', constants.YELLOW)
            sys.exit(ExitCode.EXIT_BEFORE_MAIN)
    else:
        metrics.LocalDetectEvent(
            detect_type=DetectType.ATEST_CONFIG, result=0)
    atest_configs.GLOBAL_ARGS = _parse_args(final_args)
    with atest_execution_info.AtestExecutionInfo(
            final_args, RESULTS_DIR,
            atest_configs.GLOBAL_ARGS) as result_file:
        if not atest_configs.GLOBAL_ARGS.no_metrics:
            atest_utils.print_data_collection_notice()
            USER_FROM_TOOL = os.getenv(constants.USER_FROM_TOOL, '')
            if USER_FROM_TOOL == '':
                metrics_base.MetricsBase.tool_name = constants.TOOL_NAME
            else:
                metrics_base.MetricsBase.tool_name = USER_FROM_TOOL
            USER_FROM_SUB_TOOL = os.getenv(constants.USER_FROM_SUB_TOOL, '')
            if USER_FROM_SUB_TOOL == '':
                metrics_base.MetricsBase.sub_tool_name = constants.SUB_TOOL_NAME
            else:
                metrics_base.MetricsBase.sub_tool_name = USER_FROM_SUB_TOOL

        EXIT_CODE = main(final_args, RESULTS_DIR, atest_configs.GLOBAL_ARGS)
        DETECTOR = bug_detector.BugDetector(final_args, EXIT_CODE)
        if EXIT_CODE not in EXIT_CODES_BEFORE_TEST:
            metrics.LocalDetectEvent(
                detect_type=DetectType.BUG_DETECTED,
                result=DETECTOR.caught_result)
            if result_file:
                print("Run 'atest --history' to review test result history.")
    sys.exit(EXIT_CODE)
