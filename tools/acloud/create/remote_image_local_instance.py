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
r"""RemoteImageLocalInstance class.

Create class that is responsible for creating a local instance AVD with a
remote image.
"""
import logging
import os
import shutil
import subprocess
import sys

from acloud import errors
from acloud.create import create_common
from acloud.create import local_image_local_instance
from acloud.internal import constants
from acloud.internal.lib import android_build_client
from acloud.internal.lib import auth
from acloud.internal.lib import ota_tools
from acloud.internal.lib import utils
from acloud.setup import setup_common


logger = logging.getLogger(__name__)

# Download remote image variables.
_CUTTLEFISH_COMMON_BIN_PATH = "/usr/lib/cuttlefish-common/bin/"
_CONFIRM_DOWNLOAD_DIR = ("Download dir %(download_dir)s does not have enough "
                         "space (available space %(available_space)sGB, "
                         "require %(required_space)sGB).\nPlease enter "
                         "alternate path or 'q' to exit: ")
_HOME_FOLDER = os.path.expanduser("~")
# The downloaded image artifacts will take up ~8G:
#   $du -lh --time $ANDROID_PRODUCT_OUT/aosp_cf_x86_phone-img-eng.XXX.zip
#   422M
# And decompressed becomes 7.2G (as of 11/2018).
# Let's add an extra buffer (~2G) to make sure user has enough disk space
# for the downloaded image artifacts.
_REQUIRED_SPACE = 10

_SYSTEM_IMAGE_NAME_PATTERN = r"system\.img"
_SYSTEM_MIX_IMAGE_DIR = "mix_image_{build_id}"
_DOWNLOAD_MIX_IMAGE_NAME = "{build_target}-target_files-{build_id}.zip"


@utils.TimeExecute(function_description="Downloading Android Build image")
def DownloadAndProcessImageFiles(avd_spec):
    """Download the CF image artifacts and process them.

    To download rom images, Acloud would download the tool fetch_cvd that can
    help process mixed build images.

    Args:
        avd_spec: AVDSpec object that tells us what we're going to create.

    Returns:
        extract_path: String, path to image folder.

    Raises:
        errors.GetRemoteImageError: Fails to download rom images.
    """
    cfg = avd_spec.cfg
    build_id = avd_spec.remote_image[constants.BUILD_ID]
    build_branch = avd_spec.remote_image[constants.BUILD_BRANCH]
    build_target = avd_spec.remote_image[constants.BUILD_TARGET]

    extract_path = os.path.join(
        avd_spec.image_download_dir,
        constants.TEMP_ARTIFACTS_FOLDER,
        build_id + build_target)

    logger.debug("Extract path: %s", extract_path)

    if avd_spec.force_sync and os.path.exists(extract_path):
        shutil.rmtree(extract_path)
    if not os.path.exists(extract_path):
        os.makedirs(extract_path)
        build_api = (
            android_build_client.AndroidBuildClient(auth.CreateCredentials(cfg)))

        # Download rom images via fetch_cvd
        fetch_cvd = os.path.join(extract_path, constants.FETCH_CVD)
        build_api.DownloadFetchcvd(fetch_cvd, cfg.fetch_cvd_version)
        fetch_cvd_build_args = build_api.GetFetchBuildArgs(
            build_id, build_branch, build_target,
            avd_spec.system_build_info.get(constants.BUILD_ID),
            avd_spec.system_build_info.get(constants.BUILD_BRANCH),
            avd_spec.system_build_info.get(constants.BUILD_TARGET),
            avd_spec.kernel_build_info.get(constants.BUILD_ID),
            avd_spec.kernel_build_info.get(constants.BUILD_BRANCH),
            avd_spec.kernel_build_info.get(constants.BUILD_TARGET),
            avd_spec.bootloader_build_info.get(constants.BUILD_ID),
            avd_spec.bootloader_build_info.get(constants.BUILD_BRANCH),
            avd_spec.bootloader_build_info.get(constants.BUILD_TARGET),
            avd_spec.ota_build_info.get(constants.BUILD_ID),
            avd_spec.ota_build_info.get(constants.BUILD_BRANCH),
            avd_spec.ota_build_info.get(constants.BUILD_TARGET))
        creds_cache_file = os.path.join(_HOME_FOLDER, cfg.creds_cache_file)
        fetch_cvd_cert_arg = build_api.GetFetchCertArg(creds_cache_file)
        fetch_cvd_args = [fetch_cvd, "-directory=%s" % extract_path,
                          fetch_cvd_cert_arg]
        fetch_cvd_args.extend(fetch_cvd_build_args)
        logger.debug("Download images command: %s", fetch_cvd_args)
        try:
            subprocess.check_call(fetch_cvd_args)
        except subprocess.CalledProcessError as e:
            raise errors.GetRemoteImageError("Fails to download images: %s" % e)

    return extract_path


def ConfirmDownloadRemoteImageDir(download_dir):
    """Confirm download remote image directory.

    If available space of download_dir is less than _REQUIRED_SPACE, ask
    the user to choose a different download dir or to exit out since acloud will
    fail to download the artifacts due to insufficient disk space.

    Args:
        download_dir: String, a directory for download and decompress.

    Returns:
        String, Specific download directory when user confirm to change.
    """
    while True:
        download_dir = os.path.expanduser(download_dir)
        if not os.path.exists(download_dir):
            answer = utils.InteractWithQuestion(
                "No such directory %s.\nEnter 'y' to create it, enter "
                "anything else to exit out[y/N]: " % download_dir)
            if answer.lower() == "y":
                os.makedirs(download_dir)
            else:
                sys.exit(constants.EXIT_BY_USER)

        stat = os.statvfs(download_dir)
        available_space = stat.f_bavail*stat.f_bsize/(1024)**3
        if available_space < _REQUIRED_SPACE:
            download_dir = utils.InteractWithQuestion(
                _CONFIRM_DOWNLOAD_DIR % {"download_dir":download_dir,
                                         "available_space":available_space,
                                         "required_space":_REQUIRED_SPACE})
            if download_dir.lower() == "q":
                sys.exit(constants.EXIT_BY_USER)
        else:
            return download_dir


def GetMixBuildTargetFilename(build_target, build_id):
    """Get the mix build target filename.

    Args:
        build_id: String, Build id, e.g. "2263051", "P2804227"
        build_target: String, the build target, e.g. cf_x86_phone-userdebug

    Returns:
        String, a file name, e.g. "cf_x86_phone-target_files-2263051.zip"
    """
    return _DOWNLOAD_MIX_IMAGE_NAME.format(
        build_target=build_target.split('-')[0],
        build_id=build_id)


class RemoteImageLocalInstance(local_image_local_instance.LocalImageLocalInstance):
    """Create class for a remote image local instance AVD.

    RemoteImageLocalInstance just defines logic in downloading the remote image
    artifacts and leverages the existing logic to launch a local instance in
    LocalImageLocalInstance.
    """

    def GetImageArtifactsPath(self, avd_spec):
        """Download the image artifacts and return the paths to them.

        Args:
            avd_spec: AVDSpec object that tells us what we're going to create.

        Raises:
            errors.NoCuttlefishCommonInstalled: cuttlefish-common doesn't install.

        Returns:
            local_image_local_instance.ArtifactPaths object.
        """
        if not setup_common.PackageInstalled("cuttlefish-common"):
            raise errors.NoCuttlefishCommonInstalled(
                "Package [cuttlefish-common] is not installed!\n"
                "Please run 'acloud setup --host' to install.")

        avd_spec.image_download_dir = ConfirmDownloadRemoteImageDir(
            avd_spec.image_download_dir)

        image_dir = DownloadAndProcessImageFiles(avd_spec)
        launch_cvd_path = os.path.join(image_dir, "bin",
                                       constants.CMD_LAUNCH_CVD)
        if not os.path.exists(launch_cvd_path):
            raise errors.GetCvdLocalHostPackageError(
                "No launch_cvd found. Please check downloaded artifacts dir: %s"
                % image_dir)

        mix_image_dir = None
        if avd_spec.local_system_image:
            build_id = avd_spec.remote_image[constants.BUILD_ID]
            build_target = avd_spec.remote_image[constants.BUILD_TARGET]
            mix_image_dir =os.path.join(
                image_dir, _SYSTEM_MIX_IMAGE_DIR.format(build_id=build_id))
            if not os.path.exists(mix_image_dir):
                os.makedirs(mix_image_dir)
                create_common.DownloadRemoteArtifact(
                    avd_spec.cfg, build_target, build_id,
                    GetMixBuildTargetFilename(build_target, build_id),
                    mix_image_dir, decompress=True)
            misc_info_path = super().FindMiscInfo(mix_image_dir)
            mix_image_dir = super().FindImageDir(mix_image_dir)
            tool_dirs = (avd_spec.local_tool_dirs +
                         create_common.GetNonEmptyEnvVars(
                             constants.ENV_ANDROID_SOONG_HOST_OUT,
                             constants.ENV_ANDROID_HOST_OUT))
            ota_tools_dir = os.path.abspath(
                ota_tools.FindOtaToolsDir(tool_dirs))
            system_image_path = create_common.FindLocalImage(
                avd_spec.local_system_image, _SYSTEM_IMAGE_NAME_PATTERN)
        else:
            misc_info_path = None
            ota_tools_dir = None
            system_image_path = None

        # This method does not set the optional fields because launch_cvd loads
        # the paths from the fetcher config in image_dir.
        return local_image_local_instance.ArtifactPaths(
            image_dir=mix_image_dir or image_dir,
            host_bins=image_dir,
            host_artifacts=image_dir,
            misc_info=misc_info_path,
            ota_tools_dir=ota_tools_dir,
            system_image=system_image_path,
            boot_image=None,
            vendor_boot_image=None)
