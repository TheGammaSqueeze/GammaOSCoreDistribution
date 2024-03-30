# Copyright 2019 - The Android Open Source Project
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
"""Tests for remote_instance_cf_device_factory."""

import glob
import os
import unittest
import uuid

from unittest import mock

from acloud.create import avd_spec
from acloud.internal import constants
from acloud.internal.lib import android_build_client
from acloud.internal.lib import auth
from acloud.internal.lib import cvd_compute_client_multi_stage
from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import utils
from acloud.list import list as list_instances
from acloud.public.actions import remote_instance_cf_device_factory


class RemoteInstanceDeviceFactoryTest(driver_test_lib.BaseDriverTest):
    """Test RemoteInstanceDeviceFactory method."""

    def setUp(self):
        """Set up the test."""
        super().setUp()
        self.Patch(auth, "CreateCredentials", return_value=mock.MagicMock())
        self.Patch(android_build_client.AndroidBuildClient, "InitResourceHandle")
        self.Patch(cvd_compute_client_multi_stage.CvdComputeClient, "InitResourceHandle")
        self.Patch(cvd_compute_client_multi_stage.CvdComputeClient, "LaunchCvd")
        self.Patch(list_instances, "GetInstancesFromInstanceNames", return_value=mock.MagicMock())
        self.Patch(list_instances, "ChooseOneRemoteInstance", return_value=mock.MagicMock())
        self.Patch(utils, "GetBuildEnvironmentVariable",
                   return_value="test_env_cf_arm")
        self.Patch(glob, "glob", return_vale=["fake.img"])

    # pylint: disable=protected-access
    @mock.patch.object(cvd_compute_client_multi_stage.CvdComputeClient,
                       "UpdateCertificate")
    @mock.patch.object(remote_instance_cf_device_factory.RemoteInstanceDeviceFactory,
                       "_FetchBuild")
    @mock.patch("acloud.public.actions.remote_instance_cf_device_factory."
                "cvd_utils")
    def testProcessArtifacts(self, mock_cvd_utils, mock_download,
                             mock_uploadca):
        """test ProcessArtifacts."""
        # Test image source type is local.
        args = mock.MagicMock()
        args.config_file = ""
        args.avd_type = constants.TYPE_CF
        args.flavor = "phone"
        args.local_image = constants.FIND_IN_BUILD_ENV
        args.local_system_image = None
        args.launch_args = None
        args.autoconnect = constants.INS_KEY_WEBRTC
        avd_spec_local_img = avd_spec.AVDSpec(args)
        fake_image_name = "/fake/aosp_cf_x86_phone-img-eng.username.zip"
        fake_host_package_name = "/fake/host_package.tar.gz"
        factory_local_img = remote_instance_cf_device_factory.RemoteInstanceDeviceFactory(
            avd_spec_local_img,
            fake_image_name,
            fake_host_package_name)
        factory_local_img._ProcessArtifacts()
        # cf default autoconnect webrtc and should upload certificates
        mock_uploadca.assert_called_once()
        mock_uploadca.reset_mock()
        mock_cvd_utils.UploadArtifacts.assert_called_once_with(
            mock.ANY, fake_image_name, fake_host_package_name)
        mock_cvd_utils.UploadExtraImages.assert_called_once()

        # given autoconnect to vnc should not upload certificates
        args.autoconnect = constants.INS_KEY_VNC
        avd_spec_local_img = avd_spec.AVDSpec(args)
        factory_local_img = remote_instance_cf_device_factory.RemoteInstanceDeviceFactory(
            avd_spec_local_img,
            fake_image_name,
            fake_host_package_name)
        factory_local_img._ProcessArtifacts()
        mock_uploadca.assert_not_called()

        # Test image source type is remote.
        args.local_image = None
        args.build_id = "1234"
        args.branch = "fake_branch"
        args.build_target = "fake_target"
        args.system_build_id = "2345"
        args.system_branch = "sys_branch"
        args.system_build_target = "sys_target"
        args.kernel_build_id = "3456"
        args.kernel_branch = "kernel_branch"
        args.kernel_build_target = "kernel_target"
        avd_spec_remote_img = avd_spec.AVDSpec(args)
        self.Patch(cvd_compute_client_multi_stage.CvdComputeClient, "UpdateFetchCvd")
        factory_remote_img = remote_instance_cf_device_factory.RemoteInstanceDeviceFactory(
            avd_spec_remote_img)
        factory_remote_img._ProcessArtifacts()
        mock_download.assert_called_once()

    # pylint: disable=protected-access
    @mock.patch.dict(os.environ, {constants.ENV_BUILD_TARGET:'fake-target'})
    def testCreateGceInstanceNameMultiStage(self):
        """test create gce instance."""
        # Mock uuid
        args = mock.MagicMock()
        args.config_file = ""
        args.avd_type = constants.TYPE_CF
        args.flavor = "phone"
        args.local_image = constants.FIND_IN_BUILD_ENV
        args.local_system_image = None
        args.adb_port = None
        args.launch_args = None
        fake_avd_spec = avd_spec.AVDSpec(args)
        fake_avd_spec.cfg.enable_multi_stage = True
        fake_avd_spec._instance_name_to_reuse = None

        fake_uuid = mock.MagicMock(hex="1234")
        self.Patch(uuid, "uuid4", return_value=fake_uuid)
        self.Patch(cvd_compute_client_multi_stage.CvdComputeClient, "CreateInstance")
        self.Patch(cvd_compute_client_multi_stage.CvdComputeClient,
                   "GetHostImageName", return_value="fake_image")
        fake_host_package_name = "/fake/host_package.tar.gz"
        fake_image_name = "/fake/aosp_cf_x86_phone-img-eng.username.zip"

        factory = remote_instance_cf_device_factory.RemoteInstanceDeviceFactory(
            fake_avd_spec,
            fake_image_name,
            fake_host_package_name)
        self.assertEqual(factory._CreateGceInstance(), "ins-1234-userbuild-aosp-cf-x86-phone")

        # Can't get target name from zip file name.
        fake_image_name = "/fake/aosp_cf_x86_phone.username.zip"
        factory = remote_instance_cf_device_factory.RemoteInstanceDeviceFactory(
            fake_avd_spec,
            fake_image_name,
            fake_host_package_name)
        self.assertEqual(factory._CreateGceInstance(), "ins-1234-userbuild-fake-target")

        # No image zip path, it uses local build images.
        fake_image_name = ""
        factory = remote_instance_cf_device_factory.RemoteInstanceDeviceFactory(
            fake_avd_spec,
            fake_image_name,
            fake_host_package_name)
        self.assertEqual(factory._CreateGceInstance(), "ins-1234-userbuild-fake-target")

    def testReuseInstanceNameMultiStage(self):
        """Test reuse instance name."""
        args = mock.MagicMock()
        args.config_file = ""
        args.avd_type = constants.TYPE_CF
        args.flavor = "phone"
        args.local_image = constants.FIND_IN_BUILD_ENV
        args.local_system_image = None
        args.adb_port = None
        args.launch_args = None
        fake_avd_spec = avd_spec.AVDSpec(args)
        fake_avd_spec.cfg.enable_multi_stage = True
        fake_avd_spec._instance_name_to_reuse = "fake-1234-userbuild-fake-target"
        fake_uuid = mock.MagicMock(hex="1234")
        self.Patch(uuid, "uuid4", return_value=fake_uuid)
        self.Patch(cvd_compute_client_multi_stage.CvdComputeClient, "CreateInstance")
        self.Patch(cvd_compute_client_multi_stage.CvdComputeClient,
                   "GetHostImageName", return_value="fake_image")
        fake_host_package_name = "/fake/host_package.tar.gz"
        fake_image_name = "/fake/aosp_cf_x86_phone-img-eng.username.zip"
        factory = remote_instance_cf_device_factory.RemoteInstanceDeviceFactory(
            fake_avd_spec,
            fake_image_name,
            fake_host_package_name)
        self.assertEqual(factory._CreateGceInstance(), "fake-1234-userbuild-fake-target")

    @mock.patch("acloud.public.actions.remote_instance_cf_device_factory."
                "cvd_utils")
    def testGetBuildInfoDict(self, mock_cvd_utils):
        """Test GetBuildInfoDict."""
        fake_host_package_name = "/fake/host_package.tar.gz"
        fake_image_name = "/fake/aosp_cf_x86_phone-img-eng.username.zip"
        args = mock.MagicMock()
        # Test image source type is local.
        args.config_file = ""
        args.avd_type = constants.TYPE_CF
        args.flavor = "phone"
        args.local_image = "fake_local_image"
        args.local_system_image = None
        args.adb_port = None
        args.cheeps_betty_image = None
        args.launch_args = None
        avd_spec_local_image = avd_spec.AVDSpec(args)
        factory = remote_instance_cf_device_factory.RemoteInstanceDeviceFactory(
            avd_spec_local_image,
            fake_image_name,
            fake_host_package_name)
        self.assertEqual(factory.GetBuildInfoDict(), None)
        mock_cvd_utils.assert_not_called()

        # Test image source type is remote.
        args.local_image = None
        args.build_id = "123"
        args.branch = "fake_branch"
        args.build_target = "fake_target"
        avd_spec_remote_image = avd_spec.AVDSpec(args)
        factory = remote_instance_cf_device_factory.RemoteInstanceDeviceFactory(
            avd_spec_remote_image,
            fake_image_name,
            fake_host_package_name)
        factory.GetBuildInfoDict()
        mock_cvd_utils.GetRemoteBuildInfoDict.assert_called()

    @mock.patch.object(remote_instance_cf_device_factory.RemoteInstanceDeviceFactory,
                       "_CreateGceInstance")
    @mock.patch("acloud.public.actions.remote_instance_cf_device_factory.pull")
    @mock.patch("acloud.public.actions.remote_instance_cf_device_factory."
                "cvd_utils")
    def testLocalImageCreateInstance(self, mock_cvd_utils, mock_pull,
                                     mock_create_gce_instance):
        """Test CreateInstance with local images."""
        self.Patch(
            cvd_compute_client_multi_stage,
            "CvdComputeClient",
            return_value=mock.MagicMock())
        mock_create_gce_instance.return_value = "instance"
        fake_avd_spec = mock.MagicMock()
        fake_avd_spec.image_source = constants.IMAGE_SRC_LOCAL
        fake_avd_spec._instance_name_to_reuse = None
        fake_avd_spec.no_pull_log = False

        mock_cvd_utils.ConvertRemoteLogs.return_value = [{"path": "/logcat"}]
        mock_cvd_utils.UploadExtraImages.return_value = [
            "-boot_image", "/boot/img"]

        fake_host_package_name = "/fake/host_package.tar.gz"
        fake_image_name = ""
        factory = remote_instance_cf_device_factory.RemoteInstanceDeviceFactory(
            fake_avd_spec,
            fake_image_name,
            fake_host_package_name)
        compute_client = factory.GetComputeClient()
        compute_client.LaunchCvd.return_value = {"instance": "failure"}
        factory.CreateInstance()
        mock_create_gce_instance.assert_called_once()
        mock_cvd_utils.UploadArtifacts.assert_called_once()
        compute_client.LaunchCvd.assert_called_once()
        self.assertEqual(
            ["-boot_image", "/boot/img"],
            compute_client.LaunchCvd.call_args[1].get("extra_args"))
        mock_pull.GetAllLogFilePaths.assert_called_once()
        mock_pull.PullLogs.assert_called_once()
        self.assertEqual({"instance": "failure"}, factory.GetFailures())
        self.assertEqual(3, len(factory.GetLogs().get("instance")))

    @mock.patch.object(remote_instance_cf_device_factory.RemoteInstanceDeviceFactory,
                       "_CreateGceInstance")
    @mock.patch("acloud.public.actions.remote_instance_cf_device_factory.pull")
    @mock.patch("acloud.public.actions.remote_instance_cf_device_factory."
                "cvd_utils")
    def testRemoteImageCreateInstance(self, mock_cvd_utils, mock_pull,
                                      mock_create_gce_instance):
        """Test CreateInstance with remote images."""
        self.Patch(
            cvd_compute_client_multi_stage,
            "CvdComputeClient",
            return_value=mock.MagicMock())
        mock_create_gce_instance.return_value = "instance"
        fake_avd_spec = mock.MagicMock()
        fake_avd_spec.image_source = constants.IMAGE_SRC_REMOTE
        fake_avd_spec.host_user = None
        fake_avd_spec.no_pull_log = True

        mock_cvd_utils.ConvertRemoteLogs.return_value = [{"path": "/logcat"}]
        mock_cvd_utils.UploadExtraImages.return_value = []

        factory = remote_instance_cf_device_factory.RemoteInstanceDeviceFactory(
            fake_avd_spec)
        compute_client = factory.GetComputeClient()
        compute_client.LaunchCvd.return_value = {}
        factory.CreateInstance()

        compute_client.FetchBuild.assert_called_once()
        mock_pull.GetAllLogFilePaths.assert_called_once()
        mock_pull.PullLogs.assert_not_called()
        self.assertFalse(factory.GetFailures())
        self.assertEqual(4, len(factory.GetLogs().get("instance")))

    def testGetOpenWrtInfoDict(self):
        """Test GetOpenWrtInfoDict."""
        self.Patch(cvd_compute_client_multi_stage.CvdComputeClient,
                   "GetSshConnectCmd", return_value="fake_ssh_command")
        args = mock.MagicMock()
        args.config_file = ""
        args.avd_type = constants.TYPE_CF
        args.flavor = "phone"
        args.local_image = "fake_local_image"
        args.launch_args = None
        args.openwrt = False
        avd_spec_no_openwrt = avd_spec.AVDSpec(args)
        factory = remote_instance_cf_device_factory.RemoteInstanceDeviceFactory(
            avd_spec_no_openwrt)
        self.assertEqual(None, factory.GetOpenWrtInfoDict())

        args.openwrt = True
        avd_spec_openwrt = avd_spec.AVDSpec(args)
        factory = remote_instance_cf_device_factory.RemoteInstanceDeviceFactory(
            avd_spec_openwrt)
        expect_result = {"ssh_command": "fake_ssh_command",
                         "screen_command": "screen ~/cuttlefish_runtime/console"}
        self.assertEqual(expect_result, factory.GetOpenWrtInfoDict())


if __name__ == "__main__":
    unittest.main()
