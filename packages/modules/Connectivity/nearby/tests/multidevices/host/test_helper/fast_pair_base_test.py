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

"""Base for all Nearby Mainline Fast Pair end-to-end test cases."""

from typing import List, Tuple

from mobly import base_test
from mobly import signals
from mobly.controllers import android_device

from test_helper import constants
from test_helper import fast_pair_provider_simulator
from test_helper import fast_pair_seeker

# Abbreviations for common use type.
AndroidDevice = android_device.AndroidDevice
FastPairProviderSimulator = fast_pair_provider_simulator.FastPairProviderSimulator
FastPairSeeker = fast_pair_seeker.FastPairSeeker
REQUIRED_BUILD_FINGERPRINT = constants.FAST_PAIR_PROVIDER_SIMULATOR_BUILD_FINGERPRINT


class FastPairBaseTest(base_test.BaseTestClass):
    """Base class for all Nearby Mainline Fast Pair end-to-end classes to inherit."""

    _duts: List[AndroidDevice]
    _provider: FastPairProviderSimulator
    _seeker: FastPairSeeker

    def setup_class(self) -> None:
        super().setup_class()
        self._duts = self.register_controller(android_device, min_number=2)

        provider_ad, seeker_ad = self._check_devices_supported()
        self._provider = FastPairProviderSimulator(provider_ad)
        self._seeker = FastPairSeeker(seeker_ad)
        self._provider.load_snippet()
        self._seeker.load_snippet()

    def setup_test(self) -> None:
        super().setup_test()
        self._provider.setup_provider_simulator(constants.SETUP_TIMEOUT_SEC)

    def teardown_test(self) -> None:
        super().teardown_test()
        # Create per-test excepts of logcat.
        for dut in self._duts:
            dut.services.create_output_excerpts_all(self.current_test_info)

    def _check_devices_supported(self) -> Tuple[AndroidDevice, AndroidDevice]:
        # Assume the 1st phone is provider, the 2nd one is seeker.
        provider_ad, seeker_ad = self._duts[:2]

        for ad in self._duts:
            if ad.build_info['build_fingerprint'] == REQUIRED_BUILD_FINGERPRINT:
                if ad != provider_ad:
                    provider_ad, seeker_ad = seeker_ad, provider_ad
                break
        else:
            raise signals.TestAbortClass(
                f'None of phones has custom ROM ({REQUIRED_BUILD_FINGERPRINT}) for Fast Pair '
                f'provider simulator. Skip all the test cases!')

        return provider_ad, seeker_ad
