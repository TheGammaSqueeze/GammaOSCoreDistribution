#  Copyright (C) 2022 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

"""The entry point for Nearby Mainline multi devices end-to-end test suite."""

import logging
import sys

from mobly import suite_runner

import initial_pairing_test
import seeker_discover_provider_test
import seeker_show_halfsheet_test

_BOOTSTRAP_LOGGING_FILENAME = '/tmp/nearby_multi_devices_test_suite_log.txt'
_TEST_CLASSES_LIST = [
    seeker_discover_provider_test.SeekerDiscoverProviderTest,
    seeker_show_halfsheet_test.SeekerShowHalfSheetTest,
    initial_pairing_test.InitialPairingTest,
]


def _valid_argument(arg: str) -> bool:
    return arg.startswith(('--config', '-c', '--tests', '--test_case'))


if __name__ == '__main__':
    logging.basicConfig(filename=_BOOTSTRAP_LOGGING_FILENAME, level=logging.INFO)
    suite_runner.run_suite(argv=[arg for arg in sys.argv if _valid_argument(arg)],
                           test_classes=_TEST_CLASSES_LIST)
