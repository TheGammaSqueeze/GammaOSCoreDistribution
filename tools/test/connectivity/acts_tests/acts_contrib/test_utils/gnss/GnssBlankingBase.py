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
from glob import glob
from time import sleep
from collections import namedtuple
from numpy import arange
from pandas import DataFrame
from acts.signals import TestError
from acts.signals import TestFailure
from acts.logger import epoch_to_log_line_timestamp
from acts.context import get_current_context
from acts_contrib.test_utils.gnss import LabTtffTestBase as lttb
from acts_contrib.test_utils.gnss.gnss_test_utils import launch_eecoexer
from acts_contrib.test_utils.gnss.gnss_test_utils import excute_eecoexer_function
from acts_contrib.test_utils.gnss.gnss_test_utils import start_gnss_by_gtw_gpstool
from acts_contrib.test_utils.gnss.gnss_test_utils import get_current_epoch_time
from acts_contrib.test_utils.gnss.gnss_test_utils import check_current_focus_app
from acts_contrib.test_utils.gnss.gnss_test_utils import process_ttff_by_gtw_gpstool
from acts_contrib.test_utils.gnss.gnss_test_utils import check_ttff_data
from acts_contrib.test_utils.gnss.gnss_test_utils import process_gnss_by_gtw_gpstool
from acts_contrib.test_utils.gnss.gnss_test_utils import start_pixel_logger
from acts_contrib.test_utils.gnss.gnss_test_utils import stop_pixel_logger
from acts_contrib.test_utils.gnss.dut_log_test_utils import start_diagmdlog_background
from acts_contrib.test_utils.gnss.dut_log_test_utils import get_gpstool_logs
from acts_contrib.test_utils.gnss.dut_log_test_utils import stop_background_diagmdlog
from acts_contrib.test_utils.gnss.dut_log_test_utils import get_pixellogger_bcm_log
from acts_contrib.test_utils.gnss.gnss_testlog_utils import parse_gpstool_ttfflog_to_df


def range_wi_end(ad, start, stop, step):
    """
    Generate a list of data from start to stop with the step. The list includes start and stop value
    and also supports floating point.
    Args:
        start: start value.
            Type, int or float.
        stop: stop value.
            Type, int or float.
        step: step value.
            Type, int or float.
    Returns:
        range_ls: the list of data.
    """
    if step == 0:
        ad.log.warn('Step is 0. Return empty list')
        range_ls = []
    else:
        if start == stop:
            range_ls = [stop]
        else:
            range_ls = list(arange(start, stop, step))
            if len(range_ls) > 0:
                if (step < 0 and range_ls[-1] > stop) or (step > 0 and
                                                          range_ls[-1] < stop):
                    range_ls.append(stop)
    return range_ls


def check_ttff_pe(ad, ttff_data, ttff_mode, pe_criteria):
    """Verify all TTFF results from ttff_data.

    Args:
        ad: An AndroidDevice object.
        ttff_data: TTFF data of secs, position error and signal strength.
        ttff_mode: TTFF Test mode for current test item.
        pe_criteria: Criteria for current test item.

    """
    ret = True
    ad.log.info("%d iterations of TTFF %s tests finished." %
                (len(ttff_data.keys()), ttff_mode))
    ad.log.info("%s PASS criteria is %f meters" % (ttff_mode, pe_criteria))
    ad.log.debug("%s TTFF data: %s" % (ttff_mode, ttff_data))

    if len(ttff_data.keys()) == 0:
        ad.log.error("GTW_GPSTool didn't process TTFF properly.")
        raise TestFailure("GTW_GPSTool didn't process TTFF properly.")

    if any(
            float(ttff_data[key].ttff_pe) >= pe_criteria
            for key in ttff_data.keys()):
        ad.log.error("One or more TTFF %s are over test criteria %f meters" %
                     (ttff_mode, pe_criteria))
        ret = False
    else:
        ad.log.info("All TTFF %s are within test criteria %f meters." %
                    (ttff_mode, pe_criteria))
        ret = True
    return ret


class GnssBlankingBase(lttb.LabTtffTestBase):
    """ LAB GNSS Cellular Coex Tx Power Sweep TTFF/FFPE Tests"""

    def __init__(self, controllers):
        """ Initializes class attributes. """
        super().__init__(controllers)
        self.eecoex_func = ''
        self.start_pwr = 10
        self.stop_pwr = 24
        self.offset = 1
        self.result_cell_pwr = 10
        self.gsm_sweep_params = None
        self.lte_tdd_pc3_sweep_params = None
        self.lte_tdd_pc2_sweep_params = None
        self.sa_sensitivity = -150
        self.gnss_pwr_lvl_offset = -5
        self.maskfile = None

    def setup_class(self):
        super().setup_class()
        req_params = ['sa_sensitivity', 'gnss_pwr_lvl_offset']
        self.unpack_userparams(req_param_names=req_params)
        cell_sweep_params = self.user_params.get('cell_pwr_sweep', [])
        self.gsm_sweep_params = cell_sweep_params.get("GSM", [10, 33, 1])
        self.lte_tdd_pc3_sweep_params = cell_sweep_params.get(
            "LTE_TDD_PC3", [10, 24, 1])
        self.lte_tdd_pc2_sweep_params = cell_sweep_params.get(
            "LTE_TDD_PC2", [10, 26, 1])
        self.sa_sensitivity = self.user_params.get('sa_sensitivity', -150)
        self.gnss_pwr_lvl_offset = self.user_params.get('gnss_pwr_lvl_offset', -5)

    def setup_test(self):
        super().setup_test()
        launch_eecoexer(self.dut)

        # Set DUT temperature the limit to 60 degree
        self.dut.adb.shell(
            'setprop persist.com.google.eecoexer.cellular.temperature_limit 60')

        # Get current context full path to create the log folder.
        cur_test_item_dir = get_current_context().get_full_output_path()
        self.gnss_log_path = os.path.join(self.log_path, cur_test_item_dir)
        os.makedirs(self.gnss_log_path, exist_ok=True)

        # Start GNSS chip log
        if self.diag_option == "QCOM":
            start_diagmdlog_background(self.dut, maskfile=self.maskfile)
        else:
            start_pixel_logger(self.dut)

    def teardown_test(self):
        super().teardown_test()
        # Set gnss_vendor_log_path based on GNSS solution vendor.
        gnss_vendor_log_path = os.path.join(self.gnss_log_path,
                                            self.diag_option)
        os.makedirs(gnss_vendor_log_path, exist_ok=True)

        # Stop GNSS chip log and pull the logs to local file system.
        if self.diag_option == "QCOM":
            stop_background_diagmdlog(self.dut,
                                      gnss_vendor_log_path,
                                      keep_logs=False)
        else:
            stop_pixel_logger(self.dut)
            self.log.info('Getting Pixel BCM Log!')
            get_pixellogger_bcm_log(self.dut,
                                    gnss_vendor_log_path,
                                    keep_logs=False)

        # Stop cellular Tx and close GPStool and EEcoexer APPs.
        self.stop_cell_tx()
        self.log.debug('Close GPStool APP')
        self.dut.force_stop_apk("com.android.gpstool")
        self.log.debug('Close EEcoexer APP')
        self.dut.force_stop_apk("com.google.eecoexer")

    def stop_cell_tx(self):
        """
        Stop EEcoexer Tx power.
        """
        # EEcoexer cellular stop Tx command.
        stop_cell_tx_cmd = 'CELLR,19'

        # Stop cellular Tx by EEcoexer.
        self.log.info('Stop EEcoexer Test Command: {}'.format(stop_cell_tx_cmd))
        excute_eecoexer_function(self.dut, stop_cell_tx_cmd)

    def analysis_ttff_ffpe(self, ttff_data, json_tag=''):
        """
        Pull logs and parsing logs into json file.
        Args:
            ttff_data: ttff_data from test results.
                Type, list.
            json_tag: tag for parsed json file name.
                Type, str.
        """
        # Create log directory.
        gps_log_path = os.path.join(self.gnss_log_path,
                                    'Cell_Pwr_Sweep_Results')

        # Pull logs of GTW GPStool.
        get_gpstool_logs(self.dut, gps_log_path, False)

        # Parsing the log of GTW GPStool into pandas dataframe.
        target_log_name_regx = os.path.join(gps_log_path, 'GPSLogs', 'files',
                                            'GNSS_*')
        self.log.info('Get GPStool logs from: {}'.format(target_log_name_regx))
        gps_api_log_ls = glob(target_log_name_regx)
        latest_gps_api_log = max(gps_api_log_ls, key=os.path.getctime)
        self.log.info(
            'Get latest GPStool log is: {}'.format(latest_gps_api_log))
        try:
            df_ttff_ffpe = DataFrame(
                parse_gpstool_ttfflog_to_df(latest_gps_api_log))

            # Add test case, TTFF and FFPE data into the dataframe.
            ttff_dict = {}
            for i in ttff_data:
                data = ttff_data[i]._asdict()
                ttff_dict[i] = dict(data)
            ttff_time = []
            ttff_pe = []
            test_case = []
            for value in ttff_dict.values():
                ttff_time.append(value['ttff_sec'])
                ttff_pe.append(value['ttff_pe'])
                test_case.append(json_tag)
            self.log.info('test_case length {}'.format(str(len(test_case))))

            df_ttff_ffpe['test_case'] = test_case
            df_ttff_ffpe['ttff_sec'] = ttff_time
            df_ttff_ffpe['ttff_pe'] = ttff_pe
            json_file = 'gps_log_{}.json'.format(json_tag)
            json_path = os.path.join(gps_log_path, json_file)
            # Save dataframe into json file.
            df_ttff_ffpe.to_json(json_path, orient='table', index=False)
        except ValueError:
            self.log.warning('Can\'t create the parsed the log data in file.')

    def gnss_hot_start_ttff_ffpe_test(self,
                                      iteration,
                                      sweep_enable=False,
                                      json_tag=''):
        """
        GNSS hot start ttff ffpe tset

        Args:
            iteration: hot start TTFF test iteration.
                    Type, int.
                    Default, 1.
            sweep_enable: Indicator for the function to check if it is run by cell_power_sweep()
                    Type, bool.
                    Default, False.
            json_tag: if the function is run by cell_power_sweep(), the function would use
                    this as a part of file name to save TTFF and FFPE results into json file.
                    Type, str.
                    Default, ''.
        Raise:
            TestError: fail to send TTFF start_test_action.
        """
        # Start GTW GPStool.
        test_type = namedtuple('Type', ['command', 'criteria'])
        test_type_ttff = test_type('Hot Start', self.hs_ttff_criteria)
        test_type_pe = test_type('Hot Start', self.hs_ttff_pecriteria)
        self.dut.log.info("Restart GTW GPSTool")
        start_gnss_by_gtw_gpstool(self.dut, state=True)

        # Get current time and convert to human readable format
        begin_time = get_current_epoch_time()
        log_begin_time = epoch_to_log_line_timestamp(begin_time)
        self.dut.log.debug('Start time is {}'.format(log_begin_time))

        # Run hot start TTFF
        for i in range(3):
            self.log.info('Start hot start attempt %d' % (i + 1))
            self.dut.adb.shell(
                "am broadcast -a com.android.gpstool.ttff_action "
                "--es ttff hs --es cycle {} --ez raninterval False".format(
                    iteration))
            sleep(1)
            if self.dut.search_logcat(
                    "act=com.android.gpstool.start_test_action", begin_time):
                self.dut.log.info("Send TTFF start_test_action successfully.")
                break
        else:
            check_current_focus_app(self.dut)
            raise TestError("Fail to send TTFF start_test_action.")

        # Verify hot start TTFF results
        ttff_data = process_ttff_by_gtw_gpstool(self.dut, begin_time,
                                                self.simulator_location)

        # Stop GTW GPSTool
        self.dut.log.info("Stop GTW GPSTool")
        start_gnss_by_gtw_gpstool(self.dut, state=False)

        if sweep_enable:
            self.analysis_ttff_ffpe(ttff_data, json_tag)

        result_ttff = check_ttff_data(self.dut,
                                      ttff_data,
                                      ttff_mode=test_type_ttff.command,
                                      criteria=test_type_ttff.criteria)
        result_pe = check_ttff_pe(self.dut,
                                  ttff_data,
                                  ttff_mode=test_type_pe.command,
                                  pe_criteria=test_type_pe.criteria)
        if not result_ttff or not result_pe:
            self.dut.log.warning('%s TTFF fails to reach '
                                 'designated criteria' % test_type_ttff.command)
            self.dut.log.info("Stop GTW GPSTool")
            return False

        return True

    def hot_start_gnss_power_sweep(self,
                                   start_pwr,
                                   stop_pwr,
                                   offset,
                                   wait,
                                   iteration=1,
                                   sweep_enable=False,
                                   title=''):
        """
        GNSS simulator power sweep of hot start test.

        Args:
            start_pwr: GNSS simulator power sweep start power level.
                    Type, int.
            stop_pwr: GNSS simulator power sweep stop power level.
                    Type, int.
            offset: GNSS simulator power sweep offset
                    Type, int.
            wait: Wait time before the power sweep.
                    Type, int.
            iteration: The iteration times of hot start test.
                    Type, int.
                    Default, 1.
            sweep_enable: Indicator for power sweep.
                          It will be True only in GNSS sensitivity search case.
                    Type, bool.
                    Defaule, False.
            title: the target log folder title for GNSS sensitivity search test items.
                    Type, str.
                    Default, ''.
        """

        # Calculate loop range list from gnss_simulator_power_level and sa_sensitivity
        range_ls = range_wi_end(self.dut, start_pwr, stop_pwr, offset)
        sweep_range = ','.join([str(x) for x in range_ls])

        self.log.debug(
            'Start the GNSS simulator power sweep. The sweep range is [{}]'.
            format(sweep_range))

        if sweep_enable:
            self.start_gnss_and_wait(wait)
        else:
            self.dut.log.info('Wait %d seconds to start TTFF HS' % wait)
            sleep(wait)

        # Sweep GNSS simulator power level in range_ls.
        # Do hot start for every power level.
        # Check the TTFF result if it can pass the criteria.
        gnss_pwr_lvl = -130
        for gnss_pwr_lvl in range_ls:

            # Set GNSS Simulator power level
            self.log.info('Set GNSS simulator power level to %.1f' %
                          gnss_pwr_lvl)
            self.gnss_simulator.set_power(gnss_pwr_lvl)
            json_tag = title + '_gnss_pwr_' + str(gnss_pwr_lvl)

            # GNSS hot start test
            if not self.gnss_hot_start_ttff_ffpe_test(iteration, sweep_enable,
                                                      json_tag):
                sensitivity = gnss_pwr_lvl - offset
                return False, sensitivity
        return True, gnss_pwr_lvl

    def gnss_init_power_setting(self, first_wait=180):
        """
        GNSS initial power level setting.
        Args:
            first_wait: wait time after the cold start.
                        Type, int.
                        Default, 180.
        Returns:
            True if the process is done successully and hot start results pass criteria.
        Raise:
            TestFailure: fail TTFF test criteria.
        """

        # Start and set GNSS simulator
        self.start_and_set_gnss_simulator_power()

        # Start 1st time cold start to obtain ephemeris
        process_gnss_by_gtw_gpstool(self.dut, self.test_types['cs'].criteria)

        self.hot_start_gnss_power_sweep(self.gnss_simulator_power_level,
                                        self.sa_sensitivity,
                                        self.gnss_pwr_lvl_offset, first_wait)

        return True

    def start_gnss_and_wait(self, wait=60):
        """
        The process of enable gnss and spend the wait time for GNSS to
        gather enoung information that make sure the stability of testing.

        Args:
            wait: wait time between power sweep.
                Type, int.
                Default, 60.
        """
        # Create log path for waiting section logs of GPStool.
        gnss_wait_log_dir = os.path.join(self.gnss_log_path, 'GNSS_wait')

        # Enable GNSS to receive satellites' signals for "wait_between_pwr" seconds.
        self.log.info('Enable GNSS for searching satellites')
        start_gnss_by_gtw_gpstool(self.dut, state=True)
        self.log.info('Wait for {} seconds'.format(str(wait)))
        sleep(wait)

        # Stop GNSS and pull the logs.
        start_gnss_by_gtw_gpstool(self.dut, state=False)
        get_gpstool_logs(self.dut, gnss_wait_log_dir, False)

    def cell_power_sweep(self):
        """
        Linear search cellular power level. Doing GNSS hot start with cellular coexistence
        and checking if hot start can pass hot start criteria or not.

        Returns: final power level of cellular power
        """
        # Get parameters from user params.
        ttft_iteration = self.user_params.get('ttff_iteration', 25)
        wait_before_test = self.user_params.get('wait_before_test', 60)
        wait_between_pwr = self.user_params.get('wait_between_pwr', 60)
        power_th = self.start_pwr

        # Generate the power sweep list.
        power_search_ls = range_wi_end(self.dut, self.start_pwr, self.stop_pwr,
                                       self.offset)

        # Set GNSS simulator power level.
        self.gnss_simulator.set_power(self.sa_sensitivity)

        # Create gnss log folders for init and cellular sweep
        gnss_init_log_dir = os.path.join(self.gnss_log_path, 'GNSS_init')

        # Pull all exist GPStool logs into GNSS_init folder
        get_gpstool_logs(self.dut, gnss_init_log_dir, False)

        if power_search_ls:
            # Run the cellular and GNSS coexistence test item.
            for i, pwr_lvl in enumerate(power_search_ls):
                self.log.info('Cellular power sweep loop: {}'.format(int(i)))
                self.log.info('Cellular target power: {}'.format(int(pwr_lvl)))

                # Enable GNSS to receive satellites' signals for "wait_between_pwr" seconds.
                # Wait more time before 1st power level
                if i == 0:
                    wait = wait_before_test
                else:
                    wait = wait_between_pwr
                self.start_gnss_and_wait(wait)

                # Set cellular Tx power level.
                eecoex_cmd = self.eecoex_func.format(str(pwr_lvl))
                eecoex_cmd_file_str = eecoex_cmd.replace(',', '_')
                excute_eecoexer_function(self.dut, eecoex_cmd)

                # Get the last power level that can pass hots start ttff/ffpe spec.
                if self.gnss_hot_start_ttff_ffpe_test(ttft_iteration, True,
                                                      eecoex_cmd_file_str):
                    if i + 1 == len(power_search_ls):
                        power_th = pwr_lvl
                else:
                    if i == 0:
                        power_th = self.start_pwr
                    else:
                        power_th = power_search_ls[i - 1]

                # Stop cellular Tx after a test cycle.
                self.stop_cell_tx()

        else:
            # Run the stand alone test item.
            self.start_gnss_and_wait(wait_between_pwr)

            eecoex_cmd_file_str = 'no_cellular_coex'
            self.gnss_hot_start_ttff_ffpe_test(ttft_iteration, True,
                                               eecoex_cmd_file_str)

        self.log.info('The GNSS WWAN coex celluar Tx power is {}'.format(
            str(power_th)))

        return power_th
