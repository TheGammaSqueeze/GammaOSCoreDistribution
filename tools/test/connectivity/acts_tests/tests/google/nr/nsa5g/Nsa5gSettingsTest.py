#!/usr/bin/env python3.4
#
#   Copyright 2022 - Google
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

'''
    Test Script for Telephony Settings on nsa 5G
'''

import time

from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.net import ui_utils
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import MOBILE_DATA
from acts_contrib.test_utils.tel.tel_defines import USE_SIM
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_BETWEEN_STATE_CHECK
from acts_contrib.test_utils.tel.tel_ops_utils import get_resource_value
from acts_contrib.test_utils.tel.tel_ops_utils import wait_and_click_element
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_test_utils import get_current_override_network_type
from acts_contrib.test_utils.tel.tel_5g_test_utils import provision_device_for_5g
from acts_contrib.test_utils.tel.tel_5g_utils import is_current_network_5g


class Nsa5gSettingsTest(TelephonyBaseTest):
    def setup_class(self):
        super().setup_class()
        self.number_of_devices = 1

    def setup_test(self):
        TelephonyBaseTest.setup_test(self)

    def teardown_test(self):
        ensure_phones_idle(self.log, self.android_devices)


    """ Tests Begin """
    @test_tracker_info(uuid='57debc2d-ca17-4363-8d03-9bc068fdc624')
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_disable_enable_sim(self):
        """Test sim disable and enable

        Steps:
            1. Provision device to nsa 5G
            2. Launch Settings - Network & Internet
            3. Click on SIMs
            4. Toggle Use SIM switch to Disable
            5. Verify Use SIM switch is disabled
            6. Toggle Use SIM switch to Enable
            7. Verify Use SIM switch is Enabled
            8. Verify SIM is connected to nsa 5G

        Returns:
            True is tests passes else False
        """
        ad = self.android_devices[0]

        if not provision_device_for_5g(ad.log, ad, nr_type='nsa'):
            return False
        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)

        ad.adb.shell('am start -a android.settings.WIRELESS_SETTINGS')
        wait_and_click_element(ad, 'SIMs')

        switch_value = get_resource_value(ad, USE_SIM)
        if switch_value == 'true':
            ad.log.info('SIM is enabled as expected')
        else:
            ad.log.error('SIM should be enabled but SIM is disabled')
            return False

        label_text = USE_SIM
        label_resource_id = 'com.android.settings:id/switch_text'

        ad.log.info('Disable SIM')
        wait_and_click_element(ad, label_text, label_resource_id)

        button_resource_id = 'android:id/button1'
        wait_and_click_element(ad, 'Yes', button_resource_id)
        switch_value = get_resource_value(ad, USE_SIM)
        if switch_value == 'false':
            ad.log.info('SIM is disabled as expected')
        else:
            ad.log.error('SIM should be disabled but SIM is enabled')
            return False

        ad.log.info('Enable SIM')
        wait_and_click_element(ad, label_text, label_resource_id)

        wait_and_click_element(ad, 'Yes', button_resource_id)
        switch_value = get_resource_value(ad, USE_SIM)
        if switch_value == 'true':
            ad.log.info('SIM is enabled as expected')
        else:
            ad.log.error('SIM should be enabled but SIM is disabled')
            return False

        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)

        if is_current_network_5g(ad, nr_type = 'nsa', timeout=60):
            ad.log.info('Success! attached on 5g NSA')
        else:
            ad.log.error('Failure - expected NR_NSA, current %s',
                         get_current_override_network_type(ad))
            return False

    @test_tracker_info(uuid='7233780b-eabf-4bb6-ae96-3574d0cd4fa2')
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_disable_enable_mobile_data(self):
        """Test sim disable and enable

        Steps:
            1. Provision device to nsa 5G
            2. Launch Settings - Network & Internet
            3. Click on SIMs
            4. Toggle Mobile Data switch to Disable
            5. Verify Mobile Data switch is disabled
            6. Toggle Mobile Data switch to Enable
            7. Verify Mobile Data switch is Enabled
            8. Verify Mobile Data is connected to nsa 5G

        Returns:
            True is tests passes else False
        """
        ad = self.android_devices[0]

        if not provision_device_for_5g(ad.log, ad, nr_type='nsa'):
            return False
        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)

        ad.adb.shell('am start -a android.settings.WIRELESS_SETTINGS')
        wait_and_click_element(ad, 'SIMs')
        switch_value = get_resource_value(ad, MOBILE_DATA)

        if switch_value == 'true':
            ad.log.info('Mobile data is enabled as expected')
        else:
            ad.log.error('Mobile data should be enabled but it is disabled')

        ad.log.info('Disable mobile data')
        ad.droid.telephonyToggleDataConnection(False)
        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)
        switch_value = get_resource_value(ad, MOBILE_DATA)
        if switch_value == 'false':
            ad.log.info('Mobile data is disabled as expected')
        else:
            ad.log.error('Mobile data should be disabled but it is enabled')

        ad.log.info('Enabling mobile data')
        ad.droid.telephonyToggleDataConnection(True)
        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)
        switch_value = get_resource_value(ad, MOBILE_DATA)
        if switch_value == 'true':
            ad.log.info('Mobile data is enabled as expected')
        else:
            ad.log.error('Mobile data should be enabled but it is disabled')

        if is_current_network_5g(ad, nr_type = 'nsa', timeout=60):
            ad.log.info('Success! attached on 5g NSA')
        else:
            ad.log.error('Failure - expected NR_NSA, current %s',
                         get_current_override_network_type(ad))

