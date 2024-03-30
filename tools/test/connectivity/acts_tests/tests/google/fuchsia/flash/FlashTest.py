#!/usr/bin/env python3
#
# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
"""
Script for to flash Fuchsia devices and reports the DUT's version of Fuchsia in
the Sponge test result properties. Uses the built in flashing tool for
fuchsia_devices.
"""
from acts import asserts
from acts.base_test import BaseTestClass
from acts.controllers.fuchsia_lib.base_lib import DeviceOffline
from acts.utils import get_device

MAX_FLASH_ATTEMPTS = 3


class FlashTest(BaseTestClass):
    def setup_class(self):
        super().setup_class()
        success_str = ("Congratulations! Fuchsia controllers have been "
                       "initialized successfully!")
        err_str = ("Sorry, please try verifying FuchsiaDevice is in your "
                   "config file and try again.")
        if len(self.fuchsia_devices) > 0:
            self.log.info(success_str)
        else:
            raise signals.TestAbortClass("err_str")

    def teardown_class(self):
        try:
            dut = get_device(self.fuchsia_devices, 'DUT')
            version = dut.version()
            self.record_data({'sponge_properties': {
                'DUT_VERSION': version,
            }})
            self.log.info("DUT version found: {}".format(version))
        except ValueError as err:
            self.log.warn("Failed to determine DUT: %s" % err)
        except DeviceOffline as err:
            self.log.warn("Failed to get DUT's version: %s" % err)

        return super().teardown_class()

    def test_flash_devices(self):
        for device in self.fuchsia_devices:
            flash_counter = 0
            while True:
                try:
                    device.reboot(reboot_type='flash',
                                  use_ssh=True,
                                  unreachable_timeout=120,
                                  ping_timeout=120)
                    self.log.info(f'{device.orig_ip} has been flashed.')
                    break
                except Exception as err:
                    self.log.error(
                        f'Failed to flash {device.orig_ip} with error:\n{err}')

                    if not device.device_pdu_config:
                        asserts.abort_all(
                            f'Failed to flash {device.orig_ip} and no PDU available for hard reboot'
                        )

                    flash_counter = flash_counter + 1
                    if flash_counter == MAX_FLASH_ATTEMPTS:
                        asserts.abort_all(
                            f'Failed to flash {device.orig_ip} after {MAX_FLASH_ATTEMPTS} attempts'
                        )

                    self.log.info(
                        f'Hard rebooting {device.orig_ip} and retrying flash.')
                    device.reboot(reboot_type='hard',
                                  testbed_pdus=self.pdu_devices)

    def test_report_dut_version(self):
        """Empty test to ensure the version of the DUT is reported in the Sponge
        results in the case when flashing the device is not necessary.

        Useful for when flashing the device is not necessary; specify ACTS to
        only run this test from the test class.
        """
        pass
