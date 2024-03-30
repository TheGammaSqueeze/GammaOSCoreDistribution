#!/usr/bin/env python3
#
# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""A script to copy outputs from Bazel rules to a user specified dist directory.

This script is only meant to be executed with `bazel run`. `bazel build <this
script>` doesn't actually copy the files, you'd have to `bazel run` a
copy_to_dist_dir target.

This script copies files from Bazel's output tree into a directory specified by
the user. It does not check if the dist dir already contains the file, and will
simply overwrite it.

One approach is to wipe the dist dir every time this script runs, but that may
be overly destructive and best left to an explicit rm -rf call outside of this
script.

Another approach is to error out if the file being copied already exist in the
dist dir, or perform some kind of content hash checking.
"""

import argparse
import glob
import os
import shutil
import sys
import tarfile


def files_to_dist(pattern):
    # Assume that dist.bzl is in the same package as dist.py
    runfiles_directory = os.path.dirname(__file__)
    dist_manifests = glob.glob(
        os.path.join(runfiles_directory, pattern))
    if not dist_manifests:
        print("Warning: could not find a file with pattern " + pattern +
              " in the runfiles directory: %s" % runfiles_directory)
    files_to_dist = []
    for dist_manifest in dist_manifests:
        with open(dist_manifest, "r") as f:
            files_to_dist += [line.strip() for line in f]
    return files_to_dist


def copy_files_to_dist_dir(files, archives, dist_dir, flat, prefix,
    archive_prefix):

    for src in files:
        src_relpath = os.path.basename(src) if flat else src
        src_relpath = os.path.join(prefix, src_relpath)
        src_abspath = os.path.abspath(src)

        dst = os.path.join(dist_dir, src_relpath)
        if os.path.isfile(src):
            dst_dirname = os.path.dirname(dst)
            print("[dist] Copying file: %s" % dst)
            if not os.path.exists(dst_dirname):
                os.makedirs(dst_dirname)

            shutil.copyfile(src_abspath, dst, follow_symlinks=True)
        elif os.path.isdir(src):
            print("[dist] Copying dir: %s" % dst)
            shutil.copytree(src_abspath, dst)

    for archive in archives:
        try:
            with tarfile.open(archive) as tf:
                dst_dirname = os.path.join(dist_dir, archive_prefix)
                print("[dist] Extracting archive: {} -> {}".format(archive,
                                                                   dst_dirname))
                tf.extractall(dst_dirname)
        except tarfile.TarError:
            # toybox does not support creating empty tar files, hence the build
            # system may use empty files as empty archives.
            if os.path.getsize(archive) == 0:
                print("Warning: skipping empty tar file: {}".format(archive))
                continue
             # re-raise if we do not know anything about this error
            raise


def main():
    parser = argparse.ArgumentParser(
        description="Dist Bazel output files into a custom directory.")
    parser.add_argument(
        "--dist_dir", required=True, help="""path to the dist dir.
            If relative, it is interpreted as relative to Bazel workspace root
            set by the BUILD_WORKSPACE_DIRECTORY environment variable, or
            PWD if BUILD_WORKSPACE_DIRECTORY is not set.""")
    parser.add_argument(
        "--flat",
        action="store_true",
        help="ignore subdirectories in the manifest")
    parser.add_argument(
        "--prefix", default="",
        help="path prefix to apply within dist_dir for copied files")
    parser.add_argument(
        "--archive_prefix", default="",
        help="Path prefix to apply within dist_dir for extracted archives. " +
             "Supported archives: tar.")

    args = parser.parse_args(sys.argv[1:])

    if not os.path.isabs(args.dist_dir):
        # BUILD_WORKSPACE_DIRECTORY is the root of the Bazel workspace containing
        # this binary target.
        # https://docs.bazel.build/versions/main/user-manual.html#run
        args.dist_dir = os.path.join(
            os.environ.get("BUILD_WORKSPACE_DIRECTORY"), args.dist_dir)

    files = files_to_dist("*_dist_manifest.txt")
    archives = files_to_dist("*_dist_archives_manifest.txt")
    copy_files_to_dist_dir(files, archives, **vars(args))


if __name__ == "__main__":
    main()
