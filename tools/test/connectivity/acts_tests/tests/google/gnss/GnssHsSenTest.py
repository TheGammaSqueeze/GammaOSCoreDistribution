#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
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

import os
from acts_contrib.test_utils.gnss.GnssBlankingBase import GnssBlankingBase
from acts_contrib.test_utils.gnss.dut_log_test_utils import get_gpstool_logs
from acts_contrib.test_utils.gnss.gnss_test_utils import excute_eecoexer_function


class GnssHsSenTest(GnssBlankingBase):
    """ LAB GNSS Cellular coex hot start sensitivity search"""

    def __init__(self, controllers):
        super().__init__(controllers)
        self.gnss_simulator_power_level = -130
        self.sa_sensitivity = -150
        self.gnss_pwr_lvl_offset = 5

    def gnss_hot_start_sensitivity_search_base(self, cellular_enable=False):
        """
        Perform GNSS hot start sensitivity search.

        Args:
                cellular_enable: argument to identify if Tx cellular signal is required or not.
                Type, bool.
                Default, False.
        """
        # Get parameters from user_params.
        first_wait = self.user_params.get('first_wait', 300)
        wait_between_pwr = self.user_params.get('wait_between_pwr', 60)
        gnss_pwr_sweep = self.user_params.get('gnss_pwr_sweep')
        gnss_init_pwr = gnss_pwr_sweep.get('init')
        self.gnss_simulator_power_level = gnss_init_pwr[0]
        self.sa_sensitivity = gnss_init_pwr[1]
        self.gnss_pwr_lvl_offset = gnss_init_pwr[2]
        gnss_pwr_fine_sweep = gnss_pwr_sweep.get('fine_sweep')
        ttft_iteration = self.user_params.get('ttff_iteration', 25)

        # Start the test item with gnss_init_power_setting.
        if self.gnss_init_power_setting(first_wait):
            self.log.info('Successfully set the GNSS power level to %d' %
                          self.sa_sensitivity)
            # Create gnss log folders for init and cellular sweep
            gnss_init_log_dir = os.path.join(self.gnss_log_path, 'GNSS_init')

            # Pull all exist GPStool logs into GNSS_init folder
            get_gpstool_logs(self.dut, gnss_init_log_dir, False)

            if cellular_enable:
                self.log.info('Start cellular coexistence test.')
                # Set cellular Tx power level.
                eecoex_cmd = self.eecoex_func.format('Infinity')
                eecoex_cmd_file_str = eecoex_cmd.replace(',', '_')
                excute_eecoexer_function(self.dut, eecoex_cmd)
            else:
                self.log.info('Start stand alone test.')
                eecoex_cmd_file_str = 'Stand_alone'

            for i, gnss_pwr in enumerate(gnss_pwr_fine_sweep):
                self.log.info('Start fine GNSS power level sweep part %d' %
                              (i + 1))
                sweep_start = gnss_pwr[0]
                sweep_stop = gnss_pwr[1]
                sweep_offset = gnss_pwr[2]
                self.log.info(
                    'The GNSS simulator (start, stop, offset): (%.1f, %.1f, %.1f)'
                    % (sweep_start, sweep_stop, sweep_offset))
                result, sensitivity = self.hot_start_gnss_power_sweep(
                    sweep_start, sweep_stop, sweep_offset, wait_between_pwr,
                    ttft_iteration, True, eecoex_cmd_file_str)
                if not result:
                    break
            self.log.info('The sensitivity level is: %.1f' % sensitivity)

    def test_hot_start_sensitivity_search(self):
        """
        GNSS hot start stand alone sensitivity search.
        """
        self.gnss_hot_start_sensitivity_search_base(False)

    def test_hot_start_sensitivity_search_gsm850(self):
        """
        GNSS hot start GSM850 Ch190 coexistence sensitivity search.
        """
        self.eecoex_func = 'CELLR,2,850,190,1,1,{}'
        self.log.info('Running GSM850 and GNSS coexistence sensitivity search.')
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_gsm900(self):
        """
        GNSS hot start GSM900 Ch20 coexistence sensitivity search.
        """
        self.eecoex_func = 'CELLR,2,900,20,1,1,{}'
        self.log.info('Running GSM900 and GNSS coexistence sensitivity search.')
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_gsm1800(self):
        """
        GNSS hot start GSM1800 Ch699 coexistence sensitivity search.
        """
        self.eecoex_func = 'CELLR,2,1800,699,1,1,{}'
        self.log.info(
            'Running GSM1800 and GNSS coexistence sensitivity search.')
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_gsm1900(self):
        """
        GNSS hot start GSM1900 Ch661 coexistence sensitivity search.
        """
        self.eecoex_func = 'CELLR,2,1900,661,1,1,{}'
        self.log.info(
            'Running GSM1900 and GNSS coexistence sensitivity search.')
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_lte_b38(self):
        """
        GNSS hot start LTE B38 Ch38000 coexistence sensitivity search.
        """
        self.eecoex_func = 'CELLR,5,38,38000,true,PRIMARY,{},10MHz,0,12'
        self.log.info(
            'Running LTE B38 and GNSS coexistence sensitivity search.')
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_lte_b39(self):
        """
        GNSS hot start LTE B39 Ch38450 coexistence sensitivity search.
        """
        self.eecoex_func = 'CELLR,5,39,38450,true,PRIMARY,{},10MHz,0,12'
        self.log.info(
            'Running LTE B38 and GNSS coexistence sensitivity search.')
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_lte_b40(self):
        """
        GNSS hot start LTE B40 Ch39150 coexistence sensitivity search.
        """
        self.eecoex_func = 'CELLR,5,40,39150,true,PRIMARY,{},10MHz,0,12'
        self.log.info(
            'Running LTE B38 and GNSS coexistence sensitivity search.')
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_lte_b41(self):
        """
        GNSS hot start LTE B41 Ch40620 coexistence sensitivity search.
        """
        self.eecoex_func = 'CELLR,5,41,40620,true,PRIMARY,{},10MHz,0,12'
        self.log.info(
            'Running LTE B41 and GNSS coexistence sensitivity search.')
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_lte_b42(self):
        """
        GNSS hot start LTE B42 Ch42590 coexistence sensitivity search.
        """
        self.eecoex_func = 'CELLR,5,42,42590,true,PRIMARY,{},10MHz,0,12'
        self.log.info(
            'Running LTE B42 and GNSS coexistence sensitivity search.')
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_lte_b48(self):
        """
        GNSS hot start LTE B48 Ch55990 coexistence sensitivity search.
        """
        self.eecoex_func = 'CELLR,5,48,55990,true,PRIMARY,{},10MHz,0,12'
        self.log.info(
            'Running LTE B48 and GNSS coexistence sensitivity search.')
        self.gnss_hot_start_sensitivity_search_base(True)
