#!/usr/bin/env python3
#
#   Copyright (C) 2020 The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
import os

from acts import context
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest

from mobly import utils
from mobly.base_test import STAGE_NAME_TEARDOWN_CLASS


class AbstractDeviceWlanDeviceBaseTest(WifiBaseTest):
    def setup_class(self):
        super().setup_class()

    def teardown_class(self):
        begin_time = utils.get_current_epoch_time()
        super().teardown_class()
        for device in getattr(self, "android_devices", []):
            device.take_bug_report(STAGE_NAME_TEARDOWN_CLASS, begin_time)
        for device in getattr(self, "fuchsia_devices", []):
            device.take_bug_report(STAGE_NAME_TEARDOWN_CLASS, begin_time)

    def on_fail(self, test_name, begin_time):
        """Gets a wlan_device log and calls the generic device fail on DUT."""
        self.dut.get_log(test_name, begin_time)
        self.on_device_fail(self.dut.device, test_name, begin_time)

    def on_device_fail(self, device, test_name, begin_time):
        """Gets a generic device DUT bug report.

        This method takes a bug report if the generic device does not have a
        'take_bug_report_on_fail', or if the flag is true. This method also
        power cycles if 'hard_reboot_on_fail' is True.

        Args:
            device: Generic device to gather logs from.
            test_name: Name of the test that triggered this function.
            begin_time: Logline format timestamp taken when the test started.
        """
        if (not hasattr(device, "take_bug_report_on_fail")
                or device.take_bug_report_on_fail):
            device.take_bug_report(test_name, begin_time)

        if device.hard_reboot_on_fail:
            device.reboot(reboot_type='hard', testbed_pdus=self.pdu_devices)

    def download_ap_logs(self):
        """Downloads the DHCP and hostapad logs from the access_point.

        Using the current TestClassContext and TestCaseContext this method pulls
        the DHCP and hostapd logs and outputs them to the correct path.
        """
        current_path = context.get_current_context().get_full_output_path()
        dhcp_full_out_path = os.path.join(current_path, "dhcp_log.txt")

        dhcp_log = self.access_point.get_dhcp_logs()
        if dhcp_log:
            dhcp_log_file = open(dhcp_full_out_path, 'w')
            dhcp_log_file.write(dhcp_log)
            dhcp_log_file.close()

        hostapd_logs = self.access_point.get_hostapd_logs()
        for interface in hostapd_logs:
            out_name = interface + "_hostapd_log.txt"
            hostapd_full_out_path = os.path.join(current_path, out_name)
            hostapd_log_file = open(hostapd_full_out_path, 'w')
            hostapd_log_file.write(hostapd_logs[interface])
            hostapd_log_file.close()
