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

"""Fast Pair seeker role."""

import json
from typing import Callable, Optional

from mobly import asserts
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import snippet_event

from test_helper import event_helper
from test_helper import utils

# The package name of the Nearby Mainline Fast Pair seeker Mobly snippet.
FP_SEEKER_SNIPPETS_PACKAGE = 'android.nearby.multidevices'

# Events reported from the seeker snippet.
ON_PROVIDER_FOUND_EVENT = 'onDiscovered'
ON_MANAGE_ACCOUNT_DEVICE_EVENT = 'onManageAccountDevice'

# Abbreviations for common use type.
AndroidDevice = android_device.AndroidDevice
JsonObject = utils.JsonObject
ProviderAccountKeyCallable = Callable[[], Optional[str]]
SnippetEvent = snippet_event.SnippetEvent
wait_for_event = event_helper.wait_callback_event


class FastPairSeeker:
    """A proxy for seeker snippet on the device."""

    def __init__(self, ad: AndroidDevice) -> None:
        self._ad = ad
        self._ad.debug_tag = 'MainlineFastPairSeeker'
        self._scan_result_callback = None
        self._pairing_result_callback = None

    def load_snippet(self) -> None:
        """Starts the seeker snippet and connects.

        Raises:
          SnippetError: Illegal load operations are attempted.
        """
        self._ad.load_snippet(name='fp', package=FP_SEEKER_SNIPPETS_PACKAGE)

    def start_scan(self) -> None:
        """Starts scanning to find Fast Pair provider devices."""
        self._scan_result_callback = self._ad.fp.startScan()

    def stop_scan(self) -> None:
        """Stops the Fast Pair seeker scanning."""
        self._ad.fp.stopScan()

    def wait_and_assert_provider_found(self, timeout_seconds: int,
                                       expected_model_id: str,
                                       expected_ble_mac_address: str) -> None:
        """Waits and asserts any onDiscovered event from the seeker.

        Args:
          timeout_seconds: The number of seconds to wait before giving up.
          expected_model_id: The expected model ID of the remote Fast Pair provider
            device.
          expected_ble_mac_address: The expected BLE MAC address of the remote Fast
            Pair provider device.
        """

        def _on_provider_found_event_received(provider_found_event: SnippetEvent,
                                              elapsed_time: int) -> bool:
            nearby_device_str = provider_found_event.data['device']
            self._ad.log.info('Seeker discovered first provider(%s) in %d seconds.',
                              nearby_device_str, elapsed_time)
            return expected_ble_mac_address in nearby_device_str

        def _on_provider_found_event_waiting(elapsed_time: int) -> None:
            self._ad.log.info(
                'Still waiting "%s" event callback from seeker side '
                'after %d seconds...', ON_PROVIDER_FOUND_EVENT, elapsed_time)

        def _on_provider_found_event_missed() -> None:
            asserts.fail(f'Timed out after {timeout_seconds} seconds waiting for '
                         f'the specific "{ON_PROVIDER_FOUND_EVENT}" event.')

        wait_for_event(
            callback_event_handler=self._scan_result_callback,
            event_name=ON_PROVIDER_FOUND_EVENT,
            timeout_seconds=timeout_seconds,
            on_received=_on_provider_found_event_received,
            on_waiting=_on_provider_found_event_waiting,
            on_missed=_on_provider_found_event_missed)

    def put_anti_spoof_key_device_metadata(self, model_id: str, kdm_json_file_name: str) -> None:
        """Puts a model id to FastPairAntispoofKeyDeviceMetadata pair into test data cache.

        Args:
          model_id: A string of model id to be associated with.
          kdm_json_file_name: The FastPairAntispoofKeyDeviceMetadata JSON object.
        """
        self._ad.log.info('Puts FastPairAntispoofKeyDeviceMetadata into test data cache for '
                          'model id "%s".', model_id)
        kdm_json_object = utils.load_json_fast_pair_test_data(kdm_json_file_name)
        self._ad.fp.putAntispoofKeyDeviceMetadata(
            model_id,
            utils.serialize_as_simplified_json_str(kdm_json_object))

    def set_fast_pair_scan_enabled(self, enable: bool) -> None:
        """Writes into Settings whether Fast Pair scan is enabled.

        Args:
          enable: whether the Fast Pair scan should be enabled.
        """
        self._ad.log.info('%s Fast Pair scan in Android settings.',
                          'Enables' if enable else 'Disables')
        self._ad.fp.setFastPairScanEnabled(enable)

    def wait_and_assert_halfsheet_showed(self, timeout_seconds: int,
                                         expected_model_id: str) -> None:
        """Waits and asserts the onHalfSheetShowed event from the seeker.

        Args:
          timeout_seconds: The number of seconds to wait before giving up.
          expected_model_id: A 3-byte hex string for seeker side to recognize
            the remote provider device (ex: 0x00000c).
        """
        self._ad.log.info('Waits and asserts the half sheet showed for model id "%s".',
                          expected_model_id)
        self._ad.fp.waitAndAssertHalfSheetShowed(expected_model_id, timeout_seconds)

    def dismiss_halfsheet(self) -> None:
        """Dismisses the half sheet UI if showed."""
        self._ad.fp.dismissHalfSheet()

    def start_pairing(self) -> None:
        """Starts pairing the provider via "Connect" button on half sheet UI."""
        self._pairing_result_callback = self._ad.fp.startPairing()

    def wait_and_assert_account_device(
            self, timeout_seconds: int,
            get_account_key_from_provider: ProviderAccountKeyCallable) -> None:
        """Waits and asserts the onHalfSheetShowed event from the seeker.

        Args:
          timeout_seconds: The number of seconds to wait before giving up.
          get_account_key_from_provider: The callable to get expected account key from the provider
            side.
        """

        def _on_manage_account_device_event_received(manage_account_device_event: SnippetEvent,
                                                     elapsed_time: int) -> bool:
            account_key_json_str = manage_account_device_event.data['accountDeviceJsonString']
            account_key_from_seeker = json.loads(account_key_json_str)['account_key']
            account_key_from_provider = get_account_key_from_provider()
            self._ad.log.info('Seeker add an account device with account key "%s" in %d seconds.',
                              account_key_from_seeker, elapsed_time)
            self._ad.log.info('The latest provider side account key is "%s".',
                              account_key_from_provider)
            return account_key_from_seeker == account_key_from_provider

        def _on_manage_account_device_event_waiting(elapsed_time: int) -> None:
            self._ad.log.info(
                'Still waiting "%s" event callback from seeker side '
                'after %d seconds...', ON_MANAGE_ACCOUNT_DEVICE_EVENT, elapsed_time)

        def _on_manage_account_device_event_missed() -> None:
            asserts.fail(f'Timed out after {timeout_seconds} seconds waiting for '
                         f'the specific "{ON_MANAGE_ACCOUNT_DEVICE_EVENT}" event.')

        wait_for_event(
            callback_event_handler=self._pairing_result_callback,
            event_name=ON_MANAGE_ACCOUNT_DEVICE_EVENT,
            timeout_seconds=timeout_seconds,
            on_received=_on_manage_account_device_event_received,
            on_waiting=_on_manage_account_device_event_waiting,
            on_missed=_on_manage_account_device_event_missed)
