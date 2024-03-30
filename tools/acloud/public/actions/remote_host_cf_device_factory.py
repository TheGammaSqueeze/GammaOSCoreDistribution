# Copyright 2022 - The Android Open Source Project
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

"""RemoteHostDeviceFactory implements the device factory interface and creates
cuttlefish instances on a remote host."""

import glob
import logging
import os
import shutil
import subprocess
import tempfile

from acloud import errors
from acloud.internal import constants
from acloud.internal.lib import auth
from acloud.internal.lib import cvd_compute_client_multi_stage
from acloud.internal.lib import cvd_utils
from acloud.internal.lib import utils
from acloud.internal.lib import ssh
from acloud.public.actions import base_device_factory
from acloud.pull import pull


logger = logging.getLogger(__name__)
_ALL_FILES = "*"
_HOME_FOLDER = os.path.expanduser("~")
_SCREEN_CONSOLE_COMMAND = "screen ~/cuttlefish_runtime/console"


class RemoteHostDeviceFactory(base_device_factory.BaseDeviceFactory):
    """A class that can produce a cuttlefish device.

    Attributes:
        avd_spec: AVDSpec object that tells us what we're going to create.
        local_image_artifact: A string, path to local image.
        cvd_host_package_artifact: A string, path to cvd host package.
        all_failures: A dictionary mapping instance names to errors.
        all_logs: A dictionary mapping instance names to lists of
                  report.LogFile.
        compute_client: An object of cvd_compute_client.CvdComputeClient.
        ssh: An Ssh object.
    """

    _USER_BUILD = "userbuild"

    def __init__(self, avd_spec, local_image_artifact=None,
                 cvd_host_package_artifact=None):
        """Initialize attributes."""
        self._avd_spec = avd_spec
        self._local_image_artifact = local_image_artifact
        self._cvd_host_package_artifact = cvd_host_package_artifact
        self._all_failures = {}
        self._all_logs = {}
        credentials = auth.CreateCredentials(avd_spec.cfg)
        compute_client = cvd_compute_client_multi_stage.CvdComputeClient(
            acloud_config=avd_spec.cfg,
            oauth2_credentials=credentials,
            ins_timeout_secs=avd_spec.ins_timeout_secs,
            report_internal_ip=avd_spec.report_internal_ip,
            gpu=avd_spec.gpu)
        super().__init__(compute_client)
        self._ssh = None

    def CreateInstance(self):
        """Create a single configured cuttlefish device.

        Returns:
            A string, representing instance name.
        """
        instance = self._InitRemotehost()
        image_args = self._ProcessRemoteHostArtifacts()
        failures = self._compute_client.LaunchCvd(
            instance,
            self._avd_spec,
            self._avd_spec.cfg.extra_data_disk_size_gb,
            boot_timeout_secs=self._avd_spec.boot_timeout_secs,
            extra_args=image_args)
        self._all_failures.update(failures)
        self._FindLogFiles(
            instance, instance in failures and not self._avd_spec.no_pull_log)
        return instance

    def _InitRemotehost(self):
        """Initialize remote host.

        Determine the remote host instance name, and activate ssh. It need to
        get the IP address in the common_operation. So need to pass the IP and
        ssh to compute_client.

        build_target: The format is like "aosp_cf_x86_phone". We only get info
                      from the user build image file name. If the file name is
                      not custom format (no "-"), we will use $TARGET_PRODUCT
                      from environment variable as build_target.

        Returns:
            A string, representing instance name.
        """
        image_name = os.path.basename(
            self._local_image_artifact) if self._local_image_artifact else ""
        build_target = (os.environ.get(constants.ENV_BUILD_TARGET)
                        if "-" not in image_name else
                        image_name.split("-", maxsplit=1)[0])
        build_id = self._USER_BUILD
        if self._avd_spec.image_source == constants.IMAGE_SRC_REMOTE:
            build_id = self._avd_spec.remote_image[constants.BUILD_ID]

        instance = self._compute_client.FormatRemoteHostInstanceName(
            self._avd_spec.remote_host, build_id, build_target)
        ip = ssh.IP(ip=self._avd_spec.remote_host)
        self._ssh = ssh.Ssh(
            ip=ip,
            user=self._avd_spec.host_user,
            ssh_private_key_path=(self._avd_spec.host_ssh_private_key_path or
                                  self._avd_spec.cfg.ssh_private_key_path),
            extra_args_ssh_tunnel=self._avd_spec.cfg.extra_args_ssh_tunnel,
            report_internal_ip=self._avd_spec.report_internal_ip)
        self._compute_client.InitRemoteHost(
            self._ssh, ip, self._avd_spec.host_user)
        return instance

    def _ProcessRemoteHostArtifacts(self):
        """Process remote host artifacts.

        - If images source is local, tool will upload images from local site to
          remote host.
        - If images source is remote, tool will download images from android
          build to local and unzip it then upload to remote host, because there
          is no permission to fetch build rom on the remote host.

        Returns:
            A list of strings, the launch_cvd arguments.
        """
        self._compute_client.SetStage(constants.STAGE_ARTIFACT)
        if self._avd_spec.image_source == constants.IMAGE_SRC_LOCAL:
            cvd_utils.UploadArtifacts(
                self._ssh,
                self._local_image_artifact or self._avd_spec.local_image_dir,
                self._cvd_host_package_artifact)
        else:
            try:
                artifacts_path = tempfile.mkdtemp()
                logger.debug("Extracted path of artifacts: %s", artifacts_path)
                self._DownloadArtifacts(artifacts_path)
                self._UploadRemoteImageArtifacts(artifacts_path)
            finally:
                shutil.rmtree(artifacts_path)

        return cvd_utils.UploadExtraImages(self._ssh, self._avd_spec)

    @utils.TimeExecute(function_description="Downloading Android Build artifact")
    def _DownloadArtifacts(self, extract_path):
        """Download the CF image artifacts and process them.

        - Download images from the Android Build system.
        - Download cvd host package from the Android Build system.

        Args:
            extract_path: String, a path include extracted files.

        Raises:
            errors.GetRemoteImageError: Fails to download rom images.
        """
        cfg = self._avd_spec.cfg
        build_id = self._avd_spec.remote_image[constants.BUILD_ID]
        build_branch = self._avd_spec.remote_image[constants.BUILD_BRANCH]
        build_target = self._avd_spec.remote_image[constants.BUILD_TARGET]

        # Download images with fetch_cvd
        fetch_cvd = os.path.join(extract_path, constants.FETCH_CVD)
        self._compute_client.build_api.DownloadFetchcvd(fetch_cvd,
                                                        cfg.fetch_cvd_version)
        fetch_cvd_build_args = self._compute_client.build_api.GetFetchBuildArgs(
            build_id, build_branch, build_target,
            self._avd_spec.system_build_info.get(constants.BUILD_ID),
            self._avd_spec.system_build_info.get(constants.BUILD_BRANCH),
            self._avd_spec.system_build_info.get(constants.BUILD_TARGET),
            self._avd_spec.kernel_build_info.get(constants.BUILD_ID),
            self._avd_spec.kernel_build_info.get(constants.BUILD_BRANCH),
            self._avd_spec.kernel_build_info.get(constants.BUILD_TARGET),
            self._avd_spec.bootloader_build_info.get(constants.BUILD_ID),
            self._avd_spec.bootloader_build_info.get(constants.BUILD_BRANCH),
            self._avd_spec.bootloader_build_info.get(constants.BUILD_TARGET),
            self._avd_spec.ota_build_info.get(constants.BUILD_ID),
            self._avd_spec.ota_build_info.get(constants.BUILD_BRANCH),
            self._avd_spec.ota_build_info.get(constants.BUILD_TARGET))
        creds_cache_file = os.path.join(_HOME_FOLDER, cfg.creds_cache_file)
        fetch_cvd_cert_arg = self._compute_client.build_api.GetFetchCertArg(
            creds_cache_file)
        fetch_cvd_args = [fetch_cvd, f"-directory={extract_path}",
                          fetch_cvd_cert_arg]
        fetch_cvd_args.extend(fetch_cvd_build_args)
        logger.debug("Download images command: %s", fetch_cvd_args)
        try:
            subprocess.check_call(fetch_cvd_args)
        except subprocess.CalledProcessError as e:
            raise errors.GetRemoteImageError(f"Fails to download images: {e}")

    @utils.TimeExecute(function_description="Uploading remote image artifacts")
    def _UploadRemoteImageArtifacts(self, images_dir):
        """Upload remote image artifacts to instance.

        Args:
            images_dir: String, directory of local artifacts downloaded by
                        fetch_cvd.
        """
        artifact_files = [
            os.path.basename(image)
            for image in glob.glob(os.path.join(images_dir, _ALL_FILES))
        ]
        ssh_cmd = self._ssh.GetBaseCmd(constants.SSH_BIN)
        # TODO(b/182259589): Refactor upload image command into a function.
        cmd = (f"tar -cf - --lzop -S -C {images_dir} "
               f"{' '.join(artifact_files)} | "
               f"{ssh_cmd} -- tar -xf - --lzop -S")
        logger.debug("cmd:\n %s", cmd)
        ssh.ShellCmdWithRetry(cmd)

    def _FindLogFiles(self, instance, download):
        """Find and pull all log files from instance.

        Args:
            instance: String, instance name.
            download: Whether to download the files to a temporary directory
                      and show messages to the user.
        """
        self._all_logs[instance] = [cvd_utils.TOMBSTONES]
        log_files = pull.GetAllLogFilePaths(self._ssh)
        self._all_logs[instance].extend(cvd_utils.ConvertRemoteLogs(log_files))

        if download:
            error_log_folder = pull.PullLogs(self._ssh, log_files, instance)
            self._compute_client.ExtendReportData(constants.ERROR_LOG_FOLDER,
                                                  error_log_folder)

    def GetOpenWrtInfoDict(self):
        """Get openwrt info dictionary.

        Returns:
            A openwrt info dictionary. None for the case is not openwrt device.
        """
        if not self._avd_spec.openwrt:
            return None
        return {"ssh_command": self._compute_client.GetSshConnectCmd(),
                "screen_command": _SCREEN_CONSOLE_COMMAND}

    def GetBuildInfoDict(self):
        """Get build info dictionary.

        Returns:
            A build info dictionary. None for local image case.
        """
        if self._avd_spec.image_source == constants.IMAGE_SRC_LOCAL:
            return None
        return cvd_utils.GetRemoteBuildInfoDict(self._avd_spec)

    def GetFailures(self):
        """Get failures from all devices.

        Returns:
            A dictionary that contains all the failures.
            The key is the name of the instance that fails to boot,
            and the value is a string or an errors.DeviceBootError object.
        """
        return self._all_failures

    def GetLogs(self):
        """Get all device logs.

        Returns:
            A dictionary that maps instance names to lists of report.LogFile.
        """
        return self._all_logs
