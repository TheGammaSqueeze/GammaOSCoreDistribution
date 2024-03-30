# Copyright 2018, The Android Open Source Project
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
TestInfo class.
"""

from collections import namedtuple

import constants


TestFilterBase = namedtuple('TestFilter', ['class_name', 'methods'])


class TestInfo:
    """Information needed to identify and run a test."""

    # pylint: disable=too-many-arguments
    def __init__(self, test_name, test_runner, build_targets, data=None,
                 suite=None, module_class=None, install_locations=None,
                 test_finder='', compatibility_suites=None,
                 mainline_modules=None, robo_type=None):
        """Init for TestInfo.

        Args:
            test_name: String of test name.
            test_runner: String of test runner.
            build_targets: Set of build targets.
            data: Dict of data for test runners to use.
            suite: Suite for test runners to use.
            module_class: A list of test classes. It's a snippet of class
                        in module_info. e.g. ["EXECUTABLES",  "NATIVE_TESTS"]
            install_locations: Set of install locations.
                        e.g. set(['host', 'device'])
            test_finder: String of test finder.
            compatibility_suites: A list of compatibility_suites. It's a
                        snippet of compatibility_suites in module_info. e.g.
                        ["device-tests",  "vts10"]
            mainline_modules: A string of mainline modules.
                    e.g. 'some1.apk+some2.apex+some3.apks'
            robo_type: Integer of robolectric types.
                       0: Not robolectric test
                       1. Modern robolectric test(Tradefed Runner)
                       2: Legacy robolectric test(Robolectric Runner)
        """
        self.test_name = test_name
        self.test_runner = test_runner
        self.build_targets = build_targets
        self.data = data if data else {}
        self.suite = suite
        self.module_class = module_class if module_class else []
        self.robo_type = robo_type if robo_type else 0
        self.install_locations = (install_locations if install_locations
                                  else set())
        # True if the TestInfo is built from a test configured in TEST_MAPPING.
        self.from_test_mapping = False
        # True if the test should run on host and require no device. The
        # attribute is only set through TEST_MAPPING file.
        self.host = False
        self.test_finder = test_finder
        self.compatibility_suites = (compatibility_suites
                                     if compatibility_suites else [])
        # True if test need to generate aggregate metrics result.
        self.aggregate_metrics_result = False
        self.mainline_modules = mainline_modules if mainline_modules else ""

    def __str__(self):
        host_info = (' - runs on host without device required.' if self.host
                     else '')
        return (f'test_name:{self.test_name} - '
                f'test_runner:{self.test_runner} - '
                f'build_targets:{self.build_targets} - data:{self.data} - '
                f'suite:{self.suite} - module_class:{self.module_class} - '
                f'install_locations:{self.install_locations}{host_info} - '
                f'test_finder:{self.test_finder} - '
                f'compatibility_suites:{self.compatibility_suites} - '
                f'mainline_modules:{self.mainline_modules} - '
                f'aggregate_metrics_result:{self.aggregate_metrics_result} - '
                f'robo_type:{self.robo_type}')

    def get_supported_exec_mode(self):
        """Get the supported execution mode of the test.

        Determine which execution mode does the test support by strategy:
        Modern Robolectric --> 'host'
        Legacy Robolectric --> 'both'
        JAVA_LIBRARIES --> 'both'
        Not native tests or installed only in out/target --> 'device'
        Installed only in out/host --> 'both'
        Installed under host and target --> 'both'

        Return:
            String of execution mode.
        """
        install_path = self.install_locations
        if not self.module_class:
            return constants.DEVICE_TEST
        # Let Robolectric test support host/both accordingly.
        if self.robo_type == constants.ROBOTYPE_MODERN:
            return constants.DEVICELESS_TEST
        if self.robo_type == constants.ROBOTYPE_LEGACY:
            return constants.BOTH_TEST
        if constants.MODULE_CLASS_JAVA_LIBRARIES in self.module_class:
            return constants.BOTH_TEST
        if not install_path:
            return constants.DEVICE_TEST
        # Non-Native test runs on device-only.
        if constants.MODULE_CLASS_NATIVE_TESTS not in self.module_class:
            return constants.DEVICE_TEST
        # Native test with install path as host should be treated as both.
        # Otherwise, return device test.
        if install_path == {constants.DEVICE_TEST}:
            return constants.DEVICE_TEST
        return constants.BOTH_TEST

    def get_test_paths(self):
        """Get the relative path of test_info.

        Search build target's MODULE-IN as the test path.

        Return:
            A list of string of the relative path for test, None if test
            path information not found.
        """
        test_paths = []
        for build_target in self.build_targets:
            if str(build_target).startswith(constants.MODULES_IN):
                test_paths.append(
                    str(build_target).replace(
                        constants.MODULES_IN, '').replace('-', '/'))
        return test_paths if test_paths else None

class TestFilter(TestFilterBase):
    """Information needed to filter a test in Tradefed"""

    def to_set_of_tf_strings(self):
        """Return TestFilter as set of strings in TradeFed filter format."""
        if self.methods:
            return {'%s#%s' % (self.class_name, m) for m in self.methods}
        return {self.class_name}
