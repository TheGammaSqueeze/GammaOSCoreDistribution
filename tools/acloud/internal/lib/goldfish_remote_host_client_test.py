#!/usr/bin/env python3
#
# Copyright 2021 - The Android Open Source Project
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

"""Unit tests for GoldfishRemoteHostClient."""

import unittest

from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import goldfish_remote_host_client


class GoldfishRemoteHostClientTest(driver_test_lib.BaseDriverTest):
    """Unit tests for GoldfishRemoteHostClient."""

    _IP_ADDRESS = "192.0.2.1"
    _CONSOLE_PORT = 5554
    _BUILD_INFO = {"build_id": "123456",
                   "build_target": "sdk_phone_x86_64-userdebug"}
    _INSTANCE_NAME = ("host-goldfish-192.0.2.1-5554-"
                      "123456-sdk_phone_x86_64-userdebug")
    _INVALID_NAME = "host-192.0.2.1-123456-aosp_cf_x86_phone-userdebug"

    def testParseEmulatorConsoleAddress(self):
        """Test ParseEmulatorConsoleAddress."""
        console_addr = goldfish_remote_host_client.ParseEmulatorConsoleAddress(
            self._INSTANCE_NAME)
        self.assertEqual((self._IP_ADDRESS, self._CONSOLE_PORT), console_addr)

        console_addr = goldfish_remote_host_client.ParseEmulatorConsoleAddress(
            self._INVALID_NAME)
        self.assertIsNone(console_addr)

    def testFormatInstanceName(self):
        """Test FormatInstanceName."""
        instance_name = goldfish_remote_host_client.FormatInstanceName(
            self._IP_ADDRESS, self._CONSOLE_PORT, self._BUILD_INFO)
        self.assertEqual(self._INSTANCE_NAME, instance_name)

    def testGetInstanceIP(self):
        """Test GetInstanceIP."""
        client = goldfish_remote_host_client.GoldfishRemoteHostClient()
        ip_addr = client.GetInstanceIP(self._INSTANCE_NAME)
        self.assertEqual(ip_addr.external, self._IP_ADDRESS)
        self.assertEqual(ip_addr.internal, self._IP_ADDRESS)

if __name__ == "__main__":
    unittest.main()
