#!/usr/bin/env python3
#
# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os
import tempfile
from typing import List, Set

from simpleperf_report_lib import ReportLib
from . test_utils import TestBase, TestHelper


class TestReportLib(TestBase):
    def setUp(self):
        super(TestReportLib, self).setUp()
        self.report_lib = ReportLib()

    def tearDown(self):
        self.report_lib.Close()
        super(TestReportLib, self).tearDown()

    def test_build_id(self):
        self.report_lib.SetRecordFile(TestHelper.testdata_path('perf_with_symbols.data'))
        build_id = self.report_lib.GetBuildIdForPath('/data/t2')
        self.assertEqual(build_id, '0x70f1fe24500fc8b0d9eb477199ca1ca21acca4de')

    def test_symbol(self):
        self.report_lib.SetRecordFile(TestHelper.testdata_path('perf_with_symbols.data'))
        found_func2 = False
        while self.report_lib.GetNextSample():
            symbol = self.report_lib.GetSymbolOfCurrentSample()
            if symbol.symbol_name == 'func2(int, int)':
                found_func2 = True
                self.assertEqual(symbol.symbol_addr, 0x4004ed)
                self.assertEqual(symbol.symbol_len, 0x14)
        self.assertTrue(found_func2)

    def test_sample(self):
        self.report_lib.SetRecordFile(TestHelper.testdata_path('perf_with_symbols.data'))
        found_sample = False
        while self.report_lib.GetNextSample():
            sample = self.report_lib.GetCurrentSample()
            if sample.ip == 0x4004ff and sample.time == 7637889424953:
                found_sample = True
                self.assertEqual(sample.pid, 15926)
                self.assertEqual(sample.tid, 15926)
                self.assertEqual(sample.thread_comm, 't2')
                self.assertEqual(sample.cpu, 5)
                self.assertEqual(sample.period, 694614)
                event = self.report_lib.GetEventOfCurrentSample()
                self.assertEqual(event.name, 'cpu-cycles')
                callchain = self.report_lib.GetCallChainOfCurrentSample()
                self.assertEqual(callchain.nr, 0)
        self.assertTrue(found_sample)

    def test_meta_info(self):
        self.report_lib.SetRecordFile(TestHelper.testdata_path('perf_with_trace_offcpu_v2.data'))
        meta_info = self.report_lib.MetaInfo()
        self.assertTrue("simpleperf_version" in meta_info)
        self.assertEqual(meta_info["system_wide_collection"], "false")
        self.assertEqual(meta_info["trace_offcpu"], "true")
        self.assertEqual(meta_info["event_type_info"], "cpu-clock,1,0\nsched:sched_switch,2,91")
        self.assertTrue("product_props" in meta_info)

    def test_event_name_from_meta_info(self):
        self.report_lib.SetRecordFile(TestHelper.testdata_path('perf_with_tracepoint_event.data'))
        event_names = set()
        while self.report_lib.GetNextSample():
            event_names.add(self.report_lib.GetEventOfCurrentSample().name)
        self.assertTrue('sched:sched_switch' in event_names)
        self.assertTrue('cpu-cycles' in event_names)

    def test_record_cmd(self):
        self.report_lib.SetRecordFile(TestHelper.testdata_path('perf_with_trace_offcpu_v2.data'))
        self.assertEqual(self.report_lib.GetRecordCmd(),
                         '/data/user/0/com.google.samples.apps.sunflower/simpleperf record ' +
                         '--app com.google.samples.apps.sunflower --add-meta-info ' +
                         'app_type=debuggable --in-app --tracepoint-events ' +
                         '/data/local/tmp/tracepoint_events --out-fd 3 --stop-signal-fd 4 -g ' +
                         '--size-limit 500k --trace-offcpu -e cpu-clock:u')

    def test_offcpu(self):
        self.report_lib.SetRecordFile(TestHelper.testdata_path('perf_with_trace_offcpu_v2.data'))
        total_period = 0
        sleep_function_period = 0
        sleep_function_name = "__epoll_pwait"
        while self.report_lib.GetNextSample():
            sample = self.report_lib.GetCurrentSample()
            total_period += sample.period
            if self.report_lib.GetSymbolOfCurrentSample().symbol_name == sleep_function_name:
                sleep_function_period += sample.period
                continue
            callchain = self.report_lib.GetCallChainOfCurrentSample()
            for i in range(callchain.nr):
                if callchain.entries[i].symbol.symbol_name == sleep_function_name:
                    sleep_function_period += sample.period
                    break
            self.assertEqual(self.report_lib.GetEventOfCurrentSample().name, 'cpu-clock:u')
        sleep_percentage = float(sleep_function_period) / total_period
        self.assertGreater(sleep_percentage, 0.30)

    def test_show_art_frames(self):
        def has_art_frame(report_lib):
            report_lib.SetRecordFile(TestHelper.testdata_path('perf_with_interpreter_frames.data'))
            result = False
            while report_lib.GetNextSample():
                callchain = report_lib.GetCallChainOfCurrentSample()
                for i in range(callchain.nr):
                    if callchain.entries[i].symbol.symbol_name == 'artMterpAsmInstructionStart':
                        result = True
                        break
            report_lib.Close()
            return result

        report_lib = ReportLib()
        self.assertFalse(has_art_frame(report_lib))
        report_lib = ReportLib()
        report_lib.ShowArtFrames(False)
        self.assertFalse(has_art_frame(report_lib))
        report_lib = ReportLib()
        report_lib.ShowArtFrames(True)
        self.assertTrue(has_art_frame(report_lib))

    def test_merge_java_methods(self):
        def parse_dso_names(report_lib):
            dso_names = set()
            report_lib.SetRecordFile(TestHelper.testdata_path('perf_with_interpreter_frames.data'))
            while report_lib.GetNextSample():
                dso_names.add(report_lib.GetSymbolOfCurrentSample().dso_name)
                callchain = report_lib.GetCallChainOfCurrentSample()
                for i in range(callchain.nr):
                    dso_names.add(callchain.entries[i].symbol.dso_name)
            report_lib.Close()
            has_jit_symfiles = any('TemporaryFile-' in name for name in dso_names)
            has_jit_cache = '[JIT cache]' in dso_names
            return has_jit_symfiles, has_jit_cache

        report_lib = ReportLib()
        self.assertEqual(parse_dso_names(report_lib), (False, True))

        report_lib = ReportLib()
        report_lib.MergeJavaMethods(True)
        self.assertEqual(parse_dso_names(report_lib), (False, True))

        report_lib = ReportLib()
        report_lib.MergeJavaMethods(False)
        self.assertEqual(parse_dso_names(report_lib), (True, False))

    def test_jited_java_methods(self):
        report_lib = ReportLib()
        report_lib.SetRecordFile(TestHelper.testdata_path('perf_with_jit_symbol.data'))
        has_jit_cache = False
        while report_lib.GetNextSample():
            if report_lib.GetSymbolOfCurrentSample().dso_name == '[JIT app cache]':
                has_jit_cache = True
            callchain = report_lib.GetCallChainOfCurrentSample()
            for i in range(callchain.nr):
                if callchain.entries[i].symbol.dso_name == '[JIT app cache]':
                    has_jit_cache = True
        report_lib.Close()
        self.assertTrue(has_jit_cache)

    def test_tracing_data(self):
        self.report_lib.SetRecordFile(TestHelper.testdata_path('perf_with_tracepoint_event.data'))
        has_tracing_data = False
        while self.report_lib.GetNextSample():
            event = self.report_lib.GetEventOfCurrentSample()
            tracing_data = self.report_lib.GetTracingDataOfCurrentSample()
            if event.name == 'sched:sched_switch':
                self.assertIsNotNone(tracing_data)
                self.assertIn('prev_pid', tracing_data)
                self.assertIn('next_comm', tracing_data)
                if tracing_data['prev_pid'] == 9896 and tracing_data['next_comm'] == 'swapper/4':
                    has_tracing_data = True
            else:
                self.assertIsNone(tracing_data)
        self.assertTrue(has_tracing_data)

    def test_dynamic_field_in_tracing_data(self):
        self.report_lib.SetRecordFile(TestHelper.testdata_path(
            'perf_with_tracepoint_event_dynamic_field.data'))
        has_dynamic_field = False
        while self.report_lib.GetNextSample():
            event = self.report_lib.GetEventOfCurrentSample()
            tracing_data = self.report_lib.GetTracingDataOfCurrentSample()
            if event.name == 'kprobes:myopen':
                self.assertIsNotNone(tracing_data)
                self.assertIn('name', tracing_data)
                if tracing_data['name'] == '/sys/kernel/debug/tracing/events/kprobes/myopen/format':
                    has_dynamic_field = True
            else:
                self.assertIsNone(tracing_data)
        self.assertTrue(has_dynamic_field)

    def test_add_proguard_mapping_file(self):
        with self.assertRaises(ValueError):
            self.report_lib.AddProguardMappingFile('non_exist_file')
        proguard_mapping_file = TestHelper.testdata_path('proguard_mapping.txt')
        self.report_lib.AddProguardMappingFile(proguard_mapping_file)

    def test_set_trace_offcpu_mode(self):
        # GetSupportedTraceOffCpuModes() before SetRecordFile() triggers RuntimeError.
        with self.assertRaises(RuntimeError):
            self.report_lib.GetSupportedTraceOffCpuModes()
        # SetTraceOffCpuModes() before SetRecordFile() triggers RuntimeError.
        with self.assertRaises(RuntimeError):
            self.report_lib.SetTraceOffCpuMode('on-cpu')

        mode_dict = {
            'on-cpu': {
                'cpu-clock:u': (208, 52000000),
                'sched:sched_switch': (0, 0),
            },
            'off-cpu': {
                'cpu-clock:u': (0, 0),
                'sched:sched_switch': (91, 344124304),
            },
            'on-off-cpu': {
                'cpu-clock:u': (208, 52000000),
                'sched:sched_switch': (91, 344124304),
            },
            'mixed-on-off-cpu': {
                'cpu-clock:u': (299, 396124304),
                'sched:sched_switch': (0, 0),
            },
        }

        self.report_lib.SetRecordFile(TestHelper.testdata_path('perf_with_trace_offcpu_v2.data'))
        self.assertEqual(set(self.report_lib.GetSupportedTraceOffCpuModes()), set(mode_dict.keys()))
        for mode, expected_values in mode_dict.items():
            self.report_lib.Close()
            self.report_lib = ReportLib()
            self.report_lib.SetRecordFile(
                TestHelper.testdata_path('perf_with_trace_offcpu_v2.data'))
            self.report_lib.SetTraceOffCpuMode(mode)

            cpu_clock_period = 0
            cpu_clock_samples = 0
            sched_switch_period = 0
            sched_switch_samples = 0
            while self.report_lib.GetNextSample():
                sample = self.report_lib.GetCurrentSample()
                event = self.report_lib.GetEventOfCurrentSample()
                if event.name == 'cpu-clock:u':
                    cpu_clock_period += sample.period
                    cpu_clock_samples += 1
                else:
                    self.assertEqual(event.name, 'sched:sched_switch')
                    sched_switch_period += sample.period
                    sched_switch_samples += 1
            self.assertEqual(cpu_clock_samples, expected_values['cpu-clock:u'][0])
            self.assertEqual(cpu_clock_period, expected_values['cpu-clock:u'][1])
            self.assertEqual(sched_switch_samples, expected_values['sched:sched_switch'][0])
            self.assertEqual(sched_switch_period, expected_values['sched:sched_switch'][1])

        # Check trace-offcpu modes on a profile not recorded with --trace-offcpu.
        self.report_lib.Close()
        self.report_lib = ReportLib()
        self.report_lib.SetRecordFile(TestHelper.testdata_path('perf.data'))
        self.assertEqual(self.report_lib.GetSupportedTraceOffCpuModes(), [])
        with self.assertRaises(RuntimeError):
            self.report_lib.SetTraceOffCpuMode('on-cpu')

    def test_set_sample_filter(self):
        """ Test using ReportLib.SetSampleFilter(). """
        def get_threads_for_filter(filters: List[str]) -> Set[int]:
            self.report_lib.Close()
            self.report_lib = ReportLib()
            self.report_lib.SetRecordFile(TestHelper.testdata_path('perf_display_bitmaps.data'))
            self.report_lib.SetSampleFilter(filters)
            threads = set()
            while self.report_lib.GetNextSample():
                sample = self.report_lib.GetCurrentSample()
                threads.add(sample.tid)
            return threads

        self.assertNotIn(31850, get_threads_for_filter(['--exclude-pid', '31850']))
        self.assertIn(31850, get_threads_for_filter(['--include-pid', '31850']))
        self.assertNotIn(31881, get_threads_for_filter(['--exclude-tid', '31881']))
        self.assertIn(31881, get_threads_for_filter(['--include-tid', '31881']))
        self.assertNotIn(31881, get_threads_for_filter(
            ['--exclude-process-name', 'com.example.android.displayingbitmaps']))
        self.assertIn(31881, get_threads_for_filter(
            ['--include-process-name', 'com.example.android.displayingbitmaps']))
        self.assertNotIn(31850, get_threads_for_filter(
            ['--exclude-thread-name', 'com.example.android.displayingbitmaps']))
        self.assertIn(31850, get_threads_for_filter(
            ['--include-thread-name', 'com.example.android.displayingbitmaps']))

        # Check that thread name can have space.
        self.assertNotIn(31856, get_threads_for_filter(
            ['--exclude-thread-name', 'Jit thread pool']))
        self.assertIn(31856, get_threads_for_filter(['--include-thread-name', 'Jit thread pool']))

        with tempfile.NamedTemporaryFile('w', delete=False) as filter_file:
            filter_file.write('GLOBAL_BEGIN 684943449406175\nGLOBAL_END 684943449406176')
            filter_file.flush()
            threads = get_threads_for_filter(['--filter-file', filter_file.name])
            self.assertIn(31881, threads)
            self.assertNotIn(31850, threads)
        os.unlink(filter_file.name)
