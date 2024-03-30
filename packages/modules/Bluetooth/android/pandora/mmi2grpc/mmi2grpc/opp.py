# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""OPP proxy module."""

from typing import Optional

from mmi2grpc._helpers import assert_description
from mmi2grpc._proxy import ProfileProxy

from pandora_experimental.host_grpc import Host
from pandora_experimental.host_pb2 import Connection
from pandora_experimental._android_grpc import Android
from pandora_experimental._android_pb2 import AccessType


class OPPProxy(ProfileProxy):
    """OPP proxy.

    Implements OPP PTS MMIs.
    """
    connection: Optional[Connection] = None

    def __init__(self, channel):
        super().__init__(channel)

        self.host = Host(channel)
        self._android = Android(channel)

        self.connection = None

    @assert_description
    def TSC_OBEX_MMI_iut_accept_connect_OPP(self, pts_addr: bytes, **kwargs):
        """
        Please accept the OBEX CONNECT REQ command for OPP.
        """
        if self.connection is None:
            self.connection = self.host.WaitConnection(address=pts_addr).connection

        return "OK"

    @assert_description
    def TSC_OPP_mmi_user_action_remove_object(self, **kwargs):
        """
        If necessary take action to remove any file(s) named 'BC_BV01.bmp' from
        the IUT.  

        Press 'OK' to confirm that the file is not present on the
        IUT.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_put(self, **kwargs):
        """
         Please accept the PUT REQUEST.
        """
        self._android.AcceptIncomingFile()

        return "OK"

    @assert_description
    def TSC_OPP_mmi_user_verify_does_object_exist(self, **kwargs):
        """
        Does the IUT now contain the following files?

        BC_BV01.bmp

        Note: If
        TSPX_supported_extension is not .bmp, the file content of the file will
        not be formatted for the TSPX_supported extension, this is normal.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_slc_connect_l2cap(self, pts_addr: bytes, **kwargs):
        """
        Please accept the l2cap channel connection for an OBEX connection.
        """
        self.connection = self.host.WaitConnection(address=pts_addr).connection

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_reject_action(self, **kwargs):
        """
         Take action to reject the ACTION command sent by PTS.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_disconnect(self, **kwargs):
        """
         Please accept the OBEX DISCONNECT REQ command.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_slc_disconnect(self, **kwargs):
        """
         Please accept the disconnection of the transport channel.
        """

        return "OK"
