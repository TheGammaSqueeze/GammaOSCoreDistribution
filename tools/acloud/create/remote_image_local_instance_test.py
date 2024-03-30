# Copyright 2018 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Tests for remote_image_local_instance."""

import unittest
from collections import namedtuple
import os
import shutil
import subprocess

from unittest import mock

from acloud import errors
from acloud.create import create_common
from acloud.create import remote_image_local_instance
from acloud.create import local_image_local_instance
from acloud.internal.lib import android_build_client
from acloud.internal.lib import auth
from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import ota_tools
from acloud.internal.lib import utils
from acloud.setup import setup_common


# pylint: disable=invalid-name, protected-access, no-member
class RemoteImageLocalInstanceTest(driver_test_lib.BaseDriverTest):
    """Test remote_image_local_instance methods."""

    def setUp(self):
        """Initialize remote_image_local_instance."""
        super().setUp()
        self.build_client = mock.MagicMock()
        self.Patch(
            android_build_client,
            "AndroidBuildClient",
            return_value=self.build_client)
        self.Patch(auth, "CreateCredentials", return_value=mock.MagicMock())
        self.RemoteImageLocalInstance = remote_image_local_instance.RemoteImageLocalInstance()
        self._fake_remote_image = {"build_target" : "aosp_cf_x86_64_phone-userdebug",
                                   "build_id": "1234",
                                   "branch": "aosp_master"}
        self._extract_path = "/tmp/acloud_image_artifacts/1234"

    @mock.patch.object(remote_image_local_instance, "DownloadAndProcessImageFiles")
    def testGetImageArtifactsPath(self, mock_proc):
        """Test get image artifacts path."""
        mock_proc.return_value = "/unit/test"
        avd_spec = mock.MagicMock()
        avd_spec.local_system_image = None
        # raise errors.NoCuttlefishCommonInstalled
        self.Patch(setup_common, "PackageInstalled", return_value=False)
        self.assertRaises(errors.NoCuttlefishCommonInstalled,
                          self.RemoteImageLocalInstance.GetImageArtifactsPath,
                          avd_spec)

        # Valid _DownloadAndProcessImageFiles run.
        self.Patch(setup_common, "PackageInstalled", return_value=True)
        self.Patch(remote_image_local_instance,
                   "ConfirmDownloadRemoteImageDir", return_value="/tmp")
        self.Patch(os.path, "exists", return_value=True)
        paths = self.RemoteImageLocalInstance.GetImageArtifactsPath(avd_spec)
        mock_proc.assert_called_once_with(avd_spec)
        self.assertEqual(paths.image_dir, "/unit/test")
        self.assertEqual(paths.host_bins, "/unit/test")

        # GSI
        avd_spec.local_system_image = "/test_local_system_image_dir"
        avd_spec.local_tool_dirs = "/test_local_tool_dirs"
        avd_spec.cfg = None
        avd_spec.remote_image = self._fake_remote_image
        self.Patch(os, "makedirs")
        self.Patch(create_common, "DownloadRemoteArtifact")
        self.Patch(os.path, "exists", side_effect=[True, False])
        self.Patch(create_common, "GetNonEmptyEnvVars")
        self.Patch(local_image_local_instance.LocalImageLocalInstance,
                   "FindMiscInfo", return_value="/mix_image_1234/MISC")
        self.Patch(local_image_local_instance.LocalImageLocalInstance,
                   "FindImageDir", return_value="/mix_image_1234/IMAGES")
        self.Patch(ota_tools, "FindOtaToolsDir", return_value="/ota_tools_dir")
        self.Patch(create_common, "FindLocalImage", return_value="/system_image_path")
        paths = self.RemoteImageLocalInstance.GetImageArtifactsPath(avd_spec)
        create_common.DownloadRemoteArtifact.assert_called_with(
            avd_spec.cfg, "aosp_cf_x86_64_phone-userdebug", "1234",
            "aosp_cf_x86_64_phone-target_files-1234.zip", "/unit/test/mix_image_1234",
            decompress=True)
        self.assertEqual(paths.image_dir, "/mix_image_1234/IMAGES")
        self.assertEqual(paths.misc_info, "/mix_image_1234/MISC")
        self.assertEqual(paths.host_bins, "/unit/test")
        self.assertEqual(paths.ota_tools_dir, "/ota_tools_dir")
        self.assertEqual(paths.system_image, "/system_image_path")
        create_common.DownloadRemoteArtifact.reset_mock()

        self.Patch(os.path, "exists", side_effect=[True, True])
        self.RemoteImageLocalInstance.GetImageArtifactsPath(avd_spec)
        create_common.DownloadRemoteArtifact.assert_not_called()


    @mock.patch.object(shutil, "rmtree")
    def testDownloadAndProcessImageFiles(self, mock_rmtree):
        """Test process remote cuttlefish image."""
        avd_spec = mock.MagicMock()
        avd_spec.cfg = mock.MagicMock()
        avd_spec.cfg.creds_cache_file = "cache.file"
        avd_spec.remote_image = self._fake_remote_image
        avd_spec.image_download_dir = "/tmp"
        avd_spec.force_sync = True
        self.Patch(os.path, "exists", side_effect=[True, False])
        self.Patch(os, "makedirs")
        self.Patch(subprocess, "check_call")
        remote_image_local_instance.DownloadAndProcessImageFiles(avd_spec)
        self.assertEqual(mock_rmtree.call_count, 1)
        self.assertEqual(self.build_client.GetFetchBuildArgs.call_count, 1)
        self.assertEqual(self.build_client.GetFetchCertArg.call_count, 1)

    def testConfirmDownloadRemoteImageDir(self):
        """Test confirm download remote image dir"""
        self.Patch(os.path, "exists", return_value=True)
        self.Patch(os, "makedirs")
        # Default minimum avail space should be more than 10G
        # then return download_dir directly.
        self.Patch(os, "statvfs", return_value=namedtuple(
            "statvfs", "f_bavail, f_bsize")(11, 1073741824))
        download_dir = "/tmp"
        self.assertEqual(
            remote_image_local_instance.ConfirmDownloadRemoteImageDir(
                download_dir), "/tmp")

        # Test when insuficient disk space and input 'q' to exit.
        self.Patch(os, "statvfs", return_value=namedtuple(
            "statvfs", "f_bavail, f_bsize")(9, 1073741824))
        self.Patch(utils, "InteractWithQuestion", return_value="q")
        self.assertRaises(SystemExit,
                          remote_image_local_instance.ConfirmDownloadRemoteImageDir,
                          download_dir)

        # If avail space detect as 9GB, and 2nd input 7GB both less than 10GB
        # 3rd input over 10GB, so return path should be "/tmp3".
        self.Patch(os, "statvfs", side_effect=[
            namedtuple("statvfs", "f_bavail, f_bsize")(9, 1073741824),
            namedtuple("statvfs", "f_bavail, f_bsize")(7, 1073741824),
            namedtuple("statvfs", "f_bavail, f_bsize")(11, 1073741824)])
        self.Patch(utils, "InteractWithQuestion", side_effect=["/tmp2",
                                                               "/tmp3"])
        self.assertEqual(
            remote_image_local_instance.ConfirmDownloadRemoteImageDir(
                download_dir), "/tmp3")

        # Test when path not exist, define --image-download-dir
        # enter anything else to exit out.
        download_dir = "/image_download_dir1"
        self.Patch(os.path, "exists", return_value=False)
        self.Patch(utils, "InteractWithQuestion", return_value="")
        self.assertRaises(SystemExit,
                          remote_image_local_instance.ConfirmDownloadRemoteImageDir,
                          download_dir)

        # Test using --image-dowload-dir and makedirs.
        # enter 'y' to create it.
        self.Patch(utils, "InteractWithQuestion", return_value="y")
        self.Patch(os, "statvfs", return_value=namedtuple(
            "statvfs", "f_bavail, f_bsize")(10, 1073741824))
        self.assertEqual(
            remote_image_local_instance.ConfirmDownloadRemoteImageDir(
                download_dir), "/image_download_dir1")

        # Test when 1st check fails for insufficient disk space, user inputs an
        # alternate dir but it doesn't exist and the user choose to exit.
        self.Patch(os, "statvfs", side_effect=[
            namedtuple("statvfs", "f_bavail, f_bsize")(9, 1073741824),
            namedtuple("statvfs", "f_bavail, f_bsize")(11, 1073741824)])
        self.Patch(os.path, "exists", side_effect=[True, False])
        self.Patch(utils, "InteractWithQuestion",
                   side_effect=["~/nopath", "not_y"])
        self.assertRaises(
            SystemExit,
            remote_image_local_instance.ConfirmDownloadRemoteImageDir,
            download_dir)

        # Test when 1st check fails for insufficient disk space, user inputs an
        # alternate dir but it doesn't exist and they request to create it.
        self.Patch(os, "statvfs", side_effect=[
            namedtuple("statvfs", "f_bavail, f_bsize")(9, 1073741824),
            namedtuple("statvfs", "f_bavail, f_bsize")(10, 1073741824)])
        self.Patch(os.path, "exists", side_effect=[True, False])
        self.Patch(utils, "InteractWithQuestion", side_effect=["~/nopath", "y"])
        self.assertEqual(
            remote_image_local_instance.ConfirmDownloadRemoteImageDir(
                download_dir), os.path.expanduser("~/nopath"))


if __name__ == "__main__":
    unittest.main()
