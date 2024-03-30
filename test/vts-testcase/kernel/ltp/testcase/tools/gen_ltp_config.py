#!/usr/bin/env python
#
# Copyright (C) 2020 The Android Open Source Project
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
#
import argparse
import os
import sys
from distutils.util import strtobool

import ltp_test_cases
from common import filter_utils

def run(android_build_top, arch, n_bit, is_low_mem, is_hwasan, run_staging, output_file):

    android_build_top = android_build_top
    ltp_tests = ltp_test_cases.LtpTestCases(
        android_build_top, None)

    test_filter = filter_utils.Filter()
    ltp_tests.GenConfig(
        arch,
        n_bit,
        test_filter,
        output_file=output_file,
        run_staging=run_staging,
        is_low_mem=is_low_mem,
        is_hwasan=is_hwasan)

if __name__ == '__main__':
    arg_parser = argparse.ArgumentParser(
        description='Generate LTP configuration files for VTS')
    arg_parser.add_argument('--arch',
                            dest='arch',
                            type=str,
                            choices=['arm', 'x86'],
                            required=True,
                            help="Target device architecture")
    arg_parser.add_argument('--bitness',
                            dest='bitness',
                            type=int,
                            choices=[32, 64],
                            required=True,
                            help="Target device architecture bitness")
    arg_parser.add_argument('--low-mem',
                            dest='is_low_mem',
                            type=str,
                            choices=['True', 'False'],
                            required=True,
                            help="Target device is low memory device")
    arg_parser.add_argument('--hwasan',
                            dest='is_hwasan',
                            type=str,
                            choices=['True', 'False'],
                            required=True,
                            help="Target device is hwasan")
    arg_parser.add_argument('--staging',
                            dest='run_staging',
                            type=str,
                            choices=['True', 'False'],
                            default="False",
                            help="Run all the tests, except from the disabled ones")
    arg_parser.add_argument('output_file_path',
                            nargs=1,
                            help="Path for the output file")
    args = arg_parser.parse_args()

    run(android_build_top=os.environ['ANDROID_BUILD_TOP'],
        arch=args.arch,
        n_bit=str(args.bitness),
        is_low_mem=strtobool(args.is_low_mem),
        is_hwasan=strtobool(args.is_hwasan),
        run_staging=strtobool(args.run_staging),
        output_file=args.output_file_path[0])
