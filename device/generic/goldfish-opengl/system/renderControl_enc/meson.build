# Copyright 2022 Android Open Source Project
# SPDX-License-Identifier: MIT

files_lib_render_control_enc = files(
  'renderControl_enc.cpp',
)

lib_render_control_enc = static_library(
   'render_control',
   files_lib_render_control_enc,
   cpp_args: cpp_args,
   include_directories: [inc_host, inc_opengl_codec, inc_android_emu,
                         inc_android_compat],
)
