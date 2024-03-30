#!/usr/bin/env python3
#
# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the 'License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an 'AS IS' BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Unit tests for mainline_modules_sdks.py."""
import dataclasses
import pathlib
import re
import typing
from pathlib import Path
import os
import shutil
import tempfile
import unittest
import zipfile
from unittest import mock

import mainline_modules_sdks as mm

MAINLINE_MODULES_BY_APEX = dict(
    (m.apex, m) for m in (mm.MAINLINE_MODULES + mm.BUNDLED_MAINLINE_MODULES +
                          mm.PLATFORM_SDKS_FOR_MAINLINE))


@dataclasses.dataclass()
class FakeSnapshotBuilder(mm.SnapshotBuilder):
    """A fake snapshot builder that does not run the build.

    This skips the whole build process and just creates some fake sdk
    modules.
    """

    snapshots: typing.List[typing.Any] = dataclasses.field(default_factory=list)

    @staticmethod
    def create_sdk_library_files(z, name):
        z.writestr(f"sdk_library/public/{name}-removed.txt", "")
        z.writestr(f"sdk_library/public/{name}.srcjar", "")
        z.writestr(f"sdk_library/public/{name}-stubs.jar", "")
        z.writestr(f"sdk_library/public/{name}.txt", "")

    def create_snapshot_file(self, out_dir, name, version, for_r_build):
        zip_file = Path(mm.sdk_snapshot_zip_file(out_dir, name, version))
        with zipfile.ZipFile(zip_file, "w") as z:
            z.writestr("Android.bp", "")
            if name.endswith("-sdk"):
                if for_r_build:
                    for library in for_r_build.sdk_libraries:
                        self.create_sdk_library_files(z, library.name)
                else:
                    self.create_sdk_library_files(z, re.sub(r"-.*$", "", name))

    def build_snapshots(self, build_release, sdk_versions, modules):
        self.snapshots.append((build_release.name, build_release.soong_env,
                               sdk_versions, [m.apex for m in modules]))
        # Create input file structure.
        sdks_out_dir = Path(self.mainline_sdks_dir).joinpath("test")
        sdks_out_dir.mkdir(parents=True, exist_ok=True)
        # Create a fake sdk zip file for each module.
        for module in modules:
            for sdk in module.sdks:
                for sdk_version in sdk_versions:
                    self.create_snapshot_file(sdks_out_dir, sdk, sdk_version,
                                              module.for_r_build)
        return sdks_out_dir


class TestProduceDist(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.mkdtemp()
        self.tmp_out_dir = os.path.join(self.tmp_dir, "out")
        os.mkdir(self.tmp_out_dir)
        self.tmp_dist_dir = os.path.join(self.tmp_dir, "dist")
        os.mkdir(self.tmp_dist_dir)

    def tearDown(self):
        shutil.rmtree(self.tmp_dir, ignore_errors=True)

    def produce_dist(self, modules, build_releases):
        subprocess_runner = mm.SubprocessRunner()
        snapshot_builder = FakeSnapshotBuilder(
            tool_path="path/to/mainline_modules_sdks.sh",
            subprocess_runner=subprocess_runner,
            out_dir=self.tmp_out_dir,
        )
        producer = mm.SdkDistProducer(
            subprocess_runner=subprocess_runner,
            snapshot_builder=snapshot_builder,
            dist_dir=self.tmp_dist_dir,
        )
        producer.produce_dist(modules, build_releases)

    def list_files_in_dir(self, tmp_dist_dir):
        files = []
        for abs_dir, _, filenames in os.walk(tmp_dist_dir):
            rel_dir = os.path.relpath(abs_dir, tmp_dist_dir)
            if rel_dir == ".":
                rel_dir = ""
            for f in filenames:
                files.append(os.path.join(rel_dir, f))
        return files

    def test_unbundled_modules(self):
        # Create the out/soong/build_number.txt file that is copied into the
        # snapshots.
        self.create_build_number_file()

        modules = [
            MAINLINE_MODULES_BY_APEX["com.android.art"],
            MAINLINE_MODULES_BY_APEX["com.android.ipsec"],
            # Create a google specific module.
            mm.aosp_to_google(MAINLINE_MODULES_BY_APEX["com.android.wifi"]),
        ]
        build_releases = [
            mm.Q,
            mm.R,
            mm.S,
            mm.LATEST,
            mm.LEGACY_BUILD_RELEASE,
        ]
        self.produce_dist(modules, build_releases)

        # pylint: disable=line-too-long
        self.assertEqual(
            [
                # Legacy copy of the snapshots, for use by tools that don't support build specific snapshots.
                "mainline-sdks/current/com.android.art/host-exports/art-module-host-exports-current.zip",
                "mainline-sdks/current/com.android.art/sdk/art-module-sdk-current.zip",
                "mainline-sdks/current/com.android.art/test-exports/art-module-test-exports-current.zip",
                "mainline-sdks/current/com.android.ipsec/sdk/ipsec-module-sdk-current.zip",
                "mainline-sdks/current/com.google.android.wifi/sdk/wifi-module-sdk-current.zip",
                # Build specific snapshots.
                "mainline-sdks/for-R-build/current/com.android.ipsec/sdk/ipsec-module-sdk-current.zip",
                "mainline-sdks/for-R-build/current/com.google.android.wifi/sdk/wifi-module-sdk-current.zip",
                "mainline-sdks/for-S-build/current/com.android.art/host-exports/art-module-host-exports-current.zip",
                "mainline-sdks/for-S-build/current/com.android.art/sdk/art-module-sdk-current.zip",
                "mainline-sdks/for-S-build/current/com.android.art/test-exports/art-module-test-exports-current.zip",
                "mainline-sdks/for-S-build/current/com.android.ipsec/sdk/ipsec-module-sdk-current.zip",
                "mainline-sdks/for-S-build/current/com.google.android.wifi/sdk/wifi-module-sdk-current.zip",
                "mainline-sdks/for-latest-build/current/com.android.art/host-exports/art-module-host-exports-current.zip",
                "mainline-sdks/for-latest-build/current/com.android.art/sdk/art-module-sdk-current.zip",
                "mainline-sdks/for-latest-build/current/com.android.art/test-exports/art-module-test-exports-current.zip",
                "mainline-sdks/for-latest-build/current/com.android.ipsec/sdk/ipsec-module-sdk-current.zip",
                "mainline-sdks/for-latest-build/current/com.google.android.wifi/sdk/wifi-module-sdk-current.zip",
                # Legacy stubs directory containing unpacked java_sdk_library artifacts.
                "stubs/com.android.art/sdk_library/public/art-removed.txt",
                "stubs/com.android.art/sdk_library/public/art-stubs.jar",
                "stubs/com.android.art/sdk_library/public/art.srcjar",
                "stubs/com.android.art/sdk_library/public/art.txt",
                "stubs/com.android.ipsec/sdk_library/public/android.net.ipsec.ike-removed.txt",
                "stubs/com.android.ipsec/sdk_library/public/android.net.ipsec.ike-stubs.jar",
                "stubs/com.android.ipsec/sdk_library/public/android.net.ipsec.ike.srcjar",
                "stubs/com.android.ipsec/sdk_library/public/android.net.ipsec.ike.txt",
                "stubs/com.google.android.wifi/sdk_library/public/framework-wifi-removed.txt",
                "stubs/com.google.android.wifi/sdk_library/public/framework-wifi-stubs.jar",
                "stubs/com.google.android.wifi/sdk_library/public/framework-wifi.srcjar",
                "stubs/com.google.android.wifi/sdk_library/public/framework-wifi.txt",
            ],
            sorted(self.list_files_in_dir(self.tmp_dist_dir)))

        r_snaphot_dir = os.path.join(self.tmp_out_dir,
                                     "soong/mainline-sdks/test/for-R-build")
        aosp_ipsec_r_bp_file = "com.android.ipsec/sdk_library/Android.bp"
        google_wifi_android_bp = "com.google.android.wifi/sdk_library/Android.bp"
        self.assertEqual([
            aosp_ipsec_r_bp_file,
            "com.android.ipsec/sdk_library/public/android.net.ipsec.ike-removed.txt",
            "com.android.ipsec/sdk_library/public/android.net.ipsec.ike-stubs.jar",
            "com.android.ipsec/sdk_library/public/android.net.ipsec.ike.srcjar",
            "com.android.ipsec/sdk_library/public/android.net.ipsec.ike.txt",
            "com.android.ipsec/snapshot-creation-build-number.txt",
            google_wifi_android_bp,
            "com.google.android.wifi/sdk_library/public/framework-wifi-removed.txt",
            "com.google.android.wifi/sdk_library/public/framework-wifi-stubs.jar",
            "com.google.android.wifi/sdk_library/public/framework-wifi.srcjar",
            "com.google.android.wifi/sdk_library/public/framework-wifi.txt",
            "com.google.android.wifi/snapshot-creation-build-number.txt",
            "ipsec-module-sdk-current.zip",
            "wifi-module-sdk-current.zip",
        ], sorted(self.list_files_in_dir(r_snaphot_dir)))

        def read_r_snapshot_contents(path):
            abs_path = os.path.join(r_snaphot_dir, path)
            with open(abs_path, "r", encoding="utf8") as file:
                return file.read()

        # Check the contents of the AOSP ipsec module
        ipsec_contents = read_r_snapshot_contents(aosp_ipsec_r_bp_file)
        expected = read_test_data("ipsec_for_r_Android.bp")
        self.assertEqual(expected, ipsec_contents)

        # Check the contents of the Google ipsec module
        wifi_contents = read_r_snapshot_contents(google_wifi_android_bp)
        expected = read_test_data("google_wifi_for_r_Android.bp")
        self.assertEqual(expected, wifi_contents)

    def test_old_release(self):
        modules = [
            MAINLINE_MODULES_BY_APEX["com.android.art"],  # An unnbundled module
            MAINLINE_MODULES_BY_APEX["com.android.runtime"],  # A bundled module
            MAINLINE_MODULES_BY_APEX["platform-mainline"],  # Platform SDK
        ]
        build_releases = [mm.S]
        self.produce_dist(modules, build_releases)

        # pylint: disable=line-too-long
        self.assertEqual([
            "mainline-sdks/for-S-build/current/com.android.art/host-exports/art-module-host-exports-current.zip",
            "mainline-sdks/for-S-build/current/com.android.art/sdk/art-module-sdk-current.zip",
            "mainline-sdks/for-S-build/current/com.android.art/test-exports/art-module-test-exports-current.zip",
        ], sorted(self.list_files_in_dir(self.tmp_dist_dir)))

    def test_latest_release(self):
        modules = [
            MAINLINE_MODULES_BY_APEX["com.android.art"],  # An unnbundled module
            MAINLINE_MODULES_BY_APEX["com.android.runtime"],  # A bundled module
            MAINLINE_MODULES_BY_APEX["platform-mainline"],  # Platform SDK
        ]
        build_releases = [mm.LATEST]
        self.produce_dist(modules, build_releases)

        # pylint: disable=line-too-long
        self.assertEqual(
            [
                # Bundled modules and platform SDKs.
                "bundled-mainline-sdks/com.android.runtime/host-exports/runtime-module-host-exports-current.zip",
                "bundled-mainline-sdks/com.android.runtime/sdk/runtime-module-sdk-current.zip",
                "bundled-mainline-sdks/platform-mainline/sdk/platform-mainline-sdk-current.zip",
                "bundled-mainline-sdks/platform-mainline/test-exports/platform-mainline-test-exports-current.zip",
                # Unbundled (normal) modules.
                "mainline-sdks/for-latest-build/current/com.android.art/host-exports/art-module-host-exports-current.zip",
                "mainline-sdks/for-latest-build/current/com.android.art/sdk/art-module-sdk-current.zip",
                "mainline-sdks/for-latest-build/current/com.android.art/test-exports/art-module-test-exports-current.zip",
            ],
            sorted(self.list_files_in_dir(self.tmp_dist_dir)))

    def test_legacy_release(self):
        modules = [
            MAINLINE_MODULES_BY_APEX["com.android.art"],  # An unnbundled module
            MAINLINE_MODULES_BY_APEX["com.android.runtime"],  # A bundled module
            MAINLINE_MODULES_BY_APEX["platform-mainline"],  # Platform SDK
        ]
        build_releases = [mm.LEGACY_BUILD_RELEASE]
        self.produce_dist(modules, build_releases)

        # pylint: disable=line-too-long
        self.assertEqual(
            [
                # Legacy copy of the snapshots.
                "mainline-sdks/current/com.android.art/host-exports/art-module-host-exports-current.zip",
                "mainline-sdks/current/com.android.art/sdk/art-module-sdk-current.zip",
                "mainline-sdks/current/com.android.art/test-exports/art-module-test-exports-current.zip",
                # Legacy stubs directory containing unpacked java_sdk_library artifacts.
                "stubs/com.android.art/sdk_library/public/art-removed.txt",
                "stubs/com.android.art/sdk_library/public/art-stubs.jar",
                "stubs/com.android.art/sdk_library/public/art.srcjar",
                "stubs/com.android.art/sdk_library/public/art.txt",
            ],
            sorted(self.list_files_in_dir(self.tmp_dist_dir)))

    def create_build_number_file(self):
        soong_dir = os.path.join(self.tmp_out_dir, "soong")
        os.makedirs(soong_dir, exist_ok=True)
        build_number_file = os.path.join(soong_dir, "build_number.txt")
        with open(build_number_file, "w", encoding="utf8") as f:
            f.write("build-number")

    def test_snapshot_build_order(self):
        # Create the out/soong/build_number.txt file that is copied into the
        # snapshots.
        self.create_build_number_file()

        subprocess_runner = unittest.mock.Mock(mm.SubprocessRunner)
        snapshot_builder = FakeSnapshotBuilder(
            tool_path="path/to/mainline_modules_sdks.sh",
            subprocess_runner=subprocess_runner,
            out_dir=self.tmp_out_dir,
        )
        producer = mm.SdkDistProducer(
            subprocess_runner=subprocess_runner,
            snapshot_builder=snapshot_builder,
            dist_dir=self.tmp_dist_dir,
        )

        modules = [
            MAINLINE_MODULES_BY_APEX["com.android.art"],
            MAINLINE_MODULES_BY_APEX["com.android.ipsec"],
            # Create a google specific module.
            mm.aosp_to_google(MAINLINE_MODULES_BY_APEX["com.android.wifi"]),
        ]
        build_releases = [
            mm.Q,
            mm.R,
            mm.S,
            mm.LATEST,
            mm.LEGACY_BUILD_RELEASE,
        ]

        producer.produce_dist(modules, build_releases)

        # Check the order in which the snapshots are built.
        self.assertEqual([
            (
                "R",
                {},
                ["current"],
                ["com.android.ipsec", "com.google.android.wifi"],
            ),
            (
                "latest",
                {},
                ["current"],
                [
                    "com.android.art", "com.android.ipsec",
                    "com.google.android.wifi"
                ],
            ),
            (
                "legacy",
                {},
                ["current"],
                [
                    "com.android.art", "com.android.ipsec",
                    "com.google.android.wifi"
                ],
            ),
            (
                "S",
                {
                    "SOONG_SDK_SNAPSHOT_TARGET_BUILD_RELEASE": "S"
                },
                ["current"],
                [
                    "com.android.art", "com.android.ipsec",
                    "com.google.android.wifi"
                ],
            ),
        ], snapshot_builder.snapshots)


def path_to_test_data(relative_path):
    """Construct a path to a test data file.

    The relative_path is relative to the location of this file.
    """
    this_file = __file__
    # When running as a python_test_host (name=<x>) with an embedded launcher
    # the __file__ points to .../<x>/<x>.py but the .../<x> is not a directory
    # it is a binary with the launcher and the python file embedded inside. In
    # that case a test data file <rel> is at .../<x>_data/<rel>, not
    # .../<x>/<x>_data/<rel> so it is necessary to trim the base name (<x>.py)
    # from the file.
    if not os.path.isfile(this_file):
        this_file = os.path.dirname(this_file)
    # When the python file is at .../<x>.py (or in the case of an embedded
    # launcher at .../<x>/<x>.py) then the test data is at .../<x>_data/<rel>.
    this_file_without_ext, _ = os.path.splitext(this_file)
    return os.path.join(this_file_without_ext + "_data", relative_path)


def read_test_data(relative_path):
    with open(path_to_test_data(relative_path), "r", encoding="utf8") as f:
        return f.read()


class TestSoongConfigBoilerplateInserter(unittest.TestCase):

    def apply_transformations(self, src, transformations, expected):
        producer = mm.SdkDistProducer(
            subprocess_runner=mock.Mock(mm.SubprocessRunner),
            snapshot_builder=mock.Mock(mm.SnapshotBuilder),
            script=self._testMethodName,
        )

        with tempfile.TemporaryDirectory() as tmp_dir:
            path = os.path.join(tmp_dir, "Android.bp")
            with open(path, "w", encoding="utf8") as f:
                f.write(src)

            mm.apply_transformations(producer, tmp_dir, transformations)

            with open(path, "r", encoding="utf8") as f:
                result = f.read()

        self.maxDiff = None
        self.assertEqual(expected, result)

    def test_common_mainline_module(self):
        """Tests the transformations applied to a common mainline module.

        This uses ipsec as an example of a common mainline module. This checks
        that the correct Soong config module types and variables are used and
        that it imports the definitions from the correct location.
        """
        src = read_test_data("ipsec_Android.bp.input")

        expected = read_test_data("ipsec_Android.bp.expected")

        module = MAINLINE_MODULES_BY_APEX["com.android.ipsec"]
        transformations = module.transformations(mm.S)

        self.apply_transformations(src, transformations, expected)

        # Check that Tiramisu provides the same transformations as S.
        tiramisu_transformations = module.transformations(mm.Tiramisu)
        self.assertEqual(
            transformations,
            tiramisu_transformations,
            msg="Tiramisu must use the same transformations as S")

    def test_optional_mainline_module(self):
        """Tests the transformations applied to an optional mainline module.

        This uses wifi as an example of a optional mainline module. This checks
        that the module specific Soong config module types and variables are
        used.
        """
        src = read_test_data("wifi_Android.bp.input")

        expected = read_test_data("wifi_Android.bp.expected")

        module = MAINLINE_MODULES_BY_APEX["com.android.wifi"]
        transformations = module.transformations(mm.S)

        self.apply_transformations(src, transformations, expected)

        # Check that Tiramisu provides the same transformations as S.
        tiramisu_transformations = module.transformations(mm.Tiramisu)
        self.assertEqual(
            transformations,
            tiramisu_transformations,
            msg="Tiramisu must use the same transformations as S")

    def test_art(self):
        """Tests the transformations applied to a the ART mainline module.

        The ART mainline module uses a different Soong config setup to the
        common mainline modules. This checks that the ART specific Soong config
        module types, variable and imports are used.
        """
        src = read_test_data("art_Android.bp.input")

        expected = read_test_data("art_Android.bp.expected")

        module = MAINLINE_MODULES_BY_APEX["com.android.art"]
        transformations = module.transformations(mm.S)

        self.apply_transformations(src, transformations, expected)

    def test_r_build(self):
        """Tests the transformations that are applied for the R build.

        This uses ipsec as an example of a common mainline module. That would
        usually apply the mm.SoongConfigBoilerplateInserter transformation but
        because this is being run for build R that transformation should not be
        applied.
        """
        src = read_test_data("ipsec_for_r_Android.bp")

        # There should be no changes made.
        expected = src

        module = MAINLINE_MODULES_BY_APEX["com.android.ipsec"]
        transformations = module.transformations(mm.R)

        self.apply_transformations(src, transformations, expected)


class TestFilterModules(unittest.TestCase):

    def test_no_filter(self):
        all_modules = mm.MAINLINE_MODULES + mm.BUNDLED_MAINLINE_MODULES
        modules = mm.filter_modules(all_modules, None)
        self.assertEqual(modules, all_modules)

    def test_with_filter(self):
        modules = mm.filter_modules(mm.MAINLINE_MODULES, "com.android.art")
        expected = MAINLINE_MODULES_BY_APEX["com.android.art"]
        self.assertEqual(modules, [expected])


class TestModuleProperties(unittest.TestCase):

    def test_unbundled(self):
        for module in mm.MAINLINE_MODULES:
            with self.subTest(module=module):
                self.assertFalse(module.is_bundled())

    def test_bundled(self):
        for module in (mm.BUNDLED_MAINLINE_MODULES +
                       mm.PLATFORM_SDKS_FOR_MAINLINE):
            with self.subTest(module=module):
                self.assertTrue(module.is_bundled())
                self.assertEqual(module.first_release, mm.LATEST)


if __name__ == "__main__":
    unittest.main(verbosity=2)
