#!/usr/bin/env python3
#
# Copyright 2019, The Android Open Source Project
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

"""Unittest for atest_tools."""

# pylint: disable=line-too-long

import os
import pickle
import subprocess
import unittest

from unittest import mock

import atest_utils as au
import unittest_constants as uc

from atest_enum import ExitCode
from tools import atest_tools

SEARCH_ROOT = uc.TEST_DATA_DIR
PRUNEPATH = uc.TEST_CONFIG_DATA_DIR
LOCATE = atest_tools.LOCATE
UPDATEDB = atest_tools.UPDATEDB

class AtestToolsUnittests(unittest.TestCase):
    """"Unittest Class for atest_tools.py."""

    @mock.patch('constants.INDEX_DIR', uc.INDEX_DIR)
    @mock.patch('constants.LOCATE_CACHE_MD5', uc.LOCATE_CACHE_MD5)
    @mock.patch('constants.LOCATE_CACHE', uc.LOCATE_CACHE)
    @mock.patch('tools.atest_tools.SEARCH_TOP', uc.TEST_DATA_DIR)
    def test_index_targets(self):
        """Test method index_targets."""
        if atest_tools.has_command(UPDATEDB) and atest_tools.has_command(LOCATE):
            # 1. Test run_updatedb() is functional.
            atest_tools.run_updatedb(SEARCH_ROOT, uc.LOCATE_CACHE,
                                     prunepaths=PRUNEPATH)
            # test_config/ is excluded so that a.xml won't be found.
            locate_cmd1 = [LOCATE, '-d', uc.LOCATE_CACHE, '/a.xml']
            # locate always return 0 when not found, therefore check null
            # return if nothing found.
            output = subprocess.check_output(locate_cmd1).decode()
            self.assertEqual(output, '')

            # module-info.json can be found in the search_root.
            locate_cmd2 = [LOCATE, '-d', uc.LOCATE_CACHE, 'module-info.json']
            self.assertEqual(subprocess.call(locate_cmd2), 0)

            # 2. Test get_java_result is functional.
            _cache = {}
            jproc = au.run_multi_proc(
                    func=atest_tools.get_java_result, args=[uc.LOCATE_CACHE],
                    kwargs={'class_index':uc.CLASS_INDEX,
                            'package_index':uc.PACKAGE_INDEX,
                            'qclass_index':uc.QCLASS_INDEX})
            jproc.join()
            # 2.1 Test finding a Java class.
            with open(uc.CLASS_INDEX, 'rb') as cache:
                _cache = pickle.load(cache)
            self.assertIsNotNone(_cache.get('PathTesting'))
            # 2.2 Test finding a package.
            with open(uc.PACKAGE_INDEX, 'rb') as cache:
                _cache = pickle.load(cache)
            self.assertIsNotNone(_cache.get(uc.PACKAGE))
            # 2.3 Test finding a fully qualified class name.
            with open(uc.QCLASS_INDEX, 'rb') as cache:
                _cache = pickle.load(cache)
            self.assertIsNotNone(_cache.get('android.jank.cts.ui.PathTesting'))

            # 3. Test get_cc_result is functional.
            cproc = au.run_multi_proc(
                    func=atest_tools.get_cc_result, args=[uc.LOCATE_CACHE],
                    kwargs={'cc_class_index':uc.CC_CLASS_INDEX})
            cproc.join()
            # 3.1 Test finding a CC class.
            with open(uc.CC_CLASS_INDEX, 'rb') as cache:
                _cache = pickle.load(cache)
            self.assertIsNotNone(_cache.get('HelloWorldTest'))
            # 4. Clean up.
            targets_to_delete = (uc.CC_CLASS_INDEX,
                                 uc.CLASS_INDEX,
                                 uc.LOCATE_CACHE,
                                 uc.PACKAGE_INDEX,
                                 uc.QCLASS_INDEX)
            for idx in targets_to_delete:
                os.remove(idx)
        else:
            self.assertEqual(atest_tools.has_command(UPDATEDB), False)
            self.assertEqual(atest_tools.has_command(LOCATE), False)

    def test_get_report_file(self):
        """Test method get_report_file."""
        report_file = '/tmp/acloud_status.json'

        arg_with_equal = '-a --report-file={} --all'.format(report_file)
        self.assertEqual(atest_tools.get_report_file('/abc', arg_with_equal),
                         report_file)

        arg_with_equal = '-b --report_file={} --ball'.format(report_file)
        self.assertEqual(atest_tools.get_report_file('/abc', arg_with_equal),
                         report_file)

        arg_without_equal = '-c --report-file {} --call'.format(report_file)
        self.assertEqual(atest_tools.get_report_file('/abc', arg_without_equal),
                         report_file)

        arg_without_equal = '-d --report_file {} --dall'.format(report_file)
        self.assertEqual(atest_tools.get_report_file('/abc', arg_without_equal),
                         report_file)

        arg_without_report = '-e --build-id 1234567'
        self.assertEqual(atest_tools.get_report_file('/tmp', arg_without_report),
                         report_file)

    def test_probe_acloud_status(self):
        """Test method prob_acloud_status."""
        success = os.path.join(SEARCH_ROOT, 'acloud', 'create_success.json')
        self.assertEqual(atest_tools.probe_acloud_status(success),
                         ExitCode.SUCCESS)

        failure = os.path.join(SEARCH_ROOT, 'acloud', 'create_failure.json')
        self.assertEqual(atest_tools.probe_acloud_status(failure),
                         ExitCode.AVD_CREATE_FAILURE)

        inexistence = os.path.join(SEARCH_ROOT, 'acloud', 'inexistence.json')
        self.assertEqual(atest_tools.probe_acloud_status(inexistence),
                         ExitCode.AVD_INVALID_ARGS)

    def test_get_acloud_duration(self):
        """Test method get_acloud_duration."""
        success = os.path.join(SEARCH_ROOT, 'acloud', 'create_success.json')
        success_duration = 152.659824
        self.assertEqual(atest_tools.get_acloud_duration(success),
                         success_duration)

        failure = os.path.join(SEARCH_ROOT, 'acloud', 'create_failure.json')
        failure_duration = 178.621254
        self.assertEqual(atest_tools.get_acloud_duration(failure),
                         failure_duration)

if __name__ == "__main__":
    unittest.main()
