# Copyright 2021 - The Android Open Source Project
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

"""RemoteInstanceDeviceFactory provides basic interface to create a goldfish
device factory."""

import collections
import logging
import os
import posixpath as remote_path
import re
import shutil
import tempfile
import zipfile

from acloud import errors
from acloud.internal import constants
from acloud.internal.lib import android_build_client
from acloud.internal.lib import auth
from acloud.internal.lib import goldfish_remote_host_client
from acloud.internal.lib import goldfish_utils
from acloud.internal.lib import emulator_console
from acloud.internal.lib import ota_tools
from acloud.internal.lib import utils
from acloud.internal.lib import ssh
from acloud.public import report
from acloud.public.actions import base_device_factory


logger = logging.getLogger(__name__)
# Artifacts
_SDK_REPO_IMAGE_ZIP_NAME_FORMAT = ("sdk-repo-linux-system-images-"
                                   "%(build_id)s.zip")
_EXTRA_IMAGE_ZIP_NAME_FORMAT = "emu-extra-linux-system-images-%(build_id)s.zip"
_IMAGE_ZIP_NAME_FORMAT = "%(build_target)s-img-%(build_id)s.zip"
_OTA_TOOLS_ZIP_NAME = "otatools.zip"
_SYSTEM_IMAGE_NAME = "system.img"

_EMULATOR_INFO_NAME = "emulator-info.txt"
_EMULATOR_VERSION_PATTERN = re.compile(r"require\s+version-emulator="
                                       r"(?P<build_id>\w+)")
_EMULATOR_ZIP_NAME_FORMAT = "sdk-repo-%(os)s-emulator-%(build_id)s.zip"
_EMULATOR_BIN_DIR_NAMES = ("bin64", "qemu")
_EMULATOR_BIN_NAME = "emulator"
# Remote paths
_REMOTE_WORKING_DIR = "acloud_gf"
_REMOTE_ARTIFACT_DIR = remote_path.join(_REMOTE_WORKING_DIR, "artifact")
_REMOTE_IMAGE_DIR = remote_path.join(_REMOTE_WORKING_DIR, "image")
_REMOTE_KERNEL_PATH = remote_path.join(_REMOTE_WORKING_DIR, "kernel")
_REMOTE_RAMDISK_PATH = remote_path.join(_REMOTE_WORKING_DIR, "mixed_ramdisk")
_REMOTE_EMULATOR_DIR = remote_path.join(_REMOTE_WORKING_DIR, "emulator")
_REMOTE_INSTANCE_DIR = remote_path.join(_REMOTE_WORKING_DIR, "instance")
_REMOTE_LOGCAT_PATH = os.path.join(_REMOTE_INSTANCE_DIR, "logcat.txt")
_REMOTE_STDOUTERR_PATH = os.path.join(_REMOTE_INSTANCE_DIR, "kernel.log")
# Runtime parameters
_EMULATOR_DEFAULT_CONSOLE_PORT = 5554
_DEFAULT_BOOT_TIMEOUT_SECS = 150

ArtifactPaths = collections.namedtuple(
    "ArtifactPaths",
    ["image_zip", "emulator_zip", "ota_tools_zip",
     "system_image_zip", "boot_image"])

RemotePaths = collections.namedtuple(
    "RemotePaths",
    ["image_dir", "emulator_dir", "kernel", "ramdisk"])


class RemoteHostGoldfishDeviceFactory(base_device_factory.BaseDeviceFactory):
    """A class that creates a goldfish device on a remote host.

    Attributes:
        avd_spec: AVDSpec object that tells us what we're going to create.
        ssh: Ssh object that executes commands on the remote host.
        failures: A dictionary the maps instance names to
                  error.DeviceBootError objects.
        logs: A dictionary that maps instance names to lists of report.LogFile.
    """
    def __init__(self, avd_spec):
        """Initialize the attributes and the compute client."""
        self._avd_spec = avd_spec
        self._ssh = ssh.Ssh(
            ip=ssh.IP(ip=self._avd_spec.remote_host),
            user=self._ssh_user,
            ssh_private_key_path=self._ssh_private_key_path,
            extra_args_ssh_tunnel=self._ssh_extra_args,
            report_internal_ip=False)
        self._failures = {}
        self._logs = {}
        super().__init__(compute_client=(
            goldfish_remote_host_client.GoldfishRemoteHostClient()))

    @property
    def _ssh_user(self):
        return self._avd_spec.host_user or constants.GCE_USER

    @property
    def _ssh_private_key_path(self):
        return (self._avd_spec.host_ssh_private_key_path or
                self._avd_spec.cfg.ssh_private_key_path)

    @property
    def _ssh_extra_args(self):
        return self._avd_spec.cfg.extra_args_ssh_tunnel

    def CreateInstance(self):
        """Create a goldfish instance on the remote host.

        Returns:
            The instance name.
        """
        self._InitRemoteHost()
        remote_paths = self._PrepareArtifacts()

        instance_name = goldfish_remote_host_client.FormatInstanceName(
            self._avd_spec.remote_host,
            _EMULATOR_DEFAULT_CONSOLE_PORT,
            self._avd_spec.remote_image)
        self._logs[instance_name] = [
            report.LogFile(_REMOTE_STDOUTERR_PATH,
                           constants.LOG_TYPE_KERNEL_LOG),
            report.LogFile(_REMOTE_LOGCAT_PATH, constants.LOG_TYPE_LOGCAT)]
        try:
            self._StartEmulator(remote_paths)
            self._WaitForEmulator()
        except errors.DeviceBootError as e:
            self._failures[instance_name] = e
        return instance_name

    def _InitRemoteHost(self):
        """Remove existing instance and working directory."""
        # Disable authentication for emulator console.
        self._ssh.Run("""'echo -n "" > .emulator_console_auth_token'""")
        try:
            with emulator_console.RemoteEmulatorConsole(
                    self._avd_spec.remote_host,
                    _EMULATOR_DEFAULT_CONSOLE_PORT,
                    self._ssh_user,
                    self._ssh_private_key_path,
                    self._ssh_extra_args) as console:
                console.Kill()
            logger.info("Killed existing emulator.")
        except errors.DeviceConnectionError as e:
            logger.info("Did not kill existing emulator: %s", str(e))
        # Delete instance files.
        self._ssh.Run("rm -rf %s" % _REMOTE_WORKING_DIR)

    def _PrepareArtifacts(self):
        """Prepare artifacts on remote host.

        This method retrieves artifacts from cache or Android Build API and
        uploads them to the remote host.

        Returns:
            An object of RemotePaths.
        """
        if self._avd_spec.image_download_dir:
            temp_download_dir = None
            download_dir = self._avd_spec.image_download_dir
        else:
            temp_download_dir = tempfile.mkdtemp()
            download_dir = temp_download_dir
            logger.info("--image-download-dir is not specified. Create "
                        "temporary download directory: %s", download_dir)

        try:
            artifact_paths = self._RetrieveArtifacts(download_dir)
            return self._UploadArtifacts(artifact_paths)
        finally:
            if temp_download_dir:
                shutil.rmtree(temp_download_dir, ignore_errors=True)

    @staticmethod
    def _InferEmulatorZipName(build_target, build_id):
        """Determine the emulator zip name in build artifacts.

        The emulator zip name is composed of build variables that are not
        revealed in the artifacts. This method infers the emulator zip name
        from its build target name.

        Args:
            build_target: The emulator build target name, e.g.,
                          "sdk_tools_linux", "aarch64_sdk_tools_mac".
            build_id: A string, the emulator build ID.

        Returns:
            The name of the emulator zip. e.g.,
            "sdk-repo-linux-emulator-123456.zip",
            "sdk-repo-darwin_aarch64-emulator-123456.zip".
        """
        split_target = [x for product_variant in build_target.split("-")
                        for x in product_variant.split("_")]
        if "darwin" in split_target or "mac" in split_target:
            os_name = "darwin"
        else:
            os_name = "linux"
        if "aarch64" in split_target:
            os_name = os_name + "_aarch64"
        return _EMULATOR_ZIP_NAME_FORMAT % {"os": os_name,
                                            "build_id": build_id}

    @staticmethod
    def _RetrieveArtifact(download_dir, build_api, build_target, build_id,
                          resource_id):
        """Retrieve an artifact from cache or Android Build API.

        Args:
            download_dir: The cache directory.
            build_api: An AndroidBuildClient object.
            build_target: A string, the build target of the artifact. e.g.,
                          "sdk_phone_x86_64-userdebug".
            build_id: A string, the build ID of the artifact.
            resource_id: A string, the name of the artifact. e.g.,
                         "sdk-repo-linux-system-images-123456.zip".

        Returns:
            The path to the artifact in download_dir.
        """
        local_path = os.path.join(download_dir, build_id, build_target,
                                  resource_id)
        if os.path.isfile(local_path):
            logger.info("Skip downloading existing artifact: %s", local_path)
            return local_path

        complete = False
        try:
            os.makedirs(os.path.dirname(local_path), exist_ok=True)
            build_api.DownloadArtifact(build_target, build_id, resource_id,
                                       local_path, build_api.LATEST)
            complete = True
        finally:
            if not complete and os.path.isfile(local_path):
                os.remove(local_path)
        return local_path

    def _RetrieveEmulatorBuildID(self, download_dir, build_api, build_target,
                                 build_id):
        """Retrieve required emulator build from a goldfish image build."""
        emulator_info_path = self._RetrieveArtifact(download_dir, build_api,
                                                    build_target, build_id,
                                                    _EMULATOR_INFO_NAME)
        with open(emulator_info_path, 'r') as emulator_info:
            for line in emulator_info:
                match = _EMULATOR_VERSION_PATTERN.fullmatch(line.strip())
                if match:
                    logger.info("Found emulator build ID: %s", line)
                    return match.group("build_id")
        return None

    @utils.TimeExecute(function_description="Download Android Build artifacts")
    def _RetrieveArtifacts(self, download_dir):
        """Retrieve goldfish images and tools from cache or Android Build API.

        Args:
            download_dir: The cache directory.

        Returns:
            An object of ArtifactPaths.

        Raises:
            errors.GetRemoteImageError: Fails to download rom images.
        """
        credentials = auth.CreateCredentials(self._avd_spec.cfg)
        build_api = android_build_client.AndroidBuildClient(credentials)
        # Device images.
        build_id = self._avd_spec.remote_image.get(constants.BUILD_ID)
        build_target = self._avd_spec.remote_image.get(constants.BUILD_TARGET)
        image_zip_name_format = (_EXTRA_IMAGE_ZIP_NAME_FORMAT if
                                 self._ShouldMixDiskImage() else
                                 _SDK_REPO_IMAGE_ZIP_NAME_FORMAT)
        image_zip_path = self._RetrieveArtifact(
            download_dir, build_api, build_target, build_id,
            image_zip_name_format % {"build_id": build_id})

        # Emulator tools.
        emu_build_id = self._avd_spec.emulator_build_id
        if not emu_build_id:
            emu_build_id = self._RetrieveEmulatorBuildID(
                download_dir, build_api, build_target, build_id)
            if not emu_build_id:
                raise errors.GetRemoteImageError(
                    "No emulator build ID in command line or "
                    "emulator-info.txt.")

        emu_build_target = (self._avd_spec.emulator_build_target or
                            self._avd_spec.cfg.emulator_build_target)
        emu_zip_name = self._InferEmulatorZipName(emu_build_target,
                                                  emu_build_id)
        emu_zip_path = self._RetrieveArtifact(download_dir, build_api,
                                              emu_build_target, emu_build_id,
                                              emu_zip_name)

        system_image_zip_path = self._RetrieveSystemImageZip(
            download_dir, build_api)
        boot_image_path = self._RetrieveBootImage(download_dir, build_api)
        # Retrieve OTA tools from the goldfish build which contains
        # mk_combined_img.
        ota_tools_zip_path = (
            self._RetrieveArtifact(download_dir, build_api, build_target,
                                   build_id, _OTA_TOOLS_ZIP_NAME)
            if system_image_zip_path or boot_image_path else None)

        return ArtifactPaths(image_zip_path, emu_zip_path,
                             ota_tools_zip_path, system_image_zip_path,
                             boot_image_path)

    def _RetrieveSystemImageZip(self, download_dir, build_api):
        """Retrieve system image zip if system build info is not empty.

        Args:
            download_dir: The download cache directory.
            build_api: An AndroidBuildClient object.

        Returns:
            The path to the system image zip in download_dir.
            None if the system build info is empty.
        """
        build_id = self._avd_spec.system_build_info.get(constants.BUILD_ID)
        build_target = self._avd_spec.system_build_info.get(
            constants.BUILD_TARGET)
        if build_id and build_target:
            image_zip_name = _IMAGE_ZIP_NAME_FORMAT % {
                "build_target": build_target.split("-", 1)[0],
                "build_id": build_id}
            return self._RetrieveArtifact(
                download_dir, build_api, build_target, build_id,
                image_zip_name)
        return None

    def _RetrieveBootImage(self, download_dir, build_api):
        """Retrieve boot image if kernel build info is not empty.

        Args:
            download_dir: The download cache directory.
            build_api: An AndroidBuildClient object.

        Returns:
            The path to the boot image in download_dir.
            None if the kernel build info is empty.
        """
        build_id = self._avd_spec.kernel_build_info.get(constants.BUILD_ID)
        build_target = self._avd_spec.kernel_build_info.get(
            constants.BUILD_TARGET)
        image_name = self._avd_spec.kernel_build_info.get(
            constants.BUILD_ARTIFACT)
        if build_id and build_target and image_name:
            return self._RetrieveArtifact(
                download_dir, build_api, build_target, build_id, image_name)
        return None

    @staticmethod
    def _GetSubdirNameInZip(zip_path):
        """Get the name of the only subdirectory in a zip.

        In an SDK repository zip, the images and the binaries are located in a
        subdirectory. This class needs to find out the subdirectory name in
        order to construct the remote commands.

        For example, in sdk-repo-*-emulator-*.zip, all files are in
        "emulator/". The zip entries are:

        emulator/NOTICE.txt
        emulator/emulator
        emulator/lib64/libc++.so
        ...

        This method scans the entries and returns the common subdirectory name.
        """
        sep = "/"
        with zipfile.ZipFile(zip_path, 'r') as zip_obj:
            entries = zip_obj.namelist()
            if len(entries) > 0 and sep in entries[0]:
                subdir = entries[0].split(sep, 1)[0]
                if all(e.startswith(subdir + sep) for e in entries):
                    return subdir
            logger.warning("Expect one subdirectory in %s. Actual entries: %s",
                           zip_path, " ".join(entries))
            return ""

    def _UploadArtifacts(self, artifacts_paths):
        """Process and upload all images and tools to the remote host.

        Args:
            artifact_paths: An object of ArtifactPaths.

        Returns:
            An object of RemotePaths.
        """
        remote_emulator_dir, remote_image_dir = self._UploadDeviceImages(
            artifacts_paths.emulator_zip, artifacts_paths.image_zip)

        remote_kernel_path = None
        remote_ramdisk_path = None

        if artifacts_paths.boot_image or artifacts_paths.system_image_zip:
            with tempfile.TemporaryDirectory("host_gf") as temp_dir:
                ota_tools_dir = os.path.join(temp_dir, "ota_tools")
                logger.debug("Unzip %s.", artifacts_paths.ota_tools_zip)
                with zipfile.ZipFile(artifacts_paths.ota_tools_zip,
                                     "r") as zip_file:
                    zip_file.extractall(ota_tools_dir)
                ota = ota_tools.OtaTools(ota_tools_dir)

                image_dir = os.path.join(temp_dir, "images")
                logger.debug("Unzip %s.", artifacts_paths.image_zip)
                with zipfile.ZipFile(artifacts_paths.image_zip,
                                     "r") as zip_file:
                    zip_file.extractall(image_dir)
                image_dir = os.path.join(
                    image_dir,
                    self._GetSubdirNameInZip(artifacts_paths.image_zip))

                if artifacts_paths.system_image_zip:
                    self._MixAndUploadDiskImage(
                        remote_image_dir, image_dir,
                        artifacts_paths.system_image_zip, ota)

                if artifacts_paths.boot_image:
                    remote_kernel_path, remote_ramdisk_path = (
                        self._MixAndUploadKernelImages(
                            image_dir, artifacts_paths.boot_image, ota))

        return RemotePaths(remote_image_dir, remote_emulator_dir,
                           remote_kernel_path, remote_ramdisk_path)

    def _ShouldMixDiskImage(self):
        """Determines whether a mixed disk image is required.

        This method checks whether the user requires to replace an image that
        is part of the disk image. Acloud supports replacing system and kernel
        images. Only the system is installed on the disk.

        Returns:
            Boolean, whether a mixed disk image is required.
        """
        return (self._avd_spec.system_build_info.get(constants.BUILD_ID) and
                self._avd_spec.system_build_info.get(constants.BUILD_TARGET))

    @utils.TimeExecute(
        function_description="Processing and uploading tools and images")
    def _UploadDeviceImages(self, emulator_zip_path, image_zip_path):
        """Upload artifacts to remote host and extract them.

        Args:
            emulator_zip_path: The local path to the emulator zip.
            image_zip_path: The local path to the image zip.

        Returns:
            The remote paths to the extracted emulator tools and images.
        """
        self._ssh.Run("mkdir -p " +
                      " ".join([_REMOTE_INSTANCE_DIR, _REMOTE_ARTIFACT_DIR,
                                _REMOTE_EMULATOR_DIR, _REMOTE_IMAGE_DIR]))
        self._ssh.ScpPushFile(emulator_zip_path, _REMOTE_ARTIFACT_DIR)
        self._ssh.ScpPushFile(image_zip_path, _REMOTE_ARTIFACT_DIR)

        self._ssh.Run("unzip -d %s %s" % (
            _REMOTE_EMULATOR_DIR,
            remote_path.join(_REMOTE_ARTIFACT_DIR,
                             os.path.basename(emulator_zip_path))))
        self._ssh.Run("unzip -d %s %s" % (
            _REMOTE_IMAGE_DIR,
            remote_path.join(_REMOTE_ARTIFACT_DIR,
                             os.path.basename(image_zip_path))))
        remote_emulator_subdir = remote_path.join(
            _REMOTE_EMULATOR_DIR, self._GetSubdirNameInZip(emulator_zip_path))
        remote_image_subdir = remote_path.join(
            _REMOTE_IMAGE_DIR, self._GetSubdirNameInZip(image_zip_path))
        # TODO(b/141898893): In Android build environment, emulator gets build
        # information from $ANDROID_PRODUCT_OUT/system/build.prop.
        # If image_dir is an extacted SDK repository, the file is at
        # image_dir/build.prop. Acloud copies it to
        # image_dir/system/build.prop.
        src_path = remote_path.join(remote_image_subdir, "build.prop")
        dst_path = remote_path.join(remote_image_subdir, "system",
                                    "build.prop")
        self._ssh.Run("'test -f %(dst)s || "
                      "{ mkdir -p %(dst_dir)s && cp %(src)s %(dst)s ; }'" %
                      {"src": src_path,
                       "dst": dst_path,
                       "dst_dir": remote_path.dirname(dst_path)})
        return remote_emulator_subdir, remote_image_subdir

    def _MixAndUploadDiskImage(self, remote_image_dir, image_dir,
                               system_image_zip_path, ota):
        """Mix emulator images with a system image and upload them.

        Args:
            remote_image_dir: The remote directory where the mixed disk image
                              is uploaded.
            image_dir: The directory containing emulator images.
            system_image_zip_path: The path to the zip containing the system
                                   image.
            ota: An instance of ota_tools.OtaTools.

        Returns:
            The remote path to the mixed disk image.
        """
        with tempfile.TemporaryDirectory("host_gf_disk") as temp_dir:
            logger.debug("Unzip %s.", system_image_zip_path)
            with zipfile.ZipFile(system_image_zip_path, "r") as zip_file:
                zip_file.extract(_SYSTEM_IMAGE_NAME, temp_dir)

            mixed_image = goldfish_utils.MixWithSystemImage(
                os.path.join(temp_dir, "mix_disk"),
                image_dir,
                os.path.join(temp_dir, _SYSTEM_IMAGE_NAME),
                ota)

            # TODO(b/142228085): Use -system instead of overwriting the file.
            remote_disk_image_path = os.path.join(
                remote_image_dir, goldfish_utils.SYSTEM_QEMU_IMAGE_NAME)
            self._ssh.ScpPushFile(mixed_image, remote_disk_image_path)

        return remote_disk_image_path

    def _MixAndUploadKernelImages(self, image_dir, boot_image_path, ota):
        """Mix emulator kernel images with a boot image and upload them.

        Args:
            image_dir: The directory containing emulator images.
            boot_image_path: The path to the boot image.
            ota: An instance of ota_tools.OtaTools.

        Returns:
            The remote paths to the kernel image and the ramdisk image.
        """
        with tempfile.TemporaryDirectory("host_gf_kernel") as temp_dir:
            kernel_path, ramdisk_path = goldfish_utils.MixWithBootImage(
                temp_dir, image_dir, boot_image_path, ota)

            self._ssh.ScpPushFile(kernel_path, _REMOTE_KERNEL_PATH)
            self._ssh.ScpPushFile(ramdisk_path, _REMOTE_RAMDISK_PATH)

        return _REMOTE_KERNEL_PATH, _REMOTE_RAMDISK_PATH

    @utils.TimeExecute(function_description="Start emulator")
    def _StartEmulator(self, remote_paths):
        """Start emulator command as a remote background process.

        Args:
            remote_emulator_dir: The emulator tool directory on remote host.
            remote_image_dir: The image directory on remote host.
        """
        remote_emulator_bin_path = remote_path.join(
            remote_paths.emulator_dir, _EMULATOR_BIN_NAME)
        remote_bin_paths = [
            remote_path.join(remote_paths.emulator_dir, name) for
            name in _EMULATOR_BIN_DIR_NAMES]
        remote_bin_paths.append(remote_emulator_bin_path)
        self._ssh.Run("chmod -R +x %s" % " ".join(remote_bin_paths))

        env = {constants.ENV_ANDROID_PRODUCT_OUT: remote_paths.image_dir,
               constants.ENV_ANDROID_TMP: _REMOTE_INSTANCE_DIR,
               constants.ENV_ANDROID_BUILD_TOP: _REMOTE_INSTANCE_DIR}
        adb_port = _EMULATOR_DEFAULT_CONSOLE_PORT + 1
        cmd = ["nohup", remote_emulator_bin_path, "-verbose", "-show-kernel",
               "-read-only", "-ports",
               str(_EMULATOR_DEFAULT_CONSOLE_PORT) + "," + str(adb_port),
               "-no-window",
               "-logcat-output", _REMOTE_LOGCAT_PATH,
               "-stdouterr-file", _REMOTE_STDOUTERR_PATH]

        if remote_paths.kernel:
            cmd.extend(("-kernel", remote_paths.kernel))

        if remote_paths.ramdisk:
            cmd.extend(("-ramdisk", remote_paths.ramdisk))

        cmd.extend(goldfish_utils.ConvertAvdSpecToArgs(self._avd_spec))

        # Unlock the device so that the disabled vbmeta takes effect.
        # These arguments must be at the end of the command line.
        if self._ShouldMixDiskImage():
            cmd.extend(("-qemu", "-append",
                        "androidboot.verifiedbootstate=orange"))

        # Emulator doesn't create -stdouterr-file automatically.
        self._ssh.Run(
            "'export {env} ; touch {stdouterr} ; {cmd} &'".format(
                env=" ".join(k + "=~/" + v for k, v in env.items()),
                stdouterr=_REMOTE_STDOUTERR_PATH,
                cmd=" ".join(cmd)))

    @utils.TimeExecute(function_description="Wait for emulator")
    def _WaitForEmulator(self):
        """Wait for remote emulator console to be active.

        Raises:
            errors.DeviceBootError if connection fails.
            errors.DeviceBootTimeoutError if boot times out.
        """
        ip_addr = self._avd_spec.remote_host
        console_port = _EMULATOR_DEFAULT_CONSOLE_PORT
        poll_timeout_secs = (self._avd_spec.boot_timeout_secs or
                             _DEFAULT_BOOT_TIMEOUT_SECS)
        try:
            with emulator_console.RemoteEmulatorConsole(
                    ip_addr,
                    console_port,
                    self._ssh_user,
                    self._ssh_private_key_path,
                    self._ssh_extra_args) as console:
                utils.PollAndWait(
                    func=lambda: (True if console.Ping() else
                                  console.Reconnect()),
                    expected_return=True,
                    timeout_exception=errors.DeviceBootTimeoutError,
                    timeout_secs=poll_timeout_secs,
                    sleep_interval_secs=5)
        except errors.DeviceConnectionError as e:
            raise errors.DeviceBootError("Fail to connect to %s:%d." %
                                         (ip_addr, console_port)) from e

    def GetBuildInfoDict(self):
        """Get build info dictionary.

        Returns:
            A build info dictionary.
        """
        build_info_dict = {key: val for key, val in
                           self._avd_spec.remote_image.items() if val}
        return build_info_dict

    def GetFailures(self):
        """Get Failures from all devices.

        Returns:
            A dictionary the contains all the failures.
            The key is the name of the instance that fails to boot,
            and the value is an errors.DeviceBootError object.
        """
        return self._failures

    def GetLogs(self):
        """Get log files of created instances.

        Returns:
            A dictionary that maps instance names to lists of report.LogFile.
        """
        return self._logs
