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

"""This module implements the classes and functions needed for the common
creation flow."""

import re

from acloud.internal import constants
from acloud.internal.lib import ssh
from acloud.public import config


_INSTANCE_NAME_FORMAT = ("host-goldfish-%(ip_addr)s-%(console_port)s-"
                         "%(build_id)s-%(build_target)s")
_INSTANCE_NAME_PATTERN = re.compile(
    r"host-goldfish-(?P<ip_addr>[\d.]+)-(?P<console_port>\d+)-.+")
# Report keys
_VERSION = "version"


def FormatInstanceName(ip_addr, console_port, build_info):
    """Convert address and build info to an instance name.

    Args:
        ip_addr: A string, the IP address of the host.
        console_port: An integer, the emulator console port.
        build_info: A dict containing the build ID and target.

    Returns:
        A string, the instance name.
    """
    return _INSTANCE_NAME_FORMAT % {
        "ip_addr": ip_addr,
        "console_port": console_port,
        "build_id": build_info.get(constants.BUILD_ID),
        "build_target": build_info.get(constants.BUILD_TARGET)}


def ParseEmulatorConsoleAddress(instance_name):
    """Parse emulator console address from an instance name.

    Args:
        instance_name: A string, the instance name.

    Returns:
        The IP address as a string and the console port as an integer.
        None if the name does not represent a goldfish instance on remote host.
    """
    match = _INSTANCE_NAME_PATTERN.fullmatch(instance_name)
    return ((match.group("ip_addr"), int(match.group("console_port")))
            if match else None)


class GoldfishRemoteHostClient:
    """A client that manages goldfish instance on a remote host."""

    @staticmethod
    def GetInstanceIP(instance_name):
        """Parse the IP address from an instance name."""
        ip_and_port = ParseEmulatorConsoleAddress(instance_name)
        if not ip_and_port:
            raise ValueError("Cannot parse instance name: %s" % instance_name)
        return ssh.IP(ip=ip_and_port[0])

    @staticmethod
    def WaitForBoot(_instance_name, _boot_timeout_secs):
        """Should not be called in the common creation flow."""
        raise NotImplementedError("The common creation flow should call "
                                  "GetFailures instead of this method.")

    @staticmethod
    def GetSerialPortOutput():
        """Remote hosts do not support serial log."""
        return ""

    @property
    def dict_report(self):
        """Return the key-value pairs to be written to the report."""
        return {_VERSION: config.GetVersion()}
