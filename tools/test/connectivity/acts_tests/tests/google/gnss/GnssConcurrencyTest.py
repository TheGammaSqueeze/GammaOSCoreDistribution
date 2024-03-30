#!/usr/bin/env python3.5
#
#   Copyright 2021 - The Android Open Source Project
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

import time
import datetime
import re
from acts import utils
from acts import signals
from acts.base_test import BaseTestClass
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.tel_logging_utils import start_adb_tcpdump
from acts_contrib.test_utils.tel.tel_logging_utils import stop_adb_tcpdump
from acts_contrib.test_utils.tel.tel_logging_utils import get_tcpdump_log
from acts_contrib.test_utils.gnss import gnss_test_utils as gutils

CONCURRENCY_TYPE = {
    "gnss": "GNSS location received",
    "gnss_meas": "GNSS measurement received",
    "ap_location": "reportLocation"
}


class GnssConcurrencyTest(BaseTestClass):
    """ GNSS Concurrency TTFF Tests. """

    def setup_class(self):
        super().setup_class()
        self.ad = self.android_devices[0]
        req_params = [
            "standalone_cs_criteria", "chre_tolerate_rate", "qdsp6m_path",
            "outlier_criteria", "max_outliers", "pixel_lab_location",
            "max_interval", "onchip_interval"
        ]
        self.unpack_userparams(req_param_names=req_params)
        gutils._init_device(self.ad)
        self.ad.adb.shell("setprop persist.vendor.radio.adb_log_on 0")
        self.ad.adb.shell("sync")
        gutils.reboot(self.ad)

    def setup_test(self):
        gutils.clear_logd_gnss_qxdm_log(self.ad)
        gutils.start_pixel_logger(self.ad)
        start_adb_tcpdump(self.ad)
        # related properties
        gutils.check_location_service(self.ad)
        gutils.get_baseband_and_gms_version(self.ad)
        self.load_chre_nanoapp()

    def teardown_test(self):
        gutils.stop_pixel_logger(self.ad)
        stop_adb_tcpdump(self.ad)

    def on_fail(self, test_name, begin_time):
        self.ad.take_bug_report(test_name, begin_time)
        gutils.get_gnss_qxdm_log(self.ad, self.qdsp6m_path)
        get_tcpdump_log(self.ad, test_name, begin_time)

    def is_brcm_test(self):
        """ Check the test is for BRCM and skip if not. """
        if gutils.check_chipset_vendor_by_qualcomm(self.ad):
            raise signals.TestSkip("Not BRCM chipset. Skip the test.")

    def load_chre_nanoapp(self):
        """ Load CHRE nanoapp to target Android Device. """
        for _ in range(0, 3):
            try:
                self.ad.log.info("Start to load the nanoapp")
                res = self.ad.adb.shell("chre_power_test_client load")
                if "success: 1" in res:
                    self.ad.log.info("Nano app loaded successfully")
                    break
            except Exception as e:
                self.ad.log.warning("Nano app loaded fail: %s" % e)
                gutils.reboot(self.ad)
        else:
            raise signals.TestError("Failed to load CHRE nanoapp")

    def enable_chre(self, freq):
        """ Enable or disable gnss concurrency via nanoapp.

        Args:
            freq: an int for frequency, set 0 as disable.
        """
        freq = freq * 1000
        cmd = "chre_power_test_client"
        option = "enable %d" % freq if freq != 0 else "disable"

        for type in CONCURRENCY_TYPE.keys():
            if "ap" not in type:
                self.ad.adb.shell(" ".join([cmd, type, option]))

    def parse_concurrency_result(self, begin_time, type, criteria):
        """ Parse the test result with given time and criteria.

        Args:
            begin_time: test begin time.
            type: str for location request type.
            criteria: dictionary for test criteria.
        Return: List for the failure and outlier loops and results.
        """
        results = []
        failures = []
        outliers = []
        search_results = self.ad.search_logcat(CONCURRENCY_TYPE[type],
                                               begin_time)
        start_time = utils.epoch_to_human_time(begin_time)
        start_time = datetime.datetime.strptime(start_time,
                                                "%m-%d-%Y %H:%M:%S ")
        if not search_results:
            raise signals.TestFailure(f"No log entry found for keyword:"
                                      f"{CONCURRENCY_TYPE[type]}")
        results.append(
            (search_results[0]["datetime_obj"] - start_time).total_seconds())
        samples = len(search_results) - 1
        for i in range(samples):
            target = search_results[i + 1]
            timedelt = target["datetime_obj"] - search_results[i]["datetime_obj"]
            timedelt_sec = timedelt.total_seconds()
            results.append(timedelt_sec)
            if timedelt_sec > (criteria *
                               self.chre_tolerate_rate) + self.outlier_criteria:
                failures.append(target)
                self.ad.log.error("[Failure][%s]:%.2f sec" %
                                  (target["time_stamp"], timedelt_sec))
            elif timedelt_sec > criteria * self.chre_tolerate_rate:
                outliers.append(target)
                self.ad.log.info("[Outlier][%s]:%.2f sec" %
                                 (target["time_stamp"], timedelt_sec))

        res_summary = " ".join([str(res) for res in results])
        self.ad.log.info("[%s]Overall Result: %s" % (type, res_summary))
        self.ad.log.info("TestResult %s_samples %d" % (type, samples))
        self.ad.log.info("TestResult %s_outliers %d" % (type, len(outliers)))
        self.ad.log.info("TestResult %s_failures %d" % (type, len(failures)))
        self.ad.log.info("TestResult %s_max_time %.2f" %
                         (type, max(results[1:])))

        return outliers, failures, results

    def run_gnss_concurrency_test(self, criteria, test_duration):
        """ Execute GNSS concurrency test steps.

        Args:
            criteria: int for test criteria.
            test_duration: int for test duration.
        """
        begin_time = utils.get_current_epoch_time()
        self.ad.log.info("Tests Start at %s" %
                         utils.epoch_to_human_time(begin_time))
        gutils.start_gnss_by_gtw_gpstool(
            self.ad, True, freq=criteria["ap_location"])
        self.enable_chre(criteria["gnss"])
        time.sleep(test_duration)
        self.enable_chre(0)
        gutils.start_gnss_by_gtw_gpstool(self.ad, False)
        self.validate_location_test_result(begin_time, criteria)

    def run_chre_only_test(self, criteria, test_duration):
        """ Execute CHRE only test steps.

        Args:
            criteria: int for test criteria.
            test_duration: int for test duration.
        """
        begin_time = utils.get_current_epoch_time()
        self.ad.log.info("Tests Start at %s" %
                         utils.epoch_to_human_time(begin_time))
        self.enable_chre(criteria["gnss"])
        time.sleep(test_duration)
        self.enable_chre(0)
        self.validate_location_test_result(begin_time, criteria)

    def validate_location_test_result(self, begin_time, request):
        """ Validate GNSS concurrency/CHRE test results.

        Args:
            begin_time: epoc of test begin time
            request: int for test criteria.
        """
        results = {}
        outliers = {}
        failures = {}
        failure_log = ""
        for request_type, criteria in request.items():
            criteria = criteria if criteria > 1 else 1
            self.ad.log.info("Starting process %s result" % request_type)
            outliers[request_type], failures[request_type], results[
                request_type] = self.parse_concurrency_result(
                    begin_time, request_type, criteria)
            if not results[request_type]:
                failure_log += "[%s] Fail to find location report.\n" % request_type
            if len(failures[request_type]) > 0:
                failure_log += "[%s] Test exceeds criteria: %.2f\n" % (
                    request_type, criteria)
            if len(outliers[request_type]) > self.max_outliers:
                failure_log += "[%s] Outliers excceds max amount: %d\n" % (
                    request_type, len(outliers[request_type]))

        if failure_log:
            raise signals.TestFailure(failure_log)

    def run_engine_switching_test(self, freq):
        """ Conduct engine switching test with given frequency.

        Args:
            freq: a list identify source1/2 frequency [freq1, freq2]
        """
        request = {"ap_location": self.max_interval}
        begin_time = utils.get_current_epoch_time()
        self.ad.droid.startLocating(freq[0] * 1000, 0)
        time.sleep(10)
        for i in range(5):
            gutils.start_gnss_by_gtw_gpstool(self.ad, True, freq=freq[1])
            time.sleep(10)
            gutils.start_gnss_by_gtw_gpstool(self.ad, False)
        self.ad.droid.stopLocating()
        self.calculate_position_error(begin_time)
        self.validate_location_test_result(begin_time, request)

    def calculate_position_error(self, begin_time):
        """ Calculate the position error for the logcat search results.

        Args:
            begin_time: test begin time
        """
        position_errors = []
        search_results = self.ad.search_logcat("reportLocation", begin_time)
        for result in search_results:
            # search for location like 25.000717,121.455163
            regex = r"(-?\d{1,5}\.\d{1,10}),\s*(-?\d{1,5}\.\d{1,10})"
            result = re.search(regex, result["log_message"])
            if not result:
                raise ValueError("lat/lon does not found. "
                                 f"original text: {result['log_message']}")
            lat = float(result.group(1))
            lon = float(result.group(2))
            pe = gutils.calculate_position_error(lat, lon,
                                                 self.pixel_lab_location)
            position_errors.append(pe)
        self.ad.log.info("TestResult max_position_error %.2f" %
                         max(position_errors))

    # Concurrency Test Cases
    @test_tracker_info(uuid="9b0daebf-461e-4005-9773-d5d10aaeaaa4")
    def test_gnss_concurrency_ct1(self):
        test_duration = 15
        criteria = {"ap_location": 1, "gnss": 1, "gnss_meas": 1}
        self.run_gnss_concurrency_test(criteria, test_duration)

    @test_tracker_info(uuid="f423db2f-12a0-4858-b66f-99e7ca6010c3")
    def test_gnss_concurrency_ct2(self):
        test_duration = 30
        criteria = {"ap_location": 1, "gnss": 8, "gnss_meas": 8}
        self.run_gnss_concurrency_test(criteria, test_duration)

    @test_tracker_info(uuid="f72d2df0-f70a-4a11-9f68-2a38f6974454")
    def test_gnss_concurrency_ct3(self):
        test_duration = 60
        criteria = {"ap_location": 15, "gnss": 8, "gnss_meas": 8}
        self.run_gnss_concurrency_test(criteria, test_duration)

    @test_tracker_info(uuid="8e5563fd-afcd-40d3-9392-7fc0d10f49da")
    def test_gnss_concurrency_aoc1(self):
        test_duration = 120
        criteria = {"ap_location": 61, "gnss": 1, "gnss_meas": 1}
        self.run_gnss_concurrency_test(criteria, test_duration)

    @test_tracker_info(uuid="fb258565-6ac8-4bf7-a554-01d63fc4ef54")
    def test_gnss_concurrency_aoc2(self):
        test_duration = 120
        criteria = {"ap_location": 61, "gnss": 10, "gnss_meas": 10}
        self.run_gnss_concurrency_test(criteria, test_duration)

    # CHRE Only Test Cases
    @test_tracker_info(uuid="cb85fa60-9f1a-4957-b5e3-0f2e5db70b47")
    def test_gnss_chre1(self):
        test_duration = 15
        criteria = {"gnss": 1, "gnss_meas": 1}
        self.run_chre_only_test(criteria, test_duration)

    @test_tracker_info(uuid="6ab17866-0d0e-4d9e-b3af-441d9db0e324")
    def test_gnss_chre2(self):
        test_duration = 30
        criteria = {"gnss": 8, "gnss_meas": 8}
        self.run_chre_only_test(criteria, test_duration)

    # Interval tests
    @test_tracker_info(uuid="53b161e5-335e-44a7-ae2e-eae7464a2b37")
    def test_variable_interval_via_chre(self):
        test_duration = 10
        intervals = [{
            "gnss": 0.1,
            "gnss_meas": 0.1
        }, {
            "gnss": 0.5,
            "gnss_meas": 0.5
        }, {
            "gnss": 1.5,
            "gnss_meas": 1.5
        }]
        for interval in intervals:
            self.run_chre_only_test(interval, test_duration)

    @test_tracker_info(uuid="ee0a46fe-aa5f-4dfd-9cb7-d4924f9e9cea")
    def test_variable_interval_via_framework(self):
        test_duration = 10
        intervals = [0, 0.5, 1.5]
        for interval in intervals:
            begin_time = utils.get_current_epoch_time()
            self.ad.droid.startLocating(interval * 1000, 0)
            time.sleep(test_duration)
            self.ad.droid.stopLocating()
            criteria = interval if interval > 1 else 1
            self.parse_concurrency_result(begin_time, "ap_location", criteria)

    # Engine switching test
    @test_tracker_info(uuid="8b42bcb2-cb8c-4ef9-bd98-4fb74a521224")
    def test_gps_engine_switching_host_to_onchip(self):
        self.is_brcm_test()
        freq = [1, self.onchip_interval]
        self.run_engine_switching_test(freq)

    @test_tracker_info(uuid="636041dc-2bd6-4854-aa5d-61c87943d99c")
    def test_gps_engine_switching_onchip_to_host(self):
        self.is_brcm_test()
        freq = [self.onchip_interval, 1]
        self.run_engine_switching_test(freq)
