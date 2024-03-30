#!/usr/bin/env -S python -B
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
"""Downloads mainline prebuilts from the build server."""
import os
import sys

THIS_DIR = os.path.realpath(os.path.dirname(__file__))

sys.path.append(THIS_DIR + '/../common/python')

import update_prebuilts as update

PREBUILT_DESCRIPTION = 'mainline'
MODULE_TARGET = 'mainline_modules-userdebug'
SDK_TARGET = "mainline_modules_sdks-userdebug"

COMMIT_MESSAGE_NOTE = """\
CL prepared by prebuilts/runtime/mainline/update.py.

See prebuilts/runtime/mainline/README.md for update instructions.

Test: Presubmits
"""

def InstallApexEntries(apex_base_name, install_dir):
  res = []
  for arch in ['arm', 'arm64', 'x86', 'x86_64']:
    res.append(update.InstallEntry(
        MODULE_TARGET,
        os.path.join('mainline_modules_' + arch,
                     'com.android.' + apex_base_name + '.apex'),
        os.path.join(install_dir,
                     'com.android.' + apex_base_name + '-' + arch + '.apex'),
        install_unzipped=False))
  return res

def InstallUnbundledSdkEntries(apex_base_name, sdk_type):
  return [update.InstallEntry(
      SDK_TARGET,
      os.path.join('mainline-sdks/for-latest-build/current',
                   'com.android.' + apex_base_name,
                   sdk_type,
                   apex_base_name + '-module-' + sdk_type + '-current.zip'),
      os.path.join(apex_base_name, sdk_type),
      install_unzipped=True)]

def InstallBundledSdkEntries(apex_base_name, sdk_type):
  return [update.InstallEntry(
      SDK_TARGET,
      os.path.join('bundled-mainline-sdks',
                   'com.android.' + apex_base_name,
                   sdk_type,
                   apex_base_name + '-module-' + sdk_type + '-current.zip'),
      os.path.join(apex_base_name, sdk_type),
      install_unzipped=True)]

def InstallPlatformMainlineSdkEntries(sdk_type):
  return [update.InstallEntry(
      SDK_TARGET,
      os.path.join('bundled-mainline-sdks',
                   'platform-mainline',
                   sdk_type,
                   'platform-mainline-' + sdk_type + '-current.zip'),
      os.path.join('platform', sdk_type),
      install_unzipped=True)]

def InstallSharedLibEntries(lib_name, install_dir):
  res = []
  for arch in ['arm', 'arm64', 'x86', 'x86_64']:
    res.append(update.InstallEntry(
        MODULE_TARGET,
        os.path.join(arch, lib_name + '.so'),
        os.path.join(install_dir, arch, lib_name  + '.so'),
        install_unzipped=False))
  return res

PREBUILT_INSTALL_MODULES = (
    # Conscrypt
    #InstallApexEntries('conscrypt', 'conscrypt/apex') +
    #InstallUnbundledSdkEntries('conscrypt', 'test-exports') +
    #InstallUnbundledSdkEntries('conscrypt', 'host-exports') +

    # Runtime (Bionic)
    #InstallApexEntries('runtime', 'runtime/apex') +
    # sdk and host-exports must always be updated together, because the linker
    # and the CRT object files gets embedded in the binaries on linux host
    # Bionic (see code and comments around host_bionic_linker_script in
    # build/soong).
    InstallBundledSdkEntries('runtime', 'sdk') +
    InstallBundledSdkEntries('runtime', 'host-exports') +

    # I18N
    #InstallApexEntries('i18n', 'i18n/apex') +
    #InstallBundledSdkEntries('i18n', 'sdk') +
    #InstallBundledSdkEntries('i18n', 'test-exports') +

    # tzdata
    #InstallApexEntries('tzdata', 'tzdata/apex') +
    #InstallBundledSdkEntries('tzdata', 'test-exports') +

    # statsd
    #InstallApexEntries('os.statsd', 'statsd/apex') +

    # Platform
    #InstallPlatformMainlineSdkEntries('sdk') +
    #InstallPlatformMainlineSdkEntries('test-exports') +
    # Shared libraries that are stubs in SDKs, but for which we need their
    # implementation for device testing.
    #InstallSharedLibEntries('heapprofd_client_api', 'platform/impl') +
    #InstallSharedLibEntries('libartpalette-system', 'platform/impl') +
    #InstallSharedLibEntries('liblog', 'platform/impl') +

    [])

if __name__ == '__main__':
  args = update.parse_args()
  update.main(args, THIS_DIR, PREBUILT_DESCRIPTION, PREBUILT_INSTALL_MODULES,
              [], COMMIT_MESSAGE_NOTE)
