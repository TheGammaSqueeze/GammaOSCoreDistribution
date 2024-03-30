#!/usr/bin/env python
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

import argparse
import os
import shutil
import subprocess
import sys
import tempfile

def _create_apex(args, work_dir):

    image_apex_dir = "image.apex"

    # Used for creating canned_fs_config, since every file and dir in the APEX are represented
    # by an entry in the fs_config.
    apex_subdirs = []
    apex_filepaths = []

    input_dir = os.path.join(work_dir, image_apex_dir)
    os.makedirs(input_dir, exist_ok=True)
    bazel_apexer_wrapper_manifest = open(args.bazel_apexer_wrapper_manifest, 'r')
    file_lines = bazel_apexer_wrapper_manifest.readlines()
    for line in file_lines:
        line = line.strip()
        if (len(line) == 0):
            continue
        apex_dirname, apex_filename, bazel_input_file = line.split(",")
        full_apex_dirname = "/".join([input_dir, apex_dirname])
        os.makedirs(full_apex_dirname, exist_ok=True)

        apex_filepath = "/".join([apex_dirname, apex_filename])
        apex_filepaths.append(apex_filepath)
        apex_subdirs.append(apex_dirname)

        full_apex_filepath = "/".join([input_dir, apex_filepath])
        # Because Bazel execution root is a symlink forest, all the input files are symlinks, these
        # include the dependency files declared in the BUILD files as well as the files declared
        # and created in the bzl files. For sandbox runs the former are two or more level symlinks and
        # latter are one level symlinks. For non-sandbox runs, the former are one level symlinks
        # and the latter are actual files. Here are some examples:
        #
        # Two level symlinks:
        # system/timezone/output_data/version/tz_version ->
        # /usr/local/google/home/...out/bazel/output_user_root/b1ed7e1e9af3ebbd1403e9cf794e4884/
        # execroot/__main__/system/timezone/output_data/version/tz_version ->
        # /usr/local/google/home/.../system/timezone/output_data/version/tz_version
        #
        # Three level symlinks:
        # bazel-out/android_x86_64-fastbuild-ST-4ecd5e98bfdd/bin/external/boringssl/libcrypto.so ->
        # /usr/local/google/home/yudiliu/android/aosp/master/out/bazel/output_user_root/b1ed7e1e9af3ebbd1403e9cf794e4884/
        # execroot/__main__/bazel-out/android_x86_64-fastbuild-ST-4ecd5e98bfdd/bin/external/boringssl/libcrypto.so ->
        # /usr/local/google/home/yudiliu/android/aosp/master/out/bazel/output_user_root/b1ed7e1e9af3ebbd1403e9cf794e4884/
        # execroot/__main__/bazel-out/android_x86_64-fastbuild-ST-4ecd5e98bfdd/bin/external/boringssl/
        # liblibcrypto_stripped.so ->
        # /usr/local/google/home/yudiliu/android/aosp/master/out/bazel/output_user_root/b1ed7e1e9af3ebbd1403e9cf794e4884/
        # execroot/__main__/bazel-out/android_x86_64-fastbuild-ST-4ecd5e98bfdd/bin/external/boringssl/
        # liblibcrypto_unstripped.so
        #
        # One level symlinks:
        # bazel-out/android_target-fastbuild/bin/system/timezone/apex/apex_manifest.pb ->
        # /usr/local/google/home/.../out/bazel/output_user_root/b1ed7e1e9af3ebbd1403e9cf794e4884/
        # execroot/__main__/bazel-out/android_target-fastbuild/bin/system/timezone/apex/
        # apex_manifest.pb

        if os.path.islink(bazel_input_file):
            bazel_input_file = os.readlink(bazel_input_file)

            # For sandbox run these are the 2nd level symlinks and we need to resolve
            while os.path.islink(bazel_input_file) and 'execroot/__main__' in bazel_input_file:
                bazel_input_file = os.readlink(bazel_input_file)

        shutil.copyfile(bazel_input_file, full_apex_filepath, follow_symlinks=False)

    # Make sure subdirs are unique
    apex_subdirs_set = set()
    for d in apex_subdirs:
        apex_subdirs_set.add(d)

        # Make sure all the parent dirs of the current subdir are in the set, too
        dirs = d.split("/")
        for i in range(0, len(dirs)):
            apex_subdirs_set.add("/".join(dirs[:i]))

    canned_fs_config = _generate_canned_fs_config(work_dir, apex_subdirs_set, apex_filepaths)

    # Construct the main apexer command.
    cmd = [args.apexer_path]
    cmd.append('--verbose')
    cmd.append('--force')
    cmd.append('--include_build_info')
    cmd.extend(['--file_contexts', args.file_contexts])
    cmd.extend(['--canned_fs_config', canned_fs_config])
    cmd.extend(['--key', args.key])
    cmd.extend(['--payload_type', 'image'])
    cmd.extend(['--target_sdk_version', '10000'])
    cmd.extend(['--payload_fs_type', 'ext4'])
    cmd.extend(['--apexer_tool_path', args.apexer_tool_paths])

    if args.android_manifest != None:
        cmd.extend(['--android_manifest', args.android_manifest])

    if args.pubkey != None:
        cmd.extend(['--pubkey', args.pubkey])

    if args.manifest != None:
        cmd.extend(['--manifest', args.manifest])

    if args.min_sdk_version != None:
        cmd.extend(['--min_sdk_version', args.min_sdk_version])

    if args.android_jar_path != None:
        cmd.extend(['--android_jar_path', args.android_jar_path])

    cmd.append(input_dir)
    cmd.append(args.apex_output_file)

    popen = subprocess.Popen(cmd)
    popen.wait()

    return True

# Generate filesystem config. This encodes the filemode, uid, and gid of each
# file in the APEX, including apex_manifest.json and apex_manifest.pb.
#
# NOTE: every file must have an entry.
def _generate_canned_fs_config(work_dir, dirs, filepaths):
    with tempfile.NamedTemporaryFile(mode = 'w+', dir=work_dir, delete=False) as canned_fs_config:
        config_lines = []
        config_lines += ["/ 1000 1000 0755"]
        config_lines += ["/apex_manifest.json 1000 1000 0644"]
        config_lines += ["/apex_manifest.pb 1000 1000 0644"]
        config_lines += ["/" + filepath + " 1000 1000 0644" for filepath in filepaths]
        config_lines += ["/" + d + " 0 2000 0755" for d in dirs]
        canned_fs_config.write("\n".join(config_lines))

    return canned_fs_config.name

def _parse_args(argv):
    parser = argparse.ArgumentParser(description='Build an APEX file')

    parser.add_argument(
        '--manifest',
        help='path to the APEX manifest file (.pb)')
    parser.add_argument(
        '--apex_output_file',
        required=True,
        help='path to the APEX image file')
    parser.add_argument(
        '--bazel_apexer_wrapper_manifest',
        required=True,
        help='path to the manifest file that stores the info about the files to be packaged by apexer')
    parser.add_argument(
        '--android_manifest',
        help='path to the AndroidManifest file. If omitted, a default one is created and used')
    parser.add_argument(
        '--file_contexts',
        required=True,
        help='selinux file contexts file.')
    parser.add_argument(
        '--key',
        required=True,
        help='path to the private key file.')
    parser.add_argument(
        '--pubkey',
        help='path to the public key file. Used to bundle the public key in APEX for testing.')
    parser.add_argument(
        '--apexer_path',
        required=True,
        help='Path to the apexer binary.')
    parser.add_argument(
        '--apexer_tool_paths',
        required=True,
        help='Directories containing all the tools used by apexer, separated by ":" character.')
    parser.add_argument(
        '--min_sdk_version',
        help='Default Min SDK version to use for AndroidManifest.xml')
    parser.add_argument(
        '--android_jar_path',
        help='path to use as the source of the android API.')

    return parser.parse_args(argv)

def main(argv):
    args = _parse_args(argv)

    with tempfile.TemporaryDirectory() as work_dir:
        success = _create_apex(args, work_dir)

    if not success:
        sys.exit(1)

if __name__ == '__main__':
    main(sys.argv[1:])
