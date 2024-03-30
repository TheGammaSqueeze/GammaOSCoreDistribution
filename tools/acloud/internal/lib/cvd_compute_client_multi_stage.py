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
"""A client that manages Cuttlefish Virtual Device on compute engine.

** CvdComputeClient **

CvdComputeClient derives from AndroidComputeClient. It manges a google
compute engine project that is setup for running Cuttlefish Virtual Devices.
It knows how to create a host instance from Cuttlefish Stable Host Image, fetch
Android build, and start Android within the host instance.

** Class hierarchy **

  base_cloud_client.BaseCloudApiClient
                ^
                |
       gcompute_client.ComputeClient
                ^
                |
       android_compute_client.AndroidComputeClient
                ^
                |
       cvd_compute_client_multi_stage.CvdComputeClient

"""

import logging
import os
import re
import subprocess
import tempfile
import time

from acloud import errors
from acloud.internal import constants
from acloud.internal.lib import android_build_client
from acloud.internal.lib import android_compute_client
from acloud.internal.lib import cvd_utils
from acloud.internal.lib import gcompute_client
from acloud.internal.lib import utils
from acloud.internal.lib.ssh import Ssh
from acloud.setup import mkcert


logger = logging.getLogger(__name__)

_CONFIG_ARG = "-config"
_DECOMPRESS_KERNEL_ARG = "-decompress_kernel=true"
_AGREEMENT_PROMPT_ARG = "-report_anonymous_usage_stats=y"
_UNDEFOK_ARG = "-undefok=report_anonymous_usage_stats,config"
_NUM_AVDS_ARG = "-num_instances=%(num_AVD)s"
# Connect the OpenWrt device via console file.
_ENABLE_CONSOLE_ARG = "-console=true"
_DEFAULT_BRANCH = "aosp-master"
_FETCHER_BUILD_TARGET = "aosp_cf_x86_64_phone-userdebug"
_FETCHER_NAME = "fetch_cvd"
# Time info to write in report.
_FETCH_ARTIFACT = "fetch_artifact_time"
_GCE_CREATE = "gce_create_time"
_LAUNCH_CVD = "launch_cvd_time"
# WebRTC args for launching AVD
_START_WEBRTC = "--start_webrtc"
_WEBRTC_ID = "--webrtc_device_id=%(instance)s"
_VM_MANAGER = "--vm_manager=crosvm"
_WEBRTC_ARGS = [_START_WEBRTC, _VM_MANAGER]
_VNC_ARGS = ["--start_vnc_server=true"]
_NO_RETRY = 0
# Launch cvd command for acloud report
_LAUNCH_CVD_COMMAND = "launch_cvd_command"
_CONFIG_RE = re.compile(r"^config=(?P<config>.+)")
_TRUST_REMOTE_INSTANCE_COMMAND = (
    f"\"sudo cp -p ~/{constants.WEBRTC_CERTS_PATH}/{constants.SSL_CA_NAME}.pem "
    f"{constants.SSL_TRUST_CA_DIR}/{constants.SSL_CA_NAME}.crt;"
    "sudo update-ca-certificates;\"")
# Remote host instance name
_HOST_INSTANCE_NAME_FORMAT = (constants.INSTANCE_TYPE_HOST +
                              "-%(ip_addr)s-%(build_id)s-%(build_target)s")
_HOST_INSTANCE_NAME_PATTERN = re.compile(constants.INSTANCE_TYPE_HOST +
                                         r"-(?P<ip_addr>[\d.]+)-.+")


class CvdComputeClient(android_compute_client.AndroidComputeClient):
    """Client that manages Android Virtual Device."""

    DATA_POLICY_CREATE_IF_MISSING = "create_if_missing"
    # Data policy to customize disk size.
    DATA_POLICY_ALWAYS_CREATE = "always_create"

    def __init__(self,
                 acloud_config,
                 oauth2_credentials,
                 boot_timeout_secs=None,
                 ins_timeout_secs=None,
                 report_internal_ip=None,
                 gpu=None):
        """Initialize.

        Args:
            acloud_config: An AcloudConfig object.
            oauth2_credentials: An oauth2client.OAuth2Credentials instance.
            boot_timeout_secs: Integer, the maximum time to wait for the AVD
                               to boot up.
            ins_timeout_secs: Integer, the maximum time to wait for the
                              instance ready.
            report_internal_ip: Boolean to report the internal ip instead of
                                external ip.
            gpu: String, GPU to attach to the device.
        """
        super().__init__(acloud_config, oauth2_credentials)

        self._fetch_cvd_version = acloud_config.fetch_cvd_version
        self._build_api = (
            android_build_client.AndroidBuildClient(oauth2_credentials))
        self._ssh_private_key_path = acloud_config.ssh_private_key_path
        self._boot_timeout_secs = boot_timeout_secs
        self._ins_timeout_secs = ins_timeout_secs
        self._report_internal_ip = report_internal_ip
        self._gpu = gpu
        # Store all failures result when creating one or multiple instances.
        # This attribute is only used by the deprecated create_cf command.
        self._all_failures = {}
        self._extra_args_ssh_tunnel = acloud_config.extra_args_ssh_tunnel
        self._ssh = None
        self._ip = None
        self._user = constants.GCE_USER
        self._openwrt = None
        self._stage = constants.STAGE_INIT
        self._execution_time = {_FETCH_ARTIFACT: 0, _GCE_CREATE: 0, _LAUNCH_CVD: 0}

    @staticmethod
    def FormatRemoteHostInstanceName(ip_addr, build_id, build_target):
        """Convert an IP address and build info to an instance name.

        Args:
            ip_addr: String, the IP address of the remote host.
            build_id: String, the build id.
            build_target: String, the build target, e.g., aosp_cf_x86_64_phone.

        Return:
            String, the instance name.
        """
        return _HOST_INSTANCE_NAME_FORMAT % {
            "ip_addr": ip_addr,
            "build_id": build_id,
            "build_target": build_target}

    @staticmethod
    def ParseRemoteHostAddress(instance_name):
        """Parse IP address from a remote host instance name.

        Args:
            instance_name: String, the instance name.

        Returns:
            The IP address as a string.
            None if the name does not represent a remote host instance.
        """
        match = _HOST_INSTANCE_NAME_PATTERN.fullmatch(instance_name)
        return match.group("ip_addr") if match else None

    def InitRemoteHost(self, ssh, ip, user):
        """Init remote host.

        Check if we can ssh to the remote host, stop any cf instances running
        on it, and remove existing files.

        Args:
            ssh: Ssh object.
            ip: namedtuple (internal, external) that holds IP address of the
                remote host, e.g. "external:140.110.20.1, internal:10.0.0.1"
            user: String of user log in to the instance.
        """
        self.SetStage(constants.STAGE_SSH_CONNECT)
        self._ssh = ssh
        self._ip = ip
        self._user = user
        self._ssh.WaitForSsh(timeout=self._ins_timeout_secs)
        cvd_utils.CleanUpRemoteCvd(self._ssh, raise_error=False)

    # TODO(171376263): Refactor CreateInstance() args with avd_spec.
    # pylint: disable=arguments-differ,too-many-locals,broad-except
    def CreateInstance(self, instance, image_name, image_project,
                       build_target=None, branch=None, build_id=None,
                       kernel_branch=None, kernel_build_id=None,
                       kernel_build_target=None, blank_data_disk_size_gb=None,
                       avd_spec=None, extra_scopes=None,
                       system_build_target=None, system_branch=None,
                       system_build_id=None, bootloader_build_target=None,
                       bootloader_branch=None, bootloader_build_id=None,
                       ota_build_target=None, ota_branch=None,
                       ota_build_id=None):

        """Create/Reuse a single configured cuttlefish device.
        1. Prepare GCE instance.
           Create a new instnace or get IP address for reusing the specific instance.
        2. Put fetch_cvd on the instance.
        3. Invoke fetch_cvd to fetch and run the instance.

        Args:
            instance: instance name.
            image_name: A string, the name of the GCE image.
            image_project: A string, name of the project where the image lives.
                           Assume the default project if None.
            build_target: Target name, e.g. "aosp_cf_x86_64_phone-userdebug"
            branch: Branch name, e.g. "aosp-master"
            build_id: Build id, a string, e.g. "2263051", "P2804227"
            kernel_branch: Kernel branch name, e.g. "kernel-common-android-4.14"
            kernel_build_id: Kernel build id, a string, e.g. "223051", "P280427"
            kernel_build_target: String, Kernel build target name.
            blank_data_disk_size_gb: Size of the blank data disk in GB.
            avd_spec: An AVDSpec instance.
            extra_scopes: A list of extra scopes to be passed to the instance.
            system_build_target: String of the system image target name,
                                 e.g. "cf_x86_phone-userdebug"
            system_branch: String of the system image branch name.
            system_build_id: String of the system image build id.
            bootloader_build_target: String of the bootloader target name.
            bootloader_branch: String of the bootloader branch name.
            bootloader_build_id: String of the bootloader build id.
            ota_build_target: String of the otatools target name.
            ota_branch: String of the otatools branch name.
            ota_build_id: String of the otatools build id.

        Returns:
            A string, representing instance name.
        """

        # A blank data disk would be created on the host. Make sure the size of
        # the boot disk is large enough to hold it.
        boot_disk_size_gb = (
            int(self.GetImage(image_name, image_project)["diskSizeGb"]) +
            blank_data_disk_size_gb)

        if avd_spec and avd_spec.instance_name_to_reuse:
            self._ip = self._ReusingGceInstance(avd_spec)
        else:
            self._VerifyZoneByQuota()
            self._ip = self._CreateGceInstance(instance, image_name, image_project,
                                               extra_scopes, boot_disk_size_gb,
                                               avd_spec)
        self._ssh = Ssh(ip=self._ip,
                        user=constants.GCE_USER,
                        ssh_private_key_path=self._ssh_private_key_path,
                        extra_args_ssh_tunnel=self._extra_args_ssh_tunnel,
                        report_internal_ip=self._report_internal_ip)
        try:
            self.SetStage(constants.STAGE_SSH_CONNECT)
            self._ssh.WaitForSsh(timeout=self._ins_timeout_secs)
            if avd_spec:
                if avd_spec.instance_name_to_reuse:
                    cvd_utils.CleanUpRemoteCvd(self._ssh, raise_error=False)
                return instance

            # TODO: Remove following code after create_cf deprecated.
            self.UpdateFetchCvd()

            self.FetchBuild(build_id, branch, build_target, system_build_id,
                            system_branch, system_build_target, kernel_build_id,
                            kernel_branch, kernel_build_target, bootloader_build_id,
                            bootloader_branch, bootloader_build_target,
                            ota_build_id, ota_branch, ota_build_target)
            failures = self.LaunchCvd(
                instance,
                blank_data_disk_size_gb=blank_data_disk_size_gb,
                boot_timeout_secs=self._boot_timeout_secs,
                extra_args=[])
            self._all_failures.update(failures)
            return instance
        except Exception as e:
            self._all_failures[instance] = e
            return instance

    def _GetConfigFromAndroidInfo(self):
        """Get config value from android-info.txt.

        The config in android-info.txt would like "config=phone".

        Returns:
            Strings of config value.
        """
        android_info = self._ssh.GetCmdOutput(
            "cat %s" % constants.ANDROID_INFO_FILE)
        logger.debug("Android info: %s", android_info)
        config_match = _CONFIG_RE.match(android_info)
        if config_match:
            return config_match.group("config")
        return None

    # pylint: disable=too-many-branches
    def _GetLaunchCvdArgs(self, avd_spec=None, blank_data_disk_size_gb=None,
                          decompress_kernel=None, instance=None):
        """Get launch_cvd args.

        Args:
            avd_spec: An AVDSpec instance.
            blank_data_disk_size_gb: Size of the blank data disk in GB.
            decompress_kernel: Boolean, if true decompress the kernel.
            instance: String, instance name.

        Returns:
            String, args of launch_cvd.
        """
        launch_cvd_args = []
        if blank_data_disk_size_gb and blank_data_disk_size_gb > 0:
            # Policy 'create_if_missing' would create a blank userdata disk if
            # missing. If already exist, reuse the disk.
            launch_cvd_args.append(
                "-data_policy=" + self.DATA_POLICY_CREATE_IF_MISSING)
            launch_cvd_args.append(
                "-blank_data_image_mb=%d" % (blank_data_disk_size_gb * 1024))
        if avd_spec:
            config = self._GetConfigFromAndroidInfo()
            if config:
                launch_cvd_args.append("-config=%s" % config)
            if avd_spec.hw_customize or not config:
                launch_cvd_args.append(
                    "-x_res=" + avd_spec.hw_property[constants.HW_X_RES])
                launch_cvd_args.append(
                    "-y_res=" + avd_spec.hw_property[constants.HW_Y_RES])
                launch_cvd_args.append(
                    "-dpi=" + avd_spec.hw_property[constants.HW_ALIAS_DPI])
                if constants.HW_ALIAS_DISK in avd_spec.hw_property:
                    launch_cvd_args.append(
                        "-data_policy=" + self.DATA_POLICY_ALWAYS_CREATE)
                    launch_cvd_args.append(
                        "-blank_data_image_mb="
                        + avd_spec.hw_property[constants.HW_ALIAS_DISK])
                if constants.HW_ALIAS_CPUS in avd_spec.hw_property:
                    launch_cvd_args.append(
                        "-cpus=%s" % avd_spec.hw_property[constants.HW_ALIAS_CPUS])
                if constants.HW_ALIAS_MEMORY in avd_spec.hw_property:
                    launch_cvd_args.append(
                        "-memory_mb=%s" % avd_spec.hw_property[constants.HW_ALIAS_MEMORY])
            if avd_spec.connect_webrtc:
                launch_cvd_args.extend(_WEBRTC_ARGS)
                launch_cvd_args.append(_WEBRTC_ID % {"instance": instance})
            if avd_spec.connect_vnc:
                launch_cvd_args.extend(_VNC_ARGS)
            if avd_spec.openwrt:
                launch_cvd_args.append(_ENABLE_CONSOLE_ARG)
            if avd_spec.num_avds_per_instance > 1:
                launch_cvd_args.append(
                    _NUM_AVDS_ARG % {"num_AVD": avd_spec.num_avds_per_instance})
            if avd_spec.base_instance_num:
                launch_cvd_args.append(
                    "--base-instance-num=%s" % avd_spec.base_instance_num)
            if avd_spec.launch_args:
                launch_cvd_args.append(avd_spec.launch_args)
        else:
            resolution = self._resolution.split("x")
            launch_cvd_args.append("-x_res=" + resolution[0])
            launch_cvd_args.append("-y_res=" + resolution[1])
            launch_cvd_args.append("-dpi=" + resolution[3])

        if not avd_spec and self._launch_args:
            launch_cvd_args.append(self._launch_args)

        if decompress_kernel:
            launch_cvd_args.append(_DECOMPRESS_KERNEL_ARG)

        launch_cvd_args.append(_UNDEFOK_ARG)
        launch_cvd_args.append(_AGREEMENT_PROMPT_ARG)
        return launch_cvd_args

    @utils.TimeExecute(function_description="Launching AVD(s) and waiting for boot up",
                       result_evaluator=utils.BootEvaluator)
    def LaunchCvd(self, instance, avd_spec=None,
                  blank_data_disk_size_gb=None,
                  decompress_kernel=None,
                  boot_timeout_secs=None,
                  extra_args=()):
        """Launch CVD.

        Launch AVD with launch_cvd. If the process is failed, acloud would show
        error messages and auto download log files from remote instance.

        Args:
            instance: String, instance name.
            avd_spec: An AVDSpec instance.
            blank_data_disk_size_gb: Size of the blank data disk in GB.
            decompress_kernel: Boolean, if true decompress the kernel.
            boot_timeout_secs: Integer, the maximum time to wait for the
                               command to respond.
            extra_args: Collection of strings, the extra arguments generated by
                        acloud. e.g., remote image paths.

        Returns:
           dict of faliures, return this dict for BootEvaluator to handle
           LaunchCvd success or fail messages.
        """
        self.SetStage(constants.STAGE_BOOT_UP)
        timestart = time.time()
        error_msg = ""
        launch_cvd_args = list(extra_args)
        launch_cvd_args.extend(
            self._GetLaunchCvdArgs(avd_spec, blank_data_disk_size_gb,
                                   decompress_kernel, instance))
        boot_timeout_secs = self._GetBootTimeout(
            boot_timeout_secs or constants.DEFAULT_CF_BOOT_TIMEOUT)
        ssh_command = "./bin/launch_cvd -daemon " + " ".join(launch_cvd_args)
        try:
            if avd_spec and avd_spec.base_instance_num:
                self.ExtendReportData(constants.BASE_INSTANCE_NUM, avd_spec.base_instance_num)
            self.ExtendReportData(_LAUNCH_CVD_COMMAND, ssh_command)
            self._ssh.Run(ssh_command, boot_timeout_secs, retry=_NO_RETRY)
            self._UpdateOpenWrtStatus(avd_spec)
        except (subprocess.CalledProcessError, errors.DeviceConnectionError,
                errors.LaunchCVDFail) as e:
            error_msg = ("Device %s did not finish on boot within timeout (%s secs)"
                         % (instance, boot_timeout_secs))
            if constants.ERROR_MSG_VNC_NOT_SUPPORT in str(e):
                error_msg = (
                    "VNC is not supported in the current build. Please try WebRTC such "
                    "as '$acloud create' or '$acloud create --autoconnect webrtc'")
            if constants.ERROR_MSG_WEBRTC_NOT_SUPPORT in str(e):
                error_msg = (
                    "WEBRTC is not supported in the current build. Please try VNC such "
                    "as '$acloud create --autoconnect vnc'")
            utils.PrintColorString(str(e), utils.TextColors.FAIL)

        self._execution_time[_LAUNCH_CVD] = round(time.time() - timestart, 2)
        return {instance: error_msg} if error_msg else {}

    def _GetBootTimeout(self, timeout_secs):
        """Get boot timeout.

        Timeout settings includes download artifacts and boot up.

        Args:
            timeout_secs: integer of timeout value.

        Returns:
            The timeout values for device boots up.
        """
        boot_timeout_secs = timeout_secs - self._execution_time[_FETCH_ARTIFACT]
        logger.debug("Timeout for boot: %s secs", boot_timeout_secs)
        return boot_timeout_secs

    @utils.TimeExecute(function_description="Reusing GCE instance")
    def _ReusingGceInstance(self, avd_spec):
        """Reusing a cuttlefish existing instance.

        Args:
            avd_spec: An AVDSpec instance.

        Returns:
            ssh.IP object, that stores internal and external ip of the instance.
        """
        gcompute_client.ComputeClient.AddSshRsaInstanceMetadata(
            self, constants.GCE_USER, avd_spec.cfg.ssh_public_key_path,
            avd_spec.instance_name_to_reuse)
        ip = gcompute_client.ComputeClient.GetInstanceIP(
            self, instance=avd_spec.instance_name_to_reuse, zone=self._zone)

        return ip

    @utils.TimeExecute(function_description="Creating GCE instance")
    def _CreateGceInstance(self, instance, image_name, image_project,
                           extra_scopes, boot_disk_size_gb, avd_spec):
        """Create a single configured cuttlefish device.

        Override method from parent class.
        Args:
            instance: String, instance name.
            image_name: String, the name of the GCE image.
            image_project: String, the name of the project where the image.
            extra_scopes: A list of extra scopes to be passed to the instance.
            boot_disk_size_gb: Integer, size of the boot disk in GB.
            avd_spec: An AVDSpec instance.

        Returns:
            ssh.IP object, that stores internal and external ip of the instance.
        """
        self.SetStage(constants.STAGE_GCE)
        timestart = time.time()
        metadata = self._metadata.copy()

        if avd_spec:
            metadata[constants.INS_KEY_AVD_TYPE] = avd_spec.avd_type
            metadata[constants.INS_KEY_AVD_FLAVOR] = avd_spec.flavor
            metadata[constants.INS_KEY_DISPLAY] = ("%sx%s (%s)" % (
                avd_spec.hw_property[constants.HW_X_RES],
                avd_spec.hw_property[constants.HW_Y_RES],
                avd_spec.hw_property[constants.HW_ALIAS_DPI]))
            if avd_spec.gce_metadata:
                for key, value in avd_spec.gce_metadata.items():
                    metadata[key] = value
            # Record webrtc port, it will be removed if cvd support to show it.
            if avd_spec.connect_webrtc:
                metadata[constants.INS_KEY_WEBRTC_PORT] = constants.WEBRTC_LOCAL_PORT

        disk_args = self._GetDiskArgs(
            instance, image_name, image_project, boot_disk_size_gb)
        disable_external_ip = avd_spec.disable_external_ip if avd_spec else False
        gcompute_client.ComputeClient.CreateInstance(
            self,
            instance=instance,
            image_name=image_name,
            image_project=image_project,
            disk_args=disk_args,
            metadata=metadata,
            machine_type=self._machine_type,
            network=self._network,
            zone=self._zone,
            gpu=self._gpu,
            disk_type=avd_spec.disk_type if avd_spec else None,
            extra_scopes=extra_scopes,
            disable_external_ip=disable_external_ip)
        ip = gcompute_client.ComputeClient.GetInstanceIP(
            self, instance=instance, zone=self._zone)
        logger.debug("'instance_ip': %s", ip.internal
                     if self._report_internal_ip else ip.external)

        self._execution_time[_GCE_CREATE] = round(time.time() - timestart, 2)
        return ip

    @utils.TimeExecute(function_description="Uploading build fetcher to instance")
    def UpdateFetchCvd(self):
        """Download fetch_cvd from the Build API, and upload it to a remote instance.

        The version of fetch_cvd to use is retrieved from the configuration file. Once fetch_cvd
        is on the instance, future commands can use it to download relevant Cuttlefish files from
        the Build API on the instance itself.
        """
        self.SetStage(constants.STAGE_ARTIFACT)
        download_dir = tempfile.mkdtemp()
        download_target = os.path.join(download_dir, _FETCHER_NAME)
        self._build_api.DownloadFetchcvd(download_target, self._fetch_cvd_version)
        self._ssh.ScpPushFile(src_file=download_target, dst_file=_FETCHER_NAME)
        os.remove(download_target)
        os.rmdir(download_dir)

    @utils.TimeExecute(function_description="Downloading build on instance")
    def FetchBuild(self, build_id, branch, build_target, system_build_id,
                   system_branch, system_build_target, kernel_build_id,
                   kernel_branch, kernel_build_target, bootloader_build_id,
                   bootloader_branch, bootloader_build_target, ota_build_id,
                   ota_branch, ota_build_target):
        """Execute fetch_cvd on the remote instance to get Cuttlefish runtime files.

        Args:
            build_id: String of build id, e.g. "2263051", "P2804227"
            branch: String of branch name, e.g. "aosp-master"
            build_target: String of target name.
                          e.g. "aosp_cf_x86_64_phone-userdebug"
            system_build_id: String of the system image build id.
            system_branch: String of the system image branch name.
            system_build_target: String of the system image target name,
                                 e.g. "cf_x86_phone-userdebug"
            kernel_build_id: String of the kernel image build id.
            kernel_branch: String of the kernel image branch name.
            kernel_build_target: String of the kernel image target name,
            bootloader_build_id: String of the bootloader build id.
            bootloader_branch: String of the bootloader branch name.
            bootloader_build_target: String of the bootloader target name.
            ota_build_id: String of the otatools build id.
            ota_branch: String of the otatools branch name.
            ota_build_target: String of the otatools target name.

        Returns:
            List of string args for fetch_cvd.
        """
        timestart = time.time()
        fetch_cvd_args = ["-credential_source=gce"]
        fetch_cvd_build_args = self._build_api.GetFetchBuildArgs(
            build_id, branch, build_target, system_build_id, system_branch,
            system_build_target, kernel_build_id, kernel_branch,
            kernel_build_target, bootloader_build_id, bootloader_branch,
            bootloader_build_target, ota_build_id, ota_branch, ota_build_target)
        fetch_cvd_args.extend(fetch_cvd_build_args)

        self._ssh.Run("./fetch_cvd " + " ".join(fetch_cvd_args),
                      timeout=constants.DEFAULT_SSH_TIMEOUT)
        self._execution_time[_FETCH_ARTIFACT] = round(time.time() - timestart, 2)

    @utils.TimeExecute(function_description="Update instance's certificates")
    def UpdateCertificate(self):
        """Update webrtc default certificates of the remote instance.

        For trusting both the localhost and remote instance, the process will
        upload certificates(rootCA.pem, server.crt, server.key) and the mkcert
        tool from the client workstation to remote instance where running the
        mkcert with the uploaded rootCA file and replace the webrtc frontend
        default certificates for connecting to a remote webrtc AVD without the
        insecure warning.
        """
        local_cert_dir = os.path.join(os.path.expanduser("~"),
                                      constants.SSL_DIR)
        if mkcert.AllocateLocalHostCert():
            upload_files = []
            for cert_file in (constants.WEBRTC_CERTS_FILES +
                              [f"{constants.SSL_CA_NAME}.pem"]):
                upload_files.append(os.path.join(local_cert_dir,
                                                 cert_file))
            try:
                self._ssh.ScpPushFiles(upload_files, constants.WEBRTC_CERTS_PATH)
                self._ssh.Run(_TRUST_REMOTE_INSTANCE_COMMAND)
            except subprocess.CalledProcessError:
                logger.debug("Update WebRTC frontend certificate failed.")

    @utils.TimeExecute(function_description="Upload extra files to instance")
    def UploadExtraFiles(self, extra_files):
        """Upload extra files into GCE instance.

        Args:
            extra_files: List of namedtuple ExtraFile.

        Raises:
            errors.CheckPathError: The provided path doesn't exist.
        """
        for extra_file in extra_files:
            if not os.path.exists(extra_file.source):
                raise errors.CheckPathError(
                    "The path doesn't exist: %s" % extra_file.source)
            self._ssh.ScpPushFile(extra_file.source, extra_file.target)

    def GetSshConnectCmd(self):
        """Get ssh connect command.

        Returns:
            String of ssh connect command.
        """
        return self._ssh.GetBaseCmd(constants.SSH_BIN)

    def GetInstanceIP(self, instance=None):
        """Override method from parent class.

        It need to get the IP address in the common_operation. If the class
        already defind the ip address, return the ip address.

        Args:
            instance: String, representing instance name.

        Returns:
            ssh.IP object, that stores internal and external ip of the instance.
        """
        if self._ip:
            return self._ip
        return gcompute_client.ComputeClient.GetInstanceIP(
            self, instance=instance, zone=self._zone)

    def GetHostImageName(self, stable_image_name, image_family, image_project):
        """Get host image name.

        Args:
            stable_image_name: String of stable host image name.
            image_family: String of image family.
            image_project: String of image project.

        Returns:
            String of stable host image name.

        Raises:
            errors.ConfigError: There is no host image name in config file.
        """
        if stable_image_name:
            return stable_image_name

        if image_family:
            image_name = gcompute_client.ComputeClient.GetImageFromFamily(
                self, image_family, image_project)["name"]
            logger.debug("Get the host image name from image family: %s", image_name)
            return image_name

        raise errors.ConfigError(
            "Please specify 'stable_host_image_name' or 'stable_host_image_family'"
            " in config.")

    def SetStage(self, stage):
        """Set stage to know the create progress.

        Args:
            stage: Integer, the stage would like STAGE_INIT, STAGE_GCE.
        """
        self._stage = stage

    def _UpdateOpenWrtStatus(self, avd_spec):
        """Update the OpenWrt device status.

        Args:
            avd_spec: An AVDSpec instance.
        """
        self._openwrt = avd_spec.openwrt if avd_spec else False

    @property
    def all_failures(self):
        """Return all_failures"""
        return self._all_failures

    @property
    def execution_time(self):
        """Return execution_time"""
        return self._execution_time

    @property
    def stage(self):
        """Return stage"""
        return self._stage

    @property
    def openwrt(self):
        """Return openwrt"""
        return self._openwrt

    @property
    def build_api(self):
        """Return build_api"""
        return self._build_api
