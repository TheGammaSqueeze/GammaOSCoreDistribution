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

import json
import os
import re
import tempfile
from typing import List, Optional, Set

from . test_utils import TestBase, TestHelper


class TestGeckoProfileGenerator(TestBase):
    def run_generator(self, testdata_file: str, options: Optional[List[str]] = None) -> str:
        testdata_path = TestHelper.testdata_path(testdata_file)
        args = ['gecko_profile_generator.py', '-i', testdata_path]
        if options:
            args.extend(options)
        return self.run_cmd(args, return_output=True)

    def test_golden(self):
        output = self.run_generator('perf_with_interpreter_frames.data')
        got = json.loads(output)
        golden_path = TestHelper.testdata_path('perf_with_interpreter_frames.gecko.json')
        with open(golden_path) as f:
            want = json.load(f)
        self.assertEqual(
            json.dumps(got, sort_keys=True, indent=2),
            json.dumps(want, sort_keys=True, indent=2))

    def test_sample_filters(self):
        def get_threads_for_filter(filter: str) -> Set[int]:
            report = self.run_generator('perf_display_bitmaps.data', filter.split())
            pattern = re.compile(r'"tid":\s+(\d+),')
            threads = set()
            for m in re.finditer(pattern, report):
                threads.add(int(m.group(1)))
            return threads

        self.assertNotIn(31850, get_threads_for_filter('--exclude-pid 31850'))
        self.assertIn(31850, get_threads_for_filter('--include-pid 31850'))
        self.assertIn(31850, get_threads_for_filter('--pid 31850'))
        self.assertNotIn(31881, get_threads_for_filter('--exclude-tid 31881'))
        self.assertIn(31881, get_threads_for_filter('--include-tid 31881'))
        self.assertIn(31881, get_threads_for_filter('--tid 31881'))
        self.assertNotIn(31881, get_threads_for_filter(
            '--exclude-process-name com.example.android.displayingbitmaps'))
        self.assertIn(31881, get_threads_for_filter(
            '--include-process-name com.example.android.displayingbitmaps'))
        self.assertNotIn(31850, get_threads_for_filter(
            '--exclude-thread-name com.example.android.displayingbitmaps'))
        self.assertIn(31850, get_threads_for_filter(
            '--include-thread-name com.example.android.displayingbitmaps'))

        with tempfile.NamedTemporaryFile('w', delete=False) as filter_file:
            filter_file.write('GLOBAL_BEGIN 684943449406175\nGLOBAL_END 684943449406176')
            filter_file.flush()
            threads = get_threads_for_filter('--filter-file ' + filter_file.name)
            self.assertIn(31881, threads)
            self.assertNotIn(31850, threads)
        os.unlink(filter_file.name)

    def test_show_art_frames(self):
        art_frame_str = 'art::interpreter::DoCall'
        report = self.run_generator('perf_with_interpreter_frames.data')
        self.assertNotIn(art_frame_str, report)
        report = self.run_generator('perf_with_interpreter_frames.data', ['--show-art-frames'])
        self.assertIn(art_frame_str, report)
