#!/usr/bin/env python3
#
# Copyright 2021, The Android Open Source Project
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

"""Tests the correctness of config files for LTP tests launcher."""

import pprint
import re
import unittest


class LTPConfigTest(unittest.TestCase):
    """Tests the correctness of LTP tests configuration."""

    @staticmethod
    def stable_test_entry_name(stable_tuple, no_suffix=False):
        t = stable_tuple[0]
        if no_suffix:
            for s in ["_32bit", "_64bit"]:
                if t.endswith(s):
                    t = t.removesuffix(s)
                    break
        return t

    def get_parsed_tests(self, no_suffix=False):
        from configs import stable_tests
        from configs import disabled_tests

        return {
            "STABLE_TESTS": [self.stable_test_entry_name(t, no_suffix) for t in stable_tests.STABLE_TESTS],
            "DISABLED_TESTS": disabled_tests.DISABLED_TESTS,
            "DISABLED_TESTS_HWASAN": disabled_tests.DISABLED_TESTS_HWASAN,
        }

    def test_configs_sorted_methods(self):
        parsed_tests = self.get_parsed_tests()

        for t in parsed_tests:
            with self.subTest(container=t):
                unsorted_tests = parsed_tests[t]
                sorted_tests = sorted(unsorted_tests)
                for i in range(0, len(unsorted_tests)):
                    self.assertEqual(unsorted_tests[i], sorted_tests[i])

    def test_configs_correct_format(self):
        parsed_tests = self.get_parsed_tests()

        for container in ["STABLE_TESTS", "DISABLED_TESTS", "DISABLED_TESTS_HWASAN"]:
            with self.subTest(container=container):
                test_syntax = re.compile(r"\A[\w|-]+\.[\w|-]+_(32|64)bit\Z")
                for t in parsed_tests[container]:
                    self.assertIsNotNone(test_syntax.match(t), '"{}" should be in the form "<class>.<method>_{{32,64}}bit"'.format(t))

    def test_configs_no_duplicate_methods(self):
        parsed_tests = self.get_parsed_tests()
        success = True
        multiple_occurrences = {}

        for test_container in parsed_tests:
            with self.subTest(container=test_container):
                tests_list = parsed_tests[test_container]
                for test in tests_list:
                    occurrences = tests_list.count(test)
                    if occurrences > 1:
                        multiple_occurrences[test] = occurrences
                        success = False
        self.assertTrue(success, 'Test(s) with multiple occurrencies: \n{}'.format(pprint.pformat(multiple_occurrences)))

    def test_configs_collide(self):
        parsed_tests = self.get_parsed_tests()
        success = True

        """
        DISABLED_TESTS_HWASAN is currently ignored for matching with
        STABLE_TESTS. This because tests in DISABLED_TESTS_HWASAN can be
        executed on non-HWASAN devices.
        """

        multiple_occurrences = {
            "DISABLED_TESTS": {},
            "DISABLED_TESTS_HWASAN": {},
        }

        for container_name in ["STABLE_TESTS", "DISABLED_TESTS_HWASAN"]:
            for st in parsed_tests[container_name]:
                """
                gen_ltp_config.py filters the stable tests by testing the
                existence of the disabled test substring into the stable test
                substring. Because of this, this test has to do the same, and
                list::count() is not an option.
                """
                for dt in parsed_tests["DISABLED_TESTS"]:
                    if dt in st:
                        multiple_occurrences["DISABLED_TESTS"][st] = dt
                        success = False
        self.assertTrue(success, 'Test(s) in {} also in {}: \n{}'.format(
            container_name,
            "DISABLED_TESTS",
            pprint.pformat(multiple_occurrences)))


if __name__ == '__main__':
    unittest.main(verbosity=3)
