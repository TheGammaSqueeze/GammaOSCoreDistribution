# Copyright 2022 Android Open Source Project
# SPDX-License-Identifier: MIT

files_lib_codec_common = files(
  'ChecksumCalculator.cpp',
  'goldfish_dma.cpp',
  'glUtils.cpp',
)

lib_codec_common = static_library(
   'codec_common',
   files_lib_codec_common,
   cpp_args: cpp_args,
   include_directories: [inc_android_compat, inc_qemu_pipe,
                         inc_qemu_pipe_types, inc_host]
)
