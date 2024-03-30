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

"""CTS-V Nearby Mainline Fast Pair end-to-end test case: initial pairing test."""

from test_helper import constants
from test_helper import fast_pair_base_test

# The model ID to simulate on provider side.
PROVIDER_SIMULATOR_MODEL_ID = constants.DEFAULT_MODEL_ID
# The public key to simulate as registered headsets.
PROVIDER_SIMULATOR_ANTI_SPOOFING_KEY = constants.DEFAULT_ANTI_SPOOFING_KEY
# The anti-spoof key device metadata JSON file for data provider at seeker side.
PROVIDER_SIMULATOR_KDM_JSON_FILE = constants.DEFAULT_KDM_JSON_FILE

# Time in seconds for events waiting.
SETUP_TIMEOUT_SEC = constants.SETUP_TIMEOUT_SEC
BECOME_DISCOVERABLE_TIMEOUT_SEC = constants.BECOME_DISCOVERABLE_TIMEOUT_SEC
START_ADVERTISING_TIMEOUT_SEC = constants.START_ADVERTISING_TIMEOUT_SEC
HALF_SHEET_POPUP_TIMEOUT_SEC = constants.HALF_SHEET_POPUP_TIMEOUT_SEC
MANAGE_ACCOUNT_DEVICE_TIMEOUT_SEC = constants.AVERAGE_PAIRING_TIMEOUT_SEC * 2


class InitialPairingTest(fast_pair_base_test.FastPairBaseTest):
    """Fast Pair initial pairing test."""

    def setup_test(self) -> None:
        super().setup_test()
        self._provider.start_model_id_advertising(PROVIDER_SIMULATOR_MODEL_ID,
                                                  PROVIDER_SIMULATOR_ANTI_SPOOFING_KEY)
        self._provider.wait_for_discoverable_mode(BECOME_DISCOVERABLE_TIMEOUT_SEC)
        self._provider.wait_for_advertising_start(START_ADVERTISING_TIMEOUT_SEC)
        self._seeker.put_anti_spoof_key_device_metadata(PROVIDER_SIMULATOR_MODEL_ID,
                                                        PROVIDER_SIMULATOR_KDM_JSON_FILE)
        self._seeker.set_fast_pair_scan_enabled(True)

    # TODO(b/214015364): Remove Bluetooth bound on both sides ("Forget device").
    def teardown_test(self) -> None:
        self._seeker.set_fast_pair_scan_enabled(False)
        self._provider.teardown_provider_simulator()
        self._seeker.dismiss_halfsheet()
        super().teardown_test()

    def test_seeker_initial_pair_provider(self) -> None:
        self._seeker.wait_and_assert_halfsheet_showed(
            timeout_seconds=HALF_SHEET_POPUP_TIMEOUT_SEC,
            expected_model_id=PROVIDER_SIMULATOR_MODEL_ID)
        self._seeker.start_pairing()
        self._seeker.wait_and_assert_account_device(
            get_account_key_from_provider=self._provider.get_latest_received_account_key,
            timeout_seconds=MANAGE_ACCOUNT_DEVICE_TIMEOUT_SEC)
