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

"""Fast Pair provider simulator role."""

import time

from mobly import asserts
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import jsonrpc_client_base
from mobly.controllers.android_device_lib import snippet_event
from typing import Optional

from test_helper import event_helper

# The package name of the provider simulator snippet.
FP_PROVIDER_SIMULATOR_SNIPPETS_PACKAGE = 'android.nearby.multidevices'

# Events reported from the provider simulator snippet.
ON_A2DP_SINK_PROFILE_CONNECT_EVENT = 'onA2DPSinkProfileConnected'
ON_SCAN_MODE_CHANGE_EVENT = 'onScanModeChange'
ON_ADVERTISING_CHANGE_EVENT = 'onAdvertisingChange'

# Target scan mode.
DISCOVERABLE_MODE = 'DISCOVERABLE'

# Abbreviations for common use type.
AndroidDevice = android_device.AndroidDevice
SnippetEvent = snippet_event.SnippetEvent
wait_for_event = event_helper.wait_callback_event


class FastPairProviderSimulator:
    """A proxy for provider simulator snippet on the device."""

    def __init__(self, ad: AndroidDevice) -> None:
        self._ad = ad
        self._ad.debug_tag = 'FastPairProviderSimulator'
        self._provider_status_callback = None

    def load_snippet(self) -> None:
        """Starts the provider simulator snippet and connects.

        Raises:
          SnippetError: Illegal load operations are attempted.
        """
        self._ad.load_snippet(
            name='fp', package=FP_PROVIDER_SIMULATOR_SNIPPETS_PACKAGE)

    def setup_provider_simulator(self, timeout_seconds: int) -> None:
        """Sets up the Fast Pair provider simulator.

        Args:
          timeout_seconds: The number of seconds to wait before giving up.
        """
        setup_status_callback = self._ad.fp.setupProviderSimulator()

        def _on_a2dp_sink_profile_connect_event_received(_, elapsed_time: int) -> bool:
            self._ad.log.info('Provider simulator connected to A2DP sink in %d seconds.',
                              elapsed_time)
            return True

        def _on_a2dp_sink_profile_connect_event_waiting(elapsed_time: int) -> None:
            self._ad.log.info(
                'Still waiting "%s" event callback from provider side '
                'after %d seconds...', ON_A2DP_SINK_PROFILE_CONNECT_EVENT, elapsed_time)

        def _on_a2dp_sink_profile_connect_event_missed() -> None:
            asserts.fail(f'Timed out after {timeout_seconds} seconds waiting for '
                         f'the specific "{ON_A2DP_SINK_PROFILE_CONNECT_EVENT}" event.')

        wait_for_event(
            callback_event_handler=setup_status_callback,
            event_name=ON_A2DP_SINK_PROFILE_CONNECT_EVENT,
            timeout_seconds=timeout_seconds,
            on_received=_on_a2dp_sink_profile_connect_event_received,
            on_waiting=_on_a2dp_sink_profile_connect_event_waiting,
            on_missed=_on_a2dp_sink_profile_connect_event_missed)

    def start_model_id_advertising(self, model_id: str, anti_spoofing_key: str) -> None:
        """Starts model id advertising for scanning and initial pairing.

        Args:
          model_id: A 3-byte hex string for seeker side to recognize the device (ex:
            0x00000C).
          anti_spoofing_key: A public key for registered headsets.
        """
        self._ad.log.info(
            'Provider simulator starts advertising as model id "%s" with anti-spoofing key "%s".',
            model_id, anti_spoofing_key)
        self._provider_status_callback = (
            self._ad.fp.startModelIdAdvertising(model_id, anti_spoofing_key))

    def teardown_provider_simulator(self) -> None:
        """Tears down the Fast Pair provider simulator."""
        self._ad.fp.teardownProviderSimulator()

    def get_ble_mac_address(self) -> str:
        """Gets Bluetooth low energy mac address of the provider simulator.

        The BLE mac address will be set by the AdvertisingSet.getOwnAddress()
        callback. This is the callback flow in the custom Android build. It takes
        a while after advertising started so we use retry here to wait it.

        Returns:
          The BLE mac address of the Fast Pair provider simulator.
        """
        for _ in range(3):
            try:
                return self._ad.fp.getBluetoothLeAddress()
            except jsonrpc_client_base.ApiError:
                time.sleep(1)

    def wait_for_discoverable_mode(self, timeout_seconds: int) -> None:
        """Waits onScanModeChange event to ensure provider is discoverable.

        Args:
          timeout_seconds: The number of seconds to wait before giving up.
        """

        def _on_scan_mode_change_event_received(
                scan_mode_change_event: SnippetEvent, elapsed_time: int) -> bool:
            scan_mode = scan_mode_change_event.data['mode']
            self._ad.log.info(
                'Provider simulator changed the scan mode to %s in %d seconds.',
                scan_mode, elapsed_time)
            return scan_mode == DISCOVERABLE_MODE

        def _on_scan_mode_change_event_waiting(elapsed_time: int) -> None:
            self._ad.log.info(
                'Still waiting "%s" event callback from provider side '
                'after %d seconds...', ON_SCAN_MODE_CHANGE_EVENT, elapsed_time)

        def _on_scan_mode_change_event_missed() -> None:
            asserts.fail(f'Timed out after {timeout_seconds} seconds waiting for '
                         f'the specific "{ON_SCAN_MODE_CHANGE_EVENT}" event.')

        wait_for_event(
            callback_event_handler=self._provider_status_callback,
            event_name=ON_SCAN_MODE_CHANGE_EVENT,
            timeout_seconds=timeout_seconds,
            on_received=_on_scan_mode_change_event_received,
            on_waiting=_on_scan_mode_change_event_waiting,
            on_missed=_on_scan_mode_change_event_missed)

    def wait_for_advertising_start(self, timeout_seconds: int) -> None:
        """Waits onAdvertisingChange event to ensure provider is advertising.

        Args:
          timeout_seconds: The number of seconds to wait before giving up.
        """

        def _on_advertising_mode_change_event_received(
                scan_mode_change_event: SnippetEvent, elapsed_time: int) -> bool:
            advertising_mode = scan_mode_change_event.data['isAdvertising']
            self._ad.log.info(
                'Provider simulator changed the advertising mode to %s in %d seconds.',
                advertising_mode, elapsed_time)
            return advertising_mode

        def _on_advertising_mode_change_event_waiting(elapsed_time: int) -> None:
            self._ad.log.info(
                'Still waiting "%s" event callback from provider side '
                'after %d seconds...', ON_ADVERTISING_CHANGE_EVENT, elapsed_time)

        def _on_advertising_mode_change_event_missed() -> None:
            asserts.fail(f'Timed out after {timeout_seconds} seconds waiting for '
                         f'the specific "{ON_ADVERTISING_CHANGE_EVENT}" event.')

        wait_for_event(
            callback_event_handler=self._provider_status_callback,
            event_name=ON_ADVERTISING_CHANGE_EVENT,
            timeout_seconds=timeout_seconds,
            on_received=_on_advertising_mode_change_event_received,
            on_waiting=_on_advertising_mode_change_event_waiting,
            on_missed=_on_advertising_mode_change_event_missed)

    def get_latest_received_account_key(self) -> Optional[str]:
        """Gets the latest account key received on the provider side.

        Returns:
          The account key received at provider side.
        """
        return self._ad.fp.getLatestReceivedAccountKey()
