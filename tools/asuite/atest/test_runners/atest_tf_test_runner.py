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

"""Atest Tradefed test runner class."""

# pylint: disable=line-too-long
# pylint: disable=too-many-lines

from __future__ import print_function

import json
import logging
import os
import re
import select
import shutil
import socket

from functools import partial
from pathlib import Path
from typing import Any, List, Tuple

import atest_configs
import atest_error
import atest_utils
import constants
import module_info
import result_reporter

from atest_enum import DetectType, ExitCode
from logstorage import atest_gcp_utils
from logstorage import logstorage_utils
from metrics import metrics
from test_finders import test_finder_utils
from test_finders import test_info
from test_runners import test_runner_base as trb
from .event_handler import EventHandler

POLL_FREQ_SECS = 10
SOCKET_HOST = '127.0.0.1'
SOCKET_QUEUE_MAX = 1
SOCKET_BUFFER = 4096
SELECT_TIMEOUT = 0.5

# Socket Events of form FIRST_EVENT {JSON_DATA}\nSECOND_EVENT {JSON_DATA}
# EVENT_RE has groups for the name and the data. "." does not match \n.
EVENT_RE = re.compile(r'\n*(?P<event_name>[A-Z_]+) (?P<json_data>{.*})(?=\n|.)*')

# Remove aapt from build dependency, use prebuilt version instead.
EXEC_DEPENDENCIES = ('adb', 'fastboot')

LOG_FOLDER_NAME = 'log'

_INTEGRATION_FINDERS = frozenset(['', 'INTEGRATION', 'INTEGRATION_FILE_PATH'])

# AAPT binary name
_AAPT = 'aapt'

# The exist code mapping of tradefed.
_TF_EXIT_CODE = [
    'NO_ERROR',
    'CONFIG_EXCEPTION',
    'NO_BUILD',
    'DEVICE_UNRESPONSIVE',
    'DEVICE_UNAVAILABLE',
    'FATAL_HOST_ERROR',
    'THROWABLE_EXCEPTION',
    'NO_DEVICE_ALLOCATED',
    'WRONG_JAVA_VERSION']


class TradeFedExitError(Exception):
    """Raised when TradeFed exists before test run has finished."""
    def __init__(self, exit_code):
        super().__init__()
        self.exit_code = exit_code

    def __str__(self):
        tf_error_reason = self._get_exit_reason(self.exit_code)
        return (f'TradeFed subprocess exited early with exit code='
                f'{self.exit_code}({tf_error_reason}).')

    def _get_exit_reason(self, exit_code):
        if 0 < exit_code < len(_TF_EXIT_CODE):
            return atest_utils.colorize(_TF_EXIT_CODE[exit_code], constants.RED)
        return 'Unknown exit status'

class AtestTradefedTestRunner(trb.TestRunnerBase):
    """TradeFed Test Runner class."""
    NAME = 'AtestTradefedTestRunner'
    EXECUTABLE = 'atest_tradefed.sh'
    _TF_TEMPLATE = 'template/atest_local_min'
    # Use --no-enable-granular-attempts to control reporter replay behavior.
    # TODO(b/142630648): Enable option enable-granular-attempts
    # in sharding mode.
    _LOG_ARGS = ('--logcat-on-failure --{log_root_option_name}={log_path} '
                 '{log_ext_option} '
                 '--no-enable-granular-attempts '
                 '--proto-output-file={proto_path}')
    _RUN_CMD = ('{env} {exe} {template} '
                '--template:map test=atest '
                '--template:map log_saver={log_saver} '
                '{tf_customize_template} {log_args} {args}')
    _BUILD_REQ = {'tradefed-core'}
    _RERUN_OPTION_GROUP = [constants.ITERATIONS,
                           constants.RERUN_UNTIL_FAILURE,
                           constants.RETRY_ANY_FAILURE]

    def __init__(self, results_dir: str,
                 mod_info: module_info.ModuleInfo=None, **kwargs):
        """Init stuff for base class."""
        super().__init__(results_dir, **kwargs)
        self.module_info = mod_info
        self.log_path = os.path.join(results_dir, LOG_FOLDER_NAME)
        if not os.path.exists(self.log_path):
            os.makedirs(self.log_path)
        log_args = {'log_root_option_name': constants.LOG_ROOT_OPTION_NAME,
                    'log_ext_option': constants.LOG_SAVER_EXT_OPTION,
                    'log_path': self.log_path,
                    'proto_path': os.path.join(self.results_dir, constants.ATEST_TEST_RECORD_PROTO)}
        self.run_cmd_dict = {'env': self._get_ld_library_path(),
                             'exe': self.EXECUTABLE,
                             'template': self._TF_TEMPLATE,
                             'log_saver': constants.ATEST_TF_LOG_SAVER,
                             'tf_customize_template': '',
                             'args': '',
                             'log_args': self._LOG_ARGS.format(**log_args)}
        self.is_verbose = logging.getLogger().isEnabledFor(logging.DEBUG)
        self.root_dir = os.environ.get(constants.ANDROID_BUILD_TOP)

    def _get_ld_library_path(self):
        """Get the extra environment setup string for running TF.

        Returns:
            Strings for the environment passed to TF. Currently only
            LD_LIBRARY_PATH for TF to load the correct local shared libraries.
        """
        out_dir = os.environ.get(constants.ANDROID_HOST_OUT, '')
        # From b/188179058, if a 64bit tests, it will break the tests due to the
        # elf format is not 64bit for the lib path. But for b/160741384, it is
        # ok to load lib path first. Change the lib_dirs sequence to lib64 first
        # due to ATest by default only testing the main abi and even a 32bit
        # only target the lib64 folder is actually not exist.
        lib_dirs = ['lib64', 'lib']
        path = ''
        for lib in lib_dirs:
            lib_dir = os.path.join(out_dir, lib)
            path = path + lib_dir + ':'
        return 'LD_LIBRARY_PATH=%s' % path

    def _try_set_gts_authentication_key(self):
        """Set GTS authentication key if it is available or exists.

        Strategy:
            Get APE_API_KEY from os.environ:
                - If APE_API_KEY is already set by user -> do nothing.
            Get the APE_API_KEY from constants:
                - If the key file exists -> set to env var.
            If APE_API_KEY isn't set and the key file doesn't exist:
                - Warn user some GTS tests may fail without authentication.
        """
        if os.environ.get('APE_API_KEY'):
            logging.debug('APE_API_KEY is set by developer.')
            return
        ape_api_key = constants.GTS_GOOGLE_SERVICE_ACCOUNT
        key_path = os.path.join(self.root_dir, ape_api_key)
        if ape_api_key and os.path.exists(key_path):
            logging.debug('Set APE_API_KEY: %s', ape_api_key)
            os.environ['APE_API_KEY'] = key_path
        else:
            logging.debug('APE_API_KEY not set, some GTS tests may fail'
                          ' without authentication.')

    def run_tests(self, test_infos, extra_args, reporter):
        """Run the list of test_infos. See base class for more.

        Args:
            test_infos: A list of TestInfos.
            extra_args: Dict of extra args to add to test run.
            reporter: An instance of result_report.ResultReporter.

        Returns:
            0 if tests succeed, non-zero otherwise.
        """
        reporter.log_path = self.log_path
        reporter.rerun_options = self._extract_rerun_options(extra_args)
        # Set google service key if it's available or found before
        # running tests.
        self._try_set_gts_authentication_key()
        result = 0
        creds, inv = atest_gcp_utils.do_upload_flow(extra_args)
        try:
            verify_key = atest_utils.get_verify_key([test_infos[0].test_name],
                                                    extra_args)
            if extra_args.get(constants.VERIFY_ENV_VARIABLE, False):
                # check environment variables.
                atest_utils.handle_test_env_var(
                    verify_key, result_path=constants.VERIFY_ENV_PATH)
                return 0
            # Change CWD to repo root to ensure TF can find prebuilt SDKs
            # for some path-sensitive tests like robolectric.
            os.chdir(os.path.abspath(os.getenv(constants.ANDROID_BUILD_TOP)))
            if os.getenv(trb.OLD_OUTPUT_ENV_VAR):
                result = self.run_tests_raw(test_infos, extra_args, reporter)
            result = self.run_tests_pretty(test_infos, extra_args, reporter)
        except atest_error.DryRunVerificationError as e:
            atest_utils.colorful_print(str(e), constants.RED)
            return ExitCode.VERIFY_FAILURE
        finally:
            if inv:
                try:
                    logging.disable(logging.INFO)
                    # Always set invocation status to completed due to the ATest
                    # handle whole process by its own.
                    inv['schedulerState'] = 'completed'
                    logstorage_utils.BuildClient(creds).update_invocation(inv)
                    reporter.test_result_link = (constants.RESULT_LINK
                                                 % inv['invocationId'])
                finally:
                    logging.disable(logging.NOTSET)
        return result

    def run_tests_raw(self, test_infos, extra_args, reporter):
        """Run the list of test_infos. See base class for more.

        Args:
            test_infos: A list of TestInfos.
            extra_args: Dict of extra args to add to test run.
            reporter: An instance of result_report.ResultReporter.

        Returns:
            0 if tests succeed, non-zero otherwise.
        """
        iterations = self._generate_iterations(extra_args)
        reporter.register_unsupported_runner(self.NAME)

        ret_code = ExitCode.SUCCESS
        for _ in range(iterations):
            run_cmds = self.generate_run_commands(test_infos, extra_args)
            subproc = self.run(run_cmds[0], output_to_stdout=True,
                               env_vars=self.generate_env_vars(extra_args))
            ret_code |= self.wait_for_subprocess(subproc)
        return ret_code

    def run_tests_pretty(self, test_infos, extra_args, reporter):
        """Run the list of test_infos. See base class for more.

        Args:
            test_infos: A list of TestInfos.
            extra_args: Dict of extra args to add to test run.
            reporter: An instance of result_report.ResultReporter.

        Returns:
            0 if tests succeed, non-zero otherwise.
        """
        iterations = self._generate_iterations(extra_args)
        ret_code = ExitCode.SUCCESS
        for _ in range(iterations):
            server = self._start_socket_server()
            run_cmds = self.generate_run_commands(test_infos, extra_args,
                                                  server.getsockname()[1])
            subproc = self.run(run_cmds[0], output_to_stdout=self.is_verbose,
                               env_vars=self.generate_env_vars(extra_args))
            self.handle_subprocess(subproc, partial(self._start_monitor,
                                                    server,
                                                    subproc,
                                                    reporter,
                                                    extra_args))
            server.close()
            ret_code |= self.wait_for_subprocess(subproc)
        return ret_code

    # pylint: disable=too-many-branches
    # pylint: disable=too-many-locals
    def _start_monitor(self, server, tf_subproc, reporter, extra_args):
        """Polling and process event.

        Args:
            server: Socket server object.
            tf_subproc: The tradefed subprocess to poll.
            reporter: Result_Reporter object.
            extra_args: Dict of extra args to add to test run.
        """
        inputs = [server]
        event_handlers = {}
        data_map = {}
        inv_socket = None
        while inputs:
            try:
                readable, _, _ = select.select(inputs, [], [], SELECT_TIMEOUT)
                for socket_object in readable:
                    if socket_object is server:
                        conn, addr = socket_object.accept()
                        logging.debug('Accepted connection from %s', addr)
                        conn.setblocking(False)
                        inputs.append(conn)
                        data_map[conn] = ''
                        # The First connection should be invocation
                        # level reporter.
                        if not inv_socket:
                            inv_socket = conn
                    else:
                        # Count invocation level reporter events
                        # without showing real-time information.
                        if inv_socket == socket_object:
                            reporter.silent = True
                            event_handler = event_handlers.setdefault(
                                socket_object, EventHandler(reporter,
                                                            self.NAME))
                        else:
                            event_handler = event_handlers.setdefault(
                                socket_object, EventHandler(
                                    result_reporter.ResultReporter(
                                        collect_only=extra_args.get(
                                            constants.COLLECT_TESTS_ONLY),
                                        flakes_info=extra_args.get(
                                            constants.FLAKES_INFO)),

                                    self.NAME))
                        recv_data = self._process_connection(data_map,
                                                             socket_object,
                                                             event_handler)
                        if not recv_data:
                            inputs.remove(socket_object)
                            socket_object.close()
            finally:
                # Subprocess ended and all socket clients were closed.
                if tf_subproc.poll() is not None and len(inputs) == 1:
                    inputs.pop().close()
                    if not reporter.all_test_results:
                        atest_utils.colorful_print(
                            r'No test to run. Please check: '
                            r'{} for detail.'.format(reporter.log_path),
                            constants.RED, highlight=True)
                    if not data_map:
                        metrics.LocalDetectEvent(
                            detect_type=DetectType.TF_EXIT_CODE,
                            result=tf_subproc.returncode)
                        raise TradeFedExitError(tf_subproc.returncode)
                    self._handle_log_associations(event_handlers)

    def _process_connection(self, data_map, conn, event_handler):
        """Process a socket connection betwen TF and ATest.

        Expect data of form EVENT_NAME {JSON_DATA}.  Multiple events will be
        \n deliminated.  Need to buffer data in case data exceeds socket
        buffer.
        E.q.
            TEST_RUN_STARTED {runName":"hello_world_test","runAttempt":0}\n
            TEST_STARTED {"start_time":2172917, "testName":"PrintHelloWorld"}\n
        Args:
            data_map: The data map of all connections.
            conn: Socket connection.
            event_handler: EventHandler object.

        Returns:
            True if conn.recv() has data , False otherwise.
        """
        # Set connection into blocking mode.
        conn.settimeout(None)
        data = conn.recv(SOCKET_BUFFER)
        if isinstance(data, bytes):
            data = data.decode()
        logging.debug('received: %s', data)
        if data:
            data_map[conn] += data
            while True:
                match = EVENT_RE.match(data_map[conn])
                if not match:
                    break
                try:
                    event_data = json.loads(match.group('json_data'))
                except ValueError:
                    logging.debug('Json incomplete, wait for more data')
                    break
                event_name = match.group('event_name')
                event_handler.process_event(event_name, event_data)
                data_map[conn] = data_map[conn][match.end():]
        return bool(data)

    def _start_socket_server(self):
        """Start a TCP server."""
        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        # Port 0 lets the OS pick an open port between 1024 and 65535.
        server.bind((SOCKET_HOST, 0))
        server.listen(SOCKET_QUEUE_MAX)
        server.settimeout(POLL_FREQ_SECS)
        logging.debug('Socket server started on port %s',
                      server.getsockname()[1])
        return server

    def generate_env_vars(self, extra_args):
        """Convert extra args into env vars."""
        env_vars = os.environ.copy()
        if constants.TF_GLOBAL_CONFIG:
            env_vars["TF_GLOBAL_CONFIG"] = constants.TF_GLOBAL_CONFIG
        debug_port = extra_args.get(constants.TF_DEBUG, '')
        if debug_port:
            env_vars['TF_DEBUG'] = 'true'
            env_vars['TF_DEBUG_PORT'] = str(debug_port)
        filtered_paths = []
        for path in str(env_vars['PYTHONPATH']).split(':'):
            # TODO (b/166216843) Remove the hacky PYTHON path workaround.
            if (str(path).startswith('/tmp/Soong.python_') and
                    str(path).find('googleapiclient') > 0):
                continue
            filtered_paths.append(path)
        env_vars['PYTHONPATH'] = ':'.join(filtered_paths)

        # Use prebuilt aapt if there's no aapt under android system path which
        # is aligned with build system.
        # https://android.googlesource.com/platform/build/+/master/core/config.mk#529
        if self._is_missing_exec(_AAPT):
            prebuilt_aapt = Path.joinpath(
                atest_utils.get_prebuilt_sdk_tools_dir(), _AAPT)
            if os.path.exists(prebuilt_aapt):
                env_vars['PATH'] = (str(prebuilt_aapt.parent) + ':'
                                    + env_vars['PATH'])
        return env_vars

    # pylint: disable=unnecessary-pass
    # Please keep above disable flag to ensure host_env_check is overriden.
    def host_env_check(self):
        """Check that host env has everything we need.

        We actually can assume the host env is fine because we have the same
        requirements that atest has. Update this to check for android env vars
        if that changes.
        """
        pass

    @staticmethod
    def _is_missing_exec(executable):
        """Check if system build executable is available.

        Args:
            executable: Executable we are checking for.

        Returns:
            True if executable is missing, False otherwise.
        """
        output = shutil.which(executable)
        if not output:
            return True
        # TODO: Check if there is a clever way to determine if system adb is
        # good enough.
        root_dir = os.environ.get(constants.ANDROID_BUILD_TOP, '')
        return os.path.commonprefix([output, root_dir]) != root_dir

    def get_test_runner_build_reqs(self):
        """Return the build requirements.

        Returns:
            Set of build targets.
        """
        build_req = self._BUILD_REQ
        # Use different base build requirements if google-tf is around.
        if self.module_info.is_module(constants.GTF_MODULE):
            build_req = {constants.GTF_TARGET}
        # Always add ATest's own TF target.
        build_req.add(constants.ATEST_TF_MODULE)
        # Add adb if we can't find it.
        for executable in EXEC_DEPENDENCIES:
            if self._is_missing_exec(executable):
                if self.module_info.is_module(executable):
                    build_req.add(executable)
        return build_req

    def _parse_extra_args(self, test_infos, extra_args):
        """Convert the extra args into something tf can understand.

        Args:
            extra_args: Dict of args

        Returns:
            Tuple of args to append and args not supported.
        """
        args_to_append, args_not_supported = extra_args_to_tf_args(
            self.module_info, test_infos, extra_args)

        # Set exclude instant app annotation for non-instant mode run.
        if (constants.INSTANT not in extra_args and
            self._has_instant_app_config(test_infos, self.module_info)):
            args_to_append.append(constants.TF_TEST_ARG)
            args_to_append.append(
                '{tf_class}:{option_name}:{option_value}'.format(
                    tf_class=constants.TF_AND_JUNIT_CLASS,
                    option_name=constants.TF_EXCLUDE_ANNOTATE,
                    option_value=constants.INSTANT_MODE_ANNOTATE))
        # Force append --enable-parameterized-modules if args_to_append has
        # --module-parameter in args_to_append
        if constants.TF_MODULE_PARAMETER in args_to_append:
            if constants.TF_ENABLE_PARAMETERIZED_MODULES not in args_to_append:
                args_to_append.append(constants.TF_ENABLE_PARAMETERIZED_MODULES)
        # If all the test config has config with auto enable parameter, force
        # exclude those default parameters(ex: instant_app, secondary_user)
        if self._is_all_tests_parameter_auto_enabled(test_infos):
            if constants.TF_ENABLE_PARAMETERIZED_MODULES not in args_to_append:
                args_to_append.append(constants.TF_ENABLE_PARAMETERIZED_MODULES)
                for exclude_parameter in constants.DEFAULT_EXCLUDE_PARAS:
                    args_to_append.append('--exclude-module-parameters')
                    args_to_append.append(exclude_parameter)
        return args_to_append, args_not_supported

    def _generate_metrics_folder(self, extra_args):
        """Generate metrics folder."""
        metrics_folder = ''
        if extra_args.get(constants.PRE_PATCH_ITERATIONS):
            metrics_folder = os.path.join(self.results_dir, 'baseline-metrics')
        elif extra_args.get(constants.POST_PATCH_ITERATIONS):
            metrics_folder = os.path.join(self.results_dir, 'new-metrics')
        return metrics_folder

    def _generate_iterations(self, extra_args):
        """Generate iterations."""
        iterations = 1
        if extra_args.get(constants.PRE_PATCH_ITERATIONS):
            iterations = extra_args.pop(constants.PRE_PATCH_ITERATIONS)
        elif extra_args.get(constants.POST_PATCH_ITERATIONS):
            iterations = extra_args.pop(constants.POST_PATCH_ITERATIONS)
        return iterations

    def generate_run_commands(self, test_infos, extra_args, port=None):
        """Generate a single run command from TestInfos.

        Args:
            test_infos: A set of TestInfo instances.
            extra_args: A Dict of extra args to append.
            port: Optional. An int of the port number to send events to. If
                  None, then subprocess reporter in TF won't try to connect.

        Returns:
            A list that contains the string of atest tradefed run command.
            Only one command is returned.
        """
        args = self._create_test_args(test_infos)
        metrics_folder = self._generate_metrics_folder(extra_args)

        # Create a copy of args as more args could be added to the list.
        test_args = list(args)
        if port:
            test_args.extend(['--subprocess-report-port', str(port)])
        if metrics_folder:
            test_args.extend(['--metrics-folder', metrics_folder])
            logging.info('Saved metrics in: %s', metrics_folder)
        if extra_args.get(constants.INVOCATION_ID, None):
            test_args.append('--invocation-data invocation_id=%s'
                             % extra_args[constants.INVOCATION_ID])
        if extra_args.get(constants.WORKUNIT_ID, None):
            test_args.append('--invocation-data work_unit_id=%s'
                             % extra_args[constants.WORKUNIT_ID])
        if extra_args.get(constants.LOCAL_BUILD_ID, None):
            # TODO: (b/207584685) Replace with TF local build solutions.
            test_args.append('--use-stub-build true')
            test_args.append('--stub-build-id %s'
                             % extra_args[constants.LOCAL_BUILD_ID])
            test_args.append('--stub-build-target %s'
                             % extra_args[constants.BUILD_TARGET])
        if extra_args.get(constants.ENABLE_DEVICE_PREPARER, False):
            test_args.append('--template:map preparers=%s'
                             % constants.DEVICE_SETUP_PREPARER)
        for info in test_infos:
            if constants.TEST_WITH_MAINLINE_MODULES_RE.match(info.test_name):
                test_args.append(constants.TF_ENABLE_MAINLINE_PARAMETERIZED_MODULES)
                break
        # For detailed logs, set TF options log-level/log-level-display as
        # 'VERBOSE' by default.
        log_level = 'VERBOSE'
        test_args.extend(['--log-level-display', log_level])
        test_args.extend(['--log-level', log_level])
        # Set no-early-device-release by default to speed up TF teardown time.
        if not constants.TF_EARLY_DEVICE_RELEASE in extra_args:
            test_args.extend(['--no-early-device-release'])

        args_to_add, args_not_supported = self._parse_extra_args(test_infos, extra_args)

        # If multiple devices in test config, automatically append
        # --replicate-parent-setup and --multi-device-count
        device_count = atest_configs.GLOBAL_ARGS.device_count_config
        if device_count and device_count > 1:
            args_to_add.append('--replicate-parent-setup')
            args_to_add.append('--multi-device-count')
            args_to_add.append(str(device_count))

        # TODO(b/122889707) Remove this after finding the root cause.
        env_serial = os.environ.get(constants.ANDROID_SERIAL)
        # Use the env variable ANDROID_SERIAL if it's set by user but only when
        # the target tests are not deviceless tests.
        if env_serial and '--serial' not in args_to_add and '-n' not in args_to_add:
            args_to_add.append("--serial")
            args_to_add.append(env_serial)

        test_args.extend(args_to_add)
        if args_not_supported:
            logging.info('%s does not support the following args %s',
                         self.EXECUTABLE, args_not_supported)

        # Only need to check one TestInfo to determine if the tests are
        # configured in TEST_MAPPING.
        for_test_mapping = test_infos and test_infos[0].from_test_mapping
        test_args.extend(atest_utils.get_result_server_args(for_test_mapping))
        self.run_cmd_dict['args'] = ' '.join(test_args)
        self.run_cmd_dict['tf_customize_template'] = (
            self._extract_customize_tf_templates(extra_args, test_infos))

        # Copy symbols if there are tests belong to native test.
        self._handle_native_tests(test_infos)
        return [self._RUN_CMD.format(**self.run_cmd_dict)]

    def _flatten_test_infos(self, test_infos):
        """Sort and group test_infos by module_name and sort and group filters
        by class name.

            Example of three test_infos in a set:
                Module1, {(classA, {})}
                Module1, {(classB, {Method1})}
                Module1, {(classB, {Method2}}
            Becomes a set with one element:
                Module1, {(ClassA, {}), (ClassB, {Method1, Method2})}
            Where:
                  Each line is a test_info namedtuple
                  {} = Frozenset
                  () = TestFilter namedtuple

        Args:
            test_infos: A set of TestInfo namedtuples.

        Returns:
            A set of TestInfos flattened.
        """
        results = set()
        key = lambda x: x.test_name
        for module, group in atest_utils.sort_and_group(test_infos, key):
            # module is a string, group is a generator of grouped TestInfos.
            # Module Test, so flatten test_infos:
            no_filters = False
            filters = set()
            test_runner = None
            test_finder = None
            build_targets = set()
            data = {}
            module_args = []
            for test_info_i in group:
                data.update(test_info_i.data)
                # Extend data with constants.TI_MODULE_ARG instead of
                # overwriting.
                module_args.extend(test_info_i.data.get(
                    constants.TI_MODULE_ARG, []))
                test_runner = test_info_i.test_runner
                test_finder = test_info_i.test_finder
                build_targets |= test_info_i.build_targets
                test_filters = test_info_i.data.get(constants.TI_FILTER)
                if not test_filters or no_filters:
                    # test_info wants whole module run, so hardcode no filters.
                    no_filters = True
                    filters = set()
                    continue
                filters |= test_filters
            if module_args:
                data[constants.TI_MODULE_ARG] = module_args
            data[constants.TI_FILTER] = self._flatten_test_filters(filters)
            results.add(
                test_info.TestInfo(test_name=module,
                                   test_runner=test_runner,
                                   test_finder=test_finder,
                                   build_targets=build_targets,
                                   data=data))
        return results

    @staticmethod
    def _flatten_test_filters(filters):
        """Sort and group test_filters by class_name.

            Example of three test_filters in a frozenset:
                classA, {}
                classB, {Method1}
                classB, {Method2}
            Becomes a frozenset with these elements:
                classA, {}
                classB, {Method1, Method2}
            Where:
                Each line is a TestFilter namedtuple
                {} = Frozenset

        Args:
            filters: A frozenset of test_filters.

        Returns:
            A frozenset of test_filters flattened.
        """
        results = set()
        key = lambda x: x.class_name
        for class_name, group in atest_utils.sort_and_group(filters, key):
            # class_name is a string, group is a generator of TestFilters
            assert class_name is not None
            methods = set()
            for test_filter in group:
                if not test_filter.methods:
                    # Whole class should be run
                    methods = set()
                    break
                methods |= test_filter.methods
            results.add(test_info.TestFilter(class_name, frozenset(methods)))
        return frozenset(results)

    def _is_all_tests_parameter_auto_enabled(self, test_infos):
        """Check if all the test infos are parameter auto enabled.

        Args:
            test_infos: A set of TestInfo instances.

        Returns: True if all tests are parameter auto enabled, False otherwise.
        """
        for info in test_infos:
            if not self._is_parameter_auto_enabled_cfg(info, self.module_info):
                return False
        return True

    def _create_test_args(self, test_infos):
        """Compile TF command line args based on the given test infos.

        Args:
            test_infos: A set of TestInfo instances.

        Returns: A list of TF arguments to run the tests.
        """
        args = []
        if not test_infos:
            return []

        test_infos = self._flatten_test_infos(test_infos)
        has_integration_test = False

        # Because current --include-filter arg will not working if ATest pass
        # both --module and --include-filter to TF, only test by --module will
        # be run. Make a check first, only use --module if all tests are all
        # parameter auto enabled.
        use_module_arg = self._is_all_tests_parameter_auto_enabled(test_infos)

        for info in test_infos:
            # Integration test exists in TF's jar, so it must have the option
            # if it's integration finder.
            if info.test_finder in _INTEGRATION_FINDERS:
                has_integration_test = True
            # For non-paramertize test module, use --include-filter, but for
            # tests which have auto enable paramertize config use --module
            # instead.
            if (use_module_arg
                and self._is_parameter_auto_enabled_cfg(
                    info, self.module_info)):
                args.extend([constants.TF_MODULE_FILTER, info.test_name])
            else:
                args.extend([constants.TF_INCLUDE_FILTER, info.test_name])
            for option in info.data.get(constants.TI_MODULE_ARG, []):
                if constants.TF_INCLUDE_FILTER_OPTION == option[0]:
                    suite_filter = (
                        constants.TF_SUITE_FILTER_ARG_VALUE_FMT.format(
                            test_name=info.test_name, option_value=option[1]))
                    args.extend([constants.TF_INCLUDE_FILTER, suite_filter])
                elif constants.TF_EXCLUDE_FILTER_OPTION == option[0]:
                    suite_filter = (
                        constants.TF_SUITE_FILTER_ARG_VALUE_FMT.format(
                            test_name=info.test_name, option_value=option[1]))
                    args.extend([constants.TF_EXCLUDE_FILTER, suite_filter])
                else:
                    module_arg = (
                        constants.TF_MODULE_ARG_VALUE_FMT.format(
                            test_name=info.test_name, option_name=option[0],
                            option_value=option[1]))
                    args.extend([constants.TF_MODULE_ARG, module_arg])

        # Add ATest include filter
        args.extend(get_include_filter(test_infos))

        # TODO (b/141090547) Pass the config path to TF to load configs.
        # Compile option in TF if finder is not INTEGRATION or not set.
        if not has_integration_test:
            args.append(constants.TF_SKIP_LOADING_CONFIG_JAR)
        return args

    def _extract_rerun_options(self, extra_args):
        """Extract rerun options to a string for output.

        Args:
            extra_args: Dict of extra args for test runners to use.

        Returns: A string of rerun options.
        """
        extracted_options = ['{} {}'.format(arg, extra_args[arg])
                             for arg in extra_args
                             if arg in self._RERUN_OPTION_GROUP]
        return ' '.join(extracted_options)

    def _extract_customize_tf_templates(self, extra_args, test_infos):
        """Extract tradefed template options to a string for output.

        Args:
            extra_args: Dict of extra args for test runners to use.
            test_infos: A set of TestInfo instances.

        Returns: A string of tradefed template options.
        """
        tf_templates = extra_args.get(constants.TF_TEMPLATE, [])
        for info in test_infos:
            if info.aggregate_metrics_result:
                template_key = 'metric_post_processor'
                template_value = (
                    'google/template/postprocessors/metric-file-aggregate')
                tf_templates.append(f'{template_key}={template_value}')
        return ' '.join(['--template:map %s' % x for x in tf_templates])

    def _handle_log_associations(self, event_handlers):
        """Handle TF's log associations information data.

        log_association dict:
        {'loggedFile': '/tmp/serial-util11375755456514097276.ser',
         'dataName': 'device_logcat_setup_127.0.0.1:58331',
         'time': 1602038599.856113},

        Args:
            event_handlers: Dict of {socket_object:EventHandler}.

        """
        log_associations = []
        for _, event_handler in event_handlers.items():
            if event_handler.log_associations:
                log_associations += event_handler.log_associations
        device_test_end_log_time = ''
        device_teardown_log_time = ''
        for log_association in log_associations:
            if 'device_logcat_test' in log_association.get('dataName', ''):
                device_test_end_log_time = log_association.get('time')
            if 'device_logcat_teardown' in log_association.get('dataName', ''):
                device_teardown_log_time = log_association.get('time')
        if device_test_end_log_time and device_teardown_log_time:
            teardowntime = (float(device_teardown_log_time) -
                            float(device_test_end_log_time))
            logging.debug('TF logcat teardown time=%s seconds.', teardowntime)
            metrics.LocalDetectEvent(
                detect_type=DetectType.TF_TEARDOWN_LOGCAT,
                result=int(teardowntime))

    @staticmethod
    def _has_instant_app_config(test_infos, mod_info):
        """Check if one of the input tests defined instant app mode in config.

        Args:
            test_infos: A set of TestInfo instances.
            mod_info: ModuleInfo object.

        Returns: True if one of the tests set up instant app mode.
        """
        for tinfo in test_infos:
            test_config, _ = test_finder_utils.get_test_config_and_srcs(
                tinfo, mod_info)
            if test_config:
                parameters = atest_utils.get_config_parameter(test_config)
                if constants.TF_PARA_INSTANT_APP in parameters:
                    return True
        return False

    @staticmethod
    def _is_parameter_auto_enabled_cfg(tinfo, mod_info):
        """Check if input tests contains auto enable support parameters.

        Args:
            test_infos: A set of TestInfo instances.
            mod_info: ModuleInfo object.

        Returns: True if input test has parameter setting which is not in the
                 exclude list.
        """
        test_config, _ = test_finder_utils.get_test_config_and_srcs(
            tinfo, mod_info)
        if test_config:
            parameters = atest_utils.get_config_parameter(test_config)
            if (parameters - constants.DEFAULT_EXCLUDE_PARAS
                - constants.DEFAULT_EXCLUDE_NOT_PARAS):
                return True
        return False

    def _handle_native_tests(self, test_infos):
        """Handling some extra tasks for running native tests from tradefed.

        Args:
            test_infos: A set of TestInfo instances.
        """
        for tinfo in test_infos:
            test_config, _ = test_finder_utils.get_test_config_and_srcs(
                tinfo, self.module_info)
            if test_config:
                module_name, device_path = atest_utils.get_config_gtest_args(
                    test_config)
                if module_name and device_path:
                    atest_utils.copy_native_symbols(module_name, device_path)


def generate_annotation_filter_args(
        arg_value: Any, mod_info: module_info.ModuleInfo,
        test_infos: List[test_info.TestInfo]) -> List[str]:
    """Generate TF annotation filter arguments.

    Args:
        arg_value: Argument value for annotation filter.
        mod_info: ModuleInfo object.
        test_infos: A set of TestInfo instances.

    Returns:
        List of TF annotation filter arguments.
    """
    annotation_filter_args = []
    for info in test_infos:
        test_name = info.test_name
        for keyword in arg_value:
            annotation = atest_utils.get_full_annotation_class_name(
                mod_info.get_module_info(test_name), keyword)
            if annotation:
                module_arg = (constants.TF_MODULE_ARG_VALUE_FMT.format(
                    test_name=test_name,
                    option_name=constants.INCLUDE_ANNOTATION,
                    option_value=annotation))
                annotation_filter_args.extend([constants.TF_MODULE_ARG, module_arg])
            logging.error(
                atest_utils.colorize(
                    f'Cannot find similar annotation: {keyword}',
                    constants.RED))
    return annotation_filter_args


def extra_args_to_tf_args(mod_info: module_info.ModuleInfo,
                          test_infos: List[test_info.TestInfo],
                          extra_args: trb.ARGS) -> Tuple[trb.ARGS, trb.ARGS]:
    """Convert the extra args into atest_tf_test_runner supported args.

    Args:
        mod_info: ModuleInfo object.
        test_infos: A set of TestInfo instances.
        extra_args: Dict of args

    Returns:
        Tuple of ARGS that atest_tf supported and not supported.
    """
    supported_args = []
    unsupported_args = []

    def constant_list(*value):
        return lambda *_: value

    # pylint: disable=unused-argument
    def print_message(message):
        def inner(*args):
            print(message)
            return []
        return inner

    # Mapping supported TF arguments to the processing function.
    supported_tf_args = dict({
        constants.WAIT_FOR_DEBUGGER:
            constant_list('--wait-for-debugger'),
        constants.DISABLE_INSTALL:
            constant_list('--disable-target-preparers'),
        constants.SERIAL:
            lambda arg_value, *_:
            [j for d in arg_value for j in ('--serial', d)],
        constants.SHARDING:
            lambda arg_value, *_: ['--shard-count',
                                   str(arg_value)],
        constants.DISABLE_TEARDOWN:
            constant_list('--disable-teardown'),
        constants.HOST:
            constant_list('-n', '--prioritize-host-config',
                          '--skip-host-arch-check'),
        constants.CUSTOM_ARGS:
            # custom args value is a list.
            lambda arg_value, *_: arg_value,
        constants.ALL_ABI:
            constant_list('--all-abi'),
        constants.INSTANT:
            constant_list(constants.TF_ENABLE_PARAMETERIZED_MODULES,
                          constants.TF_MODULE_PARAMETER, 'instant_app'),
        constants.USER_TYPE:
            lambda arg_value, *_: [
                constants.TF_ENABLE_PARAMETERIZED_MODULES,
                '--enable-optional-parameterization',
                constants.TF_MODULE_PARAMETER,
                str(arg_value)
            ],
        constants.ITERATIONS:
            lambda arg_value, *_: [
                '--retry-strategy', constants.ITERATIONS,
                '--max-testcase-run-count', str(arg_value)
            ],
        constants.RERUN_UNTIL_FAILURE:
            lambda arg_value, *_: [
                '--retry-strategy', constants.RERUN_UNTIL_FAILURE,
                '--max-testcase-run-count', str(arg_value)
            ],
        constants.RETRY_ANY_FAILURE:
            lambda arg_value, *_: [
                '--retry-strategy', constants.RETRY_ANY_FAILURE,
                '--max-testcase-run-count', str(arg_value)
            ],
        constants.COLLECT_TESTS_ONLY:
            constant_list('--collect-tests-only'),
        constants.NO_ENABLE_ROOT:
            constant_list('--no-enable-root'),
        constants.TF_DEBUG:
            print_message("Please attach process to your IDE..."),
        constants.ANNOTATION_FILTER:
            generate_annotation_filter_args,
        constants.TEST_FILTER:
            lambda arg_value, *_: [
                '--test-arg',
                'com.android.tradefed.testtype.AndroidJUnitTest:'
                f'include-filter:{arg_value}',
                '--test-arg',
                'com.android.tradefed.testtype.GTest:native-test-flag:'
                f'--gtest_filter={arg_value}'
            ],
        constants.TEST_TIMEOUT:
            lambda arg_value, *_: [
                '--test-arg',
                'com.android.tradefed.testtype.AndroidJUnitTest:'
                f'shell-timeout:{arg_value}',
                '--test-arg',
                'com.android.tradefed.testtype.AndroidJUnitTest:'
                f'test-timeout:{arg_value}',
                '--test-arg',
                'com.android.tradefed.testtype.HostGTest:'
                f'native-test-timeout:{arg_value}',
                '--test-arg',
                'com.android.tradefed.testtype.GTest:'
                f'native-test-timeout:{arg_value}',
            ]
    })

    for arg in extra_args:
        if arg in supported_tf_args:
            tf_args = supported_tf_args[arg](extra_args[arg], mod_info,
                                             test_infos)
            if tf_args:
                supported_args.extend(tf_args)
            continue

        if arg in (constants.TF_TEMPLATE,
                   constants.TF_EARLY_DEVICE_RELEASE,
                   constants.INVOCATION_ID,
                   constants.WORKUNIT_ID,
                   constants.REQUEST_UPLOAD_RESULT,
                   constants.LOCAL_BUILD_ID,
                   constants.BUILD_TARGET,
                   constants.ENABLE_DEVICE_PREPARER,
                   constants.DRY_RUN,
                   constants.VERIFY_ENV_VARIABLE,
                   constants.FLAKES_INFO,
                   constants.DISABLE_UPLOAD_RESULT):
            continue
        unsupported_args.append(arg)
    return supported_args, unsupported_args

def get_include_filter(test_infos: List[test_info.TestInfo]) -> List[str]:
    """Generate a list of tradefed filter argument from TestInfos.

    The tradefed argument format should be:
    atest-include-filter <module-name>:<include-filter-value>
    """
    tf_args = []
    for info in test_infos:
        filters = set()
        for test_info_filter in info.data.get(constants.TI_FILTER, []):
            filters.update(test_info_filter.to_set_of_tf_strings())
        for test_filter in filters:
            filter_arg = constants.TF_ATEST_INCLUDE_FILTER_VALUE_FMT.format(
                test_name=info.test_name, test_filter=test_filter)
            tf_args.extend([constants.TF_ATEST_INCLUDE_FILTER, filter_arg])
    return tf_args
