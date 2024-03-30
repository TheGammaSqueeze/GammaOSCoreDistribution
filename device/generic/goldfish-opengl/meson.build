# Copyright 2022 Android Open Source Project
# SPDX-License-Identifier: MIT

project('gfxstream', 'cpp', 'c',
         version : '0.0.1',
         license : 'MIT OR Apache-2.0')

cc = meson.get_compiler('cpp')
prog_python = import('python').find_installation('python3')

#===============#
# Options       #
#===============#

with_vulkan_icd_dir = get_option('vulkan-icd-dir')
if with_vulkan_icd_dir == ''
  with_vulkan_icd_dir = join_paths(get_option('datadir'), 'vulkan/icd.d')
endif

#===============#
# Configuration #
#===============#
c_args = []
cpp_args = []

# Paravirtualization moving towards virtio
cpp_args += '-DVIRTIO_GPU'
# Gfxstream
cpp_args += '-DGFXSTREAM'
# Our internal guest build
cpp_args += '-DLINUX_GUEST_BUILD'
# Don't want to goldfish OpenGL
cpp_args += '-DGOLDFISH_NO_GL'
# This is a good number for PAGE_SIZE
# But we should really do getpagesize(..).
cpp_args += '-DPAGE_SIZE=4096'
# This should just be called NO_TRACE eventually
cpp_args += '-DFUCHSIA_NO_TRACE'

#===============#
# Dependencies  #
#===============#

dl_dep = cc.find_library('dl', required: false)
drm_dep = dependency('libdrm')
thread_dep = dependency('threads')

#===============#
# Includes      #
#===============#

inc_android_emu = include_directories('android-emu')
inc_host = include_directories('host/include/libOpenglRender')
inc_system = include_directories('system/include')

#================#
# Subdirectories #
#================#

subdir('fuchsia')
subdir('android-emu/android/base')
subdir('shared')
subdir('system')
