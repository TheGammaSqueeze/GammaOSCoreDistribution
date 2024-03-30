#!/usr/bin/env python
#
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
r"""LocalImageLocalInstance class.

Create class that is responsible for creating a local instance AVD with a
local image. For launching multiple local instances under the same user,
The cuttlefish tool requires 3 variables:
- ANDROID_HOST_OUT: To locate the launch_cvd tool.
- HOME: To specify the temporary folder of launch_cvd.
- CUTTLEFISH_INSTANCE: To specify the instance id.
Acloud user must either set ANDROID_HOST_OUT or run acloud with --local-tool.
The user can optionally specify the folder by --local-instance-dir and the
instance id by --local-instance.

The adb port and vnc port of local instance will be decided according to
instance id. The rule of adb port will be '6520 + [instance id] - 1' and the vnc
port will be '6444 + [instance id] - 1'.
e.g:
If instance id = 3 the adb port will be 6522 and vnc port will be 6446.

To delete the local instance, we will call stop_cvd with the environment variable
[CUTTLEFISH_CONFIG_FILE] which is pointing to the runtime cuttlefish json.

To run this program outside of a build environment, the following setup is
required.
- One of the local tool directories is a decompressed cvd host package,
  i.e., cvd-host_package.tar.gz.
- If the instance doesn't require mixed images, the local image directory
  should be an unzipped update package, i.e., <target>-img-<build>.zip,
  which contains a super image.
- If the instance requires mixing system image, the local image directory
  should be an unzipped target files package, i.e.,
  <target>-target_files-<build>.zip,
  which contains misc info and images not packed into a super image.
- If the instance requires mixing system image, one of the local tool
  directories should be an unzipped OTA tools package, i.e., otatools.zip.
"""

import collections
import glob
import logging
import os
import re
import shutil
import subprocess
import sys

from acloud import errors
from acloud.create import base_avd_create
from acloud.create import create_common
from acloud.internal import constants
from acloud.internal.lib import cvd_utils
from acloud.internal.lib import ota_tools
from acloud.internal.lib import utils
from acloud.internal.lib.adb_tools import AdbTools
from acloud.list import list as list_instance
from acloud.list import instance
from acloud.public import report
from acloud.setup import mkcert


logger = logging.getLogger(__name__)

_SYSTEM_IMAGE_NAME_PATTERN = r"system\.img"
_MISC_INFO_FILE_NAME = "misc_info.txt"
_TARGET_FILES_IMAGES_DIR_NAME = "IMAGES"
_TARGET_FILES_META_DIR_NAME = "META"
_MIXED_SUPER_IMAGE_NAME = "mixed_super.img"
_CMD_CVD_START = " start"
_CMD_LAUNCH_CVD_ARGS = (
    " -daemon -config=%s -system_image_dir %s -instance_dir %s "
    "-undefok=report_anonymous_usage_stats,config "
    "-report_anonymous_usage_stats=y")
_CMD_LAUNCH_CVD_HW_ARGS = " -cpus %s -x_res %s -y_res %s -dpi %s -memory_mb %s"
_CMD_LAUNCH_CVD_DISK_ARGS = (
    " -blank_data_image_mb %s -data_policy always_create")
_CMD_LAUNCH_CVD_WEBRTC_ARGS = " -start_webrtc=true"
_CMD_LAUNCH_CVD_VNC_ARG = " -start_vnc_server=true"
_CMD_LAUNCH_CVD_SUPER_IMAGE_ARG = " -super_image=%s"
_CMD_LAUNCH_CVD_BOOT_IMAGE_ARG = " -boot_image=%s"
_CMD_LAUNCH_CVD_VENDOR_BOOT_IMAGE_ARG = " -vendor_boot_image=%s"
_CMD_LAUNCH_CVD_NO_ADB_ARG = " -run_adb_connector=false"
# Connect the OpenWrt device via console file.
_CMD_LAUNCH_CVD_CONSOLE_ARG = " -console=true"
_CONFIG_RE = re.compile(r"^config=(?P<config>.+)")
_CONSOLE_NAME = "console"
# Files to store the output when launching cvds.
_STDOUT = "stdout"
_STDERR = "stderr"
_MAX_REPORTED_ERROR_LINES = 10

# In accordance with the number of network interfaces in
# /etc/init.d/cuttlefish-common
_MAX_INSTANCE_ID = 10

# TODO(b/213521240): To check why the delete function is not work and
# has to manually delete temp folder.
_INSTANCES_IN_USE_MSG = ("All instances are in use. Try resetting an instance "
                         "by specifying --local-instance and an id between 1 "
                         "and %d. Alternatively, to run 'acloud delete --all' "
                         % _MAX_INSTANCE_ID)
_CONFIRM_RELAUNCH = ("\nCuttlefish AVD[id:%d] is already running. \n"
                     "Enter 'y' to terminate current instance and launch a new "
                     "instance, enter anything else to exit out[y/N]: ")

# The first two fields of this named tuple are image folder and CVD host
# package folder which are essential for local instances. The following fields
# are optional. They are set when the AVD spec requires to mix images.
ArtifactPaths = collections.namedtuple(
    "ArtifactPaths",
    ["image_dir", "host_bins", "host_artifacts", "misc_info", "ota_tools_dir",
     "system_image", "boot_image", "vendor_boot_image"])


class LocalImageLocalInstance(base_avd_create.BaseAVDCreate):
    """Create class for a local image local instance AVD."""

    @utils.TimeExecute(function_description="Total time: ",
                       print_before_call=False, print_status=False)
    def _CreateAVD(self, avd_spec, no_prompts):
        """Create the AVD.

        Args:
            avd_spec: AVDSpec object that tells us what we're going to create.
            no_prompts: Boolean, True to skip all prompts.

        Returns:
            A Report instance.
        """
        # Running instances on local is not supported on all OS.
        result_report = report.Report(command="create")
        if not utils.IsSupportedPlatform(print_warning=True):
            result_report.UpdateFailure(
                "The platform doesn't support to run acloud.")
            return result_report
        if not utils.IsSupportedKvm():
            result_report.UpdateFailure(
                "The environment doesn't support virtualization.")
            return result_report

        artifact_paths = self.GetImageArtifactsPath(avd_spec)

        try:
            ins_id, ins_lock = self._SelectAndLockInstance(avd_spec)
        except errors.CreateError as e:
            result_report.UpdateFailure(str(e))
            return result_report

        try:
            if not self._CheckRunningCvd(ins_id, no_prompts):
                # Mark as in-use so that it won't be auto-selected again.
                ins_lock.SetInUse(True)
                sys.exit(constants.EXIT_BY_USER)

            result_report = self._CreateInstance(ins_id, artifact_paths,
                                                 avd_spec, no_prompts)
            # The infrastructure is able to delete the instance only if the
            # instance name is reported. This method changes the state to
            # in-use after creating the report.
            ins_lock.SetInUse(True)
            return result_report
        finally:
            ins_lock.Unlock()

    @staticmethod
    def _SelectAndLockInstance(avd_spec):
        """Select an id and lock the instance.

        Args:
            avd_spec: AVDSpec for the device.

        Returns:
            The instance id and the LocalInstanceLock that is locked by this
            process.

        Raises:
            errors.CreateError if fails to select or lock the instance.
        """
        if avd_spec.local_instance_id:
            ins_id = avd_spec.local_instance_id
            ins_lock = instance.GetLocalInstanceLock(ins_id)
            if ins_lock.Lock():
                return ins_id, ins_lock
            raise errors.CreateError("Instance %d is locked by another "
                                     "process." % ins_id)

        for ins_id in range(1, _MAX_INSTANCE_ID + 1):
            ins_lock = instance.GetLocalInstanceLock(ins_id)
            if ins_lock.LockIfNotInUse(timeout_secs=0):
                logger.info("Selected instance id: %d", ins_id)
                return ins_id, ins_lock
        raise errors.CreateError(_INSTANCES_IN_USE_MSG)

    #pylint: disable=too-many-locals,too-many-statements
    def _CreateInstance(self, local_instance_id, artifact_paths, avd_spec,
                        no_prompts):
        """Create a CVD instance.

        Args:
            local_instance_id: Integer of instance id.
            artifact_paths: ArtifactPaths object.
            avd_spec: AVDSpec for the instance.
            no_prompts: Boolean, True to skip all prompts.

        Returns:
            A Report instance.
        """
        webrtc_port = self.GetWebrtcSigServerPort(local_instance_id)
        if avd_spec.connect_webrtc:
            utils.ReleasePort(webrtc_port)

        cvd_home_dir = instance.GetLocalInstanceHomeDir(local_instance_id)
        create_common.PrepareLocalInstanceDir(cvd_home_dir, avd_spec)
        super_image_path = None
        if artifact_paths.system_image:
            super_image_path = self._MixSuperImage(cvd_home_dir,
                                                   artifact_paths)
        runtime_dir = instance.GetLocalInstanceRuntimeDir(local_instance_id)
        # TODO(b/168171781): cvd_status of list/delete via the symbolic.
        self.PrepareLocalCvdToolsLink(cvd_home_dir, artifact_paths.host_bins)
        if avd_spec.mkcert and avd_spec.connect_webrtc:
            self._TrustCertificatesForWebRTC(artifact_paths.host_artifacts)

        hw_property = None
        if avd_spec.hw_customize:
            hw_property = avd_spec.hw_property
        config = self._GetConfigFromAndroidInfo(
            os.path.join(artifact_paths.image_dir, constants.ANDROID_INFO_FILE))
        cmd = self.PrepareLaunchCVDCmd(hw_property,
                                       avd_spec.connect_adb,
                                       artifact_paths,
                                       runtime_dir,
                                       avd_spec.connect_webrtc,
                                       avd_spec.connect_vnc,
                                       super_image_path,
                                       avd_spec.launch_args,
                                       config or avd_spec.flavor,
                                       avd_spec.openwrt,
                                       avd_spec.use_launch_cvd)

        result_report = report.Report(command="create")
        instance_name = instance.GetLocalInstanceName(local_instance_id)
        try:
            self._LaunchCvd(cmd, local_instance_id, artifact_paths.host_bins,
                            artifact_paths.host_artifacts,
                            cvd_home_dir, (avd_spec.boot_timeout_secs or
                                           constants.DEFAULT_CF_BOOT_TIMEOUT))
            logs = self._FindLogs(local_instance_id)
        except errors.LaunchCVDFail as launch_error:
            logs = self._FindLogs(local_instance_id)
            err_msg = ("Cannot create cuttlefish instance: %s\n"
                       "For more detail: %s/launcher.log" %
                       (launch_error, runtime_dir))
            if constants.ERROR_MSG_WEBRTC_NOT_SUPPORT in str(launch_error):
                err_msg = (
                    "WEBRTC is not supported in current build. Please try VNC such "
                    "as '$acloud create --autoconnect vnc'")
            result_report.SetStatus(report.Status.BOOT_FAIL)
            result_report.SetErrorType(constants.ACLOUD_BOOT_UP_ERROR)
            result_report.AddDeviceBootFailure(
                instance_name, constants.LOCALHOST, None, None, error=err_msg,
                logs=logs)
            return result_report

        active_ins = list_instance.GetActiveCVD(local_instance_id)
        if active_ins:
            update_data = None
            if avd_spec.openwrt:
                console_dir = os.path.dirname(
                    instance.GetLocalInstanceConfig(local_instance_id))
                console_path = os.path.join(console_dir, _CONSOLE_NAME)
                update_data = {"screen_command": f"screen {console_path}"}
            result_report.SetStatus(report.Status.SUCCESS)
            result_report.AddDevice(instance_name, constants.LOCALHOST,
                                    active_ins.adb_port, active_ins.vnc_port,
                                    webrtc_port, logs=logs, update_data=update_data)
            # Launch vnc client if we're auto-connecting.
            if avd_spec.connect_vnc:
                utils.LaunchVNCFromReport(result_report, avd_spec, no_prompts)
            if avd_spec.connect_webrtc:
                utils.LaunchBrowserFromReport(result_report)
            if avd_spec.unlock_screen:
                AdbTools(active_ins.adb_port).AutoUnlockScreen()
        else:
            err_msg = "cvd_status return non-zero after launch_cvd"
            logger.error(err_msg)
            result_report.SetStatus(report.Status.BOOT_FAIL)
            result_report.SetErrorType(constants.ACLOUD_BOOT_UP_ERROR)
            result_report.AddDeviceBootFailure(
                instance_name, constants.LOCALHOST, None, None, error=err_msg,
                logs=logs)
        return result_report

    @staticmethod
    def GetWebrtcSigServerPort(instance_id):
        """Get the port of the signaling server.

        Args:
            instance_id: Integer of instance id.

        Returns:
            Integer of signaling server port.
        """
        return constants.WEBRTC_LOCAL_PORT + instance_id - 1

    @staticmethod
    def _FindCvdHostBinaries(search_paths):
        """Return the directory that contains CVD host binaries."""
        for search_path in search_paths:
            if os.path.isfile(os.path.join(search_path, "bin",
                                           constants.CMD_LAUNCH_CVD)):
                return search_path

        raise errors.GetCvdLocalHostPackageError(
            "CVD host binaries are not found. Please run `make hosttar`, or "
            "set --local-tool to an extracted CVD host package.")

    @staticmethod
    def _FindCvdHostArtifactsPath(search_paths):
        """Return the directory that contains CVD host artifacts (in particular webrtc)."""
        for search_path in search_paths:
            if os.path.isfile(os.path.join(search_path, "usr/share/webrtc/certs", "server.crt")):
                return search_path

        raise errors.GetCvdLocalHostPackageError(
            "CVD host webrtc artifacts are not found. Please run `make hosttar`, or "
            "set --local-tool to an extracted CVD host package.")

    @staticmethod
    def FindMiscInfo(image_dir):
        """Find misc info in build output dir or extracted target files.

        Args:
            image_dir: The directory to search for misc info.

        Returns:
            image_dir if the directory structure looks like an output directory
            in build environment.
            image_dir/META if it looks like extracted target files.

        Raises:
            errors.CheckPathError if this method cannot find misc info.
        """
        misc_info_path = os.path.join(image_dir, _MISC_INFO_FILE_NAME)
        if os.path.isfile(misc_info_path):
            return misc_info_path
        misc_info_path = os.path.join(image_dir, _TARGET_FILES_META_DIR_NAME,
                                      _MISC_INFO_FILE_NAME)
        if os.path.isfile(misc_info_path):
            return misc_info_path
        raise errors.CheckPathError(
            "Cannot find %s in %s." % (_MISC_INFO_FILE_NAME, image_dir))

    @staticmethod
    def FindImageDir(image_dir):
        """Find images in build output dir or extracted target files.

        Args:
            image_dir: The directory to search for images.

        Returns:
            image_dir if the directory structure looks like an output directory
            in build environment.
            image_dir/IMAGES if it looks like extracted target files.

        Raises:
            errors.GetLocalImageError if this method cannot find images.
        """
        if glob.glob(os.path.join(image_dir, "*.img")):
            return image_dir
        subdir = os.path.join(image_dir, _TARGET_FILES_IMAGES_DIR_NAME)
        if glob.glob(os.path.join(subdir, "*.img")):
            return subdir
        raise errors.GetLocalImageError(
            "Cannot find images in %s." % image_dir)

    def GetImageArtifactsPath(self, avd_spec):
        """Get image artifacts path.

        This method will check if launch_cvd is exist and return the tuple path
        (image path and host bins path) where they are located respectively.
        For remote image, RemoteImageLocalInstance will override this method and
        return the artifacts path which is extracted and downloaded from remote.

        Args:
            avd_spec: AVDSpec object that tells us what we're going to create.

        Returns:
            ArtifactPaths object consisting of image directory and host bins
            package.

        Raises:
            errors.GetCvdLocalHostPackageError, errors.GetLocalImageError, or
            errors.CheckPathError if any artifact is not found.
        """
        image_dir = os.path.abspath(avd_spec.local_image_dir)
        tool_dirs = (avd_spec.local_tool_dirs +
                     create_common.GetNonEmptyEnvVars(
                         constants.ENV_ANDROID_SOONG_HOST_OUT,
                         constants.ENV_ANDROID_HOST_OUT))
        host_bins_path = self._FindCvdHostBinaries(tool_dirs)
        host_artifacts_path = self._FindCvdHostArtifactsPath(tool_dirs)

        if avd_spec.local_system_image:
            misc_info_path = self.FindMiscInfo(image_dir)
            image_dir = self.FindImageDir(image_dir)
            ota_tools_dir = os.path.abspath(
                ota_tools.FindOtaToolsDir(tool_dirs))
            system_image_path = create_common.FindLocalImage(
                avd_spec.local_system_image, _SYSTEM_IMAGE_NAME_PATTERN)
        else:
            misc_info_path = None
            ota_tools_dir = None
            system_image_path = None

        if avd_spec.local_kernel_image:
            boot_image_path, vendor_boot_image_path = cvd_utils.FindBootImages(
                avd_spec.local_kernel_image)
        else:
            boot_image_path = None
            vendor_boot_image_path = None

        return ArtifactPaths(image_dir, host_bins_path,
                             host_artifacts=host_artifacts_path,
                             misc_info=misc_info_path,
                             ota_tools_dir=ota_tools_dir,
                             system_image=system_image_path,
                             boot_image=boot_image_path,
                             vendor_boot_image=vendor_boot_image_path)

    @staticmethod
    def _MixSuperImage(output_dir, artifact_paths):
        """Mix cuttlefish images and a system image into a super image.

        Args:
            output_dir: The path to the output directory.
            artifact_paths: ArtifactPaths object.

        Returns:
            The path to the super image in output_dir.
        """
        ota = ota_tools.OtaTools(artifact_paths.ota_tools_dir)
        super_image_path = os.path.join(output_dir, _MIXED_SUPER_IMAGE_NAME)
        ota.BuildSuperImage(
            super_image_path, artifact_paths.misc_info,
            lambda partition: ota_tools.GetImageForPartition(
                partition, artifact_paths.image_dir,
                system=artifact_paths.system_image))
        return super_image_path

    @staticmethod
    def _GetConfigFromAndroidInfo(android_info_path):
        """Get config value from android-info.txt.

        The config in android-info.txt would like "config=phone".

        Args:
            android_info_path: String of android-info.txt pah.

        Returns:
            Strings of config value.
        """
        if os.path.exists(android_info_path):
            with open(android_info_path, "r") as android_info_file:
                android_info = android_info_file.read()
                logger.debug("Android info: %s", android_info)
                config_match = _CONFIG_RE.match(android_info)
                if config_match:
                    return config_match.group("config")
        return None

    @staticmethod
    def PrepareLaunchCVDCmd(hw_property, connect_adb, artifact_paths,
                            runtime_dir, connect_webrtc, connect_vnc,
                            super_image_path, launch_args, config,
                            openwrt=False, use_launch_cvd=False):
        """Prepare launch_cvd command.

        Create the launch_cvd commands with all the required args and add
        in the user groups to it if necessary.

        Args:
            hw_property: dict object of hw property.
            artifact_paths: ArtifactPaths object.
            connect_adb: Boolean flag that enables adb_connector.
            runtime_dir: String of runtime directory path.
            connect_webrtc: Boolean of connect_webrtc.
            connect_vnc: Boolean of connect_vnc.
            super_image_path: String of non-default super image path.
            launch_args: String of launch args.
            config: String of config name.
            openwrt: Boolean of enable OpenWrt devices.
            use_launch_cvd: Boolean of using launch_cvd for old build cases.

        Returns:
            String, cvd start cmd.
        """
        bin_dir = os.path.join(artifact_paths.host_bins, "bin")
        start_cvd_cmd = (os.path.join(bin_dir, constants.CMD_CVD) +
                         _CMD_CVD_START)
        if use_launch_cvd:
            start_cvd_cmd = os.path.join(bin_dir, constants.CMD_LAUNCH_CVD)
        launch_cvd_w_args = start_cvd_cmd + _CMD_LAUNCH_CVD_ARGS % (
            config, artifact_paths.image_dir, runtime_dir)
        if hw_property:
            launch_cvd_w_args = launch_cvd_w_args + _CMD_LAUNCH_CVD_HW_ARGS % (
                hw_property["cpu"], hw_property["x_res"], hw_property["y_res"],
                hw_property["dpi"], hw_property["memory"])
            if constants.HW_ALIAS_DISK in hw_property:
                launch_cvd_w_args = (launch_cvd_w_args + _CMD_LAUNCH_CVD_DISK_ARGS %
                                     hw_property[constants.HW_ALIAS_DISK])

        if not connect_adb:
            launch_cvd_w_args = launch_cvd_w_args + _CMD_LAUNCH_CVD_NO_ADB_ARG

        if connect_webrtc:
            launch_cvd_w_args = launch_cvd_w_args + _CMD_LAUNCH_CVD_WEBRTC_ARGS

        if connect_vnc:
            launch_cvd_w_args = launch_cvd_w_args + _CMD_LAUNCH_CVD_VNC_ARG

        if super_image_path:
            launch_cvd_w_args = (launch_cvd_w_args +
                                 _CMD_LAUNCH_CVD_SUPER_IMAGE_ARG %
                                 super_image_path)

        if artifact_paths.boot_image:
            launch_cvd_w_args = (launch_cvd_w_args +
                                 _CMD_LAUNCH_CVD_BOOT_IMAGE_ARG %
                                 artifact_paths.boot_image)

        if artifact_paths.vendor_boot_image:
            launch_cvd_w_args = (launch_cvd_w_args +
                                 _CMD_LAUNCH_CVD_VENDOR_BOOT_IMAGE_ARG %
                                 artifact_paths.vendor_boot_image)

        if openwrt:
            launch_cvd_w_args = launch_cvd_w_args + _CMD_LAUNCH_CVD_CONSOLE_ARG

        if launch_args:
            launch_cvd_w_args = launch_cvd_w_args + " " + launch_args

        launch_cmd = utils.AddUserGroupsToCmd(launch_cvd_w_args,
                                              constants.LIST_CF_USER_GROUPS)
        logger.debug("launch_cvd cmd:\n %s", launch_cmd)
        return launch_cmd

    @staticmethod
    def PrepareLocalCvdToolsLink(cvd_home_dir, host_bins_path):
        """Create symbolic link for the cvd tools directory.

        local instance's cvd tools could be generated in /out after local build
        or be generated in the download image folder. It creates a symbolic
        link then only check cvd_status using known link for both cases.

        Args:
            cvd_home_dir: The parent directory of the link
            host_bins_path: String of host package directory.

        Returns:
            String of cvd_tools link path
        """
        cvd_tools_link_path = os.path.join(cvd_home_dir, constants.CVD_TOOLS_LINK_NAME)
        if os.path.islink(cvd_tools_link_path):
            os.unlink(cvd_tools_link_path)
        os.symlink(host_bins_path, cvd_tools_link_path)
        return cvd_tools_link_path

    @staticmethod
    def _TrustCertificatesForWebRTC(host_bins_path):
        """Copy the trusted certificates generated by openssl tool to the
        webrtc frontend certificate directory.

        Args:
            host_bins_path: String of host package directory.
        """
        webrtc_certs_dir = os.path.join(host_bins_path,
                                        constants.WEBRTC_CERTS_PATH)
        if not os.path.isdir(webrtc_certs_dir):
            logger.debug("WebRTC frontend certificate path doesn't exist: %s",
                         webrtc_certs_dir)
            return
        local_cert_dir = os.path.join(os.path.expanduser("~"),
                                      constants.SSL_DIR)
        if mkcert.AllocateLocalHostCert():
            for cert_file_name in constants.WEBRTC_CERTS_FILES:
                shutil.copyfile(
                    os.path.join(local_cert_dir, cert_file_name),
                    os.path.join(webrtc_certs_dir, cert_file_name))

    @staticmethod
    def _CheckRunningCvd(local_instance_id, no_prompts=False):
        """Check if launch_cvd with the same instance id is running.

        Args:
            local_instance_id: Integer of instance id.
            no_prompts: Boolean, True to skip all prompts.

        Returns:
            Whether the user wants to continue.
        """
        # Check if the instance with same id is running.
        existing_ins = list_instance.GetActiveCVD(local_instance_id)
        if existing_ins:
            if no_prompts or utils.GetUserAnswerYes(_CONFIRM_RELAUNCH %
                                                    local_instance_id):
                existing_ins.Delete()
            else:
                return False
        return True

    @staticmethod
    def _StopCvd(local_instance_id, proc):
        """Call stop_cvd or kill a launch_cvd process.

        Args:
            local_instance_id: Integer of instance id.
            proc: subprocess.Popen object, the launch_cvd process.
        """
        existing_ins = list_instance.GetActiveCVD(local_instance_id)
        if existing_ins:
            try:
                existing_ins.Delete()
                return
            except subprocess.CalledProcessError as e:
                logger.error("Cannot stop instance %d: %s",
                             local_instance_id, str(e))
        else:
            logger.error("Instance %d is not active.", local_instance_id)
        logger.info("Terminate launch_cvd process.")
        proc.terminate()

    @utils.TimeExecute(function_description="Waiting for AVD(s) to boot up")
    def _LaunchCvd(self, cmd, local_instance_id, host_bins_path, host_artifacts_path,
                   cvd_home_dir, timeout):
        """Execute Launch CVD.

        Kick off the launch_cvd command and log the output.

        Args:
            cmd: String, launch_cvd command.
            local_instance_id: Integer of instance id.
            host_bins_path: String of host package directory containing binaries.
            host_artifacts_path: String of host package directory containing
              other artifacts.
            cvd_home_dir: String, the home directory for the instance.
            timeout: Integer, the number of seconds to wait for the AVD to boot up.

        Raises:
            errors.LaunchCVDFail if launch_cvd times out or returns non-zero.
        """
        cvd_env = os.environ.copy()
        cvd_env[constants.ENV_ANDROID_SOONG_HOST_OUT] = host_artifacts_path
        # launch_cvd assumes host bins are in $ANDROID_HOST_OUT.
        cvd_env[constants.ENV_ANDROID_HOST_OUT] = host_bins_path
        cvd_env[constants.ENV_CVD_HOME] = cvd_home_dir
        cvd_env[constants.ENV_CUTTLEFISH_INSTANCE] = str(local_instance_id)
        cvd_env[constants.ENV_CUTTLEFISH_CONFIG_FILE] = (
            instance.GetLocalInstanceConfigPath(local_instance_id))
        stdout_file = os.path.join(cvd_home_dir, _STDOUT)
        stderr_file = os.path.join(cvd_home_dir, _STDERR)
        # Check the result of launch_cvd command.
        # An exit code of 0 is equivalent to VIRTUAL_DEVICE_BOOT_COMPLETED
        with open(stdout_file, "w+") as f_stdout, open(stderr_file,
                                                       "w+") as f_stderr:
            try:
                proc = subprocess.Popen(
                    cmd, shell=True, env=cvd_env, stdout=f_stdout,
                    stderr=f_stderr, text=True, cwd=host_bins_path)
                proc.communicate(timeout=timeout)
                f_stdout.seek(0)
                f_stderr.seek(0)
                if proc.returncode == 0:
                    logger.info("launch_cvd stdout:\n%s", f_stdout.read())
                    logger.info("launch_cvd stderr:\n%s", f_stderr.read())
                    return
                error_msg = "launch_cvd returned %d." % proc.returncode
            except subprocess.TimeoutExpired:
                self._StopCvd(local_instance_id, proc)
                proc.communicate(timeout=5)
                error_msg = "Device did not boot within %d secs." % timeout

            f_stdout.seek(0)
            f_stderr.seek(0)
            stderr = f_stderr.read()
            logger.error("launch_cvd stdout:\n%s", f_stdout.read())
            logger.error("launch_cvd stderr:\n%s", stderr)
            split_stderr = stderr.splitlines()[-_MAX_REPORTED_ERROR_LINES:]
            raise errors.LaunchCVDFail(
                "%s Stderr:\n%s" % (error_msg, "\n".join(split_stderr)))

    @staticmethod
    def _FindLogs(local_instance_id):
        """Find log paths that will be written to report.

        Args:
            local_instance_id: An integer, the instance id.

        Returns:
            A list of report.LogFile.
        """
        log_dir = instance.GetLocalInstanceLogDir(local_instance_id)
        return [report.LogFile(os.path.join(log_dir, name), log_type)
                for name, log_type in [
                    ("launcher.log", constants.LOG_TYPE_TEXT),
                    ("kernel.log", constants.LOG_TYPE_KERNEL_LOG),
                    ("logcat", constants.LOG_TYPE_LOGCAT)]]
